"use client";
import { deriveVault, open, seal } from "./crypto";
import { db, getMeta, mergeSnapshot, setMeta, snapshot, type Snapshot } from "./db";
import { commitSigners, pullCircleState, pushCircleState, type VaultCircles } from "./member-keys";
import type { Circle } from "./circles";
import type { ShareRef } from "./domain/wipe";

/** The vault blob: health data plus circle state (lib/member-keys.ts VaultCircles) and the share list (meta `shares`).
 * Older app versions ignore the extra fields; the next push from this version restores them. */
type Vault = Snapshot & Partial<VaultCircles<Circle>> & { shares?: unknown };

/** Contract C1 (lead D7): expiresAt is the server's; an entry leaves the list 30 days after it, by this device's clock. */
const SHARE_GRACE_MS = 30 * 864e5;
/** id: the worker's validId shape (worker/util.ts); token: at least the worker's 20-character floor for a bearer. */
const isShareRef = (x: unknown): x is ShareRef => {
  const s = x as ShareRef | null;
  return !!s && typeof s.id === "string" && /^[A-Za-z0-9_-]{8,64}$/.test(s.id) && typeof s.token === "string" && s.token.length >= 20
    && typeof s.expiresAt === "number" && Number.isFinite(s.expiresAt);
};
/** Union by id: this device's entry (token) with the later expiry; entries failing isShareRef are dropped on both sides.
 * Mirrored by public/sw.js vaultShares (tests/fixtures/vault-circles.ts vaultShareCases). */
function mergeShares(local: ShareRef[], remote: unknown, now: number): ShareRef[] {
  const byId = new Map(local.filter(isShareRef).map((s) => [s.id, s]));
  for (const r of Array.isArray(remote) ? remote.filter(isShareRef) : []) {
    const cur = byId.get(r.id);
    byId.set(r.id, cur ? { ...cur, expiresAt: Math.max(cur.expiresAt, r.expiresAt) } : { id: r.id, token: r.token, expiresAt: r.expiresAt });
  }
  return [...byId.values()].filter((s) => s.expiresAt + SHARE_GRACE_MS >= now);
}

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

/** Contract C2 (lead D9): the vault id this device has seen exist (a 2xx). The worker's 404 on that id means the tombstone
 * was purged after 365 days, so it is gone like a 410; on any other id it is a first sync and creates the vault. */
const SYNCED_VAULT = "syncedVault";
const workerNotFound = async (res: Response) => ((await res.json().catch(() => null)) as { error?: unknown } | null)?.error === "not found";

/** Pull → LWW merge → push with optimistic revision. Retries on 409 (another device pushed first). */
export async function syncNow(secret: string, now = Date.now()): Promise<SyncResult> {
  try {
    const v = await deriveVault(secret);
    const headers = { "content-type": "application/json", authorization: `Bearer ${v.auth}` };
    let pulled = 0;
    for (let attempt = 0; attempt < 3; attempt++) {
      const res = await fetch(`/api/vault/${v.vaultId}`, { headers });
      if (res.status === 403) return { ok: false, error: "Sync key rejected" };
      if (res.status === VAULT_GONE) return vaultGone();
      let rev = 0, remote: Vault | null = null;
      if (res.ok) {
        const body = (await res.json()) as { rev: number; blob: string };
        rev = body.rev;
        remote = await open<Vault>(v.key, body.blob);
        pulled += (await mergeSnapshot(remote)) + (await mergeCircleState(remote));
        await setMeta(SYNCED_VAULT, v.vaultId);
      } else if (res.status !== 404 || !(await workerNotFound(res))) return { ok: false, error: `Server ${res.status}` };
      else if ((await getMeta<string | null>(SYNCED_VAULT, null)) === v.vaultId) return vaultGone();
      const circles = await circleState();
      const shares = await updateMeta<ShareRef[]>("shares", [], (cur) => mergeShares(cur, remote?.shares, now));
      const blob = await seal(v.key, { ...(await snapshot()), ...circles, shares } satisfies Vault);
      const put = await fetch(`/api/vault/${v.vaultId}`, { method: "PUT", headers, body: JSON.stringify({ baseRev: rev, blob }) });
      if (put.status === 409) continue;
      if (put.status === VAULT_GONE) return vaultGone();
      if (!put.ok) return { ok: false, error: `Push failed ${put.status}` };
      const out = (await put.json()) as { rev: number };
      await setMeta(SYNCED_VAULT, v.vaultId);
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
/** Every caller is an explicit enable, a key change or sync off, so the remembered vault id resets (C2: a restored or
 * re-created vault can be synced again). */
export const setSyncKey = async (k: string | null) => { await setMeta(SYNCED_VAULT, null); await setMeta("syncKey", k); };