import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { DatabaseSync } from "node:sqlite";
import { runInNewContext } from "node:vm";
import { buildSlug, isPrettySlug } from "../lib/domain/share.ts";
import { clientIp, json, limit, sha256, shortId, validId } from "../worker/util.ts";

// ---- worker share route (worker/index.ts share) on SQLite with the real schema

const src = readFileSync(new URL("../worker/index.ts", import.meta.url), "utf8");
const body = src.slice(src.indexOf("async function share("), src.indexOf("const esc ="));
const num = (name: string) => Number(new RegExp(`${name} = ([\\d_]+)`).exec(src)![1].replace(/_/g, ""));
const shareRoute = runInNewContext(`(${stripTypeScriptTypes(body)})`, {
  json, sha256, validId, shortId, limit, clientIp, buildSlug, isPrettySlug, Date, crypto, String, Number,
  MAX_SHARE: num("MAX_SHARE"), SHARE_TTL_MS: 365 * 864e5,
}) as (req: Request, env: unknown, id?: string) => Promise<Response>;

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

const TOKEN = "d".repeat(43); // the app mints b64u(32 random bytes)
const post = async (env: unknown, extra: Record<string, unknown> = {}) => {
  const r = await shareRoute(new Request("https://x/api/share", { method: "POST", body: JSON.stringify({ blob: "iv.ct", ...extra }) }), env);
  return { status: r.status, body: await r.json() as { id?: string; slug?: string; expiresAt?: number; error?: string } };
};
const call = async (env: unknown, method: string, id: string, token?: string) => {
  const r = await shareRoute(new Request(`https://x/api/share/${id}`, { method, headers: token ? { authorization: `Bearer ${token}` } : {} }), env, id);
  return { status: r.status, body: await r.json() as Record<string, unknown> };
};

test("worker share: POST stores only the SHA-256 of the delete token; DELETE with the token removes the row", async () => {
  const { db, env } = d1();
  const { status, body: { id, expiresAt } } = await post(env, { deleteHash: await sha256(TOKEN) });
  assert.equal(status, 201);
  const row = db.prepare("SELECT * FROM shares WHERE id = ?").get(id!) as Record<string, unknown>;
  assert.equal(expiresAt, row.expires_at, "the app stores the server's expiry (D7)");
  assert.equal(row.delete_hash, await sha256(TOKEN));
  assert.ok(!JSON.stringify(row).includes(TOKEN), "the token itself is never stored");
  assert.equal((await call(env, "GET", id!)).status, 200);
  assert.deepEqual(await call(env, "DELETE", id!, TOKEN), { status: 200, body: { ok: true } });
  assert.equal((await call(env, "GET", id!)).status, 404);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM shares").get() as { n: number }).n, 0, "row deleted, not just hidden");
});

test("worker share: DELETE is idempotent (already gone or never existed = ok)", async () => {
  const { env } = d1();
  const { body: { id } } = await post(env, { deleteHash: await sha256(TOKEN) });
  await call(env, "DELETE", id!, TOKEN);
  assert.deepEqual(await call(env, "DELETE", id!, TOKEN), { status: 200, body: { ok: true } });
  assert.equal((await call(env, "DELETE", "neverexisted", TOKEN)).status, 200);
});

test("worker share: a wrong or missing token cannot delete; a share without a hash (2.7) never matches", async () => {
  const { db, env } = d1();
  const { body: { id } } = await post(env, { deleteHash: await sha256(TOKEN) });
  assert.equal((await call(env, "DELETE", id!, "e".repeat(43))).status, 403);
  assert.equal((await call(env, "DELETE", id!)).status, 401);
  const old = await post(env); // a 2.7 client sends no hash
  assert.equal(old.status, 201);
  assert.equal((db.prepare("SELECT delete_hash FROM shares WHERE id = ?").get(old.body.id!) as { delete_hash: null }).delete_hash, null);
  assert.equal((await call(env, "DELETE", old.body.id!, TOKEN)).status, 403);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM shares").get() as { n: number }).n, 2);
});

test("worker share: DELETE also removes an expired row", async () => {
  const { db, env } = d1();
  const { body: { id } } = await post(env, { deleteHash: await sha256(TOKEN) });
  db.prepare("UPDATE shares SET expires_at = 1 WHERE id = ?").run(id!);
  assert.equal((await call(env, "GET", id!)).status, 404);
  assert.equal((await call(env, "DELETE", id!, TOKEN)).status, 200);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM shares").get() as { n: number }).n, 0);
});

test("worker share: a malformed hash is refused; DELETE by the slug path is refused", async () => {
  const { env } = d1();
  for (const deleteHash of [TOKEN, "A".repeat(64), 42, ""]) assert.equal((await post(env, { deleteHash })).status, 400, String(deleteHash));
  const { body: { slug } } = await post(env, { deleteHash: await sha256(TOKEN), handle: "ann", title: "My stack", items: 1 });
  assert.ok(slug);
  assert.equal((await call(env, "DELETE", slug!, TOKEN)).status, 400, "delete takes the id, never the slug");
  assert.equal((await call(env, "GET", slug!)).status, 200);
});

test("worker migration 0007 adds delete_hash to an existing shares table", () => {
  const db = new DatabaseSync(":memory:");
  db.exec("CREATE TABLE shares (id TEXT PRIMARY KEY, blob TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, slug TEXT, items INTEGER)");
  db.exec("INSERT INTO shares VALUES ('old', 'b', 1, 2, NULL, NULL)");
  db.exec(readFileSync(new URL("../worker/migrations/0007_share_delete.sql", import.meta.url), "utf8"));
  const cols = (db.prepare("PRAGMA table_info(shares)").all() as { name: string }[]).map((c) => c.name);
  assert.ok(cols.includes("delete_hash"));
  assert.equal((db.prepare("SELECT delete_hash FROM shares").get() as { delete_hash: null }).delete_hash, null, "old rows keep no hash");
});

// ---- app side (components/stack-manager.tsx shareStack): static proof of C1 and of where the token travels

test("share app: the token is minted on the device, only its hash is posted, meta shares is written before the copy", () => {
  const ui = readFileSync(new URL("../components/stack-manager.tsx", import.meta.url), "utf8");
  const fn = ui.slice(ui.indexOf("export async function shareStack"), ui.indexOf("export function StackManager"));
  assert.match(fn, /getRandomValues\(new Uint8Array\(32\)\)/);
  assert.match(fn, /JSON\.stringify\(\{ blob, deleteHash: await sha256Hex\(token\), \.\.\.meta \}\)/);
  assert.match(fn, /\{ id, token, expiresAt \}/);
  const rec = fn.indexOf("updateMeta<ShareRef[]>(\"shares\""); // one transaction, like lib/sync.ts
  assert.ok(rec > fn.indexOf("res.ok") && rec < fn.indexOf("clipboard"));
  assert.ok(!/`[^`]*\$\{token\}[^`]*`/.test(fn), "the token is never put into a URL");
});
