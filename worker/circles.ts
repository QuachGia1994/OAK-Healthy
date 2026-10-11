// Circles API: a live, read-only, end-to-end encrypted view of one profile (docs/feat/circle-accounts.md § API).
// Owner = holder of the write token. Members = OAK accounts that joined with the invite secret. Bearer circles
// (join_hash NULL) predate members: anyone with the link's #key reads them. The server stores ciphertext,
// public keys and hashes only. Since 2.6 the server keeps a per-circle epoch (invite rotations): owner writes carry
// the epoch they were made under and a stale one is refused with 409 + the current epoch (epochGate).
import { sessionUser } from "./auth";
import { body, clientIp, type Env, json, limit, sha256, shortId, validId } from "./util";
import { epochGate, isEpoch, STALE_EPOCH } from "../lib/member-keys";

const MAX_CIRCLE = 400_000, MAX_MEMBERS = 20, HOUR = 60 * 60e3;
const isB64 = (s: unknown, min: number, max: number): s is string => typeof s === "string" && s.length >= min && s.length <= max && /^[A-Za-z0-9_.-]+$/.test(s);
const isBlob = (s: unknown): s is string => typeof s === "string" && s.includes(".");
const isJoinAuth = (s: unknown): s is string => isB64(s, 40, 64);
const bearer = (req: Request) => req.headers.get("authorization")?.replace(/^Bearer /, "") ?? "";
const forbidden = () => json({ error: "forbidden" }, 403);

interface CircleRow { blob: string; write_hash: string; join_hash: string | null; updated_at: number; epoch: number | null }
const badEpoch = (e: unknown) => e !== undefined && !isEpoch(e);
/** A guarded write lost a race after the gate passed: answer with the epoch it lost to. */
async function staleEpoch(env: Env, id: string) {
  const r = await env.DB.prepare("SELECT epoch FROM circles WHERE id = ?").bind(id).first<{ epoch: number | null }>();
  return json({ error: STALE_EPOCH, epoch: r?.epoch ?? null }, 409);
}

export async function circle(req: Request, env: Env, id?: string, sub?: string): Promise<Response> {
  if (!id) return req.method === "POST" ? create(req, env) : json({ error: "method" }, 405);
  if (!validId(id)) return json({ error: "bad id" }, 400);
  const row = await env.DB.prepare("SELECT blob, write_hash, join_hash, updated_at, epoch FROM circles WHERE id = ?").bind(id).first<CircleRow>();
  if (sub === "join" || sub === "me") return member(req, env, id, row, sub);
  if (!sub && req.method === "GET") {
    if (!row) return json({ error: "not found" }, 404);
    return row.join_hash ? json({ error: "Members only" }, 403) : json({ blob: row.blob, updatedAt: row.updated_at });
  }
  return owner(req, env, id, row, sub);
}

async function create(req: Request, env: Env) {
  const token = bearer(req);
  const b = await body<{ blob?: string; joinAuth?: string; epoch?: number }>(req);
  if (!b || !isBlob(b.blob) || token.length < 20 || (b.joinAuth !== undefined && !isJoinAuth(b.joinAuth)) || badEpoch(b.epoch)) return json({ error: "bad body" }, 400);
  if (b.blob.length > MAX_CIRCLE) return json({ error: "too large" }, 413);
  if (!(await limit(env, `circle:${clientIp(req)}`, 20, HOUR))) return json({ error: "Too many attempts. Try again later." }, 429);
  const cid = shortId(12);
  await env.DB.prepare("INSERT INTO circles (id, blob, write_hash, join_hash, updated_at, epoch) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(cid, b.blob, await sha256(token), b.joinAuth ? await sha256(b.joinAuth) : null, Date.now(), b.epoch ?? null).run();
  return json({ id: cid }, 201);
}

async function owner(req: Request, env: Env, id: string, row: CircleRow | null, sub?: string) {
  const authed = !!row && row.write_hash === (await sha256(bearer(req)));
  if (req.method === "DELETE" && !sub) {
    if (!row) return json({ ok: true }); // already gone: idempotent
    if (!authed) return forbidden();
    await env.DB.batch([env.DB.prepare("DELETE FROM circles WHERE id = ?").bind(id), env.DB.prepare("DELETE FROM circle_members WHERE circle_id = ?").bind(id)]);
    return json({ ok: true });
  }
  if (!row || !authed) return forbidden();
  if (!sub && req.method === "PUT") return publish(req, env, id, row);
  if (sub === "members" && req.method === "GET") {
    const { results } = await env.DB.prepare(
      `SELECT m.user_id, m.pub, m.proof, m.joined_at, m.wrap IS NOT NULL AS active, u.email FROM circle_members m
       LEFT JOIN users u ON u.id = m.user_id WHERE m.circle_id = ? ORDER BY m.joined_at`).bind(id)
      .all<{ user_id: string; pub: string; proof: string; joined_at: number; active: number; email: string | null }>();
    const members = results.map((m) => ({ userId: m.user_id, email: m.email, pub: m.pub, proof: m.proof, active: !!m.active, joinedAt: m.joined_at }));
    return json({ members, epoch: row.epoch });
  }
  if (sub === "revoke" && req.method === "POST") {
    const b = await body<{ userId?: string; joinAuth?: string; epoch?: number }>(req);
    if (!b || typeof b.userId !== "string" || !validId(b.userId) || !isJoinAuth(b.joinAuth) || badEpoch(b.epoch)) return json({ error: "bad body" }, 400);
    const hash = await sha256(b.joinAuth);
    // A retry of a rotation that already landed (the owner lost the response): its fresh join_hash is still the
    // current one, so the rotation and the member removal are both done. Report it, so the owner adopts the new invite.
    if (row.join_hash === hash) return json({ ok: true, epoch: row.epoch });
    const gate = epochGate(row.epoch, b.epoch);
    if (!gate.ok) return json({ error: gate.error, epoch: gate.epoch }, 409);
    // The member row goes only if this rotation won the guarded UPDATE (its fresh join_hash is unique).
    const next = gate.base === null ? null : gate.base + 1;
    const [rotated] = await env.DB.batch([
      env.DB.prepare("UPDATE circles SET join_hash = ?, epoch = ? WHERE id = ? AND epoch IS ?").bind(hash, next, id, row.epoch),
      env.DB.prepare("DELETE FROM circle_members WHERE circle_id = ? AND user_id = ? AND EXISTS (SELECT 1 FROM circles WHERE id = ? AND join_hash = ?)").bind(id, b.userId, id, hash),
    ]);
    return rotated.meta.changes ? json({ ok: true, epoch: next }) : staleEpoch(env, id);
  }
  return json({ error: "method" }, 405);
}

/** Every member publish carries a fresh content key; members left out of `wraps` lose read access at once. */
async function publish(req: Request, env: Env, id: string, row: CircleRow) {
  const b = await body<{ blob?: string; wraps?: Record<string, string>; epoch?: number }>(req);
  if (!b || !isBlob(b.blob) || b.blob.length > MAX_CIRCLE || badEpoch(b.epoch)) return json({ error: "bad body" }, 400);
  const wraps = Object.entries(b.wraps && typeof b.wraps === "object" ? b.wraps : {});
  if (wraps.length > MAX_MEMBERS || wraps.some(([uid, w]) => !validId(uid) || !isB64(w, 100, 400))) return json({ error: "bad body" }, 400);
  const gate = epochGate(row.epoch, b.epoch);
  if (!gate.ok) return json({ error: gate.error, epoch: gate.epoch }, 409);
  const now = Date.now(), next = gate.base ?? row.epoch;
  const stmts = [env.DB.prepare("UPDATE circles SET blob = ?, updated_at = ?, epoch = ? WHERE id = ? AND epoch IS ?").bind(b.blob, now, next, id, row.epoch)];
  if (row.join_hash) {
    // Wraps change only if this publish won the guarded UPDATE above (same batch = one transaction).
    const won = "AND EXISTS (SELECT 1 FROM circles WHERE id = ? AND updated_at = ? AND epoch IS ?)";
    stmts.push(env.DB.prepare(`UPDATE circle_members SET wrap = NULL WHERE circle_id = ? ${won}`).bind(id, id, now, next));
    for (const [uid, w] of wraps) stmts.push(env.DB.prepare(`UPDATE circle_members SET wrap = ? WHERE circle_id = ? AND user_id = ? ${won}`).bind(w, id, uid, id, now, next));
  }
  const [stored] = await env.DB.batch(stmts);
  return stored.meta.changes ? json({ ok: true, epoch: next }) : staleEpoch(env, id);
}

async function member(req: Request, env: Env, id: string, row: CircleRow | null, sub: "join" | "me") {
  const u = await sessionUser(req, env);
  if (!u) return json({ error: "Sign in first" }, 401);
  if (!row?.join_hash) return json({ error: "This circle was deleted or never existed." }, 404);
  if (sub === "me" && req.method === "GET") {
    const m = await env.DB.prepare("SELECT wrap FROM circle_members WHERE circle_id = ? AND user_id = ?").bind(id, u.id).first<{ wrap: string | null }>();
    if (!m) return json({ error: "You are not a member of this circle." }, 404);
    return m.wrap ? json({ blob: row.blob, wrap: m.wrap, updatedAt: row.updated_at }) : json({ pending: true });
  }
  if (sub !== "join" || req.method !== "POST") return json({ error: "method" }, 405);
  if (!(await limit(env, `circle-join:${u.id}`, 20, HOUR))) return json({ error: "Too many attempts. Try again later." }, 429);
  const b = await body<{ joinAuth?: string; pub?: string; proof?: string }>(req);
  if (!b || !isJoinAuth(b.joinAuth) || !isB64(b.pub, 80, 100) || !isB64(b.proof, 40, 50)) return json({ error: "bad body" }, 400);
  if ((await sha256(b.joinAuth)) !== row.join_hash) return json({ error: "This invite link is no longer valid. Ask for a new one." }, 403);
  // One statement, so concurrent joins cannot push the circle past MAX_MEMBERS.
  const r = await env.DB.prepare(
    `INSERT INTO circle_members (circle_id, user_id, pub, proof, wrap, joined_at)
     SELECT ?1, ?2, ?3, ?4, NULL, ?5 WHERE (SELECT COUNT(*) FROM circle_members WHERE circle_id = ?1 AND user_id != ?2) < ?6
     ON CONFLICT(circle_id, user_id) DO UPDATE SET pub = excluded.pub, proof = excluded.proof, wrap = NULL`)
    .bind(id, u.id, b.pub, b.proof, Date.now(), MAX_MEMBERS).run();
  return r.meta.changes ? json({ ok: true }) : json({ error: "This circle is full." }, 409);
}

/** The account's member keypair: public key plus the private key sealed with a sync-key-derived key. */
export async function circleKey(req: Request, env: Env): Promise<Response> {
  const u = await sessionUser(req, env);
  if (!u) return json({ error: "Sign in first" }, 401);
  const read = () => env.DB.prepare("SELECT pub, sealed FROM member_keys WHERE user_id = ?").bind(u.id).first<{ pub: string; sealed: string }>();
  if (req.method === "GET") return json({ key: await read() });
  if (req.method !== "PUT") return json({ error: "method" }, 405);
  if (!(await limit(env, `circle-key:${u.id}`, 20, HOUR))) return json({ error: "Too many attempts. Try again later." }, 429);
  const b = await body<{ pub?: string; sealed?: string; replace?: boolean }>(req);
  if (!b || !isB64(b.pub, 80, 100) || !isB64(b.sealed, 100, 2000)) return json({ error: "bad body" }, 400);
  // First writer wins unless the client could not open the stored key (sync key rotated) and asks to replace it.
  const conflict = b.replace === true ? "DO UPDATE SET pub = excluded.pub, sealed = excluded.sealed, updated_at = excluded.updated_at" : "DO NOTHING";
  await env.DB.prepare(`INSERT INTO member_keys (user_id, pub, sealed, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(user_id) ${conflict}`)
    .bind(u.id, b.pub, b.sealed, Date.now()).run();
  return json({ key: await read() });
}
