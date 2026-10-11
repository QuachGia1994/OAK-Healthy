"use client";
import { deriveVault, open, seal } from "./crypto";
import { db, getMeta, mergeSnapshot, setMeta, snapshot, type Snapshot } from "./db";
import { commitSigners, pullCircleState, pushCircleState, type VaultCircles } from "./member-keys";
import type { Circle } from "./circles";
import type { ShareRef } from "./domain/wipe";
import { cleanShareFields, isShareId, parseSharesGone, SHARE_GRACE_MS, type ShareGone } from "./domain/shares";

/** The share list (meta `shares`, contract C1) and its tombstones (meta `sharesGone`, contract C3), as meta or vault fields. */
type ShareLists = { shares?: unknown; sharesGone?: unknown };
/** The vault blob: health data plus circle state (lib/member-keys.ts VaultCircles) and the share lists.
 * Older app versions ignore the extra fields; the next push from this version restores them. */
type Vault = Snapshot & Partial<VaultCircles<Circle>> & ShareLists;

const list = (x: unknown): unknown[] => (Array.isArray(x) ? x : []);
/** A valid entry with only its known fields, or null. token: at least the worker's 20-character bearer floor; a bad
 * title, createdAt or refusedAt is dropped by cleanShareFields and the entry kept (2.8 entries have none of them). */
function shareEntry(x: unknown, now: number): ShareRef | null {
  const s = x as Partial<ShareRef> | null;
  if (!s || !isShareId(s.id) || typeof s.token !== "string" || s.token.length < 20
    || typeof s.expiresAt !== "number" || !Number.isFinite(s.expiresAt)) return null;
  const e: ShareRef = { id: s.id, token: s.token, expiresAt: s.expiresAt };
  if (s.title !== undefined) e.title = s.title;
  if (s.createdAt !== undefined) e.createdAt = s.createdAt;
  if (s.refusedAt !== undefined) e.refusedAt = s.refusedAt;
  return cleanShareFields(e, now);
}
/** Tombstones (parseSharesGone: valid, clamped to now, 400 days): union by id with the earliest `at`. Entries: union by
 * id, per field this device's first (token, title, createdAt, refusedAt), else the vault's, with the later expiry; none whose id
 * has a tombstone. Mirrored by public/sw.js vaultShares (tests/fixtures/vault-circles.ts vaultShareCases). */
function mergeShareLists(local: ShareLists, remote: ShareLists, now: number) {
  const gone = new Map<string, ShareGone>();
  for (const g of [...parseSharesGone(local.sharesGone, now), ...parseSharesGone(remote.sharesGone, now)]) {
    const cur = gone.get(g.id);
    if (!cur || g.at < cur.at) gone.set(g.id, g);
  }
  const byId = new Map<string, ShareRef>();
  for (const s of list(local.shares).map((x) => shareEntry(x, now))) if (s) byId.set(s.id, s);
  for (const r of list(remote.shares).map((x) => shareEntry(x, now))) {
    const cur = r && byId.get(r.id);
    if (r) byId.set(r.id, cur ? { ...r, ...cur, expiresAt: Math.max(cur.expiresAt, r.expiresAt) } : r);
  }
  const shares = [...byId.values()].filter((s) => !gone.has(s.id) && s.expiresAt + SHARE_GRACE_MS >= now);
  return { shares, sharesGone: [...gone.values()] };
}
/** Both share keys in one transaction: a delete writes both (C3), so neither half of it can be lost to this merge. */
const mergeShareMeta = (remote: ShareLists, now: number) =>
  db.transaction("rw", db.meta, async () => {
    const next = mergeShareLists({ shares: (await db.meta.get("shares"))?.value, sharesGone: (await db.meta.get("sharesGone"))?.value }, remote, now);
    await db.meta.put({ key: "shares", value: next.shares });
    await db.meta.put({ key: "sharesGone", value: next.sharesGone });
    return next;
  });

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
      const shareLists = await mergeShareMeta(remote ?? {}, now);
      const blob = await seal(v.key, { ...(await snapshot()), ...circles, ...shareLists } satisfies Vault);
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