// Web Push reminders with VAPID (RFC 8292). Pushes carry no payload: the service worker wakes up and builds the
// notification from on-device data, so the server only knows "ping this endpoint at minute N (UTC)".
import { DurableObject } from "cloudflare:workers";
import { b64u, body, type Env, json, sha256 } from "./util";

const te = new TextEncoder();
let signKey: CryptoKey | null = null;

async function vapidJwt(env: Env, aud: string) {
  signKey ??= await crypto.subtle.importKey("jwk", JSON.parse(env.VAPID_PRIVATE_JWK!), { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const enc = (o: unknown) => b64u(te.encode(JSON.stringify(o)));
  const unsigned = `${enc({ typ: "JWT", alg: "ES256" })}.${enc({ aud, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: env.VAPID_SUBJECT || "mailto:privacy@oak-healthy.app" })}`;
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, signKey, te.encode(unsigned));
  return `${unsigned}.${b64u(sig)}`;
}

export async function sendPush(env: Env, endpoint: string, topic?: string) {
  const aud = new URL(endpoint).origin;
  const h: Record<string, string> = { authorization: `vapid t=${await vapidJwt(env, aud)}, k=${env.VAPID_PUBLIC}`, ttl: "600", urgency: "high", "content-length": "0" };
  if (topic) h.topic = topic;
  return fetch(endpoint, { method: "POST", headers: h });
}

const PUSH_HOSTS = ["fcm.googleapis.com", "android.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com"];
const okEndpoint = (e: unknown): e is string => {
  if (typeof e !== "string" || e.length > 1000) return false;
  // Only real browser push services (no SSRF to arbitrary hosts).
  try { const u = new URL(e); return u.protocol === "https:" && !u.port && PUSH_HOSTS.some((h) => u.hostname === h || u.hostname.endsWith("." + h)); } catch { return false; }
};

export async function push(req: Request, env: Env, action: string): Promise<Response> {
  if (!env.VAPID_PUBLIC || !env.VAPID_PRIVATE_JWK) return json({ error: "Push is not configured" }, 503);
  if (action === "key" && req.method === "GET") return json({ key: env.VAPID_PUBLIC });
  const b = await body<{ endpoint?: string; minutes?: number[] }>(req);
  if (!okEndpoint(b?.endpoint)) return json({ error: "bad endpoint" }, 400);
  const id = await sha256(b.endpoint);
  if (action === "subscribe" && req.method === "PUT") {
    const mins = [...new Set((b.minutes ?? []).filter((n) => Number.isInteger(n) && n >= 0 && n < 1440))].slice(0, 96);
    const list = mins.length ? "," + mins.map((n) => String(n).padStart(4, "0")).join(",") + "," : ",";
    await env.DB.prepare("INSERT INTO push_subs (id, endpoint, minutes, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET minutes = excluded.minutes, updated_at = excluded.updated_at")
      .bind(id, b.endpoint, list, Date.now()).run();
    await armClock(env).catch(() => {});
    return json({ ok: true, count: mins.length });
  }
  if (action === "subscribe" && req.method === "DELETE") {
    await env.DB.prepare("DELETE FROM push_subs WHERE id = ?").bind(id).run();
    return json({ ok: true });
  }
  if (action === "test" && req.method === "POST") {
    const row = await env.DB.prepare("SELECT endpoint FROM push_subs WHERE id = ?").bind(id).first<{ endpoint: string }>();
    if (!row) return json({ error: "not subscribed" }, 404);
    const r = await sendPush(env, row.endpoint, "test");
    return json({ ok: r.ok, status: r.status });
  }
  return json({ error: "not found" }, 404);
}

/** Cron (every minute): ping endpoints whose reminder list contains the current UTC minute. */
export async function remind(env: Env, when = Date.now()) {
  if (!env.VAPID_PUBLIC || !env.VAPID_PRIVATE_JWK) return 0;
  const d = new Date(when);
  const key = "%," + String(d.getUTCHours() * 60 + d.getUTCMinutes()).padStart(4, "0") + ",%";
  // Workers Free allows 50 subrequests per invocation: cap at 45 pushes per minute (raise on a paid plan).
  const rows = await env.DB.prepare("SELECT id, endpoint FROM push_subs WHERE minutes LIKE ? ORDER BY updated_at LIMIT 45").bind(key).all<{ id: string; endpoint: string }>();
  let sent = 0;
  await Promise.all(rows.results.map(async (r) => {
    const res = await sendPush(env, r.endpoint, "dose").catch(() => null);
    if (res?.ok) sent++;
    else if (res && (res.status === 404 || res.status === 410)) await env.DB.prepare("DELETE FROM push_subs WHERE id = ?").bind(r.id).run();
  }));
  return sent;
}

/** Minute clock without cron triggers: one Durable Object re-arms its alarm at the start of every minute. */
export class ReminderClock extends DurableObject<Env> {
  async fetch() {
    if ((await this.ctx.storage.getAlarm()) == null) await this.ctx.storage.setAlarm(nextMinute());
    return new Response("armed");
  }
  async alarm() {
    await this.ctx.storage.setAlarm(nextMinute());
    const n = await remind(this.env).catch((e) => { console.error("remind", e); return 0; });
    if (n) console.log("reminders sent", n);
  }
}
const nextMinute = () => Math.floor(Date.now() / 60e3) * 60e3 + 60e3 + 500;
export const armClock = (env: Env) => env.CLOCK.get(env.CLOCK.idFromName("clock")).fetch("https://clock/arm");
