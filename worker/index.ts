// OAK Healthy edge API: zero-knowledge vault + encrypted share links. Static app served from ./out.
import { auth, keys, oauth } from "./auth";
import { circle, circleKey } from "./circles";
import { armClock, push } from "./push";
export { ReminderClock } from "./push";
import { clientIp, type Env, json, limit, sameOrigin, sha256, shortId, validId } from "./util";
import { buildSlug, isPrettySlug } from "../lib/domain/share";

const MAX_VAULT = 1_500_000, MAX_SHARE = 200_000, SHARE_TTL_MS = 365 * 864e5;

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
    const body = (await req.json().catch(() => null)) as { blob?: string; handle?: string; title?: string; items?: number } | null;
    if (!body || typeof body.blob !== "string" || !body.blob.includes(".")) return json({ error: "bad body" }, 400);
    if (body.blob.length > MAX_SHARE) return json({ error: "too large" }, 413);
    if (!(await limit(env, `share:${clientIp(req)}`, 30, 60 * 60e3))) return json({ error: "Too many attempts. Try again later." }, 429);
    const sid = shortId();
    const items = Number.isInteger(body.items) && body.items! >= 0 && body.items! <= 500 ? body.items! : null;
    const pretty = typeof body.handle === "string" && typeof body.title === "string";
    // Opt-in pretty URL: retry the random suffix on the (unlikely) unique-index collision.
    for (let i = 0; i < (pretty ? 4 : 1); i++) {
      const slug = pretty ? buildSlug(body.handle!.slice(0, 80), body.title!.slice(0, 120), crypto.getRandomValues(new Uint8Array(4))) : null;
      try {
        await env.DB.prepare("INSERT INTO shares (id, blob, created_at, expires_at, slug, items) VALUES (?, ?, ?, ?, ?, ?)").bind(sid, body.blob, Date.now(), Date.now() + SHARE_TTL_MS, slug, items).run();
        return json(slug ? { id: sid, slug } : { id: sid }, 201);
      } catch (e) { if (!pretty || !String(e).includes("UNIQUE")) throw e; }
    }
    return json({ error: "Try again" }, 503);
  }
  if (req.method === "GET" && id) {
    const bySlug = id.includes("/");
    if (bySlug ? !isPrettySlug(...(id.split("/") as [string, string])) : !validId(id)) return json({ error: "bad id" }, 400);
    const row = await env.DB.prepare(`SELECT blob FROM shares WHERE ${bySlug ? "slug" : "id"} = ? AND expires_at > ?`).bind(id, Date.now()).first<{ blob: string }>();
    return row ? json({ blob: row.blob }) : json({ error: "not found" }, 404);
  }
  return json({ error: "method" }, 405);
}

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/** Link previews for share URLs. Only public metadata (handle, slug words, item count) is used: the stack itself
 * is encrypted with the key in the #fragment, which never reaches the server. */
async function withShareOg(res: Response, env: Env, url: URL, key: string) {
  const bySlug = key.includes("/");
  const ok = bySlug ? isPrettySlug(...(key.split("/") as [string, string])) : validId(key);
  const row = ok ? await env.DB.prepare(`SELECT items FROM shares WHERE ${bySlug ? "slug" : "id"} = ? AND expires_at > ?`).bind(key, Date.now()).first<{ items: number | null }>().catch(() => null) : null;
  let title = "Shared supplement stack";
  if (bySlug) {
    const [h, s] = key.split("/");
    const words = (x: string) => x.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
    title = `${words(s.replace(/-[a-z0-9]{4}$/, ""))} · @${h}`;
  }
  const n = row?.items;
  const desc = row ? `${n != null ? `${n} routine${n === 1 ? "" : "s"} · ` : ""}End-to-end encrypted. Open to view and copy it into OAK Healthy.` : "This share link has expired or never existed.";
  const tags = [
    ["og:title", title], ["og:description", desc], ["og:url", url.origin + url.pathname], ["og:type", "website"], ["og:site_name", "OAK Healthy"],
    ["og:image", `${url.origin}/icon-512.png`], ["twitter:card", "summary"], ["twitter:title", title], ["twitter:description", desc],
  ].map(([k, v]) => `<meta ${k.startsWith("og:") ? "property" : "name"}="${k}" content="${esc(v)}">`).join("");
  const out = new HTMLRewriter()
    .on("title", { element(e) { e.setInnerContent(`${title} — OAK Healthy`); } })
    .on("head", { element(e) { e.append(`${tags}<meta name="description" content="${esc(desc)}">`, { html: true }); } })
    .transform(new Response(res.body, res));
  const h = new Headers(out.headers);
  h.set("cache-control", "no-store");
  return new Response(out.body, { status: res.status, headers: h });
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
      if (p.startsWith("/api/circle")) {
        if (!sameOrigin(req, env, url)) return json({ error: "bad origin" }, 403);
        if (p === "/api/circle-key") return await circleKey(req, env);
        pm = p.match(/^\/api\/circle(?:\/([^/]+)(?:\/(members|revoke|join|me))?)?$/);
        if (pm) return await circle(req, env, pm[1], pm[2]);
      }
      let m = p.match(/^\/api\/vault\/([^/]+)$/);
      if (m) return await vault(req, env, m[1]);
      if (p === "/api/share") return await share(req, env);
      m = p.match(/^\/api\/share\/([^/]+(?:\/[^/]+)?)$/);
      if (m) return await share(req, env, m[1]);
      if (p.startsWith("/api/")) return json({ error: "not found" }, 404);
      // Pretty share URL /s/<id> → static share viewer (key stays in #fragment, client-side only).
      const pretty = p.match(/^\/(s|c)\/([^/]+)(?:\/([^/]+))?\/?$/);
      if (pretty && (pretty[1] === "s" || !pretty[3])) {
        const res = await env.ASSETS.fetch(new Request(new URL(`/${pretty[1]}/`, url), req));
        if (pretty[1] === "s" && res.ok) return await withShareOg(res, env, url, pretty[3] ? `${pretty[2]}/${pretty[3]}` : pretty[2]);
        return new Response(res.body, res);
      }
      return env.ASSETS.fetch(req);
    } catch (e) {
      console.error("unhandled", p, e);
      return json({ error: "server" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;