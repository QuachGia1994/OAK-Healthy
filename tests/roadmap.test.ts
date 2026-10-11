import { test } from "node:test";
import assert from "node:assert/strict";
import { scheduledAt } from "../lib/domain/day.ts";
import { analyze } from "../lib/domain/knowledge.ts";
import { slotOf } from "../lib/domain/schedule.ts";
import { adherenceOn, correlationLabel, expiresOn, inventoryAlert, markerAdherence, pearson, safeUrl, sortRoutines, suggestSchedule } from "../lib/domain/stack.ts";
import { buildSlug, isPrettySlug, slugify } from "../lib/domain/share.ts";
import type { Biomarker, Intake, Routine } from "../lib/domain/types.ts";

const R = (o: Partial<Routine>): Routine => ({ id: "r1", clientId: "p", name: "Vitamin D3", dose: "", time: "08:00", startDate: "2026-01-01", cycle: { daysOn: 1, daysOff: 0, continuous: true }, category: "vitamin", paused: false, stock: null, perDose: 1, updatedAt: 0, ...o });

test("stack table sorts by column and keeps untracked stock last", () => {
  const rows = [R({ id: "a", name: "Zinc", time: "21:00", stock: 30 }), R({ id: "b", name: "Iron", time: "10:00", stock: 3 }), R({ id: "c", name: "Biotin", time: "08:00", stock: null })];
  assert.deepEqual(sortRoutines(rows, "name", "asc", "2026-02-01").map((r) => r.id), ["c", "b", "a"]);
  assert.deepEqual(sortRoutines(rows, "time", "desc", "2026-02-01").map((r) => r.id), ["a", "b", "c"]);
  assert.deepEqual(sortRoutines(rows, "stock", "asc", "2026-02-01").map((r) => r.id), ["b", "a", "c"]);
  assert.deepEqual(sortRoutines(rows, "stock", "desc", "2026-02-01").map((r) => r.id), ["a", "b", "c"]);
  assert.deepEqual(sortRoutines([R({ id: "p", paused: true }), R({ id: "x" })], "status", "asc", "2026-02-01").map((r) => r.id), ["x", "p"]);
});

test("inventory: reorder link is http(s) only, shelf life after opening", () => {
  assert.equal(safeUrl("javascript:alert(1)"), null);
  assert.equal(safeUrl("not a url"), null);
  assert.equal(safeUrl(" https://shop.example/d3 "), "https://shop.example/d3");
  assert.equal(expiresOn({ openedOn: "2026-01-01", shelfLifeDays: 90 }), "2026-04-01");
  assert.equal(expiresOn({ openedOn: "2026-01-01", shelfLifeDays: null }), null);
  const a = inventoryAlert(R({ stock: 6, perDose: 1, openedOn: "2026-01-01", shelfLifeDays: 30, buyUrl: "https://shop.example/x" }), "2026-01-25");
  assert.equal(a.low, true);
  assert.equal(a.expiryDays, 6);
  assert.equal(a.expiring, true);
  assert.equal(a.expired, false);
  assert.equal(a.reorderUrl, "https://shop.example/x");
  assert.equal(inventoryAlert(R({ openedOn: "2026-01-01", shelfLifeDays: 10 }), "2026-02-01").expired, true);
});

test("chrono engine suggests slots that fix timing and split clashes", () => {
  const stack = [R({ id: "fe", name: "Iron", time: "08:00" }), R({ id: "ca", name: "Calcium", time: "09:00" }), R({ id: "cf", name: "Caffeine", time: "19:00" }), R({ id: "d", name: "Vitamin D3", time: "08:00" })];
  const s = suggestSchedule(stack);
  const caf = s.find((x) => x.id === "cf");
  assert.ok(caf && Number(caf.to.split(":")[0]) < 14 && caf.reason === "timing");
  const applied = stack.map((r) => ({ ...r, time: s.find((x) => x.id === r.id)?.to ?? r.time }));
  assert.equal(analyze(applied).filter((i) => i.level === "clash").length, 0, "no clashes left");
  assert.notEqual(slotOf(applied.find((r) => r.id === "fe")!.time), slotOf(applied.find((r) => r.id === "ca")!.time));
  assert.ok(!s.some((x) => x.id === "d"), "untouched routines are not suggested");
  assert.deepEqual(suggestSchedule([R({ name: "Vitamin D3" }), R({ id: "m", name: "Magnesium", time: "21:00" })]), []);
});

test("adherence vs biomarker correlation", () => {
  assert.equal(pearson([1, 2, 3], [2, 4, 6])!.toFixed(3), "1.000");
  assert.equal(pearson([1, 2], [1, 2]), null);
  assert.equal(pearson([1, 1, 1], [1, 2, 3]), null);
  assert.equal(correlationLabel(-0.8), "strong");
  assert.equal(correlationLabel(0.2), "weak");
  const r = R({ id: "d3", startDate: "2026-01-01" });
  const intakes = new Map<string, Intake>();
  for (let d = 1; d <= 31; d++) {
    if (d % 2) continue; // took it every other day in January
    const day = `2026-01-${String(d).padStart(2, "0")}`, at = scheduledAt(day, "08:00");
    intakes.set(`d3@${at}`, { key: `d3@${at}`, supplementId: "d3", clientId: "p", scheduledAt: at, status: "Taken", updatedAt: at });
  }
  assert.equal(adherenceOn(r, intakes, "2026-01-10", 10), 0.5);
  assert.equal(adherenceOn(r, intakes, "2025-12-01", 10), null);
  const series: Biomarker[] = ["2026-01-04", "2026-01-20", "2026-01-31"].map((date, i) => ({ id: String(i), clientId: "p", marker: "vitd", value: 20 + i * 5, unit: "ng/mL", date, updatedAt: i }));
  const out = markerAdherence(series, r, intakes, 30);
  assert.equal(out.points.length, 3);
  assert.ok(out.points.every((p) => p.adherence != null));
});

test("pretty share slugs", () => {
  assert.equal(slugify("Kiểm tra Đậu phụ & Vitamin D3!"), "kiem-tra-dau-phu-vitamin-d3");
  assert.equal(slugify("   "), "");
  const s = buildSlug("Kim Phong", "Morning stack", new Uint8Array([0, 1, 2, 3]));
  assert.equal(s, "kim-phong/morning-stack-abcd");
  const [h, sl] = s.split("/");
  assert.ok(isPrettySlug(h, sl));
  assert.ok(!isPrettySlug("../etc", "x"));
  assert.equal(buildSlug("", "", new Uint8Array([5, 5, 5, 5])), "oak/stack-ffff");
});
