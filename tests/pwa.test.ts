import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { doseAt, doseWrite, planned, takenNext } from "../lib/domain/intake-key.ts";
import { dosesForDay } from "../lib/domain/schedule.ts";
import { MAX_SPANS, scheduleOn, withHistory } from "../lib/domain/schedule-history.ts";
import { utcMinutes } from "../lib/domain/remind.ts";
import type { Intake, Routine } from "../lib/domain/types.ts";
import { circle, committed, pendingCircle, SHARES_NOW, VAULT_CIRCLE_FIELDS, vaultCircleCases, vaultShareCases } from "./fixtures/vault-circles.ts";

type Taken = typeof takenNext;
/** public/sw.js is plain, unbundled JS: run it with an inert `self` (or the given globals) and read its top-level mirror functions. */
function loadSw(globals: Record<string, unknown> = {}) {
  const ctx: Record<string, unknown> = { self: { addEventListener() {} }, TextEncoder, TextDecoder, crypto, ...globals };
  runInNewContext(readFileSync(new URL("../public/sw.js", import.meta.url), "utf8"), ctx);
  return ctx as {
    doseAt: typeof doseAt; planned: typeof planned; doseWrite: typeof doseWrite; takenNext: Taken; utcMinutes: typeof utcMinutes;
    materialize: (r: Routine, now: number) => Routine; writeTaken: (db: unknown, doses: { id: string; at: number }[], now: number) => Promise<void>;
    vaultCircles: (remote: unknown, meta: Rec[]) => unknown; vaultShares: (remote: unknown, meta: Rec[], now: number) => unknown;
    vaultKeys: (secret: string) => Promise<{ id: string; key: CryptoKey }>;
  };
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

// doseWrite is the dashboard's markDose rule; takenNext (notification) is doseWrite("Taken") behind the plan check.
const wrote = (status: Intake["status"]) => ({ key: `vit-d@${at8}`, supplementId: "vit-d", clientId: "me", scheduledAt: at8, status, updatedAt: 9, deletedAt: null });
const writeCases: [string, Routine, Intake | undefined, "Taken" | "Skipped" | null, unknown][] = [
  ["Taken decrements stock", routine, undefined, "Taken", { intake: wrote("Taken"), routine: { ...routine, stock: 28, updatedAt: 9 } }],
  ["Skipped leaves stock", routine, undefined, "Skipped", { intake: wrote("Skipped"), routine: null }],
  ["Taken again moves stock once", routine, intake("Taken"), "Taken", { intake: wrote("Taken"), routine: null }],
  ["Taken to Skipped gives the dose back", routine, intake("Taken"), "Skipped", { intake: wrote("Skipped"), routine: { ...routine, stock: 32, updatedAt: 9 } }],
  ["clearing Taken tombstones it and gives the dose back", routine, intake("Taken"), null, { intake: { ...intake("Taken"), updatedAt: 9, deletedAt: 9 }, routine: { ...routine, stock: 32, updatedAt: 9 } }],
  ["clearing Skipped tombstones it, stock unchanged", routine, intake("Skipped"), null, { intake: { ...intake("Skipped"), updatedAt: 9, deletedAt: 9 }, routine: null }],
  ["clearing a dose never logged writes nothing", routine, undefined, null, { intake: null, routine: null }],
  ["untracked stock leaves the routine alone", { ...routine, stock: null }, intake("Taken"), null, { intake: { ...intake("Taken"), updatedAt: 9, deletedAt: 9 }, routine: null }],
];
for (const [name, r, prev, status, want] of writeCases) {
  test(`doseWrite ${name}: markDose and service worker agree`, () => {
    assert.deepEqual(doseWrite(r, at8, prev, status, 9), want);
    assert.deepEqual(JSON.parse(JSON.stringify(sw.doseWrite(r, at8, prev, status, 9))), want);
  });
}

// The SW re-registers reminder minutes itself (pushsubscriptionchange after the server dropped the row): same rule as the app.
const remindCases: [string, (Partial<Routine> & { remind?: boolean })[], number][] = [
  ["UTC+7, dedupe, sorted", [{ time: "08:00" }, { time: "08:00" }, { time: "21:30" }], -420],
  ["wraps past UTC midnight", [{ time: "23:30" }, { time: "00:15" }], 300],
  ["skips paused, deleted and muted routines", [{ time: "07:00", paused: true }, { time: "07:10", deletedAt: 1 }, { time: "07:20", remind: false }, { time: "07:30" }], 0],
  ["ignores a bad time", [{ time: "later" }, { time: "09:05" }], -60],
];
for (const [name, patches, offset] of remindCases) {
  test(`utcMinutes ${name}: app and service worker agree`, () => {
    const rs = patches.map((p) => ({ ...routine, ...p }) as Routine);
    const want = utcMinutes(rs, offset);
    assert.ok(want.length);
    assert.deepEqual(JSON.parse(JSON.stringify(sw.utcMinutes(rs, offset))), want);
  });
}

// pushsubscriptionchange / swSync with no tab open: a fake IndexedDB (getAll + meta put), fetch and pushManager.
type Rec = { key: string; value: unknown };
type Req = { result?: unknown; onsuccess?: () => void };
type Sub = { endpoint: string } | null;
const OLD = "https://fcm.googleapis.com/fcm/send/old", NEW = "https://fcm.googleapis.com/fcm/send/new";
const later = (f: () => void) => setTimeout(f, 0);
function fakeIdb(meta: Rec[], stores: Record<string, unknown[]> = {}) {
  const db = {
    objectStoreNames: { contains: (s: string) => s === "meta" || s in stores },
    transaction() {
      const tx: { oncomplete?: () => void; objectStore: (s: string) => unknown } = {
        objectStore: (s: string) => ({
          getAll() { const q: Req = {}; later(() => { q.result = (s === "meta" ? meta : stores[s]).slice(); q.onsuccess?.(); }); return q; },
          put(v: Rec) { meta.splice(0, meta.length, ...meta.filter((m) => m.key !== v.key), JSON.parse(JSON.stringify(v))); later(() => tx.oncomplete?.()); },
        }),
      };
      return tx;
    },
    close() {},
  };
  return { open() { const r: Req = {}; later(() => { r.result = db; r.onsuccess?.(); }); return r; } };
}
const routines = [{ ...routine, time: "08:00" }, { ...routine, id: "mg", time: "21:30" }, { ...routine, id: "off", time: "12:00", remind: false }];
async function subscriptionChange(meta: Rec[], event: { oldSubscription?: Sub; newSubscription?: Sub }, current: Sub = null, status: Record<string, number> = {}) {
  const on: Record<string, (e: unknown) => void> = {};
  const calls: [string, unknown][] = [];
  const pushManager = {
    getSubscription: async () => current,
    subscribe: async (o: unknown) => { calls.push(["subscribe", JSON.parse(JSON.stringify(o))]); return { endpoint: NEW }; },
  };
  const fetch = async (url: string, init?: { body?: string }) => {
    calls.push([url, init?.body ? JSON.parse(init.body) : undefined]);
    const s = status[url] ?? 200;
    return url === "/api/push/key" ? { ok: true, status: 200, json: async () => ({ key: "vapid" }) } : { ok: s === 200, status: s };
  };
  loadSw({ self: { addEventListener: (t: string, h: (e: unknown) => void) => { on[t] = h; }, registration: { pushManager } }, fetch, indexedDB: fakeIdb(meta, { routines }) });
  let done: unknown;
  on.pushsubscriptionchange({ ...event, waitUntil: (p: unknown) => { done = p; } });
  await done;
  return calls;
}
const pushMeta = (meta: Rec[]) => meta.find((m) => m.key === "push")?.value;
const move = ["/api/push/move", { endpoint: OLD, to: NEW }];

test("pushsubscriptionchange moves the server row to newSubscription and remembers it", async () => {
  const meta = [{ key: "push", value: { endpoint: OLD } }];
  assert.deepEqual(await subscriptionChange(meta, { oldSubscription: { endpoint: OLD }, newSubscription: { endpoint: NEW } }), [move]);
  assert.deepEqual(pushMeta(meta), { endpoint: NEW });
});
test("pushsubscriptionchange without newSubscription subscribes again with the server key", async () => {
  const meta = [{ key: "push", value: { endpoint: OLD } }];
  assert.deepEqual(await subscriptionChange(meta, { oldSubscription: null, newSubscription: null }), [
    ["/api/push/key", undefined], ["subscribe", { userVisibleOnly: true, applicationServerKey: "vapid" }], move,
  ]);
  assert.deepEqual(pushMeta(meta), { endpoint: NEW });
});
test("pushsubscriptionchange reuses a subscription the browser already made", async () => {
  const meta = [{ key: "push", value: { endpoint: OLD } }];
  assert.deepEqual(await subscriptionChange(meta, {}, { endpoint: NEW }), [move]);
  assert.deepEqual(pushMeta(meta), { endpoint: NEW });
});
test("pushsubscriptionchange does nothing when reminders are off", async () => {
  const meta = [{ key: "push", value: null }];
  assert.deepEqual(await subscriptionChange(meta, { oldSubscription: { endpoint: OLD }, newSubscription: { endpoint: NEW } }), []);
  assert.equal(pushMeta(meta), null);
});
test("pushsubscriptionchange after the server dropped the row registers the new endpoint with local reminder minutes", async () => {
  const meta = [{ key: "push", value: { endpoint: OLD } }];
  const minutes = utcMinutes(routines, new Date().getTimezoneOffset());
  assert.equal(minutes.length, 2);
  assert.deepEqual(await subscriptionChange(meta, { newSubscription: { endpoint: NEW } }, null, { "/api/push/move": 404 }),
    [move, ["/api/push/subscribe", { endpoint: NEW, minutes }]]);
  assert.deepEqual(pushMeta(meta), { endpoint: NEW });
});
test("pushsubscriptionchange server error keeps the old endpoint so the browser can retry", async () => {
  for (const status of [{ "/api/push/move": 500 }, { "/api/push/move": 404, "/api/push/subscribe": 503 }]) {
    const meta = [{ key: "push", value: { endpoint: OLD } }];
    await assert.rejects(subscriptionChange(meta, { newSubscription: { endpoint: NEW } }, null, status));
    assert.deepEqual(pushMeta(meta), { endpoint: OLD });
  }
});

/** The vault route's own 404 (worker/index.ts vault GET with no row). */
const workerNotFound = { ok: false, status: 404, json: async () => ({ error: "not found" }) };
// swSync pushes the same vault fields as lib/sync.ts: the four stores plus circle state, shares and sharesGone from meta.
test("swSync vault push carries circles, circlePins and circleSeen", async () => {
  const puts: string[] = [];
  const fetch = async (_url: string, init?: { method?: string; body?: string }) => {
    if (init?.method === "PUT") { puts.push(JSON.parse(init.body!).blob); return { ok: true, status: 200 }; }
    return workerNotFound;
  };
  const meta = [{ key: "syncKey", value: "OAK-ABCD-EFGH-JKLM" }, { key: "circles", value: [circle, pendingCircle] }, { key: "circlePins", value: { c9: "pub9" } }, { key: "circleSeen", value: { c9: 6 } }];
  const ctx = loadSw({ fetch, btoa, atob, indexedDB: fakeIdb(meta, { profiles: [], routines: [routine], intakes: [], biomarkers: [] }) });
  await (ctx as unknown as { swSync: () => Promise<void> }).swSync();
  assert.equal(puts.length, 1);
  const [iv, ct] = puts[0].split(".");
  const unb = (s: string) => Buffer.from(s, "base64url");
  const { key } = await ctx.vaultKeys("OAK-ABCD-EFGH-JKLM");
  const snap = JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb(iv) }, key, unb(ct))));
  assert.deepEqual(Object.keys(snap).sort(), ["biomarkers", "circlePins", "circleSeen", "circles", "intakes", "profiles", "routines", "shares", "sharesGone", "v"]);
  assert.deepEqual(snap.circles, [circle, { ...pendingCircle, sign: committed }]);
  assert.deepEqual(snap.circlePins, { c9: "pub9" });
  assert.deepEqual(snap.circleSeen, { c9: 6 });
  assert.equal(meta.find((m) => m.key === "syncedVault")?.value, (await ctx.vaultKeys("OAK-ABCD-EFGH-JKLM")).id, "remembered after the push");
});
// Contract C2 in the worker: the vault route's 404 on the id this device synced before stops like a 410 (key left for
// the tab's syncNow); a 404 that is not the worker's throws so the browser retries, and neither pushes.
async function swSyncWith(get: unknown, syncedBefore: boolean) {
  const calls: string[] = [];
  const fetch = async (_url: string, init?: { method?: string }) => { calls.push(init?.method ?? "GET"); return get; };
  const meta: Rec[] = [{ key: "syncKey", value: "OAK-ABCD-EFGH-JKLM" }];
  const ctx = loadSw({ fetch, btoa, atob, indexedDB: fakeIdb(meta, { profiles: [], routines: [], intakes: [], biomarkers: [] }) });
  if (syncedBefore) meta.push({ key: "syncedVault", value: (await ctx.vaultKeys("OAK-ABCD-EFGH-JKLM")).id });
  const run = (ctx as unknown as { swSync: () => Promise<void> }).swSync();
  return { run, calls, meta };
}
test("swSync on the worker's 404 for a vault this device synced before: no push, no throw, sync key left", async () => {
  const { run, calls, meta } = await swSyncWith(workerNotFound, true);
  await run;
  assert.deepEqual(calls, ["GET"]);
  assert.equal(meta.find((m) => m.key === "syncKey")?.value, "OAK-ABCD-EFGH-JKLM");
});
test("swSync on a 404 that is not the worker's: throws for a browser retry, no push", async () => {
  const { run, calls } = await swSyncWith({ ok: false, status: 404, json: async () => { throw new SyntaxError("<html>"); } }, false);
  await assert.rejects(run, /vault 404/);
  assert.deepEqual(calls, ["GET"]);
});
// A deleted vault (server tombstone) answers 410: the worker stops without a push and without throwing (no browser
// retry) and leaves the sync key, so the next tab syncNow hits the 410, turns sync off and the SyncAgent says so once.
for (const [name, status] of [["GET", { get: 410 }], ["PUT", { get: 404, put: 410 }]] as const) {
  test(`swSync on a deleted vault (${name} 410): no push, no throw, sync key left for the tab`, async () => {
    const calls: string[] = [];
    const fetch = async (_url: string, init?: { method?: string }) => {
      const method = init?.method ?? "GET";
      calls.push(method);
      return { ok: false, status: method === "PUT" ? (status as { put?: number }).put : status.get, json: async () => ({ error: "not found" }) };
    };
    const meta: Rec[] = [{ key: "syncKey", value: "OAK-ABCD-EFGH-JKLM" }];
    const ctx = loadSw({ fetch, btoa, atob, indexedDB: fakeIdb(meta, { profiles: [], routines: [], intakes: [], biomarkers: [] }) });
    await (ctx as unknown as { swSync: () => Promise<void> }).swSync();
    assert.deepEqual(calls, name === "GET" ? ["GET"] : ["GET", "PUT"], "no push after the 410");
    assert.equal(meta.find((m) => m.key === "syncKey")?.value, "OAK-ABCD-EFGH-JKLM", "the tab's syncNow turns sync off");
  });
}
// The shared vault fixture, through the SW mirror (tests/circles.test.ts runs the same cases through lib/member-keys.ts).
for (const { name, remote, local, want } of vaultCircleCases) {
  test(`vaultCircles ${name}`, () => {
    const meta = Object.entries(local).map(([key, value]) => ({ key, value }));
    const pushed = JSON.parse(JSON.stringify(sw.vaultCircles(remote, meta)));
    assert.deepEqual(Object.keys(pushed).sort(), VAULT_CIRCLE_FIELDS, "no circle field dropped");
    assert.deepEqual(pushed, want);
  });
}
// The shared share-list fixture through the SW mirror (tests/sync.test.ts runs the same cases through syncNow).
for (const { name, remote, local, want } of vaultShareCases) {
  test(`vaultShares ${name}`, () => {
    const meta = [{ key: "shares", value: structuredClone(local.shares) }, { key: "sharesGone", value: structuredClone(local.sharesGone) }];
    assert.deepEqual(JSON.parse(JSON.stringify(sw.vaultShares(remote, meta, SHARES_NOW))), want);
  });
}

// Schedule history (docs/feat/schedule-history.md) rides inside the routine record; public/sw.js carries it as stored,
// except that its Taken write first materializes the implicit span (mirror of withHistory(r, today)). These pin that,
// plus the accepted LWW-per-record residual.
type Row = Record<string, unknown>;
const spans = [{ from: "2026-10-01", time: "07:00", startDate: "2026-10-01", cycle: daily, paused: false }, { from: "2026-10-05", time: "08:00", startDate: "2026-10-01", cycle: daily, paused: false }];
/** IndexedDB stand-in with get/put per store, enough for sw.js mergeInto and writeTaken: like IndexedDB, the transaction
 *  completes only once no request is pending (a get issued from a get's onsuccess keeps it open). */
function memDb(stores: Record<string, Row[]>) {
  const id = (s: string) => (s === "intakes" ? "key" : "id");
  return {
    objectStoreNames: { contains: (s: string) => s in stores },
    transaction() {
      let pending = 0, done = false;
      const settle = () => later(() => { if (!pending && !done) { done = true; tx.oncomplete?.(); } });
      const tx: { oncomplete?: () => void; objectStore: (s: string) => unknown } = {
        objectStore: (s: string) => ({
          get(k: unknown) { const q: Req = {}; pending++; later(() => { q.result = stores[s].find((x) => x[id(s)] === k); q.onsuccess?.(); pending--; settle(); }); return q; },
          put(v: Row) { stores[s] = [...stores[s].filter((x) => x[id(s)] !== v[id(s)]), JSON.parse(JSON.stringify(v))]; },
        }),
      };
      setTimeout(settle, 5);
      return tx;
    },
  };
}
const mergeSw = async (local: Row[], remote: Row[]) => {
  const stores = { routines: local };
  await (loadSw() as unknown as { mergeInto: (db: unknown, snap: unknown) => Promise<void> }).mergeInto(memDb(stores), { routines: remote });
  return stores.routines;
};

test("schedule history: the service worker's doseWrite, vault merge and vault push keep a routine's history as stored", async () => {
  const r = { ...routine, history: spans } as Routine;
  assert.deepEqual(JSON.parse(JSON.stringify(sw.doseWrite(r, at8, undefined, "Taken", 9))).routine.history, spans);
  assert.deepEqual((await mergeSw([{ ...routine }], [{ ...r, updatedAt: 5 }]))[0].history, spans);
  const puts: string[] = [];
  const fetch = async (_url: string, init?: { method?: string; body?: string }) => {
    if (init?.method === "PUT") { puts.push(JSON.parse(init.body!).blob); return { ok: true, status: 200 }; }
    return workerNotFound;
  };
  const ctx = loadSw({ fetch, btoa, atob, indexedDB: fakeIdb([{ key: "syncKey", value: "OAK-ABCD-EFGH-JKLM" }], { profiles: [], routines: [r], intakes: [], biomarkers: [] }) });
  await (ctx as unknown as { swSync: () => Promise<void> }).swSync();
  const [iv, ct] = puts[0].split(".");
  const { key } = await ctx.vaultKeys("OAK-ABCD-EFGH-JKLM");
  const snap = JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: Buffer.from(iv, "base64url") }, key, Buffer.from(ct, "base64url"))));
  assert.deepEqual(snap.routines[0].history, spans);
});

test("schedule history: last writer wins per routine, so a newer stock-only record drops an older record's new span (accepted residual)", async () => {
  const got = await mergeSw([{ ...routine, history: spans, updatedAt: 5 }], [{ ...routine, stock: 28, updatedAt: 7 }]);
  assert.equal(got[0].stock, 28);
  assert.equal(got[0].history, undefined);
});

const oct = (d: number, h = 12) => new Date(2026, 9, d, h, 0).getTime();
const many = Array.from({ length: MAX_SPANS }, (_, i) => ({ ...spans[0], from: new Date(Date.UTC(2026, 5, 1 + i)).toISOString().slice(0, 10), time: i % 2 ? "09:00" : "07:00" }));
const materializeCases: [string, Routine][] = [
  ["implicit span appended from the updatedAt day", { ...routine, history: [spans[0]], updatedAt: oct(5) } as Routine],
  ["implicit span on the last span's day replaces it (one span left: no history)", { ...routine, history: [spans[0]], updatedAt: oct(1) } as Routine],
  ["last span equals the current fields: unchanged", { ...routine, history: spans, updatedAt: oct(9) } as Routine],
  ["no history: unchanged, still no history", { ...routine, updatedAt: oct(5) } as Routine],
  ["updatedAt after today (clock ahead) counts from today", { ...routine, history: [spans[0]], updatedAt: oct(20) } as Routine],
  ["a span from a later day is dropped", { ...routine, time: "09:00", history: [spans[0], { ...spans[1], from: "2026-10-20" }], updatedAt: oct(5) } as Routine],
  ["invalid history is dropped", { ...routine, history: [{ from: "x" }], updatedAt: oct(5) } as unknown as Routine],
  ["paused counts as a schedule change", { ...routine, time: "07:00", paused: true, history: [spans[0]], updatedAt: oct(6) } as Routine],
  ["deleted counts as a schedule change", { ...routine, time: "07:00", deletedAt: oct(6), history: [spans[0]], updatedAt: oct(6) } as Routine],
  ["over MAX_SPANS: the oldest fold into the first", { ...routine, history: many, updatedAt: oct(5) } as Routine],
];
for (const [name, r] of materializeCases) {
  test(`schedule history materialize ${name}: app (withHistory) and service worker agree`, () => {
    const now = at8 + 60_000;
    assert.deepEqual(JSON.parse(JSON.stringify(sw.materialize(r, now))), JSON.parse(JSON.stringify(withHistory(r, "2026-10-11"))));
  });
}

const swTaken = async (r: Row, now: number) => {
  const stores: Record<string, Row[]> = { routines: [r], intakes: [] };
  await loadSw().writeTaken(memDb(stores), [{ id: "vit-d", at: at8 }], now);
  return { routine: stores.routines[0] as unknown as Routine, intakes: stores.intakes };
};

test("schedule history: the notification's Taken stores the implicit span before moving updatedAt, so past days keep their schedule", async () => {
  const r = { ...routine, history: [spans[0]], updatedAt: oct(5) } as Routine;
  assert.equal(scheduleOn(r, "2026-10-08").time, "08:00", "current fields from the updatedAt day (Oct 5)");
  const { routine: next, intakes } = await swTaken(r as unknown as Row, at8 + 60_000);
  assert.equal(intakes.length, 1);
  assert.equal(next.stock, 28);
  assert.equal(next.updatedAt, at8 + 60_000);
  assert.deepEqual(next.history, spans, "implicit span stored from Oct 5");
  assert.equal(scheduleOn(next, "2026-10-08").time, "08:00", "still from Oct 5, not from the Taken day");
  assert.equal(scheduleOn(next, "2026-10-03").time, "07:00");
});

test("schedule history: the notification's Taken on a routine without history writes no history", async () => {
  const { routine: next } = await swTaken({ ...routine, updatedAt: oct(5) }, at8 + 60_000);
  assert.equal(next.stock, 28);
  assert.equal("history" in next, false);
});
