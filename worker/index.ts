// OAK Healthy edge API: zero-knowledge vault + encrypted share links. Static app served from ./out.
interface Env { ASSETS: Fetcher; DB: D1Database }

const MAX_VAULT = 1_500_000, MAX_SHARE = 200_000, SHARE_TTL_MS = 365 * 864e5;
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

async function sha256(s: string) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function shortId(n = 10) {
  const a = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => a[b % a.length]).join("");
}
const validId = (s: string) => /^[A-Za-z0-9_-]{8,64}$/.test(s);

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

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const p = url.pathname;
    try {
      if (p === "/api/health") return json({ ok: true, service: "oak-healthy", time: Date.now() });
      let m = p.match(/^\/api\/vault\/([^/]+)$/);
      if (m) return await vault(req, env, m[1]);
      if (p === "/api/share") return await share(req, env);
      m = p.match(/^\/api\/share\/([^/]+)$/);
      if (m) return await share(req, env, m[1]);
      if (p.startsWith("/api/")) return json({ error: "not found" }, 404);
      // Pretty share URL /s/<id> → static share viewer (key stays in #fragment, client-side only).
      if (/^\/s\/[^/]+\/?$/.test(p)) {
        const res = await env.ASSETS.fetch(new Request(new URL("/s/", url), req));
        return new Response(res.body, res);
      }
      return env.ASSETS.fetch(req);
    } catch (e) {
      return json({ error: "server", detail: String(e) }, 500);
    }
  },
} satisfies ExportedHandler<Env>;