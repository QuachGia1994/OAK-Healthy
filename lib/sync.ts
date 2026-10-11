"use client";
import { deriveVault, open, seal } from "./crypto";
import { db, getMeta, mergeSnapshot, setMeta, snapshot, type Snapshot } from "./db";
import { commitSigners, pullCircleState, pushCircleState, type VaultCircles } from "./member-keys";
import type { Circle } from "./circles";

/** The vault blob: health data plus circle state (lib/member-keys.ts VaultCircles). Older app versions ignore the extra
 * fields; the next push from this version restores them. */
type Vault = Snapshot & Partial<VaultCircles<Circle>>;

/** Read-modify-write of one meta key in one Dexie transaction, so concurrent writers cannot lose each other's
 * update. fn must be synchronous (an await on non-Dexie work would end the transaction). */
export const updateMeta = <T>(key: string, fallback: T, fn: (cur: T) => T) =>
  db.transaction("rw", db.meta, async () => {
    const cur = ((await db.meta.get(key))?.value as T | undefined) ?? fallback, next = fn(cur);
    if (next !== cur) await db.meta.put({ key, value: next });
    return next;
  });

/** Circle meta, one key per vault field. */
const readCircleState = async (): Promise<VaultCircles<Circle>> => ({
  circles: await getMeta<Circle[]>("circles", []),
  circlePins: await getMeta<Record<string, string>>("circlePins", {}),
  circleSeen: await getMeta<Record<string, number>>("circleSeen", {}),
});
/** Merge the pulled vault's circle state into meta in one transaction; returns how many fields changed. */
const mergeCircleState = (v: Vault) =>
  db.transaction("rw", db.meta, async () => {
    const cur = await readCircleState(), next = pullCircleState(cur, v);
    const changed = (Object.keys(next) as (keyof VaultCircles)[]).filter((k) => JSON.stringify(cur[k]) !== JSON.stringify(next[k]));
    for (const k of changed) await db.meta.put({ key: k, value: next[k] });
    return changed.length;
  });
const circleState = async () => pushCircleState(await readCircleState());

export type SyncResult = { ok: true; pulled: number; rev: number } | { ok: false; error: string; gone?: true };

/** 410: the vault was deleted ("Delete everything" on another device left a tombstone). Turn sync off here instead of
 * pushing this device's copy back up. */
const VAULT_GONE = 410;
async function vaultGone(): Promise<SyncResult> {
  await setSyncKey(null);
  return { ok: false, gone: true, error: "Your synced data was deleted on another device, so sync is off here." };
}

/** Pull → LWW merge → push with optimistic revision. Retries on 409 (another device pushed first). */
export async function syncNow(secret: string): Promise<SyncResult> {
  try {
    const v = await deriveVault(secret);
    const headers = { "content-type": "application/json", authorization: `Bearer ${v.auth}` };
    let pulled = 0;
    for (let attempt = 0; attempt < 3; attempt++) {
      const res = await fetch(`/api/vault/${v.vaultId}`, { headers });
      if (res.status === 403) return { ok: false, error: "Sync key rejected" };
      if (res.status === VAULT_GONE) return vaultGone();
      let rev = 0;
      if (res.ok) {
        const body = (await res.json()) as { rev: number; blob: string };
        rev = body.rev;
        const remote = await open<Vault>(v.key, body.blob);
        pulled += (await mergeSnapshot(remote)) + (await mergeCircleState(remote));
      } else if (res.status !== 404) return { ok: false, error: `Server ${res.status}` };
      const circles = await circleState();
      const blob = await seal(v.key, { ...(await snapshot()), ...circles } satisfies Vault);
      const put = await fetch(`/api/vault/${v.vaultId}`, { method: "PUT", headers, body: JSON.stringify({ baseRev: rev, blob }) });
      if (put.status === 409) continue;
      if (put.status === VAULT_GONE) return vaultGone();
      if (!put.ok) return { ok: false, error: `Push failed ${put.status}` };
      const out = (await put.json()) as { rev: number };
      await updateMeta<Circle[]>("circles", [], (list) => commitSigners(list, circles.circles));
      await setMeta("lastSync", Date.now());
      return { ok: true, pulled, rev: out.rev };
    }
    return { ok: false, error: "Conflict, try again" };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** Ask the service worker to retry sync when connectivity returns (Background Sync API; Chromium browsers).
 * Elsewhere this is a no-op and the online/focus listeners in SyncAgent cover it. */
export async function requestBackgroundSync(): Promise<boolean> {
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    const sync = (reg as (ServiceWorkerRegistration & { sync?: { register(tag: string): Promise<void> } }) | undefined)?.sync;
    if (!sync) return false;
    await sync.register("oak-sync");
    return true;
  } catch { return false; }
}

export const getSyncKey = () => getMeta<string | null>("syncKey", null);
export const setSyncKey = (k: string | null) => setMeta("syncKey", k);