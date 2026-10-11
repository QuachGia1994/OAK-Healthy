// Dose history as CSV for a doctor or coach (docs/feat/history-csv.md). Built on the device only; never sent anywhere.
import type { Intake, Profile, Routine } from "./types.ts";
import { addDays, dayWindow, scheduledAt, toLocalDay } from "./day.ts";
import { doseKey } from "./schedule.ts";
import { plannedOn, scheduleOn } from "./schedule-history.ts";
import { isMissed } from "./timing.ts";

export interface HistoryInput { routines: Routine[]; intakes: Intake[]; profiles: Profile[] }
export type HistoryColumn = "date" | "time" | "person" | "supplement" | "dose" | "status" | "recordedAt";
export const HISTORY_COLUMNS: HistoryColumn[] = ["date", "time", "person", "supplement", "dose", "status", "recordedAt"];
/** Headers and status words in the UI language, from the caller's t(). */
export interface HistoryLabels { headers: Record<HistoryColumn, string>; Taken: string; Skipped: string; Missed: string }
/** from: epoch ms of the range start (historyStart); rows run from it through now. locale: the UI language for sorting. */
export interface HistoryOptions { from: number; now: number; labels: HistoryLabels; locale?: string }
export type HistoryRange = 30 | 90 | "all";

const hhmm = (at: number) => { const d = new Date(at); return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };
const stamp = (at: number) => `${toLocalDay(new Date(at))} ${hhmm(at)}`;
const cmp = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);
const live = (i: Intake) => !i.deletedAt;

/** Range start: local midnight of today - (N - 1), or for "all" the earliest live intake or routine start (now when there is none). */
export function historyStart(range: HistoryRange, input: HistoryInput, now: number): number {
  const today = toLocalDay(new Date(now));
  if (range !== "all") return dayWindow(addDays(today, -(range - 1)))[0];
  const starts = [...input.intakes.filter(live).map((i) => i.scheduledAt), ...input.routines.filter((r) => !r.deletedAt).map((r) => dayWindow(r.startDate)[0])];
  return starts.length ? Math.min(...starts) : now;
}

export const historyFileName = (now: number) => `oak-healthy-history-${toLocalDay(new Date(now))}.csv`;

/** A text cell a spreadsheet would run as a formula (OWASP CSV injection: = + - @, or a leading tab/CR) gets a leading ',
 *  also after leading invisible characters: \s (ASCII and Unicode spaces, NBSP, U+3000, BOM) and the zero-width/format
 *  characters U+180E, U+200B-200D, U+2060. Fullwidth ＝＋－＠ and U+2212 are not formula characters and stay unquoted. */
const guard = (s: string) => (/^(?:[\t\r]|[\s\u180E\u200B-\u200D\u2060]*[=+\-@])/.test(s) ? `'${s}` : s);
/** Synced vault data is a boundary: a status this version does not know is written raw, never as a label lookup. */
const statusWord = (s: unknown, l: HistoryLabels) => (s === "Taken" || s === "Skipped" ? l[s] : typeof s === "string" ? s : "");
const quote = (s: string) => (/[",\r\n]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s);

interface Row { at: number; person: string; supplement: string; dose: string; status: string; recordedAt: string }

/** Slots the dashboard plans (plannedOn, at the time of that day's schedule span) and already counts Missed (isMissed) with no live record. */
function missedRows(routines: Routine[], recorded: Set<string>, person: (r: Routine) => string, o: HistoryOptions): Row[] {
  const out: Row[] = [];
  const last = toLocalDay(new Date(o.now));
  for (const r of routines) {
    for (let day = toLocalDay(new Date(o.from)); day <= last; day = addDays(day, 1)) {
      const s = scheduleOn(r, day), at = scheduledAt(day, s.time);
      if (at < o.from || recorded.has(doseKey(r.id, at)) || !isMissed(at, o.now) || !plannedOn(r, day, s)) continue;
      out.push({ at, person: person(r), supplement: r.name, dose: r.dose, status: o.labels.Missed, recordedAt: "" });
    }
  }
  return out;
}

/** One row per recorded dose (its stored status; unknown ones as written) and per Missed slot in [from, now]; RFC 4180, CRLF, UTF-8 BOM. */
export function historyCsv(input: HistoryInput, o: HistoryOptions): string {
  const byId = new Map(input.routines.map((r) => [r.id, r]));
  const names = new Map(input.profiles.map((p) => [p.id, p.name]));
  const person = (r: Routine) => names.get(r.clientId) ?? "";
  const records = input.intakes.filter((i) => live(i) && i.scheduledAt >= o.from && i.scheduledAt <= o.now);
  const rows: Row[] = records.map((i) => {
    const r = byId.get(i.supplementId);
    return { at: i.scheduledAt, person: r ? person(r) : "", supplement: r?.name ?? "", dose: r?.dose ?? "", status: statusWord(i.status, o.labels), recordedAt: stamp(i.updatedAt) };
  });
  rows.push(...missedRows(input.routines, new Set(records.map((i) => i.key)), person, o));
  const keyed = rows.map((x) => ({ ...x, date: toLocalDay(new Date(x.at)), time: hhmm(x.at) }));
  keyed.sort((a, b) => cmp(a.date, b.date) || cmp(a.time, b.time) || a.person.localeCompare(b.person, o.locale) || a.supplement.localeCompare(b.supplement, o.locale));
  const lines = [HISTORY_COLUMNS.map((c) => o.labels.headers[c]),
    ...keyed.map((x) => [x.date, x.time, guard(x.person), guard(x.supplement), guard(x.dose), guard(x.status), x.recordedAt])];
  return "\uFEFF" + lines.map((l) => l.map(quote).join(",")).join("\r\n") + "\r\n";
}
