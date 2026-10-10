// Local-day codec. A date-only value is a literal calendar day in the user's local zone (HEALTH_DATA_FLOW § Date/time semantics).
export function toLocalDay(d: Date): string {
  const y = d.getFullYear(), m = d.getMonth() + 1, day = d.getDate();
  return `${y}-${String(m).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

export function parseLocalDay(s: string): Date {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** Half-open [start, end) epoch window of a local day; DST days may be 23 or 25 hours. */
export function dayWindow(day: string): [number, number] {
  const start = parseLocalDay(day);
  const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 1);
  return [start.getTime(), end.getTime()];
}

export function addDays(day: string, n: number): string {
  const d = parseLocalDay(day);
  return toLocalDay(new Date(d.getFullYear(), d.getMonth(), d.getDate() + n));
}

/** Whole calendar days between two local days (b - a), DST-safe. */
export function daysBetween(a: string, b: string): number {
  const ua = Date.UTC(...(a.split("-").map(Number) as [number, number, number]).map((v, i) => (i === 1 ? v - 1 : v)) as [number, number, number]);
  const ub = Date.UTC(...(b.split("-").map(Number) as [number, number, number]).map((v, i) => (i === 1 ? v - 1 : v)) as [number, number, number]);
  return Math.round((ub - ua) / 86_400_000);
}

export function scheduledAt(day: string, time: string): number {
  const d = parseLocalDay(day);
  const [h, m] = time.split(":").map(Number);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), h || 0, m || 0).getTime();
}
