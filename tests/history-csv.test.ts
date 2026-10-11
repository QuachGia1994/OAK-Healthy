import { test } from "node:test";
import assert from "node:assert/strict";
import { historyCsv, historyFileName, historyStart, type HistoryLabels } from "../lib/domain/history-csv.ts";
import { dayWindow, scheduledAt } from "../lib/domain/day.ts";
import type { Intake, Profile, Routine, ScheduleSpan } from "../lib/domain/types.ts";

const EN: HistoryLabels = { headers: { date: "Date", time: "Time", person: "Person", supplement: "Supplement", dose: "Dose", status: "Status", recordedAt: "Recorded at" }, Taken: "Taken", Skipped: "Skipped", Missed: "Missed" };
const HEAD = "Date,Time,Person,Supplement,Dose,Status,Recorded at";
const R = (o: Partial<Routine>): Routine => ({ id: "r1", clientId: "p1", name: "Vitamin D3", dose: "1000 IU", time: "08:00", startDate: "2026-03-01", cycle: { daysOn: 1, daysOff: 0, continuous: true }, category: "vitamin", paused: false, stock: null, perDose: 1, updatedAt: 0, ...o });
const P = (id: string, name: string, deletedAt?: number): Profile => ({ id, name, color: "#000", createdAt: 0, updatedAt: 0, deletedAt });
const I = (r: Routine, day: string, status: Intake["status"], updatedAt: number, o: Partial<Intake> = {}): Intake => {
  const at = scheduledAt(day, r.time);
  return { key: `${r.id}@${at}`, supplementId: r.id, clientId: r.clientId, scheduledAt: at, status, updatedAt, ...o };
};
const S = (from: string, o: Partial<ScheduleSpan> = {}): ScheduleSpan => ({ from, time: "08:00", startDate: "2026-03-01", cycle: { daysOn: 1, daysOff: 0, continuous: true }, paused: false, ...o });
const at = scheduledAt;
/** Rows without the BOM, header and final CRLF. */
const body = (csv: string) => { assert.ok(csv.startsWith("\uFEFF" + HEAD + "\r\n")); assert.ok(csv.endsWith("\r\n")); return csv.slice(1).split("\r\n").slice(1, -1); };
const from = dayWindow("2026-03-01")[0];

test("history CSV: Taken, Skipped and Missed rows; cycle off-days and future slots are not listed", () => {
  const a = R({}), b = R({ id: "r2", name: "Magnesium", dose: "200 mg", time: "21:00", cycle: { daysOn: 1, daysOff: 1, continuous: false } });
  const now = at("2026-03-03", "20:00");
  const csv = historyCsv({ routines: [a, b], profiles: [P("p1", "Me")], intakes: [I(a, "2026-03-01", "Taken", at("2026-03-01", "08:05")), I(a, "2026-03-02", "Skipped", at("2026-03-02", "09:30")), I(b, "2026-03-01", "Taken", at("2026-03-01", "21:10"))] }, { from, now, labels: EN });
  assert.deepEqual(body(csv), [
    "2026-03-01,08:00,Me,Vitamin D3,1000 IU,Taken,2026-03-01 08:05",
    "2026-03-01,21:00,Me,Magnesium,200 mg,Taken,2026-03-01 21:10",
    "2026-03-02,08:00,Me,Vitamin D3,1000 IU,Skipped,2026-03-02 09:30",
    "2026-03-03,08:00,Me,Vitamin D3,1000 IU,Missed,",
  ]);
});

test("history CSV: an unrecorded slot is Missed only after the dashboard's 2 h, and a slot before from is not listed", () => {
  const a = R({ time: "18:30" });
  const rows = (now: number, f = from) => body(historyCsv({ routines: [a], profiles: [P("p1", "Me")], intakes: [] }, { from: f, now, labels: EN }));
  assert.deepEqual(rows(at("2026-03-01", "20:30")), []);
  assert.deepEqual(rows(at("2026-03-01", "20:31")), ["2026-03-01,18:30,Me,Vitamin D3,1000 IU,Missed,"]);
  assert.deepEqual(rows(at("2026-03-02", "21:00"), dayWindow("2026-03-02")[0]), ["2026-03-02,18:30,Me,Vitamin D3,1000 IU,Missed,"]);
});

test("history CSV: paused and deleted routines keep their records but get no Missed rows; a cleared record is dropped; an orphan record stays", () => {
  const paused = R({ paused: true }), gone = R({ id: "r2", name: "Zinc", deletedAt: at("2026-03-02", "12:00") });
  const orphan: Intake = { key: "rx@1", supplementId: "rx", clientId: "p1", scheduledAt: at("2026-03-02", "07:00"), status: "Skipped", updatedAt: at("2026-03-02", "07:01"), deletedAt: null };
  const now = at("2026-03-03", "20:00");
  const csv = historyCsv({ routines: [paused, gone], profiles: [P("p1", "Me")], intakes: [
    I(paused, "2026-03-01", "Taken", at("2026-03-01", "08:01")), I(gone, "2026-03-01", "Taken", at("2026-03-01", "08:02")),
    I(paused, "2026-03-02", "Taken", at("2026-03-02", "08:03"), { deletedAt: at("2026-03-02", "08:04") }), orphan,
  ] }, { from, now, labels: EN });
  assert.deepEqual(body(csv), [
    "2026-03-01,08:00,Me,Vitamin D3,1000 IU,Taken,2026-03-01 08:01",
    "2026-03-01,08:00,Me,Zinc,1000 IU,Taken,2026-03-01 08:02",
    "2026-03-02,07:00,,,,Skipped,2026-03-02 07:01",
  ]);
});

test("history CSV: after a dose time edit on a day, earlier days keep the old time (no Missed at the new one) and the new time is Missed from that day", () => {
  const before = R({ time: "08:00" }), after = { ...before, time: "09:00", history: [S("2026-03-01"), S("2026-03-02", { time: "09:00" })] };
  const csv = historyCsv({ routines: [after], profiles: [P("p1", "Me")], intakes: [I(before, "2026-03-01", "Taken", at("2026-03-01", "08:05"))] }, { from, now: at("2026-03-02", "23:00"), labels: EN });
  assert.deepEqual(body(csv), ["2026-03-01,08:00,Me,Vitamin D3,1000 IU,Taken,2026-03-01 08:05", "2026-03-02,09:00,Me,Vitamin D3,1000 IU,Missed,"]);
});

test("history CSV: a routine paused on a day has Missed rows before that day and none from it", () => {
  const r = R({ paused: true, history: [S("2026-03-01"), S("2026-03-03", { paused: true })] });
  const csv = historyCsv({ routines: [r], profiles: [P("p1", "Me")], intakes: [] }, { from, now: at("2026-03-04", "20:00"), labels: EN });
  assert.deepEqual(body(csv), ["2026-03-01,08:00,Me,Vitamin D3,1000 IU,Missed,", "2026-03-02,08:00,Me,Vitamin D3,1000 IU,Missed,"]);
});

test("history CSV: after a cycle change on a day, the old cycle's off-days before it stay off", () => {
  const r = R({ history: [S("2026-03-01", { cycle: { daysOn: 1, daysOff: 1, continuous: false } }), S("2026-03-03")] });
  const csv = historyCsv({ routines: [r], profiles: [P("p1", "Me")], intakes: [] }, { from, now: at("2026-03-04", "20:00"), labels: EN });
  assert.deepEqual(body(csv).map((l) => l.slice(0, 16)), ["2026-03-01,08:00", "2026-03-03,08:00", "2026-03-04,08:00"]);
});

test("history CSV: a deleted routine has no Missed rows, with or without a closing span (deleted = never planned)", () => {
  const t = at("2026-03-03", "12:00");
  const old = R({ deletedAt: t }), closed = R({ id: "r2", name: "Zinc", deletedAt: t, history: [S("2026-03-01"), S("2026-03-03", { deleted: true })] });
  assert.deepEqual(body(historyCsv({ routines: [old, closed], profiles: [P("p1", "Me")], intakes: [] }, { from, now: at("2026-03-04", "20:00"), labels: EN })), []);
});

test("history CSV: a same-day time edit after that day's Taken keeps the Taken; the new time is Missed only once 2 h past", () => {
  const before = R({ time: "08:00" }), after = { ...before, time: "20:00", history: [S("2026-03-01"), S("2026-03-02", { time: "20:00" })] };
  const rows = (now: number) => body(historyCsv({ routines: [after], profiles: [P("p1", "Me")], intakes: [I(before, "2026-03-02", "Taken", at("2026-03-02", "08:05"))] }, { from, now, labels: EN }));
  const old = "2026-03-01,08:00,Me,Vitamin D3,1000 IU,Missed,", taken = "2026-03-02,08:00,Me,Vitamin D3,1000 IU,Taken,2026-03-02 08:05";
  assert.deepEqual(rows(at("2026-03-02", "21:59")), [old, taken]);
  assert.deepEqual(rows(at("2026-03-02", "22:01")), [old, taken, "2026-03-02,20:00,Me,Vitamin D3,1000 IU,Missed,"]);
});

test("history CSV: every profile, deleted ones included, sorted by person in the UI language; labels come from the caller", () => {
  const VI: HistoryLabels = { headers: { date: "Ngày", time: "Giờ", person: "Người", supplement: "Thực phẩm bổ sung", dose: "Liều", status: "Trạng thái", recordedAt: "Ghi lúc" }, Taken: "Đã uống", Skipped: "Bỏ qua", Missed: "Bỏ lỡ" };
  const rs = [R({ id: "a", clientId: "p2" }), R({ id: "b", clientId: "p1" }), R({ id: "c", clientId: "p3" })];
  const csv = historyCsv({ routines: rs, profiles: [P("p1", "Đức"), P("p2", "Bình"), P("p3", "An", 5)], intakes: [] }, { from, now: at("2026-03-01", "12:00"), labels: VI, locale: "vi-VN" });
  assert.deepEqual(csv.slice(1).split("\r\n").slice(0, -1), [
    "Ngày,Giờ,Người,Thực phẩm bổ sung,Liều,Trạng thái,Ghi lúc",
    "2026-03-01,08:00,An,Vitamin D3,1000 IU,Bỏ lỡ,",
    "2026-03-01,08:00,Bình,Vitamin D3,1000 IU,Bỏ lỡ,",
    "2026-03-01,08:00,Đức,Vitamin D3,1000 IU,Bỏ lỡ,",
  ]);
});

test("history CSV: RFC 4180 quoting, and a text cell that starts like a formula gets a leading quote", () => {
  const r = R({ name: '=HYPERLINK("http://x","y")', dose: "-5 mg", clientId: "p1" });
  const r2 = R({ id: "r2", name: 'Fish oil, "triple"', dose: "1\ncap", time: "09:00" });
  const csv = historyCsv({ routines: [r, r2], profiles: [P("p1", "@me")], intakes: [] }, { from, now: at("2026-03-01", "12:00"), labels: EN });
  assert.equal(csv, "\uFEFF" + HEAD + "\r\n"
    + `2026-03-01,08:00,'@me,"'=HYPERLINK(""http://x"",""y"")",'-5 mg,Missed,\r\n`
    + `2026-03-01,09:00,'@me,"Fish oil, ""triple""","1\ncap",Missed,\r\n`);
});

test("history CSV: a text cell with leading whitespace before a formula character gets a leading quote; other leading whitespace stays", () => {
  const r = R({ name: "  =SUM(A1)", dose: "\n+1" });
  const r2 = R({ id: "r2", clientId: "p2", name: " Zinc", dose: "  5 mg", time: "09:00" });
  const csv = historyCsv({ routines: [r, r2], profiles: [P("p1", " @me"), P("p2", "Me")], intakes: [] }, { from, now: at("2026-03-01", "12:00"), labels: EN });
  assert.deepEqual(body(csv), [
    `2026-03-01,08:00,' @me,'  =SUM(A1),"'\n+1",Missed,`,
    "2026-03-01,09:00,Me, Zinc,  5 mg,Missed,",
  ]);
});

test("history CSV: a synced record with a status this version does not know writes the raw status, escaped like user text", () => {
  const r = R({});
  const odd = (day: string, status: unknown) => I(r, day, status as Intake["status"], at(day, "08:01"));
  const intakes = [odd("2026-03-01", "Pending"), odd("2026-03-02", "=cmd"), odd("2026-03-03", "a,b"), odd("2026-03-04", "headers"), odd("2026-03-05", undefined)];
  const csv = historyCsv({ routines: [r], profiles: [P("p1", "Me")], intakes }, { from, now: at("2026-03-05", "09:00"), labels: EN });
  assert.deepEqual(body(csv), [
    "2026-03-01,08:00,Me,Vitamin D3,1000 IU,Pending,2026-03-01 08:01",
    "2026-03-02,08:00,Me,Vitamin D3,1000 IU,'=cmd,2026-03-02 08:01",
    '2026-03-03,08:00,Me,Vitamin D3,1000 IU,"a,b",2026-03-03 08:01',
    "2026-03-04,08:00,Me,Vitamin D3,1000 IU,headers,2026-03-04 08:01",
    "2026-03-05,08:00,Me,Vitamin D3,1000 IU,,2026-03-05 08:01",
  ]);
});

test("history CSV: an empty range is the header only", () => {
  assert.equal(historyCsv({ routines: [], profiles: [], intakes: [] }, { from, now: from, labels: EN }), "\uFEFF" + HEAD + "\r\n");
});

test("history range: last N days start at local midnight of today - (N - 1); all starts at the earliest live record or routine start", () => {
  const now = at("2026-04-10", "15:00");
  const empty = { routines: [], intakes: [], profiles: [] };
  assert.equal(historyStart(30, empty, now), dayWindow("2026-03-12")[0]);
  assert.equal(historyStart(90, empty, now), dayWindow("2026-01-11")[0]);
  assert.equal(historyStart("all", empty, now), now);
  const r = R({ startDate: "2026-02-01" });
  const early = I(r, "2026-01-20", "Taken", 0), cleared = I(r, "2026-01-05", "Taken", 0, { deletedAt: 1 });
  assert.equal(historyStart("all", { routines: [r], intakes: [early, cleared], profiles: [] }, now), at("2026-01-20", "08:00"));
  assert.equal(historyStart("all", { routines: [r, R({ id: "x", startDate: "2025-01-01", deletedAt: 1 })], intakes: [], profiles: [] }, now), dayWindow("2026-02-01")[0]);
  assert.equal(historyFileName(at("2026-04-10", "00:30")), "oak-healthy-history-2026-04-10.csv");
});
