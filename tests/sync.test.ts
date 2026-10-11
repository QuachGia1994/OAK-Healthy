import { test } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";
import { deriveVault, newSyncKey, open, seal } from "../lib/crypto.ts";
import { SHARES_NOW, VAULT_CIRCLE_FIELDS, vaultCircleCases, vaultShareCases } from "./fixtures/vault-circles.ts";

// lib/sync.ts imports app modules without extensions and lib/db.ts (Dexie, IndexedDB). Resolve the extensions and
// replace lib/db.ts with an in-memory meta table, so syncNow runs through its public entry.
const fakeDb = `
const meta = (globalThis.oakMeta ??= new Map());
export const db = { meta: { get: async (key) => (meta.has(key) ? { key, value: meta.get(key) } : undefined), put: async ({ key, value }) => { meta.set(key, value); } }, transaction: (_mode, _table, fn) => fn() };
export const getMeta = async (key, fallback) => (meta.has(key) && meta.get(key) != null ? meta.get(key) : fallback);
export const setMeta = async (key, value) => { meta.set(key, value); };
export const snapshot = async () => ({ v: 1, profiles: [], routines: [], intakes: [], biomarkers: [] });
export const mergeSnapshot = async () => 0;`;
const hooks = `
export async function resolve(spec, ctx, next) {
  if (spec === "./db" && ctx.parentURL?.endsWith("/lib/sync.ts")) return { url: "oak-fake:db", shortCircuit: true };
  if (/^\\.\\.?\\//.test(spec) && !/\\.[cm]?[jt]sx?$/.test(spec)) return next(spec + ".ts", ctx);
  return next(spec, ctx);
}
export async function load(url, ctx, next) {
  return url === "oak-fake:db" ? { format: "module", shortCircuit: true, source: ${JSON.stringify(fakeDb)} } : next(url, ctx);
}`;
register(`data:text/javascript,${encodeURIComponent(hooks)}`, import.meta.url);
const { setSyncKey, syncNow } = await import("../lib/sync.ts");
const meta = (globalThis as unknown as { oakMeta: Map<string, unknown> }).oakMeta;
/** The vault route's own 404 (worker/index.ts vault GET with no row). */
const notFound = { ok: false, status: 404, json: async () => ({ error: "not found" }) };
const pushed = { ok: true, status: 200, json: async () => ({ rev: 2 }) };
const emptyVault = { v: 1, profiles: [], routines: [], intakes: [], biomarkers: [] };
/** syncNow against a fake vault route: GET answers `get`, PUT answers `put`; returns the methods called and pushed blobs. */
async function syncWith(key: string, get: unknown, put: unknown = pushed) {
  const calls: string[] = [], puts: string[] = [];
  globalThis.fetch = (async (_url: string, init?: { method?: string; body?: string }) => {
    const method = init?.method ?? "GET";
    calls.push(method);
    if (method === "PUT") { puts.push(JSON.parse(init!.body!).blob); return put; }
    return get;
  }) as unknown as typeof fetch;
  return { r: await syncNow(key, SHARES_NOW), calls, puts };
}

// The shared vault fixture through syncNow itself: pull the sealed vault, merge into meta, push; the pushed circle
// fields must equal the fixture's want (tests/circles.test.ts and tests/pwa.test.ts run the same cases).
for (const { name, remote, local, want } of vaultCircleCases) {
  test(`syncNow vault circle state ${name}`, async () => {
    const key = newSyncKey(), v = await deriveVault(key), puts: string[] = [];
    meta.set("syncKey", key);
    for (const f of VAULT_CIRCLE_FIELDS) meta.set(f, structuredClone(local[f as keyof typeof local]));
    const blob = Object.keys(remote).length ? await seal(v.key, { v: 1, profiles: [], routines: [], intakes: [], biomarkers: [], ...remote }) : null;
    globalThis.fetch = (async (_url: string, init?: { method?: string; body?: string }) => {
      if (init?.method === "PUT") { puts.push(JSON.parse(init.body!).blob); return { ok: true, status: 200, json: async () => ({ rev: 2 }) }; }
      return blob ? { ok: true, status: 200, json: async () => ({ rev: 1, blob }) } : notFound;
    }) as unknown as typeof fetch;
    const r = await syncNow(key);
    assert.equal(r.ok, true);
    assert.equal(puts.length, 1);
    const pushed = await open<Record<string, unknown>>(v.key, puts[0]);
    const circles = Object.fromEntries(Object.entries(pushed).filter(([k]) => VAULT_CIRCLE_FIELDS.includes(k)));
    assert.deepEqual(Object.keys(circles).sort(), VAULT_CIRCLE_FIELDS, "no circle field dropped");
    assert.deepEqual(JSON.parse(JSON.stringify(circles)), want);
  });
}

/** The vault was deleted (server tombstone, 410): sync turns off on this device and nothing is pushed. */
for (const [name, status] of [["GET", { get: 410 }], ["PUT", { get: 404, put: 410 }]] as const) {
  test(`syncNow on a deleted vault (${name} 410): sync key cleared, no push, gone`, async () => {
    const key = newSyncKey(), calls: string[] = [];
    meta.set("syncKey", key);
    globalThis.fetch = (async (_url: string, init?: { method?: string }) => {
      const method = init?.method ?? "GET";
      calls.push(method);
      return { ok: false, status: method === "PUT" ? (status as { put?: number }).put : status.get, json: async () => ({ error: "not found" }) };
    }) as unknown as typeof fetch;
    const r = await syncNow(key);
    assert.equal(r.ok, false);
    assert.equal(!r.ok && r.gone, true);
    assert.equal(meta.get("syncKey"), null, "sync is off on this device");
    assert.deepEqual(calls, name === "GET" ? ["GET"] : ["GET", "PUT"], "no push after the 410");
  });
}

// Contract C2: the worker's 404 on a vault id this device synced before = the tombstone was purged = gone.
test("syncNow on the worker's 404 for a vault this device synced before: gone, sync off, no push", async () => {
  const key = newSyncKey();
  meta.set("syncKey", key); meta.set("syncedVault", (await deriveVault(key)).vaultId);
  const { r, calls } = await syncWith(key, notFound);
  assert.equal(!r.ok && r.gone, true);
  assert.deepEqual(calls, ["GET"]);
  assert.equal(meta.get("syncKey"), null);
});

test("syncNow first sync of a new key creates the vault and remembers its id only after the PUT succeeded", async () => {
  const key = newSyncKey(), { vaultId } = await deriveVault(key);
  meta.set("syncKey", key); meta.set("syncedVault", "vault-of-the-old-key");
  const failed = await syncWith(key, notFound, { ok: false, status: 500, json: async () => ({}) });
  assert.deepEqual([failed.r.ok, failed.calls], [false, ["GET", "PUT"]]);
  assert.equal(meta.get("syncedVault"), "vault-of-the-old-key", "a 404 before the create proves nothing");
  const { r, calls } = await syncWith(key, notFound);
  assert.deepEqual([r.ok, calls], [true, ["GET", "PUT"]]);
  assert.equal(meta.get("syncedVault"), vaultId);
});

test("syncNow on a 404 that is not the worker's (proxy, captive portal): a server error, no push, sync stays on", async () => {
  const key = newSyncKey();
  meta.set("syncKey", key); meta.set("syncedVault", (await deriveVault(key)).vaultId);
  const { r, calls } = await syncWith(key, { ok: false, status: 404, json: async () => { throw new SyntaxError("<html>"); } });
  assert.deepEqual(r, { ok: false, error: "Server 404" });
  assert.deepEqual(calls, ["GET"]);
  assert.equal(meta.get("syncKey"), key);
});

test("turning sync on again with the same key forgets the synced vault, so a 404 re-creates it", async () => {
  const key = newSyncKey();
  meta.set("syncedVault", (await deriveVault(key)).vaultId);
  await setSyncKey(key);
  const { r, calls } = await syncWith(key, notFound);
  assert.deepEqual([r.ok, calls], [true, ["GET", "PUT"]]);
});

// Contracts C1 + C3, the shared fixture through syncNow: the pushed lists and meta `shares` / `sharesGone` all equal want.
for (const { name, remote, local, want } of vaultShareCases) {
  test(`syncNow vault shares ${name}`, async () => {
    const key = newSyncKey(), v = await deriveVault(key);
    meta.set("syncKey", key); meta.set("shares", structuredClone(local.shares)); meta.set("sharesGone", structuredClone(local.sharesGone));
    const blob = await seal(v.key, { ...emptyVault, ...remote });
    const { r, puts } = await syncWith(key, { ok: true, status: 200, json: async () => ({ rev: 1, blob }) });
    assert.equal(r.ok, true);
    const pushed = await open<{ shares: unknown; sharesGone: unknown }>(v.key, puts[0]);
    assert.deepEqual({ shares: pushed.shares, sharesGone: pushed.sharesGone }, want);
    assert.deepEqual({ shares: meta.get("shares"), sharesGone: meta.get("sharesGone") }, want);
  });
}
