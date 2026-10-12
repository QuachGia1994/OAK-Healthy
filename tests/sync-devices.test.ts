// Pins for lib/sync.ts syncNow with the real lib/db.ts (LWW mergeSnapshot, snapshot): the 409 retry, the 403 paths,
// last-writer-wins (with its deterministic tie-break, twin of public/sw.js) between two devices that edited offline, and
// the user-facing text for every sync error (syncErrorText). tests/sync.test.ts stubs lib/db.ts; here only Dexie is
// replaced, by an in-memory stand-in with one table set per device.
// PROCESS-WIDE: the register() loader hooks and the globalThis.fetch / window overrides below apply to the whole process.
// That is safe because `node --test` runs each test file in its own process; do not merge this file into another one.
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { register } from "node:module";
import { deriveVault, newSyncKey, open } from "../lib/crypto.ts";
import { VI } from "../lib/i18n-vi.ts";
import type { Biomarker, Intake, Profile, Routine } from "../lib/domain/types.ts";

const fakeDexie = `
const stores = (globalThis.oakStores ??= {});
const rows = (t) => { const d = (stores[globalThis.oakDevice] ??= {}); return (d[t] ??= new Map()); };
class Table {
  constructor(name, pk) { this.name = name; this.pk = pk; }
  async get(k) { const v = rows(this.name).get(k); return v === undefined ? undefined : structuredClone(v); }
  async put(v) { rows(this.name).set(v[this.pk], structuredClone(v)); return v[this.pk]; }
  async add(v) { return this.put(v); }
  async count() { return rows(this.name).size; }
  async toArray() { return [...rows(this.name).values()].map((v) => structuredClone(v)); }
  where(f) { const t = this; return { equals: (x) => ({ count: async () => [...rows(t.name).values()].filter((v) => v[f] === x).length }) }; }
}
export default class Dexie {
  constructor(name) { this.dbName = name; }
  version() { return { stores: (s) => { for (const [t, def] of Object.entries(s)) this[t] = new Table(t, def.split(",")[0].trim()); return this; } }; }
  transaction(...args) { return args[args.length - 1](); }
}`;
const hooks = `
export async function resolve(spec, ctx, next) {
  if (spec === "dexie") return { url: "oak-fake:dexie", shortCircuit: true };
  if (/^\\.\\.?\\//.test(spec) && !/\\.[cm]?[jt]sx?$/.test(spec)) return next(spec + ".ts", ctx);
  return next(spec, ctx);
}
export async function load(url, ctx, next) {
  return url === "oak-fake:dexie" ? { format: "module", shortCircuit: true, source: ${JSON.stringify(fakeDexie)} } : next(url, ctx);
}`;
register(`data:text/javascript,${encodeURIComponent(hooks)}`, import.meta.url);
const g = globalThis as unknown as { oakDevice: string; oakStores: Record<string, unknown>; window: EventTarget };
g.window = new EventTarget(); // lib/db.ts changed() dispatches oak:changed
const dbm = await import("../lib/db.ts");
const { db, blankRoutine } = dbm;
const syncm = await import("../lib/sync.ts");
const { syncNow } = syncm;
const { tr } = await import("../lib/i18n.ts");

/** The vault route (worker/index.ts): GET 200 {rev, blob} or its own 404; PUT with baseRev = current rev, else 409. */
const server = {
  vault: null as { rev: number; blob: string } | null, calls: [] as string[],
  beforePut: null as null | (() => Promise<void>), force: {} as { get?: number; put?: number; throw?: Error; blob?: string },
};
const reply = (status: number, body: unknown) => ({ ok: status >= 200 && status < 300, status, json: async () => body });
globalThis.fetch = (async (_url: string, init?: { method?: string; body?: string }) => {
  const method = init?.method ?? "GET";
  server.calls.push(`${g.oakDevice}:${method}`);
  if (server.force.throw) throw server.force.throw;
  if (method === "GET") {
    if (server.force.get) return reply(server.force.get, { error: "forbidden" });
    if (server.force.blob) return reply(200, { rev: 1, blob: server.force.blob });
    return server.vault ? reply(200, server.vault) : reply(404, { error: "not found" });
  }
  const hook = server.beforePut; server.beforePut = null;
  if (hook) await hook();
  if (server.force.put) return reply(server.force.put, { error: "forced" });
  const { baseRev, blob } = JSON.parse(init!.body!) as { baseRev: number; blob: string };
  if ((server.vault?.rev ?? 0) !== baseRev) return reply(409, { error: "conflict" });
  server.vault = { rev: baseRev + 1, blob };
  return reply(200, { rev: server.vault.rev });
}) as unknown as typeof fetch;

let key = "";
/** Run fn on one device; the module's single db reads that device's tables. */
const on = async <T>(dev: string, fn: () => Promise<T>) => { g.oakDevice = dev; return fn(); };
const sync = (dev: string) => on(dev, () => syncNow(key));
const routines = (dev: string) => on(dev, () => db.routines.toArray() as Promise<Routine[]>);
const meta = (dev: string, k: string) => on(dev, async () => (await db.meta.get(k))?.value);
const put = (dev: string, r: Routine) => on(dev, () => db.routines.put(r));
type Vault = { routines: Routine[]; profiles: Profile[]; intakes: Intake[]; biomarkers: Biomarker[] };
const vault = async () => open<Vault>((await deriveVault(key)).key, server.vault!.blob);
const vaultRoutines = async () => (await vault()).routines;
const calls = (dev: string) => server.calls.filter((c) => c.startsWith(`${dev}:`)).map((c) => c.split(":")[1]);
const TABLES = ["profiles", "routines", "intakes", "biomarkers"] as const;
/** Every health table of a device (or the vault), as plain JSON, for whole-state comparisons. */
const state = async (dev: string | null) => {
  const src = dev === null ? await vault() : await on(dev, async () => Object.fromEntries(await Promise.all(TABLES.map(async (t) => [t, await db[t].toArray()]))) as Vault);
  return JSON.parse(JSON.stringify(Object.fromEntries(TABLES.map((t) => [t, [...(src[t] ?? [])].sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)))]))));
};

const base = (name: string, updatedAt: number, id = "r-1"): Routine => ({ ...blankRoutine("p-1"), id, name, updatedAt });
const profile = (name: string, updatedAt: number): Profile => ({ id: "p-1", name, color: "#1F6B4D", createdAt: 1, updatedAt });
const intake = (status: Intake["status"], updatedAt: number): Intake =>
  ({ key: "r-1@1000", supplementId: "r-1", clientId: "p-1", scheduledAt: 1000, status, updatedAt, deletedAt: null } as Intake);
const biomarker = (value: number, updatedAt: number): Biomarker =>
  ({ id: "b-1", clientId: "p-1", marker: "vitd", value, unit: "ng/mL", date: "2026-10-01", updatedAt, deletedAt: null } as Biomarker);
/** Put one record per table on a device. */
const putAll = (dev: string, p: Profile, r: Routine, i: Intake, b: Biomarker) =>
  on(dev, async () => { await db.profiles.put(p); await db.routines.put(r); await db.intakes.put(i); await db.biomarkers.put(b); });

/** Both devices linked to one vault that already holds routine r-1 ("Base", updatedAt 100). */
async function linkedPair() {
  await put("A", base("Base", 100)); await put("B", base("Base", 100));
  for (const d of ["A", "B"]) await on(d, () => db.meta.put({ key: "syncKey", value: key }));
  assert.equal((await sync("A")).ok, true); assert.equal((await sync("B")).ok, true);
  server.calls = [];
}

beforeEach(() => {
  for (const k of Object.keys(g.oakStores ?? {})) delete g.oakStores[k];
  Object.assign(server, { vault: null, calls: [], beforePut: null, force: {} });
  key = newSyncKey();
});

// ---- last-writer-wins: the same record edited offline on two devices ----

test("LWW: the older edit syncs first, the newer one wins on both devices and in the vault, one routine, older dropped", async () => {
  await linkedPair();
  await put("A", base("Older (A)", 1000)); await put("B", base("Newer (B)", 2000));
  assert.equal((await sync("A")).ok, true);
  const b = await sync("B");
  assert.equal(b.ok && b.pulled, 0, "B pulls A's older copy and keeps its own");
  const a = await sync("A");
  assert.equal(a.ok && a.pulled, 1, "A takes B's newer copy");
  for (const rs of [await routines("A"), await routines("B"), await vaultRoutines()]) {
    assert.deepEqual(rs.map((r) => [r.id, r.name, r.updatedAt]), [["r-1", "Newer (B)", 2000]]);
  }
});

test("LWW: the newer edit syncs first, the older device takes it on its next sync and does not push its own back", async () => {
  await linkedPair();
  await put("A", base("Older (A)", 1000)); await put("B", base("Newer (B)", 2000));
  assert.equal((await sync("B")).ok, true);
  const a = await sync("A");
  assert.equal(a.ok && a.pulled, 1);
  assert.equal((await sync("B")).ok, true);
  for (const rs of [await routines("A"), await routines("B"), await vaultRoutines()]) {
    assert.deepEqual(rs.map((r) => [r.id, r.name]), [["r-1", "Newer (B)"]]);
  }
});

test("LWW: a newer delete (tombstone) beats an older offline edit, so the routine is not resurrected", async () => {
  await linkedPair();
  await put("A", { ...base("Base", 3000), deletedAt: 3000 }); await put("B", base("Edited (B)", 2000));
  await sync("B"); await sync("A"); await sync("B");
  for (const rs of [await routines("A"), await routines("B"), await vaultRoutines()]) {
    assert.deepEqual(rs.map((r) => [r.id, r.deletedAt]), [["r-1", 3000]]);
  }
});

test("LWW: edits to different routines on two devices both survive (no loss, no duplicate)", async () => {
  await linkedPair();
  await put("A", base("Only on A", 1000, "r-a")); await put("B", base("Only on B", 1000, "r-b"));
  await sync("A"); await sync("B"); await sync("A");
  for (const dev of ["A", "B"]) assert.deepEqual((await routines(dev)).map((r) => r.id).sort(), ["r-1", "r-a", "r-b"], dev);
  assert.deepEqual((await vaultRoutines()).map((r) => r.id).sort(), ["r-1", "r-a", "r-b"]);
});

test("LWW on every table: the newer profile, intake and biomarker win on both devices and in the vault", async () => {
  await linkedPair();
  await putAll("A", profile("Old name", 1000), base("Old (A)", 1000), intake("Skipped", 1000), biomarker(20, 1000));
  await putAll("B", profile("New name", 2000), base("New (B)", 2000), intake("Taken", 2000), biomarker(35, 2000));
  await sync("A"); await sync("B"); await sync("A");
  const want = await state("B");
  assert.deepEqual(want.profiles.map((p: Profile) => p.name), ["New name"]);
  assert.deepEqual(want.intakes.map((i: Intake) => i.status), ["Taken"]);
  assert.deepEqual(want.biomarkers.map((b: Biomarker) => b.value), [35]);
  assert.deepEqual(await state("A"), want);
  assert.deepEqual(await state(null), want);
});

// F1 (lane-37): on an exact updatedAt tie the record with the larger sorted-key JSON (lib/db.ts stable) wins on every
// device, so both devices and the vault converge whatever order they sync in; identical copies stay a no-op.
for (const order of [["A", "B", "A"], ["B", "A", "B"]]) {
  test(`LWW tie (same updatedAt on every table), sync order ${order.join("→")}: A, B and the vault converge on one copy`, async () => {
    await linkedPair();
    await putAll("A", profile("Tie A", 1500), base("Tie (A)", 1500), intake("Skipped", 1500), biomarker(20, 1500));
    await putAll("B", profile("Tie B", 1500), base("Tie (B)", 1500), intake("Taken", 1500), biomarker(35, 1500));
    for (const d of order) assert.equal((await sync(d)).ok, true);
    const a = await state("A");
    assert.deepEqual(await state("B"), a, "both devices hold the same records");
    assert.deepEqual(await state(null), a, "and so does the vault");
    for (const t of TABLES) assert.equal(a[t].length, 1, `${t}: one copy`);
    // the winner is the larger stable() string, the same rule on both sides
    assert.deepEqual([a.profiles[0].name, a.routines[0].name, a.intakes[0].status, a.biomarkers[0].value], ["Tie B", "Tie (B)", "Taken", 35]);
    for (const d of ["A", "B"]) { const r = await sync(d); assert.equal(r.ok && r.pulled, 0, `${d}: equal copies are not pulled again`); }
  });
}

// ---- the tie-break rule itself: lib/db.ts newer/stable and their public/sw.js twins (mergeInto) ----

type Rec = { updatedAt: number } & Record<string, unknown>;
const sw = (() => {
  const ctx: Record<string, unknown> = { self: { addEventListener() {} }, TextEncoder, TextDecoder, crypto };
  runInNewContext(readFileSync(new URL("../public/sw.js", import.meta.url), "utf8"), ctx);
  return ctx as { newer?: (cur: Rec | undefined, item: Rec) => boolean; stable?: (x: unknown) => string };
})();
const tieCases: [string, Rec | undefined, Rec][] = [
  ["no local copy", undefined, { id: "x", updatedAt: 1 }],
  ["incoming newer", { id: "x", n: "a", updatedAt: 1 }, { id: "x", n: "b", updatedAt: 2 }],
  ["incoming older", { id: "x", n: "b", updatedAt: 2 }, { id: "x", n: "a", updatedAt: 1 }],
  ["tie, incoming larger", { id: "x", n: "a", updatedAt: 5 }, { id: "x", n: "b", updatedAt: 5 }],
  ["tie, incoming smaller", { id: "x", n: "b", updatedAt: 5 }, { id: "x", n: "a", updatedAt: 5 }],
  ["tie, identical", { id: "x", n: "a", updatedAt: 5 }, { id: "x", n: "a", updatedAt: 5 }],
  ["tie, same content in another key order", { updatedAt: 5, n: "a", id: "x", c: { q: 1, p: 2 } }, { id: "x", c: { p: 2, q: 1 }, n: "a", updatedAt: 5 }],
  ["tie, nested difference", { id: "x", c: { p: 1 }, updatedAt: 5 }, { id: "x", c: { p: 2 }, updatedAt: 5 }],
  ["tie, undefined field vs missing", { id: "x", h: undefined, updatedAt: 5 }, { id: "x", updatedAt: 5 }],
];
for (const [name, cur, item] of tieCases) {
  test(`LWW rule ${name}: app (lib/db.ts newer) and service worker (sw.js newer) agree`, () => {
    assert.equal(typeof dbm.newer, "function", "lib/db.ts exports newer");
    assert.equal(typeof sw.newer, "function", "public/sw.js has newer");
    assert.equal(sw.newer!(cur, item), dbm.newer(cur, item));
    if (cur) assert.equal(dbm.newer(cur, item) && dbm.newer(item, cur), false, "never both ways: a total order");
  });
}
test("LWW rule: stable() is key-order independent and identical in the app and the service worker", () => {
  const a = { b: 1, a: [{ y: 2, x: 1 }], c: { e: null, d: "s" } }, b = { c: { d: "s", e: null }, a: [{ x: 1, y: 2 }], b: 1 };
  assert.equal(dbm.stable(a), dbm.stable(b));
  assert.equal(sw.stable!(a), dbm.stable(a));
  assert.notEqual(JSON.stringify(a), JSON.stringify(b), "plain JSON.stringify would differ");
});

// ---- 409: another device pushed between this device's GET and PUT ----

test("409: the push loses the race, syncNow pulls again, merges the other device's change and pushes all; pulled sums both pulls", async () => {
  await linkedPair();
  await put("A", base("From A, before", 1000, "r-a")); await sync("A"); // B's first GET pulls this one
  await put("A", base("From A, during", 1000, "r-b")); await put("B", base("Edited (B)", 2000));
  const revBefore = server.vault!.rev;
  // A completes a whole sync while B's first PUT is in flight, then B's request continues.
  server.beforePut = async () => { assert.equal((await sync("A")).ok, true); g.oakDevice = "B"; };
  const b = await sync("B");
  assert.deepEqual(calls("B"), ["GET", "PUT", "GET", "PUT"], "one retry after the 409");
  assert.equal(b.ok, true);
  assert.equal(b.ok && b.rev, revBefore + 2, "A's push, then B's");
  assert.equal(b.ok && b.pulled, 2, "r-a on the first pull + r-b on the retry (pulled +=)");
  assert.deepEqual((await vaultRoutines()).map((r) => [r.id, r.name]).sort(), [["r-1", "Edited (B)"], ["r-a", "From A, before"], ["r-b", "From A, during"]]);
  assert.equal(typeof (await meta("B", "lastSync")), "number");
});

test("409 three times: 'Conflict, try again', 3 pulls + 3 pushes, local edits kept, lastSync not moved, sync stays on", async () => {
  await linkedPair();
  await on("B", () => db.meta.put({ key: "lastSync", value: 1 }));
  await put("B", base("Edited (B)", 2000));
  server.force.put = 409;
  const b = await sync("B");
  assert.deepEqual(b, { ok: false, error: "Conflict, try again" });
  assert.deepEqual(calls("B"), ["GET", "PUT", "GET", "PUT", "GET", "PUT"]);
  assert.deepEqual((await routines("B")).map((r) => r.name), ["Edited (B)"], "nothing lost locally; the next sync pushes it");
  assert.equal(await meta("B", "lastSync"), 1);
  assert.equal(await meta("B", "syncKey"), key);
});

// ---- 403: the worker rejects the bearer (wrong or rotated key) ----

test("403 on GET: 'Sync key rejected', no push, local data and the sync key kept, no background retry, retried on the next sync", async () => {
  await linkedPair();
  await put("B", base("Edited (B)", 2000));
  const before = { vault: server.vault, lastSync: await meta("B", "lastSync") };
  server.force.get = 403;
  const b = await sync("B");
  assert.deepEqual(b, { ok: false, error: "Sync key rejected" }, "not gone: sync is not turned off");
  assert.deepEqual(calls("B"), ["GET"]);
  assert.equal(server.vault, before.vault, "nothing pushed");
  assert.deepEqual((await routines("B")).map((r) => r.name), ["Edited (B)"]);
  assert.equal(await meta("B", "syncKey"), key);
  assert.equal(await meta("B", "lastSync"), before.lastSync);
  assert.equal((syncm.NETWORK_ERROR ?? /fetch|network|load failed/i).test(b.ok ? "" : b.error), false, "SyncAgent does not register Background Sync for it");
  server.force = {};
  assert.equal((await sync("B")).ok, true, "the next trigger (focus, online, a change) just tries again");
});

test("403 on PUT: 'Push failed 403', the pulled vault is merged locally, local edits kept, lastSync not moved, no retry", async () => {
  await linkedPair();
  await put("A", base("From A", 1000, "r-a")); await sync("A");
  await put("B", base("Edited (B)", 2000));
  const lastSync = await meta("B", "lastSync");
  server.force.put = 403;
  const b = await sync("B");
  assert.deepEqual(b, { ok: false, error: "Push failed 403" });
  assert.deepEqual(calls("B"), ["GET", "PUT"], "no retry after a 403");
  assert.deepEqual((await routines("B")).map((r) => [r.id, r.name]).sort(), [["r-1", "Edited (B)"], ["r-a", "From A"]]);
  assert.equal(await meta("B", "lastSync"), lastSync);
  assert.equal(await meta("B", "syncKey"), key);
});

// ---- F2 (lane-37): what the user reads for each error. r.error stays raw (SyncAgent's retry filter and the log use it);
// syncErrorText turns it into a translated sentence and never shows raw server or exception text. ----

const viText = (raw: string) => syncm.syncErrorText(raw, (s: string, v?: Record<string, string | number>) => tr("vi", s, v));
const enText = (raw: string) => syncm.syncErrorText(raw, (s: string, v?: Record<string, string | number>) => tr("en", s, v));
const viValues = new Set(Object.values(VI));
/** One raw error per path, produced by syncNow itself. */
const errorPaths: [string, () => void, RegExp][] = [
  ["409 ×3", () => { server.force.put = 409; }, /^Conflict, try again$/],
  ["403 on GET", () => { server.force.get = 403; }, /^Sync key rejected$/],
  ["403 on PUT", () => { server.force.put = 403; }, /^Push failed 403$/],
  ["500 on GET", () => { server.force.get = 500; }, /^Server 500$/],
  ["offline, Chromium", () => { server.force.throw = new TypeError("Failed to fetch"); }, /^Failed to fetch$/],
  ["offline, Safari", () => { server.force.throw = new TypeError("Load failed"); }, /^Load failed$/],
  ["offline, Firefox", () => { server.force.throw = new TypeError("NetworkError when attempting to fetch resource."); }, /^NetworkError/],
  ["unknown (vault does not decrypt)", () => { server.force.blob = "AAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAA"; }, /./],
];
for (const [name, arrange, raw] of errorPaths) {
  test(`sync error ${name}: r.error stays raw, the user reads a Vietnamese sentence with no raw text`, async () => {
    assert.equal(typeof syncm.syncErrorText, "function", "lib/sync.ts exports syncErrorText");
    await linkedPair();
    arrange();
    const r = await sync("B");
    assert.equal(r.ok, false);
    const error = r.ok ? "" : r.error;
    assert.match(error, raw, "raw error kept for the retry filter and the log");
    const vi = viText(error);
    assert.ok(viValues.has(vi.replace(/\b\d{3}\b/, "{status}")), `a VI string: ${vi}`);
    assert.ok(!/fetch|load failed|networkerror|operation|push failed|server \d/i.test(vi), `no raw text: ${vi}`);
    assert.ok(!/fetch|load failed|networkerror|operation|push failed|server \d/i.test(enText(error)), `English is also clean: ${enText(error)}`);
  });
}
test("sync errors: the three browser fetch failures are network errors (Background Sync retry) and read as 'can't reach the server' (offline or server down)", () => {
  for (const raw of ["Failed to fetch", "Load failed", "NetworkError when attempting to fetch resource."]) {
    assert.equal(syncm.NETWORK_ERROR.test(raw), true, raw);
    assert.equal(enText(raw), "Can't reach the server. Check your connection and try again.");
    assert.equal(viText(raw), "Không kết nối được máy chủ. Hãy kiểm tra kết nối rồi thử lại.");
  }
  assert.equal(viText("Server 502"), tr("vi", "Server error {status}. Try again later.", { status: 502 }));
  assert.equal(viText("Request failed (500)"), tr("vi", "Server error {status}. Try again later.", { status: 500 }), "Delete everything's lib/account.ts error");
  assert.equal(viText("Your synced data was deleted on another device, so sync is off here."), VI["Your synced data was deleted on another device, so sync is off here."]);
  assert.equal(viText("{\"error\":\"boom\"} at worker.js:1"), VI["Something went wrong. Try again."], "unknown raw text is never shown");
});

test("sync errors: a 403 on PUT reads as a rejected key, like the 403 on GET, not as a server error", () => {
  for (const raw of ["Sync key rejected", "Push failed 403"]) {
    assert.equal(viText(raw), "Khoá đồng bộ bị từ chối", raw);
    assert.equal(enText(raw), "Sync key rejected", raw);
  }
});

// Loose source pins (no exact formatting): the UI reads errors through syncErrorText and retries on NETWORK_ERROR.
const settings = readFileSync(new URL("../components/settings.tsx", import.meta.url), "utf8");
test("settings.tsx: the sync agent retries on NETWORK_ERROR and every shown sync / delete error goes through syncErrorText", () => {
  assert.match(settings, /NETWORK_ERROR\.test\(\s*r\.error\s*\)/, "SyncAgent's background-retry filter");
  assert.doesNotMatch(settings, /\bt\(\s*r\.error\s*\)|\btr\(\s*r\.error\s*\)|\bt\(\s*failed\.error\s*\)/, "no raw error passed straight to t()");
  assert.ok((settings.match(/syncErrorText\(/g) ?? []).length >= 3, "manual sync toast, vault-gone toast, delete-everything error");
});
