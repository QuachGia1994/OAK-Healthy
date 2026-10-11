/** A next= target is followed only when the URL parser itself resolves it to our origin (it treats "\" as "/" and
 * strips tabs, so prefix checks are not enough). Returns the same-origin path to open, or null. */
export function safeNext(next: string, origin: string, current: string): string | null {
  if (!next) return null;
  let u: URL;
  try { u = new URL(next, origin); } catch { return null; }
  if (u.origin !== origin || u.pathname === current) return null;
  return u.pathname + u.search + u.hash;
}
