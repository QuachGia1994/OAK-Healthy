export interface Env {
  ASSETS: Fetcher; DB: D1Database; CLOCK: DurableObjectNamespace;
  AUTH_SECRET?: string; APP_ORIGIN?: string; RESEND_API_KEY?: string; MAIL_FROM?: string; DEV_MAIL?: string;
  GOOGLE_CLIENT_ID?: string; GOOGLE_CLIENT_SECRET?: string; GITHUB_CLIENT_ID?: string; GITHUB_CLIENT_SECRET?: string;
  VAPID_PUBLIC?: string; VAPID_PRIVATE_JWK?: string; VAPID_SUBJECT?: string;
}
export const json = (data: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json", "cache-control": "no-store", ...headers } });
const te = new TextEncoder();
export const hex = (b: ArrayBuffer) => [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
export const sha256 = async (s: string) => hex(await crypto.subtle.digest("SHA-256", te.encode(s)));
export const b64u = (b: ArrayBuffer | Uint8Array) => btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
export const unb64u = (s: string) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
export const randomToken = (n = 32) => b64u(crypto.getRandomValues(new Uint8Array(n)));
export function shortId(n = 10) {
  const a = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => a[b % a.length]).join("");
}
export const validId = (s: string) => /^[A-Za-z0-9_-]{8,64}$/.test(s);
export async function hmac(secret: string, msg: string) {
  const k = await crypto.subtle.importKey("raw", te.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", k, te.encode(msg)));
}
export const body = async <T>(req: Request) => (await req.json().catch(() => null)) as T | null;
export const appOrigin = (env: Env, url: URL) => env.APP_ORIGIN || url.origin;
export const clientIp = (req: Request) => req.headers.get("cf-connecting-ip") ?? "local";

/** CSRF: browsers always send Origin on cross-site POST/PUT/DELETE; require it to match ours. */
export function sameOrigin(req: Request, env: Env, url: URL) {
  if (req.method === "GET" || req.method === "HEAD") return true;
  const o = req.headers.get("origin");
  return o === url.origin || o === env.APP_ORIGIN;
}

/** Fixed-window rate limit stored in D1. Returns true when the call is allowed. */
export async function limit(env: Env, key: string, max: number, windowMs: number) {
  const now = Date.now();
  const row = await env.DB.prepare(
    `INSERT INTO rate_limits (key, count, window_start) VALUES (?1, 1, ?2)
     ON CONFLICT(key) DO UPDATE SET count = CASE WHEN window_start < ?3 THEN 1 ELSE count + 1 END,
       window_start = CASE WHEN window_start < ?3 THEN ?2 ELSE window_start END
     RETURNING count`).bind(key, now, now - windowMs).first<{ count: number }>();
  return (row?.count ?? 1) <= max;
}
