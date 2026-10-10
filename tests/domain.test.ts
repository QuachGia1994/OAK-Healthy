import { test } from "node:test";
import assert from "node:assert/strict";
import { addDays, daysBetween, dayWindow, scheduledAt } from "../lib/domain/day.ts";
import { cycleStatus, isExpired } from "../lib/domain/cycle.ts";
import { completionRate, doseState, isLateTaken, MISSED_AFTER_MS } from "../lib/domain/timing.ts";
import { dayStats, daysOfStockLeft, dosesForDay, slotOf, streak } from "../lib/domain/schedule.ts";
import { analyze } from "../lib/domain/knowledge.ts";
import type { Intake, Routine } from "../lib/domain/types.ts";

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

test("interaction engine flags clashes and late stimulants", () => {
  const out = analyze([R({ id: "a", name: "Iron", time: "08:00" }), R({ id: "b", name: "Calcium", time: "09:00" }), R({ id: "c", name: "Caffeine", time: "19:00" })]);
  assert.ok(out.some((i) => i.level === "clash" && i.title.includes("Iron")));
  assert.ok(out.some((i) => i.level === "timing" && i.title.includes("Caffeine")));
});
