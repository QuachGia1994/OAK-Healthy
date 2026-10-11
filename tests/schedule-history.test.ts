import { test } from "node:test";
import assert from "node:assert/strict";
import { forSave, MAX_SPANS, parseHistory, plannedOn, scheduleOn, trimHistory, withHistory } from "../lib/domain/schedule-history.ts";
import { addDays, scheduledAt } from "../lib/domain/day.ts";
import { dosesForDay } from "../lib/domain/schedule.ts";
import type { Routine, ScheduleSpan } from "../lib/domain/types.ts";

const daily = { daysOn: 1, daysOff: 0, continuous: true, durationDays: null };
const R = (o: Partial<Routine> = {}): Routine => ({ id: "r1", clientId: "p", name: "Vitamin D3", dose: "", time: "08:00", startDate: "2026-03-01", cycle: daily, category: "vitamin", paused: false, stock: null, perDose: 1, updatedAt: scheduledAt("2026-03-01", "07:00"), ...o });
const S = (from: string, o: Partial<ScheduleSpan> = {}): ScheduleSpan => ({ from, time: "08:00", startDate: "2026-03-01", cycle: daily, paused: false, ...o });
/** The schedule on a day in short form: "08:00", "08:00 paused". */
const on = (r: Routine, day: string) => { const s = scheduleOn(r, day); return s.time + (s.paused ? " paused" : ""); };
const pad = (n: number) => String(n).padStart(2, "0");
/** n spans on consecutive days from 2025-01-01, each with its own time. */
const many = (n: number) => Array.from({ length: n }, (_, i) => S(addDays("2025-01-01", i), { time: `${pad(Math.floor(i / 60) % 24)}:${pad(i % 60)}` }));

test("schedule history: a routine without history has its current schedule on every day", () => {
  const r = R({ time: "09:30", paused: true });
  for (const day of ["2020-01-01", "2026-03-01", "2030-12-31"]) assert.equal(on(r, day), "09:30 paused");
  assert.equal(on(R({ history: "junk" as unknown as ScheduleSpan[] }), "2026-03-02"), "08:00", "unreadable history falls back to the current fields");
});

test("schedule history: a schedule edit appends a span from today, a second edit that day replaces it, other edits add none", () => {
  const r0 = R();
  const r1 = withHistory({ ...r0, time: "09:00" }, "2026-03-05", r0);
  assert.deepEqual(r1.history, [S("2026-03-01"), S("2026-03-05", { time: "09:00" })]);
  assert.deepEqual(withHistory({ ...r1, time: "10:00" }, "2026-03-05", r1).history, [S("2026-03-01"), S("2026-03-05", { time: "10:00" })]);
  assert.deepEqual(withHistory({ ...r1, cycle: { daysOn: 2, daysOff: 1, continuous: false } }, "2026-03-07", r1).history,
    [S("2026-03-01"), S("2026-03-05", { time: "09:00" }), S("2026-03-07", { time: "09:00", cycle: { daysOn: 2, daysOff: 1, continuous: false } })]);
  assert.deepEqual(withHistory({ ...r1, startDate: "2026-03-03" }, "2026-03-07", r1).history?.at(-1), S("2026-03-07", { time: "09:00", startDate: "2026-03-03" }), "a start date edit is a span (cycle phase)");
  const other = { ...r1, stock: 5, name: "D3 forte", remind: false, note: "x" };
  assert.deepEqual(withHistory(other, "2026-03-07", r1), other, "stock, name, remind and note add no span");
  assert.equal(withHistory({ ...r0, stock: 5 }, "2026-03-07", r0).history, undefined);
  assert.equal(withHistory({ ...r1, time: "08:00" }, "2026-03-05", r1).history, undefined, "edited back the same day: no span left");
});

test("schedule history: a span holds from its own day; days before the first span use the first span", () => {
  const r = R({ time: "09:00", history: [S("2026-03-01"), S("2026-03-05", { time: "09:00" })] });
  assert.equal(on(r, "2026-02-20"), "08:00");
  assert.equal(on(r, "2026-03-04"), "08:00");
  assert.equal(on(r, "2026-03-05"), "09:00");
  assert.equal(on(r, "2026-12-31"), "09:00");
});

test("schedule history: current fields that differ from the last span hold from the routine's updatedAt day; a write materializes that span", () => {
  const r = R({ time: "10:00", updatedAt: scheduledAt("2026-03-08", "12:00"), history: [S("2026-03-01"), S("2026-03-05", { time: "09:00" })] });
  assert.equal(on(r, "2026-03-07"), "09:00");
  assert.equal(on(r, "2026-03-08"), "10:00");
  assert.deepEqual(withHistory(r, "2026-03-10").history, [S("2026-03-01"), S("2026-03-05", { time: "09:00" }), S("2026-03-08", { time: "10:00" })]);
  const same = R({ time: "09:00", history: [S("2026-03-01"), S("2026-03-05", { time: "09:00" })] });
  assert.equal(withHistory(same, "2026-03-10"), same, "nothing to materialize: the same record");
});

test("schedule history: parseHistory rejects data that is not a span list", () => {
  for (const bad of [undefined, null, "x", {}, [1], [S("2026-3-1")], [S("2026-03-01", { time: "8am" })], [S("2026-03-01", { startDate: "" })],
    [S("2026-03-01", { cycle: { daysOn: -1, daysOff: 0, continuous: false } })], [S("2026-03-01", { cycle: null as never })],
    [{ ...S("2026-03-01"), paused: "no" }], [{ ...S("2026-03-01"), deleted: false }]])
    assert.equal(parseHistory(bad), undefined, JSON.stringify(bad));
  const ok = [S("2026-03-01"), S("2026-03-05", { time: "09:00", paused: true }), S("2026-03-09", { deleted: true })];
  assert.deepEqual(parseHistory(ok), ok);
  assert.deepEqual(parseHistory([ok[1], ok[0]]), [ok[0], ok[1]], "sorted by from");
});

test("schedule history: parseHistory keeps the later duplicate, drops spans after today, folds a long list to 64", () => {
  assert.deepEqual(parseHistory([S("2026-03-01"), S("2026-03-05", { time: "09:00" }), S("2026-03-05", { time: "10:00" })]), [S("2026-03-01"), S("2026-03-05", { time: "10:00" })]);
  assert.deepEqual(parseHistory([S("2026-03-01"), S("2026-03-09", { time: "09:00" })], "2026-03-08"), [S("2026-03-01")]);
  const r = R({ time: "11:00", updatedAt: scheduledAt("2026-03-06", "12:00"), history: parseHistory([S("2026-03-01"), S("2026-03-05", { time: "09:00" })]) });
  assert.deepEqual([on(r, "2026-03-05"), on(r, "2026-03-06")], ["09:00", "11:00"], "a last span that is not the current fields is kept");
  const h = many(200), got = parseHistory(h)!;
  assert.equal(MAX_SPANS, 64);
  assert.equal(got.length, 64);
  assert.deepEqual(got[0], { ...h[136], from: h[0].from });
  assert.deepEqual(got.slice(1), h.slice(137));
});

test("schedule history: the 65th span folds the two oldest into one (earliest from, newer fields)", () => {
  const h = many(64), r = R({ time: h[63].time, history: h, updatedAt: scheduledAt(h[63].from, "12:00") });
  const got = withHistory({ ...r, time: "23:59" }, "2026-03-01", r).history!;
  assert.equal(got.length, 64);
  assert.deepEqual(got[0], { ...h[1], from: h[0].from });
  assert.deepEqual(got.at(-1), S("2026-03-01", { time: "23:59" }));
});

test("schedule history: trimHistory keeps the span holding on the cut day and every later one", () => {
  const h = [S("2026-01-01"), S("2026-02-01", { time: "09:00" }), S("2026-03-01", { time: "10:00" })];
  assert.deepEqual(trimHistory(h, "2026-02-15"), h.slice(1));
  assert.deepEqual(trimHistory(h, "2026-02-01"), h.slice(1));
  assert.deepEqual(trimHistory(h, "2026-03-20"), h.slice(2));
  assert.deepEqual(trimHistory(h, "2025-12-01"), h);
  assert.equal(trimHistory(undefined, "2026-02-01"), undefined);
});

test("schedule history: delete writes a closing span, undo delete removes it; a deleted routine is never planned", () => {
  const r0 = R(), gone = withHistory({ ...r0, deletedAt: scheduledAt("2026-03-05", "12:00") }, "2026-03-05", r0);
  assert.deepEqual(gone.history, [S("2026-03-01"), S("2026-03-05", { deleted: true })]);
  for (const r of [gone, { ...r0, deletedAt: 1 }]) for (const day of ["2026-03-02", "2026-03-05", "2026-03-06"]) assert.equal(plannedOn(r, day), false, day);
  const back = withHistory({ ...gone, deletedAt: null }, "2026-03-07", gone);
  assert.equal(back.history, undefined);
  assert.equal(plannedOn(back, "2026-03-02"), true);
  assert.equal(plannedOn(R({ paused: true }), "2026-03-02"), false);
  assert.equal(plannedOn(R({ history: [S("2026-03-01", { paused: true }), S("2026-03-04")] }), "2026-03-02"), false);
  assert.equal(plannedOn(R({ startDate: "2026-03-03" }), "2026-03-02"), false);
});

test("schedule history: Undo re-saves the old record and gets the old history back; an edit of the stored record adds the span", () => {
  const r0 = R(), e1 = forSave({ ...r0, time: "09:00" }, r0, "2026-03-05");
  assert.equal(e1.history?.length, 2);
  assert.equal(forSave(r0, e1, "2026-03-05").history, undefined, "undo of the first edit");
  const e2 = forSave({ ...e1, time: "10:00" }, e1, "2026-03-06");
  assert.deepEqual(forSave(e1, e2, "2026-03-06").history, e1.history, "undo of a later edit");
  assert.equal(forSave(R({ id: "new" }), undefined, "2026-03-06").history, undefined, "a new routine has no history");
});

test("schedule history: an edit of a copy loaded before a sync changed the stored history holds from today, keeps the stored spans, and a past Taken stays planned", () => {
  const r0 = R(), dur = { ...daily, durationDays: 365 };
  const stored = { ...r0, cycle: dur, history: [S("2026-03-01"), S("2026-03-05", { cycle: dur })], updatedAt: scheduledAt("2026-03-05", "12:00") };
  const saved = forSave({ ...r0, time: "09:00" }, stored, "2026-03-10");
  assert.deepEqual(saved.history, [S("2026-03-01"), S("2026-03-05", { cycle: dur }), S("2026-03-10", { time: "09:00" })]);
  const at = scheduledAt("2026-03-08", "08:00");
  const taken = new Map([[`r1@${at}`, { key: `r1@${at}`, supplementId: "r1", clientId: "p", scheduledAt: at, status: "Taken" as const, updatedAt: at }]]);
  assert.equal(dosesForDay([saved], taken, "2026-03-08")[0]?.intake?.status, "Taken", "the 08:00 Taken on Mar 8 still matches its slot");
});

test("schedule history: Duplicate (a new id) does not copy the original's history", () => {
  const r0 = R(), e1 = forSave({ ...r0, time: "09:00" }, r0, "2026-03-05");
  const copy = forSave({ ...e1, id: "copy", name: "Vitamin D3 (copy)" }, undefined, "2026-03-06");
  assert.equal(copy.history, undefined);
  assert.equal("history" in copy, false);
  assert.equal(copy.time, "09:00");
});
