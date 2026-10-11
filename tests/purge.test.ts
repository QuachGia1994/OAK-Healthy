import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { DatabaseSync } from "node:sqlite";
import { runInNewContext } from "node:vm";

// worker/push.ts imports cloudflare:workers, so its purge block (the constants up to the ReminderClock class) runs alone.
const src = readFileSync(new URL("../worker/push.ts", import.meta.url), "utf8");
const block = src.slice(src.indexOf("const PURGE_EVERY_MS"), src.indexOf("/** Minute clock"));
const { purgeDaily } = runInNewContext(`${stripTypeScriptTypes(block)}\n({ purgeDaily })`, { Date }) as
  { purgeDaily: (env: unknown, storage: unknown, now: number) => Promise<number> };

const DAY = 864e5, NOW = 1000 * DAY;
/** The real schema on node:sqlite behind D1's prepare/bind/run, and a Durable Object storage map. */
function world() {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../worker/schema.sql", import.meta.url), "utf8"));
  const prepare = (q: string) => ({ bind: (...a: unknown[]) => ({ run: async () => ({ meta: { changes: Number(db.prepare(q).run(...(a as never[])).changes) } }) }) });
  const kept = new Map<string, unknown>();
  const storage = { get: async (k: string) => kept.get(k), put: async (k: string, v: unknown) => { kept.set(k, v); } };
  const add = (id: string, deletedAt: number | null) =>
    db.prepare("INSERT INTO vaults (id, rev, blob, auth_hash, updated_at, deleted_at) VALUES (?, 1, ?, 'h', 1, ?)").run(id, deletedAt == null ? "cipher" : "", deletedAt);
  const ids = () => (db.prepare("SELECT id FROM vaults ORDER BY id").all() as { id: string }[]).map((r) => r.id);
  const share = (id: string, expiresAt: number) =>
    db.prepare("INSERT INTO shares (id, blob, created_at, expires_at) VALUES (?, 'cipher', 1, ?)").run(id, expiresAt);
  const shares = () => (db.prepare("SELECT id FROM shares ORDER BY id").all() as { id: string }[]).map((r) => r.id);
  return { purge: (now: number) => purgeDaily({ DB: { prepare } }, storage, now), kept, add, ids, share, shares };
}

test("the alarm purges after the reminders, behind its own catch", () => {
  const alarm = src.slice(src.indexOf("async alarm()"), src.indexOf("const nextMinute"));
  const at = alarm.indexOf("purgeDaily(");
  assert.ok(alarm.indexOf("remind(") >= 0 && at > alarm.indexOf("remind("), "purgeDaily runs after remind");
  assert.match(alarm.slice(at), /^purgeDaily\([^)]*\)\.catch\(/, "a purge failure cannot fail the alarm");
});

test("purge deletes expired share links; a live share stays", async () => {
  const w = world();
  w.share("expired", NOW - 1); w.share("live", NOW + DAY); w.add("old", NOW - 366 * DAY);
  assert.equal(await w.purge(NOW), 2, "one tombstone + one share");
  assert.deepEqual(w.shares(), ["live"]);
  assert.deepEqual(w.ids(), []);
});

test("a full share batch alone leaves the day open", async () => {
  const w = world();
  for (let i = 0; i < 500; i++) w.share(`x${i}`, NOW - DAY);
  assert.equal(await w.purge(NOW), 500);
  assert.equal(w.kept.get("lastPurge"), undefined);
  assert.equal(await w.purge(NOW + 60e3), 0);
  assert.equal(w.kept.get("lastPurge"), NOW + 60e3);
});

test("purge deletes tombstones older than 365 days; a live vault and a younger tombstone stay", async () => {
  const w = world();
  w.add("live", null); w.add("young", NOW - 364 * DAY); w.add("old", NOW - 366 * DAY);
  assert.equal(await w.purge(NOW), 1);
  assert.deepEqual(w.ids(), ["live", "young"]);
  assert.equal(w.kept.get("lastPurge"), NOW);
});

test("purge runs at most once a day", async () => {
  const w = world();
  assert.equal(await w.purge(NOW), 0);
  w.add("old", NOW - 400 * DAY);
  assert.equal(await w.purge(NOW + DAY - 60e3), 0, "same day: no statement");
  assert.deepEqual(w.ids(), ["old"]);
  assert.equal(await w.purge(NOW + DAY), 1);
  assert.deepEqual(w.ids(), []);
});

test("a backlog over one batch drains 500 per alarm, then closes the day", async () => {
  const w = world();
  for (let i = 0; i < 501; i++) w.add(`t${i}`, NOW - 366 * DAY);
  w.add("live", null);
  assert.equal(await w.purge(NOW), 500);
  assert.equal(w.kept.get("lastPurge"), undefined, "a full batch leaves the day open");
  assert.equal(await w.purge(NOW + 60e3), 1);
  assert.equal(w.kept.get("lastPurge"), NOW + 60e3);
  assert.deepEqual(w.ids(), ["live"]);
});
