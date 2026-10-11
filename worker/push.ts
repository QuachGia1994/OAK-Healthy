// Web Push reminders with VAPID (RFC 8292). Pushes carry no payload: the service worker wakes up and builds the
// notification from on-device data, so the server only knows "ping this endpoint at minute N (UTC)".
import { DurableObject } from "cloudflare:workers";
import { b64u, body, type Env, json, sha256 } from "./util";
import { okEndpoint, okSnooze } from "../lib/domain/remind";

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

export async function push(req: Request, env: Env, action: string): Promise<Response> {
  if (!env.VAPID_PUBLIC || !env.VAPID_PRIVATE_JWK) return json({ error: "Push is not configured" }, 503);
  if (action === "key" && req.method === "GET") return json({ key: env.VAPID_PUBLIC });
  const b = await body<{ endpoint?: string; minutes?: number[]; snooze?: number; to?: string }>(req);
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
  // One-off ping N minutes from now (notification "Snooze" action). One pending snooze per endpoint; latest wins.
  if (action === "snooze" && req.method === "POST") {
    if (!okSnooze(b.snooze)) return json({ error: "bad snooze" }, 400);
    const at = Math.floor((Date.now() + b.snooze * 60e3) / 60e3) * 60e3;
    const r = await env.DB.prepare("UPDATE push_subs SET snooze_at = ? WHERE id = ?").bind(at, id).run();
    if (!r.meta.changes) return json({ error: "not subscribed" }, 404);
    await armClock(env).catch(() => {});
    return json({ ok: true, at });
  }
  // The browser rotated the subscription (sw.js pushsubscriptionchange, maybe with no tab open): move minutes and any
  // pending snooze from the old endpoint's row to the new one and delete the old row, in one batch.
  if (action === "move" && req.method === "POST") {
    if (!okEndpoint(b.to)) return json({ error: "bad endpoint" }, 400);
    const to = await sha256(b.to);
    if (to === id) return json({ ok: true });
    const [moved] = await env.DB.batch([
      env.DB.prepare("INSERT INTO push_subs (id, endpoint, minutes, updated_at, snooze_at) SELECT ?, ?, minutes, ?, snooze_at FROM push_subs WHERE id = ? ON CONFLICT(id) DO UPDATE SET endpoint = excluded.endpoint, minutes = excluded.minutes, updated_at = excluded.updated_at, snooze_at = excluded.snooze_at")
        .bind(to, b.to, Date.now(), id),
      env.DB.prepare("DELETE FROM push_subs WHERE id = ?").bind(id),
    ]);
    if (!moved.meta.changes) return json({ error: "not subscribed" }, 404);
    await armClock(env).catch(() => {});
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

/** Every minute: ping endpoints whose reminder list contains the current UTC minute, or whose snooze is due. */
export async function remind(env: Env, when = Date.now()) {
  if (!env.VAPID_PUBLIC || !env.VAPID_PRIVATE_JWK) return 0;
  const d = new Date(when);
  const key = "%," + String(d.getUTCHours() * 60 + d.getUTCMinutes()).padStart(4, "0") + ",%";
  // Workers Free allows 50 subrequests per invocation: cap at 45 pushes per minute (raise on a paid plan).
  const rows = await env.DB.prepare("SELECT id, endpoint FROM push_subs WHERE minutes LIKE ? OR snooze_at <= ? ORDER BY updated_at LIMIT 45").bind(key, when + 30e3).all<{ id: string; endpoint: string }>();
  if (rows.results.length) await env.DB.prepare(`UPDATE push_subs SET snooze_at = NULL WHERE snooze_at <= ? AND id IN (${rows.results.map(() => "?").join(",")})`).bind(when + 30e3, ...rows.results.map((r) => r.id)).run();
  let sent = 0;
  await Promise.all(rows.results.map(async (r) => {
    const res = await sendPush(env, r.endpoint, "dose").catch(() => null);
    if (res?.ok) sent++;
    else if (res && (res.status === 404 || res.status === 410)) await env.DB.prepare("DELETE FROM push_subs WHERE id = ?").bind(r.id).run();
  }));
  return sent;
}

// Daily purge (docs/arch/HEALTH_DATA_FLOW.md § Tombstone purge). DELETE … LIMIT needs a compile flag D1 may lack, so
// each statement bounds an id subquery. Per row: [statement, age before `now` it must exceed].
const PURGE_EVERY_MS = 864e5, PURGE_BATCH = 500;
const PURGES: [string, number][] = [
  // Vault tombstones (Delete everything) after 365 days; live vaults (deleted_at NULL) never match.
  ["DELETE FROM vaults WHERE id IN (SELECT id FROM vaults WHERE deleted_at IS NOT NULL AND deleted_at < ? LIMIT ?)", 365 * 864e5],
  // Expired share links (GET already hides them).
  ["DELETE FROM shares WHERE id IN (SELECT id FROM shares WHERE expires_at < ? LIMIT ?)", 0],
];

/** At most once a day; a full batch leaves the day open, so a backlog drains one batch per statement per minute. */
async function purgeDaily(env: Env, storage: Pick<DurableObjectStorage, "get" | "put">, now = Date.now()) {
  if (now - ((await storage.get<number>("lastPurge")) ?? 0) < PURGE_EVERY_MS) return 0;
  let total = 0, full = false;
  for (const [sql, age] of PURGES) {
    const n = (await env.DB.prepare(sql).bind(now - age, PURGE_BATCH).run()).meta.changes;
    total += n;
    full ||= n >= PURGE_BATCH;
  }
  if (!full) await storage.put("lastPurge", now);
  return total;
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
    const purged = await purgeDaily(this.env, this.ctx.storage).catch((e) => { console.error("purge", e); return 0; });
    if (purged) console.log("rows purged", purged);
  }
}
const nextMinute = () => Math.floor(Date.now() / 60e3) * 60e3 + 60e3 + 500;
export const armClock = (env: Env) => env.CLOCK.get(env.CLOCK.idFromName("clock")).fetch("https://clock/arm");
