import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { doseAt, doseWrite, planned, takenNext } from "../lib/domain/intake-key.ts";
import { dosesForDay } from "../lib/domain/schedule.ts";
import { utcMinutes } from "../lib/domain/remind.ts";
import type { Intake, Routine } from "../lib/domain/types.ts";

type Taken = typeof takenNext;
/** public/sw.js is plain, unbundled JS: run it with an inert `self` (or the given globals) and read its top-level mirror functions. */
function loadSw(globals: Record<string, unknown> = {}) {
  const ctx: Record<string, unknown> = { self: { addEventListener() {} }, TextEncoder, TextDecoder, crypto, ...globals };
  runInNewContext(readFileSync(new URL("../public/sw.js", import.meta.url), "utf8"), ctx);
  return ctx as {
    doseAt: typeof doseAt; planned: typeof planned; doseWrite: typeof doseWrite; takenNext: Taken; utcMinutes: typeof utcMinutes;
    vaultCircles: (remote: unknown, meta: Rec[]) => unknown; vaultKeys: (secret: string) => Promise<{ key: CryptoKey }>;
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

// swSync pushes the same vault fields as lib/sync.ts: the four stores plus circle state from meta.
const circle = { id: "c1", name: "Family", updatedAt: 3 };
test("swSync vault push carries circles and circlePins", async () => {
  const puts: string[] = [];
  const fetch = async (_url: string, init?: { method?: string; body?: string }) => {
    if (init?.method === "PUT") { puts.push(JSON.parse(init.body!).blob); return { ok: true, status: 200 }; }
    return { ok: false, status: 404 };
  };
  const meta = [{ key: "syncKey", value: "OAK-ABCD-EFGH-JKLM" }, { key: "circles", value: [circle] }, { key: "circlePins", value: { c9: "pub9" } }];
  const ctx = loadSw({ fetch, btoa, atob, indexedDB: fakeIdb(meta, { profiles: [], routines: [routine], intakes: [], biomarkers: [] }) });
  await (ctx as unknown as { swSync: () => Promise<void> }).swSync();
  assert.equal(puts.length, 1);
  const [iv, ct] = puts[0].split(".");
  const unb = (s: string) => Buffer.from(s, "base64url");
  const { key } = await ctx.vaultKeys("OAK-ABCD-EFGH-JKLM");
  const snap = JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb(iv) }, key, unb(ct))));
  assert.deepEqual(Object.keys(snap).sort(), ["biomarkers", "circlePins", "circles", "intakes", "profiles", "routines", "v"]);
  assert.deepEqual(snap.circles, [circle]);
  assert.deepEqual(snap.circlePins, { c9: "pub9" });
});
// The SW cannot run mergeCircles (member-keys.ts): it passes the pulled vault's circles through and the next tab sync merges.
const circleCases: [string, unknown, Rec[], unknown][] = [
  ["no remote vault: local circle state", {}, [{ key: "circles", value: [circle] }, { key: "circlePins", value: { c1: "a" } }], { circles: [circle], circlePins: { c1: "a" } }],
  ["remote circles pass through, pins: this device wins", { circles: [{ ...circle, updatedAt: 7 }], circlePins: { c1: "r", c2: "b" } },
    [{ key: "circles", value: [circle] }, { key: "circlePins", value: { c1: "a" } }], { circles: [{ ...circle, updatedAt: 7 }], circlePins: { c1: "a", c2: "b" } }],
  ["empty remote circles keep local ones", { circles: [] }, [{ key: "circles", value: [circle] }], { circles: [circle], circlePins: {} }],
  ["nothing anywhere", {}, [], { circles: [], circlePins: {} }],
];
for (const [name, remote, meta, want] of circleCases) {
  test(`vaultCircles ${name}`, () => assert.deepEqual(JSON.parse(JSON.stringify(sw.vaultCircles(remote, meta))), want));
}
