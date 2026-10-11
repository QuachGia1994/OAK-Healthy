import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { doseAt, planned, takenNext } from "../lib/domain/intake-key.ts";
import { dosesForDay } from "../lib/domain/schedule.ts";
import type { Intake, Routine } from "../lib/domain/types.ts";

type Taken = typeof takenNext;
/** public/sw.js is plain, unbundled JS: run it with an inert `self` and read its top-level mirror functions. */
function loadSw() {
  const ctx: Record<string, unknown> = { self: { addEventListener() {} }, TextEncoder, TextDecoder, crypto };
  runInNewContext(readFileSync(new URL("../public/sw.js", import.meta.url), "utf8"), ctx);
  return ctx as { doseAt: typeof doseAt; planned: typeof planned; takenNext: Taken };
}
const sw = loadSw();

const at8 = new Date(2026, 9, 11, 8, 0).getTime();
const daily = { daysOn: 1, daysOff: 0, continuous: true, durationDays: null };
const routine = { id: "vit-d", clientId: "me", stock: 30, perDose: 2, updatedAt: 1, time: "08:00", startDate: "2026-10-01", cycle: daily, paused: false, deletedAt: null } as Routine;
const intake = (status: Intake["status"], deletedAt: number | null = null): Intake =>
  ({ key: `vit-d@${at8}`, supplementId: "vit-d", clientId: "me", scheduledAt: at8, status, updatedAt: 5, deletedAt });

const atCases: [string, string, number, number][] = [
  ["on time", "08:00", new Date(2026, 9, 11, 8, 1).getTime(), at8],
  ["shown 2 min early", "08:00", new Date(2026, 9, 11, 7, 58).getTime(), at8],
  ["snoozed past midnight", "23:30", new Date(2026, 9, 12, 0, 30).getTime(), new Date(2026, 9, 11, 23, 30).getTime()],
];
for (const [name, time, now, want] of atCases) {
  test(`doseAt ${name}: app and service worker give the dashboard's scheduledAt`, () => {
    assert.equal(doseAt(time, now), want);
    assert.equal(sw.doseAt(time, now), want);
  });
}

// 2026-10-11 is day 10 from startDate 2026-10-01.
const planCases: [string, Partial<Routine>, boolean][] = [
  ["daily routine", {}, true],
  ["cycle OFF day (2 on / 2 off, day 10 % 4 = 2)", { cycle: { daysOn: 2, daysOff: 2, continuous: false, durationDays: null } }, false],
  ["cycle ON day (3 on / 2 off, day 10 % 5 = 0)", { cycle: { daysOn: 3, daysOff: 2, continuous: false, durationDays: null } }, true],
  ["start date in the future", { startDate: "2026-10-12" }, false],
  ["duration ended", { cycle: { ...daily, durationDays: 10 } }, false],
  ["last day of duration", { cycle: { ...daily, durationDays: 11 } }, true],
  ["paused", { paused: true }, false],
  ["deleted", { deletedAt: 3 }, false],
];
for (const [name, patch, want] of planCases) {
  test(`planned ${name}: app and service worker follow the dashboard plan`, () => {
    const r = { ...routine, ...patch } as Routine;
    assert.equal(dosesForDay([r], new Map(), "2026-10-11").length === 1, want);
    assert.equal(planned(r, at8), want);
    assert.equal(sw.planned(r, at8), want);
  });
}

const takenCases: [string, Routine, Intake | undefined, unknown][] = [
  ["cycle OFF day writes nothing", { ...routine, cycle: { daysOn: 2, daysOff: 2, continuous: false, durationDays: null } }, undefined, null],
  ["first Taken decrements stock by perDose", routine, undefined, {
    intake: { key: `vit-d@${at8}`, supplementId: "vit-d", clientId: "me", scheduledAt: at8, status: "Taken", updatedAt: 9, deletedAt: null },
    routine: { ...routine, stock: 28, updatedAt: 9 },
  }],
  ["Skipped then Taken decrements once", routine, intake("Skipped"), {
    intake: { key: `vit-d@${at8}`, supplementId: "vit-d", clientId: "me", scheduledAt: at8, status: "Taken", updatedAt: 9, deletedAt: null },
    routine: { ...routine, stock: 28, updatedAt: 9 },
  }],
  ["already Taken writes nothing", routine, intake("Taken"), null],
  ["undone Taken counts again", { ...routine, stock: 1 }, intake("Taken", 6), {
    intake: { key: `vit-d@${at8}`, supplementId: "vit-d", clientId: "me", scheduledAt: at8, status: "Taken", updatedAt: 9, deletedAt: null },
    routine: { ...routine, stock: 0, updatedAt: 9 },
  }],
  ["untracked stock leaves the routine alone", { ...routine, stock: null }, undefined, {
    intake: { key: `vit-d@${at8}`, supplementId: "vit-d", clientId: "me", scheduledAt: at8, status: "Taken", updatedAt: 9, deletedAt: null },
    routine: null,
  }],
];
for (const [name, r, prev, want] of takenCases) {
  test(`takenNext ${name}: app and service worker agree`, () => {
    assert.deepEqual(takenNext(r, at8, prev, 9), want);
    assert.deepEqual(JSON.parse(JSON.stringify(sw.takenNext(r, at8, prev, 9))), want);
  });
}
