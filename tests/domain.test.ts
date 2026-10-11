import { test } from "node:test";
import assert from "node:assert/strict";
import { addDays, daysBetween, dayWindow, scheduledAt } from "../lib/domain/day.ts";
import { cycleStatus, isExpired } from "../lib/domain/cycle.ts";
import { completionRate, doseState, isLateTaken, MISSED_AFTER_MS } from "../lib/domain/timing.ts";
import { dayStats, daysOfStockLeft, dosesForDay, slotOf, streak } from "../lib/domain/schedule.ts";
import { analyze } from "../lib/domain/knowledge.ts";
import { adherenceOn } from "../lib/domain/stack.ts";
import type { Intake, Routine, ScheduleSpan } from "../lib/domain/types.ts";

const R = (o: Partial<Routine>): Routine => ({ id: "r1", clientId: "p", name: "Vitamin D3", dose: "", time: "08:00", startDate: "2026-01-01", cycle: { daysOn: 1, daysOff: 0, continuous: true }, category: "vitamin", paused: false, stock: null, perDose: 1, updatedAt: 0, ...o });

test("day math is calendar-based", () => {
  assert.equal(addDays("2026-02-28", 1), "2026-03-01");
  assert.equal(daysBetween("2026-01-01", "2026-12-31"), 364);
  assert.equal(daysBetween("2026-03-28", "2026-03-30"), 2);
  const [a, b] = dayWindow("2026-05-05");
  assert.ok(b > a);
});

test("cycle on/off and duration", () => {
  const cfg = { daysOn: 2, daysOff: 1, continuous: false };
  assert.equal(cycleStatus("2026-01-01", cfg, "2025-12-31"), "OFF");
  assert.equal(cycleStatus("2026-01-01", cfg, "2026-01-01"), "ON");
  assert.equal(cycleStatus("2026-01-01", cfg, "2026-01-02"), "ON");
  assert.equal(cycleStatus("2026-01-01", cfg, "2026-01-03"), "OFF");
  assert.equal(cycleStatus("2026-01-01", cfg, "2026-01-04"), "ON");
  assert.equal(isExpired("2026-01-01", { ...cfg, durationDays: 10 }, "2026-01-11"), true);
  assert.equal(cycleStatus("2026-01-01", { ...cfg, continuous: true, durationDays: 10 }, "2026-01-10"), "ON");
});

test("dose timing policy", () => {
  const at = 1_000_000_000;
  assert.equal(doseState(undefined, at, at + MISSED_AFTER_MS + 1), "missed");
  assert.equal(doseState(undefined, at, at - 60_000), "soon");
  assert.equal(doseState(undefined, at, at + 1), "due");
  assert.equal(doseState("Taken", at, at + MISSED_AFTER_MS * 5), "taken");
  assert.equal(isLateTaken("Taken", at, at + 21 * 60_000), true);
  assert.equal(completionRate(0, 0), null);
  assert.equal(completionRate(3, 1), 0.75);
});

test("schedule, stats, streak, stock", () => {
  const r = R({});
  const intakes = new Map<string, Intake>();
  for (const day of ["2026-01-01", "2026-01-02", "2026-01-03"]) {
    const at = scheduledAt(day, "08:00");
    intakes.set(`r1@${at}`, { key: `r1@${at}`, supplementId: "r1", clientId: "p", scheduledAt: at, status: "Taken", updatedAt: at });
  }
  assert.equal(dosesForDay([r], intakes, "2026-01-02").length, 1);
  assert.equal(dosesForDay([R({ paused: true })], intakes, "2026-01-02").length, 0);
  const stats = dayStats([r], intakes, "2026-01-04", 4);
  assert.equal(streak(stats), 3);
  assert.equal(slotOf("21:30"), "bedtime");
  assert.equal(daysOfStockLeft(R({ stock: 10, perDose: 2 })), 5);
});

// Schedule history (docs/feat/schedule-history.md): past days follow the span that held on them.
const S = (from: string, o: Partial<ScheduleSpan> = {}): ScheduleSpan => ({ from, time: "08:00", startDate: "2026-01-01", cycle: { daysOn: 1, daysOff: 0, continuous: true }, paused: false, ...o });
const taken = (...slots: [string, string][]) => new Map<string, Intake>(slots.map(([day, time]) => {
  const at = scheduledAt(day, time);
  return [`r1@${at}`, { key: `r1@${at}`, supplementId: "r1", clientId: "p", scheduledAt: at, status: "Taken", updatedAt: at }];
}));
const counts = (s: { planned: number; taken: number }[]) => s.map((d) => `${d.taken}/${d.planned}`);

test("dayStats on past days uses the time and pause of the span that held on each day", () => {
  const r = R({ time: "09:00", history: [S("2026-01-01"), S("2026-01-03", { paused: true }), S("2026-01-05", { time: "09:00" })] });
  const stats = dayStats([r], taken(["2026-01-01", "08:00"], ["2026-01-02", "08:00"], ["2026-01-05", "09:00"]), "2026-01-05", 5);
  assert.deepEqual(counts(stats), ["1/1", "1/1", "0/0", "0/0", "1/1"]);
  assert.equal(streak(stats), 3);
});

test("dayStats: a deleted routine without history is planned on no day (unchanged)", () => {
  assert.deepEqual(counts(dayStats([R({ deletedAt: 5 })], taken(["2026-01-02", "08:00"]), "2026-01-03", 3)), ["0/0", "0/0", "0/0"]);
});

test("dayStats after a same-day time edit: that day plans the new time; the day before keeps the old one", () => {
  const r = R({ time: "20:00", history: [S("2026-01-01"), S("2026-01-10", { time: "20:00" })] });
  assert.deepEqual(counts(dayStats([r], taken(["2026-01-09", "08:00"], ["2026-01-10", "08:00"]), "2026-01-10", 2)), ["1/1", "0/1"]);
});

test("adherenceOn ignores pauses in the history too, and counts each day at its span's time", () => {
  const r = R({ time: "09:00", history: [S("2026-01-01"), S("2026-01-03", { paused: true }), S("2026-01-05", { time: "09:00" })] });
  assert.equal(adherenceOn(r, taken(["2026-01-01", "08:00"], ["2026-01-02", "08:00"], ["2026-01-05", "09:00"]), "2026-01-06", 6), 0.5);
});

test("interaction engine flags clashes and late stimulants", () => {
  const out = analyze([R({ id: "a", name: "Iron", time: "08:00" }), R({ id: "b", name: "Calcium", time: "09:00" }), R({ id: "c", name: "Caffeine", time: "19:00" })]);
  assert.ok(out.some((i) => i.level === "clash" && i.title.includes("Iron")));
  assert.ok(out.some((i) => i.level === "timing" && i.title.includes("Caffeine")));
});

import { matchInText, findReference, DICTIONARY } from "../lib/domain/knowledge.ts";
import { parseLabel } from "../lib/domain/label.ts";
import { VI } from "../lib/i18n-vi.ts";
import { byMarker, markerStatus } from "../lib/domain/biomarkers.ts";

test("knowledge base: aliases, sources and new clashes", () => {
  assert.ok(DICTIONARY.length >= 35);
  assert.equal(findReference("cholecalciferol")?.name, "Vitamin D3");
  const hosts = new Set(["ods.od.nih.gov", "www.nccih.nih.gov", "medlineplus.gov", "examine.com"]);
  assert.ok(DICTIONARY.every((r) => r.source?.startsWith("https://") && hosts.has(new URL(r.source).host)), "every entry cites a source on an allowed host");
  assert.equal(new Set(DICTIONARY.map((r) => r.source)).size, DICTIONARY.length, "sources are distinct");
  assert.deepEqual(DICTIONARY.filter((r) => !VI[r.advice]).map((r) => r.name), [], "every advice has a Vietnamese translation");
  const out = analyze([R({ id: "a", name: "Psyllium", time: "19:00" }), R({ id: "b", name: "Magnesium", time: "19:30" })]);
  assert.ok(out.some((i) => i.level === "clash" && i.ids.includes("a") && i.ids.includes("b")));
  assert.ok(analyze([R({ id: "z", name: "Zinc", time: "23:00" })]).some((i) => i.level === "timing"));
});

test("label OCR parsing finds name, dose and count", () => {
  const g = parseLabel("NOW Foods\nVitamin D-3 (as cholecalciferol) 5,000 IU\n120 Softgels\nServing size: 1 softgel");
  assert.equal(g.name, "Vitamin D3");
  assert.equal(g.dose, "5000 IU");
  assert.equal(g.stock, 120);
  assert.equal(g.perDose, 1);
  assert.equal(matchInText("Magnesium Glycinate 200 mg")[0]?.name, "Magnesium");
  assert.equal(parseLabel("Vitamin D-3 5000 IU\n60 Softgels").name, "Vitamin D3");
  assert.equal(parseLabel("Omega 3 Fish Oil 1000 mg").name, "Omega-3");
  assert.equal(parseLabel("Lion's Mane 500 mg").name, "Lion's Mane");
  assert.equal(matchInText("Vitamin D3 2000 IU").length, 1);
});

test("biomarker status and grouping", () => {
  assert.equal(markerStatus({ marker: "vitd", value: 18, unit: "ng/mL" }), "low");
  assert.equal(markerStatus({ marker: "vitd", value: 45, unit: "ng/mL" }), "normal");
  assert.equal(markerStatus({ marker: "vitd", value: 45, unit: "nmol/L" }), "unknown");
  const g = byMarker([
    { id: "1", clientId: "p", marker: "vitd", value: 18, unit: "ng/mL", date: "2026-01-01", updatedAt: 1 },
    { id: "2", clientId: "p", marker: "vitd", value: 40, unit: "ng/mL", date: "2026-06-01", updatedAt: 2 },
  ]);
  assert.equal(g[0].latest.value, 40);
  assert.equal(g[0].series.length, 2);
});
