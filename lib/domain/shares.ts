// Share links card in Settings (docs/feat/delete-everything.md): the list, deleting one link, and the meta
// `sharesGone` ids that keep a deleted link from coming back when another device merges its list (contract C3).
import { isRefused, shareDeleteOutcome, type ShareRef } from "./wipe.ts";

/** A share link deleted on some device: only the id, never the token. A refused link (C4) stays in `shares` with refusedAt;
 * only the wipe may write its id here (D8). */
export interface ShareGone { id: string; at: number }
/** Longest stored title, in characters (code points). */
export const SHARE_TITLE_MAX = 120;
/** A gone id is kept this long, past the longest share life (365 d), then dropped. */
export const SHARE_GONE_TTL_MS = 400 * 864e5;
/** An entry leaves the list 30 days after its expiresAt: the sync merge drops it (lib/sync.ts, public/sw.js mirror), and
 * the card hides it too, so a device with sync off also stops showing it. */
export const SHARE_GRACE_MS = 30 * 864e5;
/** The one share-id shape (worker validId; public/sw.js keeps a mirror). */
export const SHARE_ID_RE = /^[A-Za-z0-9_-]{8,64}$/;
export const isShareId = (id: unknown): id is string => typeof id === "string" && SHARE_ID_RE.test(id);

/** The list name as a share title: trimmed, cut to SHARE_TITLE_MAX characters; blank → undefined. */
export function shareTitle(name: unknown): string | undefined {
  if (typeof name !== "string") return undefined;
  const t = [...name.trim()].slice(0, SHARE_TITLE_MAX).join("").trim();
  return t || undefined;
}

/** A bad title (not a string, empty, over SHARE_TITLE_MAX) or createdAt (not a finite number) drops only that field.
 * C4 refusedAt (the server refused this entry's DELETE for good): kept when isRefused (any finite number), clamped to now;
 * else only it is dropped. */
export function cleanShareFields<T extends ShareRef>(e: T, now: number): T {
  const out = { ...e };
  if (out.refusedAt !== undefined) {
    if (!isRefused(out)) delete out.refusedAt;
    else out.refusedAt = Math.min(out.refusedAt, now);
  }
  const t = out.title as unknown;
  if (t !== undefined && (typeof t !== "string" || !t || [...t].length > SHARE_TITLE_MAX)) delete out.title;
  const c = out.createdAt as unknown;
  if (c !== undefined && (typeof c !== "number" || !Number.isFinite(c))) delete out.createdAt;
  return out;
}

/** meta `sharesGone` as stored or pulled: entries with a share-id-shaped id and a finite time (clamped to now);
 * ones older than SHARE_GONE_TTL_MS are dropped; anything not a list is []. */
export function parseSharesGone(raw: unknown, now: number): ShareGone[] {
  if (!Array.isArray(raw)) return [];
  const out: ShareGone[] = [];
  for (const g of raw) {
    if (!g || typeof g !== "object") continue;
    const { id, at } = g as { id?: unknown; at?: unknown };
    if (!isShareId(id) || typeof at !== "number" || !Number.isFinite(at)) continue;
    const t = Math.min(at, now);
    if (t + SHARE_GONE_TTL_MS < now) continue;
    out.push({ id, at: t });
  }
  return out;
}

/** Removes the share from the list and records its id as gone; an id already gone keeps its first time. */
export function forgetShare(shares: ShareRef[], gone: ShareGone[], id: string, at: number) {
  return {
    shares: shares.filter((s) => s.id !== id),
    gone: gone.some((g) => g.id === id) ? gone : [...gone, { id, at }],
  };
}

/** C4: marks the entry refused (the server will never delete it); the first time stays. It stays in `shares`, never in `sharesGone`. */
export function markRefused(shares: ShareRef[], id: string, at: number): ShareRef[] {
  // Only an entry still listed and not yet marked changes; otherwise the same array (no write, never re-adds one).
  return shares.some((s) => s.id === id && !isRefused(s)) ? shares.map((s) => (s.id === id ? { ...s, refusedAt: at } : s)) : shares;
}

export interface ShareRow { id: string; title: string | null; createdAt: number | null; expiresAt: number; expired: boolean; refused: boolean }
/** Rows for the card: gone ids and entries past the 30 d grace hidden; newest created first, entries without a created
 * date (2.8) last, ties by later expiry. refused (C4): shown as "Couldn't be deleted", no Delete. */
export function shareRows(shares: ShareRef[], gone: ShareGone[], now: number): ShareRow[] {
  const g = new Set(gone.map((x) => x.id));
  return shares
    .filter((s) => !g.has(s.id) && s.expiresAt + SHARE_GRACE_MS >= now)
    .map((s) => {
      const c = cleanShareFields(s, now);
      return { id: s.id, title: c.title ?? null, createdAt: c.createdAt ?? null, expiresAt: s.expiresAt, expired: s.expiresAt <= now, refused: c.refusedAt !== undefined };
    })
    .sort((a, b) => (b.createdAt ?? -Infinity) - (a.createdAt ?? -Infinity) || b.expiresAt - a.expiresAt);
}

export interface DeleteOneDeps {
  shares(): Promise<ShareRef[]>;
  /** The share DELETE; throws when the request fails (offline). */
  request(s: ShareRef): Promise<{ status: number }>;
  /** forgetShare in meta, one transaction (2xx only, C3). */
  forget(id: string): Promise<void>;
  /** markRefused in meta, one write (C4). */
  refuse(id: string): Promise<void>;
}
/** failed.tooMany: the server's 429; any other failure (offline, 5xx) carries no text, so no raw fetch/HTTP message reaches the UI. */
export type DeleteOneResult = { outcome: "deleted" } | { outcome: "kept"; expiresAt: number } | { outcome: "failed"; tooMany: boolean };

/** Deletes one share link. 200: deleted (gone). 400/401/403/405 (shareDeleteOutcome "kept"): the server will never delete
 * it, so it stays listed, marked refused (C4), and the UI says when it expires; an entry already marked sends nothing. Offline, 429, 5xx: stays listed (tooMany marks the 429). */
export async function deleteOne(d: DeleteOneDeps, id: string): Promise<DeleteOneResult> {
  const s = (await d.shares()).find((x) => x.id === id);
  if (!s) return { outcome: "deleted" }; // already gone (another tab)
  if (isRefused(s)) return { outcome: "kept", expiresAt: s.expiresAt };
  let status: number;
  try { status = (await d.request(s)).status; } catch { return { outcome: "failed", tooMany: false }; }
  const o = shareDeleteOutcome(status);
  if (o === "retry") return { outcome: "failed", tooMany: status === 429 };
  if (o === "ok") { await d.forget(id); return { outcome: "deleted" }; }
  await d.refuse(id);
  return { outcome: "kept", expiresAt: s.expiresAt };
}
