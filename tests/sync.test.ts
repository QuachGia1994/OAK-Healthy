import { test } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";
import { deriveVault, newSyncKey, open, seal } from "../lib/crypto.ts";
import { VAULT_CIRCLE_FIELDS, vaultCircleCases } from "./fixtures/vault-circles.ts";

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
const { syncNow } = await import("../lib/sync.ts");
const meta = (globalThis as unknown as { oakMeta: Map<string, unknown> }).oakMeta;

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
      return blob ? { ok: true, status: 200, json: async () => ({ rev: 1, blob }) } : { ok: false, status: 404, json: async () => ({}) };
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
      return { ok: false, status: method === "PUT" ? (status as { put?: number }).put : status.get, json: async () => ({}) };
    }) as unknown as typeof fetch;
    const r = await syncNow(key);
    assert.equal(r.ok, false);
    assert.equal(!r.ok && r.gone, true);
    assert.equal(meta.get("syncKey"), null, "sync is off on this device");
    assert.deepEqual(calls, name === "GET" ? ["GET"] : ["GET", "PUT"], "no push after the 410");
  });
}
