"use client";
import { deriveVault, open, seal } from "./crypto";
import { db, getMeta, mergeSnapshot, setMeta, snapshot, type Snapshot } from "./db";
import { commitSigners, mergeCircles } from "./member-keys";
import type { Circle } from "./circles";

/** The vault blob: health data plus circle state (owner circles; owner keys this account pinned as a member).
 * Older app versions ignore the extra fields; the next push from this version restores them. */
type Vault = Snapshot & { circles?: Circle[]; circlePins?: Record<string, string> };

/** Read-modify-write of one meta key in one Dexie transaction, so concurrent writers cannot lose each other's
 * update. fn must be synchronous (an await on non-Dexie work would end the transaction). */
export const updateMeta = <T>(key: string, fallback: T, fn: (cur: T) => T) =>
  db.transaction("rw", db.meta, async () => {
    const cur = ((await db.meta.get(key))?.value as T | undefined) ?? fallback, next = fn(cur);
    if (next !== cur) await db.meta.put({ key, value: next });
    return next;
  });

async function mergeCircleState(v: Vault) {
  let n = 0;
  const count = <T>(cur: T, next: T) => { if (JSON.stringify(cur) !== JSON.stringify(next)) { n++; return next; } return cur; };
  if (v.circles?.length) await updateMeta<Circle[]>("circles", [], (cur) => count(cur, mergeCircles(cur, v.circles!)));
  // A pin set on this device (from an invite link) wins over a synced one.
  if (v.circlePins) await updateMeta<Record<string, string>>("circlePins", {}, (cur) => count(cur, { ...v.circlePins, ...cur }));
  return n;
}
/** What this push sends. A signer is committed once it is in the vault, so the pushed copy drops `pending`. */
const circleState = async () => ({
  circles: (await getMeta<Circle[]>("circles", [])).map((c) => (c.sign?.pending ? { ...c, sign: { pub: c.sign.pub, priv: c.sign.priv, createdAt: c.sign.createdAt } } : c)),
  circlePins: await getMeta<Record<string, string>>("circlePins", {}),
}) satisfies Pick<Vault, "circles" | "circlePins">;

export type SyncResult = { ok: true; pulled: number; rev: number } | { ok: false; error: string };

/** Pull → LWW merge → push with optimistic revision. Retries on 409 (another device pushed first). */
export async function syncNow(secret: string): Promise<SyncResult> {
  try {
    const v = await deriveVault(secret);
    const headers = { "content-type": "application/json", authorization: `Bearer ${v.auth}` };
    let pulled = 0;
    for (let attempt = 0; attempt < 3; attempt++) {
      const res = await fetch(`/api/vault/${v.vaultId}`, { headers });
      if (res.status === 403) return { ok: false, error: "Sync key rejected" };
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