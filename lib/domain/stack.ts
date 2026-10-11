import type { Biomarker, Intake, Routine } from "./types.ts";
import { addDays, daysBetween } from "./day.ts";
import { cycleStatus, isExpired } from "./cycle.ts";
import { daysOfStockLeft, dosesForDay, slotOf } from "./schedule.ts";
import { analyze, findReference } from "./knowledge.ts";

// ---------- Stack table: status + sorting ----------
export type RoutineStatus = "active" | "off" | "paused" | "ended";
export function routineStatus(r: Routine, today: string): RoutineStatus {
  if (r.paused) return "paused";
  if (isExpired(r.startDate, r.cycle, today)) return "ended";
  return cycleStatus(r.startDate, r.cycle, today) === "ON" ? "active" : "off";
}

export type SortKey = "name" | "time" | "cycle" | "stock" | "status";
export type SortDir = "asc" | "desc";
const STATUS_ORDER: Record<RoutineStatus, number> = { active: 0, off: 1, paused: 2, ended: 3 };

/** Stable sort for the stack table. Untracked values (null) always sink to the bottom, whatever the direction. */
export function sortRoutines(rows: Routine[], key: SortKey, dir: SortDir, today: string): Routine[] {
  const val = (r: Routine): string | number | null =>
    key === "name" ? r.name.toLowerCase()
    : key === "time" ? r.time
    : key === "cycle" ? (r.cycle.continuous ? 1 : r.cycle.daysOn / (r.cycle.daysOn + r.cycle.daysOff))
    : key === "status" ? STATUS_ORDER[routineStatus(r, today)]
    : daysOfStockLeft(r);
  return [...rows].sort((a, b) => {
    const x = val(a), y = val(b);
    if (x == null || y == null) return x == null && y == null ? a.name.localeCompare(b.name) : x == null ? 1 : -1;
    const c = typeof x === "number" && typeof y === "number" ? x - y : String(x).localeCompare(String(y));
    return (dir === "asc" ? c : -c) || a.name.localeCompare(b.name);
  });
}

// ---------- Inventory: reorder link + shelf life after opening ----------
export const LOW_STOCK_DAYS = 7, EXPIRY_WARN_DAYS = 14;

/** Only http(s) links are ever rendered, so a synced or shared value can't smuggle in javascript: URLs. */
export function safeUrl(u?: string | null): string | null {
  if (!u) return null;
  try {
    const x = new URL(u.trim());
    return x.protocol === "https:" || x.protocol === "http:" ? x.href : null;
  } catch { return null; }
}

export function expiresOn(r: Pick<Routine, "openedOn" | "shelfLifeDays">): string | null {
  return r.openedOn && r.shelfLifeDays && r.shelfLifeDays > 0 ? addDays(r.openedOn, r.shelfLifeDays) : null;
}

export interface InventoryAlert { daysLeft: number | null; low: boolean; expiryDays: number | null; expiring: boolean; expired: boolean; reorderUrl: string | null }

export function inventoryAlert(r: Routine, today: string): InventoryAlert {
  const daysLeft = daysOfStockLeft(r);
  const exp = expiresOn(r);
  const expiryDays = exp ? daysBetween(today, exp) : null;
  return {
    daysLeft, low: daysLeft != null && daysLeft <= LOW_STOCK_DAYS,
    expiryDays, expiring: expiryDays != null && expiryDays >= 0 && expiryDays <= EXPIRY_WARN_DAYS, expired: expiryDays != null && expiryDays < 0,
    reorderUrl: safeUrl(r.buyUrl),
  };
}

// ---------- Chrono-timing: automatic time-slot suggestions ----------
export const SLOT_TIMES = ["08:00", "12:30", "19:00", "21:30"];
const hourOf = (t: string) => Number(t.split(":")[0]);

/** Hard timing rules, mirroring the warnings in analyze(). */
export function fitsTime(r: Routine, time: string): boolean {
  const ref = findReference(r.name), h = hourOf(time);
  if ((ref?.stimulating || r.name.toLowerCase().includes("caffeine")) && h >= 14) return false;
  if (ref?.calming && h < 18) return false;
  if (ref?.food === "with" && (h >= 22 || h < 6)) return false;
  return true;
}

export interface ScheduleSuggestion { id: string; name: string; from: string; to: string; reason: "timing" | "clash"; partner?: string }

const clashCount = (rs: Routine[]) => analyze(rs).filter((i) => i.level === "clash").length;

/** Proposes new dose times: fix timing-rule violations first (reference time, else the first fitting slot),
 * then split clashing pairs into different slots while never breaking a timing rule or adding clashes. */
export function suggestSchedule(routines: Routine[]): ScheduleSuggestion[] {
  const live = routines.filter((r) => !r.deletedAt && !r.paused);
  const work = new Map(live.map((r) => [r.id, { ...r }]));
  const why = new Map<string, Pick<ScheduleSuggestion, "reason" | "partner">>();
  const options = (r: Routine) => [...new Set([findReference(r.name)?.time, ...SLOT_TIMES].filter((t): t is string => !!t))];

  for (const r of work.values()) {
    if (fitsTime(r, r.time)) continue;
    const to = options(r).find((t) => fitsTime(r, t));
    if (to) { r.time = to; why.set(r.id, { reason: "timing" }); }
  }
  for (let pass = 0; pass < 12; pass++) {
    const list = [...work.values()];
    const clash = analyze(list).find((i) => i.level === "clash");
    if (!clash) break;
    const before = clashCount(list);
    const [a, b] = clash.ids.map((id) => work.get(id)!);
    let moved = false;
    for (const [mv, keep] of [[b, a], [a, b]] as const) {
      for (const t of options(mv)) {
        if (!fitsTime(mv, t) || slotOf(t) === slotOf(keep.time)) continue;
        if (clashCount(list.map((x) => (x.id === mv.id ? { ...x, time: t } : x))) >= before) continue;
        mv.time = t; why.set(mv.id, { reason: "clash", partner: keep.name }); moved = true; break;
      }
      if (moved) break;
    }
    if (!moved) break;
  }
  return live.filter((r) => work.get(r.id)!.time !== r.time).map((r) => ({ id: r.id, name: r.name, from: r.time, to: work.get(r.id)!.time, ...why.get(r.id)! }));
}

// ---------- Biomarkers vs adherence ----------
/** Share of planned doses of one routine taken in the `window` days ending on `endDay` (null = nothing planned).
 *  Pauses are ignored, also the ones in its schedule history (each day still uses that day's time and cycle). */
export function adherenceOn(r: Routine, intakes: Map<string, Intake>, endDay: string, window = 30): number | null {
  let planned = 0, taken = 0;
  const open: Routine = { ...r, paused: false, ...(Array.isArray(r.history) ? { history: r.history.map((s) => ({ ...s, paused: false })) } : {}) };
  for (let i = 0; i < window; i++)
    for (const d of dosesForDay([open], intakes, addDays(endDay, -i))) {
      planned++;
      if (d.intake?.status === "Taken" && !d.intake.deletedAt) taken++;
    }
  return planned ? taken / planned : null;
}

/** Pearson correlation; null with fewer than 3 points or no variance. */
export function pearson(xs: number[], ys: number[]): number | null {
  const n = Math.min(xs.length, ys.length);
  if (n < 3) return null;
  const mx = xs.slice(0, n).reduce((a, b) => a + b, 0) / n, my = ys.slice(0, n).reduce((a, b) => a + b, 0) / n;
  let sxy = 0, sxx = 0, syy = 0;
  for (let i = 0; i < n; i++) { const dx = xs[i] - mx, dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy; }
  return sxx === 0 || syy === 0 ? null : sxy / Math.sqrt(sxx * syy);
}

export interface CorrelationPoint { date: string; value: number; adherence: number | null }
export function markerAdherence(series: Biomarker[], r: Routine, intakes: Map<string, Intake>, window = 30): { points: CorrelationPoint[]; r: number | null } {
  const points = series.filter((b) => !b.deletedAt).map((b) => ({ date: b.date, value: b.value, adherence: adherenceOn(r, intakes, b.date, window) }));
  const ok = points.filter((p) => p.adherence != null);
  return { points, r: pearson(ok.map((p) => p.adherence!), ok.map((p) => p.value)) };
}

export function correlationLabel(r: number | null): "none" | "weak" | "moderate" | "strong" {
  if (r == null) return "none";
  const a = Math.abs(r);
  return a >= 0.7 ? "strong" : a >= 0.4 ? "moderate" : "weak";
}
