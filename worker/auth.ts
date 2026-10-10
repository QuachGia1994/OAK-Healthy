// Accounts without breaking zero-knowledge: the browser stretches the password (PBKDF2-SHA256, 600k) into
// authKey (sent here, stored salted+hashed) and encKey (never sent) that wraps the OAK sync key. The server
// stores only that wrapped blob, so it can sign you in but can never read your vault.
import { appOrigin, b64u, body, clientIp, type Env, hmac, json, limit, randomToken, sha256 } from "./util";

const SESSION_MS = 30 * 864e5, RESET_MS = 30 * 60e3, OAUTH_MS = 10 * 60e3;
const COOKIE = "oak_sid";
const emailOk = (e: unknown): e is string => typeof e === "string" && e.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
const normEmail = (e: string) => e.trim().toLowerCase();
const isB64 = (s: unknown, min: number, max: number): s is string => typeof s === "string" && s.length >= min && s.length <= max && /^[A-Za-z0-9_.-]+$/.test(s);
// Fail closed: without AUTH_SECRET only local dev (DEV_MAIL=1) may use a fixed secret; production requests 500.
const secret = (env: Env) => {
  if (env.AUTH_SECRET) return env.AUTH_SECRET;
  if (env.DEV_MAIL === "1") return "dev-only-secret-set-AUTH_SECRET";
  throw new Error("AUTH_SECRET is not set");
};
const tooMany = () => json({ error: "Too many attempts. Try again later." }, 429);

interface User { id: string; email: string; salt: string | null; auth_hash: string | null; wrapped: string | null; created_at: number }

function cookie(req: Request, name: string) {
  const m = (req.headers.get("cookie") ?? "").match(new RegExp(`(?:^|;\\s*)${name}=([^;]+)`));
  return m?.[1];
}
const setCookie = (url: URL, value: string, maxAge: number) =>
  `${COOKIE}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${url.protocol === "https:" ? "; Secure" : ""}`;

async function newSession(env: Env, url: URL, userId: string) {
  const sid = randomToken();
  const now = Date.now();
  await env.DB.prepare("INSERT INTO sessions (id_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)").bind(await sha256(sid), userId, now, now + SESSION_MS).run();
  await env.DB.prepare("DELETE FROM sessions WHERE expires_at < ?").bind(now).run();
  return setCookie(url, sid, SESSION_MS / 1000);
}

export async function sessionUser(req: Request, env: Env): Promise<User | null> {
  const sid = cookie(req, COOKIE);
  if (!sid) return null;
  return env.DB.prepare("SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.id_hash = ? AND s.expires_at > ?")
    .bind(await sha256(sid), Date.now()).first<User>();
}

/** Same response shape for unknown emails: a deterministic HMAC "salt" so attackers can't enumerate accounts. */
async function saltFor(env: Env, email: string) {
  const u = await env.DB.prepare("SELECT salt FROM users WHERE email = ?").bind(email).first<{ salt: string | null }>();
  return u?.salt ?? b64u((await hmac(secret(env), "salt:" + email)).slice(0, 16));
}
const authHash = (salt: string, authKey: string) => sha256(`${salt}:${authKey}`);

async function sendMail(env: Env, to: string, subject: string, html: string) {
  if (!env.RESEND_API_KEY) return false;
  const r = await fetch("https://api.resend.com/emails", {
    method: "POST", headers: { authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({ from: env.MAIL_FROM || "OAK Healthy <onboarding@resend.dev>", to, subject, html }),
  });
  return r.ok;
}

const providers = (env: Env) => ({
  google: !!(env.GOOGLE_CLIENT_ID && env.GOOGLE_CLIENT_SECRET),
  github: !!(env.GITHUB_CLIENT_ID && env.GITHUB_CLIENT_SECRET),
  email: !!env.RESEND_API_KEY,
});

export async function auth(req: Request, env: Env, url: URL, action: string): Promise<Response> {
  const ip = clientIp(req);
  const m = req.method;

  if (action === "providers" && m === "GET") return json(providers(env));

  if (action === "me" && m === "GET") {
    const u = await sessionUser(req, env);
    if (!u) return json({ user: null });
    const ids = await env.DB.prepare("SELECT provider FROM identities WHERE user_id = ?").bind(u.id).all<{ provider: string }>();
    return json({ user: { email: u.email, hasPassword: !!u.auth_hash, wrapped: u.wrapped, providers: ids.results.map((r) => r.provider), createdAt: u.created_at } });
  }

  if (action === "salt" && m === "POST") {
    const b = await body<{ email?: string }>(req);
    if (!emailOk(b?.email)) return json({ error: "Enter a valid email" }, 400);
    if (!(await limit(env, `salt:${ip}`, 30, 15 * 60e3))) return tooMany();
    return json({ salt: await saltFor(env, normEmail(b.email)) });
  }

  if (action === "signup" && m === "POST") {
    const b = await body<{ email?: string; salt?: string; authKey?: string; wrapped?: string }>(req);
    if (!emailOk(b?.email) || !isB64(b.salt, 16, 64) || !isB64(b.authKey, 40, 64) || !isB64(b.wrapped, 20, 2000)) return json({ error: "Invalid sign-up" }, 400);
    if (!(await limit(env, `signup:${ip}`, 10, 60 * 60e3))) return tooMany();
    const email = normEmail(b.email);
    const id = crypto.randomUUID();
    const r = await env.DB.prepare("INSERT OR IGNORE INTO users (id, email, salt, auth_hash, wrapped, created_at) VALUES (?, ?, ?, ?, ?, ?)")
      .bind(id, email, b.salt, await authHash(b.salt, b.authKey), b.wrapped, Date.now()).run();
    if (!r.meta.changes) return json({ error: "An account with this email already exists. Sign in or reset your password." }, 409);
    return json({ ok: true }, 201, { "set-cookie": await newSession(env, url, id) });
  }

  if (action === "login" && m === "POST") {
    const b = await body<{ email?: string; authKey?: string }>(req);
    if (!emailOk(b?.email) || !isB64(b.authKey, 40, 64)) return json({ error: "Wrong email or password" }, 400);
    const email = normEmail(b.email);
    if (!(await limit(env, `login:${ip}:${email}`, 10, 15 * 60e3)) || !(await limit(env, `login:${ip}`, 50, 15 * 60e3))) return tooMany();
    const u = await env.DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first<User>();
    if (!u || !u.salt || !u.auth_hash || (await authHash(u.salt, b.authKey)) !== u.auth_hash) return json({ error: "Wrong email or password" }, 401);
    return json({ wrapped: u.wrapped }, 200, { "set-cookie": await newSession(env, url, u.id) });
  }

  if (action === "logout" && m === "POST") {
    const sid = cookie(req, COOKIE);
    if (sid) await env.DB.prepare("DELETE FROM sessions WHERE id_hash = ?").bind(await sha256(sid)).run();
    return json({ ok: true }, 200, { "set-cookie": setCookie(url, "", 0) });
  }

  if (action === "me" && m === "DELETE") {
    const u = await sessionUser(req, env);
    if (!u) return json({ error: "Sign in first" }, 401);
    await env.DB.batch(["sessions", "identities", "reset_tokens", "wrapped_keys"].map((t) => env.DB.prepare(`DELETE FROM ${t} WHERE user_id = ?`).bind(u.id))
      .concat(env.DB.prepare("DELETE FROM users WHERE id = ?").bind(u.id)));
    return json({ ok: true }, 200, { "set-cookie": setCookie(url, "", 0) });
  }

  if (action === "forgot" && m === "POST") {
    const b = await body<{ email?: string; lang?: string }>(req);
    if (!emailOk(b?.email)) return json({ error: "Enter a valid email" }, 400);
    const email = normEmail(b.email);
    const generic = { ok: true, message: "If that email has an account, a reset link is on its way." };
    if (!(await limit(env, `forgot:${email}`, 3, 60 * 60e3)) || !(await limit(env, `forgot:${ip}`, 10, 60 * 60e3))) return json(generic);
    const u = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first<{ id: string }>();
    if (!u) return json(generic);
    const token = randomToken();
    await env.DB.prepare("INSERT INTO reset_tokens (token_hash, user_id, expires_at, used) VALUES (?, ?, ?, 0)").bind(await sha256(token), u.id, Date.now() + RESET_MS).run();
    const link = `${appOrigin(env, url)}/reset/#t=${token}`;
    const vi = b.lang === "vi";
    await sendMail(env, email, vi ? "Đặt lại mật khẩu OAK Healthy" : "Reset your OAK Healthy password",
      `<p>${vi ? "Bấm liên kết dưới đây trong 30 phút để đặt mật khẩu mới:" : "Use this link within 30 minutes to choose a new password:"}</p><p><a href="${link}">${link}</a></p><p>${vi ? "Nếu bạn không yêu cầu, hãy bỏ qua email này." : "If you didn't ask for this, ignore this email."}</p>`);
    return json(env.DEV_MAIL === "1" ? { ...generic, devLink: link } : generic);
  }

  if (action === "reset" && m === "POST") {
    const b = await body<{ token?: string; salt?: string; authKey?: string; wrapped?: string; rotated?: boolean }>(req);
    if (!isB64(b?.token, 40, 64) || !isB64(b.salt, 16, 64) || !isB64(b.authKey, 40, 64) || !isB64(b.wrapped, 20, 2000)) return json({ error: "Invalid reset" }, 400);
    if (!(await limit(env, `reset:${ip}`, 20, 60 * 60e3))) return tooMany();
    const th = await sha256(b.token);
    const claim = await env.DB.prepare("UPDATE reset_tokens SET used = 1 WHERE token_hash = ? AND used = 0 AND expires_at > ? RETURNING user_id")
      .bind(th, Date.now()).first<{ user_id: string }>();
    if (!claim) return json({ error: "This reset link is invalid or has expired." }, 400);
    const uid = claim.user_id;
    const stmts = [
      env.DB.prepare("UPDATE users SET salt = ?, auth_hash = ?, wrapped = ? WHERE id = ?").bind(b.salt, await authHash(b.salt, b.authKey), b.wrapped, uid),
      env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(uid),
      env.DB.prepare("DELETE FROM reset_tokens WHERE user_id = ?").bind(uid),
    ];
    if (b.rotated) stmts.push(env.DB.prepare("DELETE FROM wrapped_keys WHERE user_id = ?").bind(uid));
    await env.DB.batch(stmts);
    return json({ ok: true }, 200, { "set-cookie": await newSession(env, url, uid) });
  }

  // Password users can re-wrap after rotating the sync key on a signed-in device.
  if (action === "wrap" && m === "POST") {
    const u = await sessionUser(req, env);
    if (!u) return json({ error: "Sign in first" }, 401);
    const b = await body<{ salt?: string; authKey?: string; wrapped?: string }>(req);
    if (!isB64(b?.salt, 16, 64) || !isB64(b.authKey, 40, 64) || !isB64(b.wrapped, 20, 2000)) return json({ error: "bad body" }, 400);
    if (!u.salt || b.salt !== u.salt || (await authHash(u.salt, b.authKey)) !== u.auth_hash) return json({ error: "Wrong password" }, 403);
    await env.DB.prepare("UPDATE users SET wrapped = ? WHERE id = ?").bind(b.wrapped, u.id).run();
    return json({ ok: true });
  }

  return json({ error: "not found" }, 404);
}

// ---- OAuth 2.0 authorization code + PKCE (Google, GitHub). Exact redirect_uri, verified email only, no silent merge.
type P = "google" | "github";
const OA = {
  google: { auth: "https://accounts.google.com/o/oauth2/v2/auth", token: "https://oauth2.googleapis.com/token", scope: "openid email" },
  github: { auth: "https://github.com/login/oauth/authorize", token: "https://github.com/login/oauth/access_token", scope: "read:user user:email" },
};
const creds = (env: Env, p: P) => (p === "google" ? [env.GOOGLE_CLIENT_ID, env.GOOGLE_CLIENT_SECRET] : [env.GITHUB_CLIENT_ID, env.GITHUB_CLIENT_SECRET]);
const STATE_COOKIE = "oak_oauth";
// The state is bound to this browser by an HttpOnly cookie (login-CSRF / forced account linking defence).
const stateCookie = (url: URL, value: string, maxAge: number) =>
  `${STATE_COOKIE}=${value}; Path=/api/auth/oauth/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${url.protocol === "https:" ? "; Secure" : ""}`;
const back = (env: Env, url: URL, q: string) => new Response(null, { status: 302, headers: { location: `${appOrigin(env, url)}/login/?${q}`, "set-cookie": stateCookie(url, "", 0) } });

export async function oauth(req: Request, env: Env, url: URL, p: string, step: string): Promise<Response> {
  if (p !== "google" && p !== "github") return json({ error: "unknown provider" }, 404);
  const [id, sec] = creds(env, p);
  if (!id || !sec) return back(env, url, "error=provider_off");
  const redirect = `${appOrigin(env, url)}/api/auth/oauth/${p}/callback`;

  if (step === "start") {
    if (!(await limit(env, `oauth:${clientIp(req)}`, 30, 15 * 60e3))) return back(env, url, "error=rate");
    const state = randomToken(24), verifier = randomToken(48);
    const challenge = b64u(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
    await env.DB.prepare("INSERT INTO oauth_states (state, provider, verifier, expires_at) VALUES (?, ?, ?, ?)").bind(state, p, verifier, Date.now() + OAUTH_MS).run();
    const q = new URLSearchParams({ client_id: id, redirect_uri: redirect, response_type: "code", scope: OA[p].scope, state, code_challenge: challenge, code_challenge_method: "S256" });
    if (p === "google") q.set("prompt", "select_account");
    return new Response(null, { status: 302, headers: { location: `${OA[p].auth}?${q}`, "set-cookie": stateCookie(url, state, OAUTH_MS / 1000) } });
  }

  if (step === "callback") {
    const code = url.searchParams.get("code"), state = url.searchParams.get("state");
    if (!code || !state) return back(env, url, "error=oauth_denied");
    const bound = cookie(req, STATE_COOKIE);
    if (!bound || bound.length !== state.length || (await sha256(bound)) !== (await sha256(state))) return back(env, url, "error=oauth_state");
    const st = await env.DB.prepare("DELETE FROM oauth_states WHERE state = ? AND provider = ? AND expires_at > ? RETURNING verifier").bind(state, p, Date.now()).first<{ verifier: string }>();
    if (!st) return back(env, url, "error=oauth_state");
    const tok = await fetch(OA[p].token, {
      method: "POST", headers: { accept: "application/json", "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ client_id: id, client_secret: sec, code, redirect_uri: redirect, grant_type: "authorization_code", code_verifier: st.verifier }),
    }).then((r) => r.json() as Promise<{ access_token?: string }>).catch(() => ({} as { access_token?: string }));
    if (!tok.access_token) return back(env, url, "error=oauth_token");
    const h = { authorization: `Bearer ${tok.access_token}`, "user-agent": "oak-healthy", accept: "application/json" };
    let subject = "", email = "";
    if (p === "google") {
      const u = await fetch("https://openidconnect.googleapis.com/v1/userinfo", { headers: h }).then((r) => r.json() as Promise<{ sub?: string; email?: string; email_verified?: boolean }>);
      if (!u.sub || !u.email || !u.email_verified) return back(env, url, "error=email_unverified");
      subject = u.sub; email = u.email;
    } else {
      const u = await fetch("https://api.github.com/user", { headers: h }).then((r) => r.json() as Promise<{ id?: number }>);
      const es = await fetch("https://api.github.com/user/emails", { headers: h }).then((r) => r.json() as Promise<{ email: string; primary: boolean; verified: boolean }[]>).catch(() => []);
      const e = Array.isArray(es) ? es.find((x) => x.primary && x.verified) : undefined;
      if (!u.id || !e) return back(env, url, "error=email_unverified");
      subject = String(u.id); email = e.email;
    }
    email = normEmail(email);
    let userId = (await env.DB.prepare("SELECT user_id FROM identities WHERE provider = ? AND subject = ?").bind(p, subject).first<{ user_id: string }>())?.user_id;
    if (!userId) {
      const signedIn = await sessionUser(req, env);
      const existing = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first<{ id: string }>();
      if (signedIn && (!existing || existing.id === signedIn.id)) userId = signedIn.id; // explicit link from a signed-in session
      else if (existing) return back(env, url, `error=account_exists&provider=${p}`); // never auto-merge by email
      else {
        userId = crypto.randomUUID();
        await env.DB.prepare("INSERT INTO users (id, email, salt, auth_hash, wrapped, created_at) VALUES (?, ?, NULL, NULL, NULL, ?)").bind(userId, email, Date.now()).run();
      }
      await env.DB.prepare("INSERT OR IGNORE INTO identities (provider, subject, user_id, created_at) VALUES (?, ?, ?, ?)").bind(p, subject, userId, Date.now()).run();
    }
    const h2 = new Headers({ location: `${appOrigin(env, url)}/account/?signedin=${p}` });
    h2.append("set-cookie", await newSession(env, url, userId));
    h2.append("set-cookie", stateCookie(url, "", 0));
    return new Response(null, { status: 302, headers: h2 });
  }
  return json({ error: "not found" }, 404);
}

// ---- Passkey (WebAuthn PRF) wraps of the sync key. The PRF output never reaches the server; it only stores ciphertext.
export async function keys(req: Request, env: Env, id?: string): Promise<Response> {
  const u = await sessionUser(req, env);
  if (!u) return json({ error: "Sign in first" }, 401);
  if (req.method === "GET" && !id) {
    const r = await env.DB.prepare("SELECT id, cred_id AS credId, prf_salt AS salt, blob, label, created_at AS createdAt FROM wrapped_keys WHERE user_id = ? ORDER BY created_at").bind(u.id).all();
    return json({ keys: r.results });
  }
  if (req.method === "POST" && !id) {
    const b = await body<{ credId?: string; salt?: string; blob?: string; label?: string }>(req);
    if (!isB64(b?.credId, 16, 1400) || !isB64(b.salt, 16, 64) || !isB64(b.blob, 20, 2000)) return json({ error: "bad body" }, 400);
    const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM wrapped_keys WHERE user_id = ?").bind(u.id).first<{ n: number }>();
    if ((n?.n ?? 0) >= 10) return json({ error: "Up to 10 passkeys" }, 400);
    const kid = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO wrapped_keys (id, user_id, cred_id, prf_salt, blob, label, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
      .bind(kid, u.id, b.credId, b.salt, b.blob, String(b.label ?? "Passkey").slice(0, 60), Date.now()).run();
    return json({ id: kid }, 201);
  }
  if (req.method === "DELETE" && id) {
    await env.DB.prepare("DELETE FROM wrapped_keys WHERE id = ? AND user_id = ?").bind(id, u.id).run();
    return json({ ok: true });
  }
  return json({ error: "method" }, 405);
}
