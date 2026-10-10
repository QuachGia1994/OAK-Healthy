// OAK Healthy edge API: zero-knowledge vault + encrypted share links. Static app served from ./out.
import { auth, keys, oauth } from "./auth";
import { armClock, push } from "./push";
export { ReminderClock } from "./push";
import { clientIp, type Env, json, limit, sameOrigin, sha256, shortId, validId } from "./util";

const MAX_VAULT = 1_500_000, MAX_SHARE = 200_000, MAX_CIRCLE = 400_000, SHARE_TTL_MS = 365 * 864e5;

async function vault(req: Request, env: Env, id: string) {
  if (!validId(id)) return json({ error: "bad id" }, 400);
  const token = req.headers.get("authorization")?.replace(/^Bearer /, "") ?? "";
  if (token.length < 20) return json({ error: "unauthorized" }, 401);
  const authHash = await sha256(token);
  const row = await env.DB.prepare("SELECT rev, blob, auth_hash FROM vaults WHERE id = ?").bind(id).first<{ rev: number; blob: string; auth_hash: string }>();
  if (row && row.auth_hash !== authHash) return json({ error: "forbidden" }, 403);

  if (req.method === "GET") return row ? json({ rev: row.rev, blob: row.blob }) : json({ error: "not found" }, 404);
  if (req.method === "PUT") {
    const body = (await req.json().catch(() => null)) as { baseRev?: number; blob?: string } | null;
    if (!body || typeof body.blob !== "string" || typeof body.baseRev !== "number") return json({ error: "bad body" }, 400);
    if (body.blob.length > MAX_VAULT) return json({ error: "too large" }, 413);
    const now = Date.now();
    if (!row) {
      if (body.baseRev !== 0) return json({ error: "conflict" }, 409);
      const r = await env.DB.prepare("INSERT OR IGNORE INTO vaults (id, rev, blob, auth_hash, updated_at) VALUES (?, 1, ?, ?, ?)").bind(id, body.blob, authHash, now).run();
      return r.meta.changes ? json({ rev: 1 }) : json({ error: "conflict" }, 409);
    }
    const r = await env.DB.prepare("UPDATE vaults SET rev = rev + 1, blob = ?, updated_at = ? WHERE id = ? AND rev = ? AND auth_hash = ?")
      .bind(body.blob, now, id, body.baseRev, authHash).run();
    return r.meta.changes ? json({ rev: body.baseRev + 1 }) : json({ error: "conflict" }, 409);
  }
  if (req.method === "DELETE") {
    await env.DB.prepare("DELETE FROM vaults WHERE id = ? AND auth_hash = ?").bind(id, authHash).run();
    return json({ ok: true });
  }
  return json({ error: "method" }, 405);
}

async function share(req: Request, env: Env, id?: string) {
  if (req.method === "POST" && !id) {
    const body = (await req.json().catch(() => null)) as { blob?: string } | null;
    if (!body || typeof body.blob !== "string" || !body.blob.includes(".")) return json({ error: "bad body" }, 400);
    if (body.blob.length > MAX_SHARE) return json({ error: "too large" }, 413);
    if (!(await limit(env, `share:${clientIp(req)}`, 30, 60 * 60e3))) return json({ error: "Too many attempts. Try again later." }, 429);
    const sid = shortId();
    await env.DB.prepare("INSERT INTO shares (id, blob, created_at, expires_at) VALUES (?, ?, ?, ?)").bind(sid, body.blob, Date.now(), Date.now() + SHARE_TTL_MS).run();
    return json({ id: sid }, 201);
  }
  if (req.method === "GET" && id) {
    if (!validId(id)) return json({ error: "bad id" }, 400);
    const row = await env.DB.prepare("SELECT blob FROM shares WHERE id = ? AND expires_at > ?").bind(id, Date.now()).first<{ blob: string }>();
    return row ? json({ blob: row.blob }) : json({ error: "not found" }, 404);
  }
  return json({ error: "method" }, 405);
}

/** Circles: a live, read-only, end-to-end encrypted view of one profile for family or a coach.
 * The publisher holds a write token; viewers hold only the decryption key in the URL fragment. */
async function circle(req: Request, env: Env, id?: string) {
  const token = req.headers.get("authorization")?.replace(/^Bearer /, "") ?? "";
  if (req.method === "POST" && !id) {
    const b = (await req.json().catch(() => null)) as { blob?: string } | null;
    if (!b || typeof b.blob !== "string" || !b.blob.includes(".") || token.length < 20) return json({ error: "bad body" }, 400);
    if (b.blob.length > MAX_CIRCLE) return json({ error: "too large" }, 413);
    if (!(await limit(env, `circle:${clientIp(req)}`, 20, 60 * 60e3))) return json({ error: "Too many attempts. Try again later." }, 429);
    const cid = shortId(12);
    await env.DB.prepare("INSERT INTO circles (id, blob, write_hash, updated_at) VALUES (?, ?, ?, ?)").bind(cid, b.blob, await sha256(token), Date.now()).run();
    return json({ id: cid }, 201);
  }
  if (!id || !validId(id)) return json({ error: "bad id" }, 400);
  if (req.method === "GET") {
    const row = await env.DB.prepare("SELECT blob, updated_at FROM circles WHERE id = ?").bind(id).first<{ blob: string; updated_at: number }>();
    return row ? json({ blob: row.blob, updatedAt: row.updated_at }) : json({ error: "not found" }, 404);
  }
  const wh = await sha256(token);
  if (req.method === "PUT") {
    const b = (await req.json().catch(() => null)) as { blob?: string } | null;
    if (!b || typeof b.blob !== "string" || b.blob.length > MAX_CIRCLE) return json({ error: "bad body" }, 400);
    const r = await env.DB.prepare("UPDATE circles SET blob = ?, updated_at = ? WHERE id = ? AND write_hash = ?").bind(b.blob, Date.now(), id, wh).run();
    return r.meta.changes ? json({ ok: true }) : json({ error: "forbidden" }, 403);
  }
  if (req.method === "DELETE") {
    const r = await env.DB.prepare("DELETE FROM circles WHERE id = ? AND write_hash = ?").bind(id, wh).run();
    if (r.meta.changes) return json({ ok: true });
    const exists = await env.DB.prepare("SELECT 1 FROM circles WHERE id = ?").bind(id).first();
    return exists ? json({ error: "forbidden" }, 403) : json({ ok: true }); // already gone: idempotent
  }
  return json({ error: "method" }, 405);
}

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    const p = url.pathname;
    try {
      if (p === "/api/health") { ctx.waitUntil(armClock(env).catch(() => {})); return json({ ok: true, service: "oak-healthy", time: Date.now() }); }
      if (p.startsWith("/api/auth/") || p.startsWith("/api/keys")) {
        if (!sameOrigin(req, env, url)) return json({ error: "bad origin" }, 403);
        let a = p.match(/^\/api\/auth\/oauth\/([a-z]+)\/(start|callback)$/);
        if (a) return await oauth(req, env, url, a[1], a[2]);
        a = p.match(/^\/api\/auth\/([a-z]+)$/);
        if (a) return await auth(req, env, url, a[1]);
        a = p.match(/^\/api\/keys(?:\/([A-Za-z0-9-]{8,64}))?$/);
        if (a) return await keys(req, env, a[1]);
      }
      let pm = p.match(/^\/api\/push\/([a-z]+)$/);
      if (pm) return await push(req, env, pm[1]);
      pm = p.match(/^\/api\/circle(?:\/([^/]+))?$/);
      if (pm) return await circle(req, env, pm[1]);
      let m = p.match(/^\/api\/vault\/([^/]+)$/);
      if (m) return await vault(req, env, m[1]);
      if (p === "/api/share") return await share(req, env);
      m = p.match(/^\/api\/share\/([^/]+)$/);
      if (m) return await share(req, env, m[1]);
      if (p.startsWith("/api/")) return json({ error: "not found" }, 404);
      // Pretty share URL /s/<id> → static share viewer (key stays in #fragment, client-side only).
      const pretty = p.match(/^\/(s|c)\/[^/]+\/?$/);
      if (pretty) {
        const res = await env.ASSETS.fetch(new Request(new URL(`/${pretty[1]}/`, url), req));
        return new Response(res.body, res);
      }
      return env.ASSETS.fetch(req);
    } catch (e) {
      console.error("unhandled", p, e);
      return json({ error: "server" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;