// Reminder rules shared by the app (lib/reminders.ts), the worker (snooze bounds) and tests.
import type { Routine } from "./types";

export const SNOOZE_MIN = 5, SNOOZE_MAX = 180;
export const okSnooze = (n: unknown): n is number => Number.isInteger(n) && (n as number) >= SNOOZE_MIN && (n as number) <= SNOOZE_MAX;

// Browser push services only (no SSRF to arbitrary hosts). Chrome also issues endpoints on *.google.com (e.g. jmt17.google.com).
const PUSH_HOSTS = ["fcm.googleapis.com", "android.googleapis.com", "google.com", "updates.push.services.mozilla.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com"];
export const okEndpoint = (e: unknown): e is string => {
  if (typeof e !== "string" || e.length > 1000) return false;
  try { const u = new URL(e); return u.protocol === "https:" && !u.port && PUSH_HOSTS.some((h) => u.hostname === h || u.hostname.endsWith("." + h)); } catch { return false; }
};

type R = Pick<Routine, "deletedAt" | "paused"> & { remind?: boolean };
/** Active, not paused, and the per-routine reminder toggle is on (missing = on, for older data). */
export const remindable = (r: R) => !r.deletedAt && !r.paused && r.remind !== false;

/** Local HH:mm of remindable routines → sorted unique UTC minute-of-day, for a getTimezoneOffset() value. */
export function utcMinutes(rs: (R & Pick<Routine, "time">)[], offset: number) {
  const set = new Set<number>();
  for (const r of rs) {
    if (!remindable(r)) continue;
    const [h, m] = r.time.split(":").map(Number);
    if (Number.isFinite(h) && Number.isFinite(m)) set.add((((h * 60 + m + offset) % 1440) + 1440) % 1440);
  }
  return [...set].sort((a, b) => a - b);
}
