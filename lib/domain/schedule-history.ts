// Which schedule held on a past day (docs/feat/schedule-history.md). A routine's `history` lists effective-from spans;
// the effective last span always equals the current fields, so today's list never reads history. Pure, no I/O.
// public/sw.js mirrors withHistory(r, today) as `materialize` for the notification's Taken (parity: tests/pwa.test.ts);
// planned() (intake-key.ts) and the notification's plan check keep the current fields.
import type { CycleConfig, Routine, ScheduleSpan } from "./types.ts";
import { cycleStatus } from "./cycle.ts";
import { toLocalDay } from "./day.ts";

/** Spans kept per routine; older ones fold into the first (vault size, docs/feat/schedule-history.md § Limits). */
export const MAX_SPANS = 64;
export type Schedule = Omit<ScheduleSpan, "from">;

const DAY = /^\d{4}-\d{2}-\d{2}$/, HHMM = /^([01]?\d|2[0-3]):[0-5]\d$/;
const count = (x: unknown) => typeof x === "number" && Number.isFinite(x) && x >= 0;
const isDay = (x: unknown): x is string => typeof x === "string" && DAY.test(x) && !Number.isNaN(Date.parse(x));
function isCycle(x: unknown): x is CycleConfig {
  const c = x as Partial<CycleConfig> | null;
  return !!c && typeof c === "object" && count(c.daysOn) && count(c.daysOff) && typeof c.continuous === "boolean" && (c.durationDays == null || count(c.durationDays));
}
function isSpan(x: unknown): x is ScheduleSpan {
  const s = x as Partial<ScheduleSpan> | null;
  return !!s && typeof s === "object" && isDay(s.from) && typeof s.time === "string" && HHMM.test(s.time) && isDay(s.startDate)
    && isCycle(s.cycle) && typeof s.paused === "boolean" && (s.deleted === undefined || s.deleted === true);
}

/** The schedule fields of a routine as a span body (deleted = !!deletedAt). */
export function scheduleOf(r: Routine): Schedule {
  return { time: r.time, startDate: r.startDate, cycle: { ...r.cycle }, paused: !!r.paused, ...(r.deletedAt ? { deleted: true as const } : {}) };
}
const sameCycle = (a: CycleConfig, b: CycleConfig) =>
  a.daysOn === b.daysOn && a.daysOff === b.daysOff && a.continuous === b.continuous && (a.durationDays ?? null) === (b.durationDays ?? null);
export const sameSchedule = (a: Schedule, b: Schedule) =>
  a.time === b.time && a.startDate === b.startDate && a.paused === b.paused && !!a.deleted === !!b.deleted && sameCycle(a.cycle, b.cycle);

/** Fold the oldest spans into one so at most MAX_SPANS remain: from = the earliest, fields = the newest folded. */
function cap(h: ScheduleSpan[]): ScheduleSpan[] {
  if (h.length <= MAX_SPANS) return h;
  const k = h.length - MAX_SPANS + 1;
  return [{ ...h[k - 1], from: h[0].from }, ...h.slice(k)];
}

/** Boundary check for synced, imported or circle data: undefined unless every span is valid. Sorted by from; a later
 *  duplicate day wins; with `today`, spans from a later day are dropped (a device clock ahead); capped at MAX_SPANS. */
export function parseHistory(x: unknown, today?: string): ScheduleSpan[] | undefined {
  if (!Array.isArray(x) || !x.every(isSpan)) return undefined;
  const byDay = new Map<string, ScheduleSpan>();
  for (const s of x) if (!today || s.from <= today) byDay.set(s.from, s);
  const h = [...byDay.values()].sort((a, b) => (a.from < b.from ? -1 : 1));
  return h.length ? cap(h) : undefined;
}

const read = new WeakMap<object, ScheduleSpan[] | null>();
function stored(r: Routine): ScheduleSpan[] | undefined {
  const x: unknown = r.history;
  if (!x || typeof x !== "object") return undefined;
  if (!read.has(x)) read.set(x, parseHistory(x) ?? null);
  return read.get(x) ?? undefined;
}

/** Set `s` from s.from: replaces the last span when it starts that day or later, else appends. */
function put(h: ScheduleSpan[], s: ScheduleSpan): ScheduleSpan[] {
  const last = h[h.length - 1];
  return last && s.from <= last.from ? [...h.slice(0, -1), { ...s, from: last.from }] : [...h, s];
}
/** The stored spans plus the implicit one: current fields that differ from the last span hold from the updatedAt day. */
function effective(r: Routine, h: ScheduleSpan[]): ScheduleSpan[] {
  const cur = scheduleOf(r);
  return sameSchedule(h[h.length - 1], cur) ? h : put(h, { from: toLocalDay(new Date(r.updatedAt)), ...cur });
}

/** The schedule that held on `day`: no history = the current fields; days before the first span use the first span. */
export function scheduleOn(r: Routine, day: string): Schedule {
  const h = stored(r);
  if (!h) return scheduleOf(r);
  const all = effective(r, h);
  let s = all[0];
  for (const x of all) if (x.from <= day) s = x; else break;
  return s;
}

/** The dashboard plans this routine on `day`: never when deleted (docs § Delete), else not paused and cycle ON in that day's span. */
export function plannedOn(r: Routine, day: string, s: Schedule = scheduleOn(r, day)): boolean {
  return !r.deletedAt && !s.paused && cycleStatus(s.startDate, s.cycle, day) === "ON";
}

/** History for writing `r`, edited from `before` (default r itself): materialize before's implicit span, then a changed
 *  schedule sets a span from today (same day: replaced), undo delete drops the closing span, equal neighbours merge, cap.
 *  One span left = no history (the current fields hold on every day). Returns r itself when nothing changes.
 *  Twin: public/sw.js `materialize` (the no-edit case); change both and the parity table in tests/pwa.test.ts. */
export function withHistory(r: Routine, today: string, before: Routine = r): Routine {
  const old = scheduleOf(before), next = scheduleOf(r);
  let h = parseHistory(before.history, today) ?? [];
  if (h.length) h = effective({ ...before, updatedAt: Math.min(before.updatedAt, new Date(today + "T23:59").getTime()) }, h);
  if (!next.deleted && h[h.length - 1]?.deleted) h = h.slice(0, -1);
  // No history yet: the old fields held on every day (an undelete compares them without the delete).
  const base: Schedule = h[h.length - 1] ?? (old.deleted && !next.deleted ? scheduleOf({ ...before, deletedAt: null }) : old);
  if (!sameSchedule(base, next)) {
    if (!h.length) h = [{ from: before.startDate < today ? before.startDate : today, ...base }];
    h = put(h, { from: today, ...next });
  }
  const merged = h.filter((s, i) => i === 0 || !sameSchedule(s, h[i - 1]));
  h = cap(merged);
  if (h.length <= 1) {
    if (r.history === undefined) return r;
    const out = { ...r };
    delete out.history;
    return out;
  }
  return JSON.stringify(h) === JSON.stringify(r.history) ? r : { ...r, history: h };
}

/** saveRoutine's rule: every save is an edit on top of the stored record, so a copy loaded before a sync still gets its
 *  span from today and keeps the stored spans; Undo re-saves the old fields and merges the new span away. A new id
 *  (new routine, Duplicate) starts with no history. */
export function forSave(r: Routine, stored: Routine | undefined, today: string): Routine {
  if (stored) return withHistory(r, today, stored);
  if (r.history === undefined) return r;
  const out = { ...r };
  delete out.history;
  return out;
}

/** The span holding on `sinceDay` and every later one (circle payload: same 60-day cut as intakes). */
export function trimHistory(h: ScheduleSpan[] | undefined, sinceDay: string): ScheduleSpan[] | undefined {
  if (!h) return undefined;
  let i = 0;
  while (i + 1 < h.length && h[i + 1].from <= sinceDay) i++;
  return h.slice(i);
}
