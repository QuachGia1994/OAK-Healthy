import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { DatabaseSync } from "node:sqlite";
import { runInNewContext } from "node:vm";
import { deleteEverything, shareDeleteOutcome, takeWipedFlag, WIPED_FLAG, type ShareRef, type WipeDeps, type WipeProgress } from "../lib/domain/wipe.ts";
import { json, sha256, validId } from "../worker/util.ts";

// ---- orchestrator (lib/domain/wipe.ts) against an in-memory server and device

/** fail: a call name ("pull", "circle:<id>", "vault", ...) that throws on its next call only. */
/** known: whether the UI already knew the user was signed in when the run started (else the account step asks /me). */
/** shares: meta `shares` (x already expired: its row may still be on the server); server: share ids still stored. */
const SHARES: ShareRef[] = [{ id: "s1", token: "k1", expiresAt: 2e12 }, { id: "x", token: "kx", expiresAt: 1 }, { id: "s2", token: "k2", expiresAt: 2e12 }];
function world(o: { circles?: string[]; key?: boolean; account?: boolean; known?: boolean; shares?: ShareRef[] } = {}) {
  const s = {
    calls: [] as string[], fail: new Set<string>(), lost: false, session: true,
    circles: new Set(o.circles ?? ["c1", "c2"]), vault: o.key ?? true, account: o.account ?? true, reminders: true,
    syncOn: o.key ?? true, saved: null as WipeProgress | null, cleared: false,
    shares: structuredClone(o.shares ?? SHARES), server: new Set((o.shares ?? SHARES).map((x) => x.id)), forbid: new Set<string>(),
  };
  const hit = (name: string) => { s.calls.push(name); if (s.fail.delete(name)) throw new Error(`${name} failed`); };
  const deps: WipeDeps = {
    progress: async () => s.saved ?? { vault: s.syncOn ? { id: "vid", auth: "vauth" } : null, account: o.known ?? s.account, done: [] },
    signedIn: async () => { hit("me"); return s.account && s.session; },
    save: async (p) => { s.saved = structuredClone(p); },
    pull: async () => hit("pull"),
    ownedCircles: async () => [...s.circles], // a deleted circle becomes a tombstone and drops out of this list
    deleteCircle: async (id) => { hit(`circle:${id}`); s.circles.delete(id); },
    disableReminders: async () => { hit("reminders"); s.reminders = false; },
    shares: async () => structuredClone(s.shares),
    saveShares: async (list) => { s.shares = structuredClone(list); },
    deleteShare: async (x) => {
      hit(`share:${x.id}`); assert.ok(x.token);
      if (s.forbid.has(x.id)) return "kept"; // 403: the row has no hash (made through a 2.7 worker) or another one
      s.server.delete(x.id); return "ok";
    },
    syncOff: async () => { s.calls.push("syncOff"); s.syncOn = false; },
    deleteVault: async (v) => { hit("vault"); assert.deepEqual(v, { id: "vid", auth: "vauth" }); s.vault = false; },
    deleteAccount: async () => {
      hit("account");
      if (!s.account || !s.session) return "signed-out";
      s.account = false;
      if (s.lost) { s.lost = false; throw new Error("Failed to fetch"); } // deleted on the server, answer lost
      return "ok";
    },
    clearDevice: async () => { hit("device"); s.cleared = true; s.saved = null; },
  };
  return { s, deps };
}
const ALL = ["pull", "circle:c1", "circle:c2", "reminders", "share:s1", "share:x", "share:s2", "syncOff", "vault", "account", "device"];

test("wipe: pull, owned circles, reminders, share links, sync off, vault, account, then this device", async () => {
  const { s, deps } = world(), steps: string[] = [];
  assert.deepEqual(await deleteEverything(deps, (x) => steps.push(x)), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ALL);
  assert.deepEqual(steps, ["circles", "reminders", "shares", "vault", "account", "device"]);
  assert.ok(!s.circles.size && !s.reminders && !s.vault && !s.account && s.cleared);
});

test("wipe: every listed share, expired ones too, is deleted before sync goes off, and leaves the list", async () => {
  const { s, deps } = world();
  await deleteEverything(deps);
  assert.ok(s.calls.indexOf("share:s2") < s.calls.indexOf("syncOff"), "shares before the vault step");
  assert.ok(s.calls.includes("share:x"), "expired rows are not purged on the server, so they are deleted too");
  assert.equal(s.server.size, 0);
  assert.deepEqual(s.shares, []);
});

test("wipe: a 2.7 marker (vault already done, no shares step) still deletes the listed shares, then resumes", async () => {
  const { s, deps } = world();
  s.saved = { vault: { id: "vid", auth: "vauth" }, account: true, done: ["circles", "reminders", "vault"] } as WipeProgress;
  s.syncOn = false;
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["share:s1", "share:x", "share:s2", "account", "device"]);
});

test("wipe: a share the server refuses (403) is skipped and counted; the run goes on to the vault", async () => {
  const { s, deps } = world();
  s.forbid.add("s1");
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 1 });
  assert.deepEqual(s.calls, ALL, "no loop on s1");
  assert.ok(s.server.has("s1") && !s.server.has("s2") && !s.vault);
});

test("wipe: the skipped count survives a failure and retry, without counting twice", async () => {
  const { s, deps } = world();
  s.forbid.add("s1"); s.fail.add("share:s2");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "shares", error: "share:s2 failed" });
  assert.equal(s.saved?.sharesKept, 1);
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 1 });
  assert.deepEqual(s.calls, ["share:s2", "syncOff", "vault", "account", "device"]);
});

test("wipe: a failed share stops before the vault; the deleted one leaves the list; retry deletes the rest", async () => {
  const { s, deps } = world();
  s.fail.add("share:s2");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "shares", error: "share:s2 failed" });
  assert.deepEqual(s.shares.map((x) => x.id), ["s2"], "s1 and x deleted are gone from meta shares");
  assert.ok(s.syncOn && s.vault && s.account && !s.cleared, "nothing after the shares step ran");
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["share:s2", "syncOff", "vault", "account", "device"], "s1 is not deleted twice");
  assert.equal(s.server.has("s2"), false);
});

test("wipe: no listed shares, the step only clears the list", async () => {
  const { s, deps } = world({ shares: [] });
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.ok(!s.calls.some((c) => c.startsWith("share:")));
});

test("wipe: share DELETE answers: 200 ok; 400/401/403/405 kept (a malformed vault entry never blocks); 5xx/429 throw", () => {
  assert.equal(shareDeleteOutcome(200), "ok");
  for (const s of [400, 401, 403, 405]) assert.equal(shareDeleteOutcome(s), "kept", String(s));
  for (const s of [429, 500, 503]) assert.equal(shareDeleteOutcome(s), "retry", String(s));
  const acc = readFileSync(new URL("../lib/account.ts", import.meta.url), "utf8");
  assert.match(acc, /const o = shareDeleteOutcome\(res\.status\);\s*if \(o !== "retry"\) return o;/);
});

test("wipe: a malformed entry (short token, bad id) is kept and the run reaches the vault", async () => {
  const { s, deps } = world({ shares: [{ id: "bad id/x", token: "", expiresAt: 0 }, ...SHARES] });
  const del = deps.deleteShare;
  deps.deleteShare = async (x) => (x.id === "bad id/x" ? shareDeleteOutcome(400) as "kept" : del(x));
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 1 });
  assert.ok(!s.vault && s.cleared && !s.shares.length);
});

test("wipe: 'Everything deleted' flag is read once after the reload", () => {
  const m = new Map<string, string>([[WIPED_FLAG, "2"]]);
  const st = { getItem: (k: string) => m.get(k) ?? null, removeItem: (k: string) => void m.delete(k) };
  assert.deepEqual(takeWipedFlag(st), { sharesKept: 2 });
  assert.equal(takeWipedFlag(st), null, "a second load shows nothing");
  m.set(WIPED_FLAG, "junk");
  assert.deepEqual(takeWipedFlag(st), { sharesKept: 0 });
  assert.equal(takeWipedFlag({ getItem: () => { throw new Error("blocked"); }, removeItem: () => {} }), null, "storage off: no toast, no crash");
});

test("wipe UI: the flag is set before the reload, and SyncAgent (on every app page) shows the toast", () => {
  const ui = readFileSync(new URL("../components/settings.tsx", import.meta.url), "utf8");
  const ok = ui.slice(ui.indexOf("if (r.ok) {"), ui.indexOf("setFailed(r)"));
  assert.ok(ok.indexOf("sessionStorage.setItem(WIPED_FLAG") >= 0 && ok.indexOf("sessionStorage.setItem(WIPED_FLAG") < ok.indexOf("location.replace(\"/\")"));
  const agent = ui.slice(ui.indexOf("export function SyncAgent"), ui.indexOf("function CircleRow"));
  assert.match(agent, /takeWipedFlag\(sessionStorage\)[\s\S]*toast\.success\(tr\("Everything deleted"\)/);
  const shell = readFileSync(new URL("../components/app-shell.tsx", import.meta.url), "utf8");
  assert.ok(shell.includes("<SyncAgent />") && shell.indexOf("<SyncAgent />") < shell.indexOf("<Toaster position=\"bottom-right\" customAriaLabel={t(\"Notifications\")} mobileOffset"));
});

test("wipe: the progress marker is written after each finished step", async () => {
  const { s, deps } = world(), seen: string[][] = [];
  const save = deps.save;
  deps.save = async (p) => { seen.push([...p.done]); await save(p); };
  await deleteEverything(deps);
  assert.deepEqual(seen.at(-1), ["circles", "reminders", "shares", "vault", "account"]);
  assert.ok(seen.some((d) => d.join() === "circles") && seen.some((d) => d.join() === "circles,reminders"));
  assert.equal(s.saved, null, "the final clear removes it");
});

test("wipe: a failed circle stops before reminders and the vault; retry resumes with the rest", async () => {
  const { s, deps } = world();
  s.fail.add("circle:c2");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "circles", error: "circle:c2 failed" });
  assert.deepEqual(s.calls, ["pull", "circle:c1", "circle:c2"]);
  assert.ok(s.reminders && s.vault && s.account && !s.cleared);
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["pull", "circle:c2", "reminders", "share:s1", "share:x", "share:s2", "syncOff", "vault", "account", "device"], "c1 is not deleted twice");
});

test("wipe: a failed vault delete keeps the vault auth for the retry although sync is already off", async () => {
  const { s, deps } = world();
  s.fail.add("vault");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "vault", error: "vault failed" });
  assert.equal(s.syncOn, false);
  assert.ok(s.account && !s.cleared);
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["syncOff", "vault", "account", "device"], "finished steps are skipped");
  assert.ok(!s.vault);
});

test("wipe: retry after the account step succeeded does not call the account again", async () => {
  const { s, deps } = world();
  s.fail.add("device");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "device", error: "device failed" });
  assert.ok(!s.account);
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["device"]);
});

test("wipe: account deleted but the answer was lost, so the retry's 401 counts as done", async () => {
  const { s, deps } = world();
  s.lost = true;
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "account", error: "Failed to fetch" });
  assert.equal(s.saved?.accountSent, true);
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["account", "device"]);
});

test("wipe: a 401 before the account was deleted (session expired) stops until the user signs in", async () => {
  const { s, deps } = world();
  s.session = false;
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "account", error: "Sign in first" });
  assert.ok(s.account && !s.cleared);
  assert.equal(s.saved?.accountSent, false, "a 401 answer is not a sent delete");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "account", error: "Sign in first" }, "still not done");
  s.session = true; s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["account", "device"]);
  assert.ok(!s.account);
});

test("wipe: no account and sync off: circles, reminders, a /me check and this device", async () => {
  const { s, deps } = world({ key: false, account: false, circles: ["c1"], shares: [] });
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["pull", "circle:c1", "reminders", "syncOff", "me", "device"]);
});

test("wipe: a failed /me check stops at the account step instead of skipping it; retry resumes", async () => {
  const { s, deps } = world({ known: false }); // Settings before /me loaded
  s.fail.add("me");
  assert.deepEqual(await deleteEverything(deps), { ok: false, step: "account", error: "me failed" });
  assert.ok(s.account && !s.cleared, "account kept, device not cleared");
  s.calls = [];
  assert.deepEqual(await deleteEverything(deps), { ok: true, sharesKept: 0 });
  assert.deepEqual(s.calls, ["me", "account", "device"]);
  assert.ok(!s.account);
});

// ---- worker vault route (worker/index.ts vault) on SQLite with the real schema

const src = readFileSync(new URL("../worker/index.ts", import.meta.url), "utf8");
const body = src.slice(src.indexOf("async function vault("), src.indexOf("async function share("));
const MAX_VAULT = Number(/MAX_VAULT = ([\d_]+)/.exec(src)![1].replace(/_/g, ""));
const vaultRoute = runInNewContext(`(${stripTypeScriptTypes(body)})`, { json, sha256, validId, MAX_VAULT, Date }) as
  (req: Request, env: unknown, id: string) => Promise<Response>;

/** D1's prepare/bind/first/run/all over node:sqlite. */
function d1(schema = readFileSync(new URL("../worker/schema.sql", import.meta.url), "utf8")) {
  const db = new DatabaseSync(":memory:");
  db.exec(schema);
  const prepare = (q: string) => {
    const st = (args: unknown[]) => ({
      first: async () => (db.prepare(q).get(...(args as never[])) ?? null),
      run: async () => ({ meta: { changes: Number(db.prepare(q).run(...(args as never[])).changes) } }),
      all: async () => ({ results: db.prepare(q).all(...(args as never[])) }),
    });
    return { bind: (...a: unknown[]) => st(a), ...st([]) };
  };
  return { db, env: { DB: { prepare } } };
}
const ID = "vault-id-123", TOKEN = "t".repeat(32);
const call = async (env: unknown, method: string, init: { token?: string; body?: unknown } = {}) => {
  const r = await vaultRoute(new Request(`https://x/api/vault/${ID}`, {
    method, headers: { authorization: `Bearer ${init.token ?? TOKEN}` }, body: init.body ? JSON.stringify(init.body) : undefined,
  }), env, ID);
  return { status: r.status, body: await r.json() as Record<string, unknown> };
};

test("worker vault: DELETE leaves a tombstone; GET and PUT under that auth answer 410", async () => {
  const { db, env } = d1();
  assert.equal((await call(env, "PUT", { body: { baseRev: 0, blob: "secret" } })).status, 200);
  assert.deepEqual(await call(env, "DELETE"), { status: 200, body: { ok: true } });
  const row = db.prepare("SELECT blob, auth_hash, deleted_at FROM vaults WHERE id = ?").get(ID) as { blob: string; auth_hash: string; deleted_at: number };
  assert.equal(row.blob, "", "only hashed ids and the time stay");
  assert.equal(row.auth_hash, await sha256(TOKEN));
  assert.ok(row.deleted_at > 0);
  assert.deepEqual(await call(env, "GET"), { status: 410, body: { error: "gone" } });
  assert.deepEqual(await call(env, "PUT", { body: { baseRev: 1, blob: "again" } }), { status: 410, body: { error: "gone" } });
  assert.deepEqual(await call(env, "PUT", { body: { baseRev: 0, blob: "again" } }), { status: 410, body: { error: "gone" } }, "a first push cannot revive it");
  assert.equal((db.prepare("SELECT blob FROM vaults WHERE id = ?").get(ID) as { blob: string }).blob, "");
});

test("worker vault: DELETE is idempotent, and with no vault yet it creates the tombstone", async () => {
  const { db, env } = d1();
  assert.equal((await call(env, "DELETE")).status, 200);
  const first = (db.prepare("SELECT deleted_at FROM vaults WHERE id = ?").get(ID) as { deleted_at: number }).deleted_at;
  assert.equal((await call(env, "DELETE")).status, 200);
  assert.equal((db.prepare("SELECT deleted_at FROM vaults WHERE id = ?").get(ID) as { deleted_at: number }).deleted_at, first);
  assert.equal((await call(env, "PUT", { body: { baseRev: 0, blob: "x" } })).status, 410, "INSERT OR IGNORE cannot revive it");
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM vaults").get() as { n: number }).n, 1);
});

test("worker vault: another token gets 403 on a tombstone and cannot delete a live vault", async () => {
  const { db, env } = d1();
  await call(env, "PUT", { body: { baseRev: 0, blob: "secret" } });
  assert.equal((await call(env, "DELETE", { token: "u".repeat(32) })).status, 403);
  assert.equal((db.prepare("SELECT blob FROM vaults WHERE id = ?").get(ID) as { blob: string }).blob, "secret");
  await call(env, "DELETE");
  assert.equal((await call(env, "GET", { token: "u".repeat(32) })).status, 403);
});

test("worker vault: a live vault still syncs (GET, PUT with baseRev, 409 on a stale rev)", async () => {
  const { env } = d1();
  assert.equal((await call(env, "GET")).status, 404);
  assert.deepEqual((await call(env, "PUT", { body: { baseRev: 0, blob: "a" } })).body, { rev: 1 });
  assert.deepEqual((await call(env, "PUT", { body: { baseRev: 1, blob: "b" } })).body, { rev: 2 });
  assert.equal((await call(env, "PUT", { body: { baseRev: 1, blob: "c" } })).status, 409);
  assert.deepEqual((await call(env, "GET")).body, { rev: 2, blob: "b" });
});

test("worker migration 0006 adds deleted_at to an existing vaults table", () => {
  const db = new DatabaseSync(":memory:");
  db.exec("CREATE TABLE vaults (id TEXT PRIMARY KEY, rev INTEGER NOT NULL, blob TEXT NOT NULL, auth_hash TEXT NOT NULL, updated_at INTEGER NOT NULL)");
  db.exec(readFileSync(new URL("../worker/migrations/0006_vault_tombstone.sql", import.meta.url), "utf8"));
  const cols = (db.prepare("PRAGMA table_info(vaults)").all() as { name: string }[]).map((c) => c.name);
  assert.ok(cols.includes("deleted_at"));
});
