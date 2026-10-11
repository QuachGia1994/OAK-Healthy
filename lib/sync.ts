"use client";
import { deriveVault, open, seal } from "./crypto";
import { getMeta, mergeSnapshot, setMeta, snapshot, type Snapshot } from "./db";

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
        pulled += await mergeSnapshot(await open<Snapshot>(v.key, body.blob));
      } else if (res.status !== 404) return { ok: false, error: `Server ${res.status}` };
      const blob = await seal(v.key, await snapshot());
      const put = await fetch(`/api/vault/${v.vaultId}`, { method: "PUT", headers, body: JSON.stringify({ baseRev: rev, blob }) });
      if (put.status === 409) continue;
      if (!put.ok) return { ok: false, error: `Push failed ${put.status}` };
      const out = (await put.json()) as { rev: number };
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