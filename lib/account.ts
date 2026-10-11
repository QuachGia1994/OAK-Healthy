"use client";
// Client side of OAK accounts. The password never leaves the device: PBKDF2 stretches it into authKey (proves who
// you are) and encKey (wraps your sync key). Your OAK sync key doubles as the recovery code.
import { b64u, deriveVault, newSyncKey, open, seal, unb64u, isValidKey } from "./crypto";
import { adoptSyncedProfiles, db, getMeta, setMeta } from "./db";
import { deleteEverything, type VaultAuth, type WipeProgress, type WipeStep } from "./domain/wipe";
import { getSyncKey, setSyncKey, syncNow } from "./sync";
import { getLang } from "./i18n";

export const PBKDF2_ITERATIONS = 600_000;
const te = new TextEncoder();

export interface Me { email: string; hasPassword: boolean; wrapped: string | null; providers: string[]; createdAt: number }
export interface Providers { google: boolean; github: boolean; email: boolean }

export async function stretch(password: string, saltB64: string) {
  const base = await crypto.subtle.importKey("raw", te.encode(password.normalize("NFKC")), "PBKDF2", false, ["deriveBits"]);
  const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: unb64u(saltB64), iterations: PBKDF2_ITERATIONS }, base, 512));
  const encKey = await crypto.subtle.importKey("raw", bits.slice(32), "AES-GCM", false, ["encrypt", "decrypt"]);
  return { authKey: b64u(bits.slice(0, 32)), encKey };
}
export const newSalt = () => b64u(crypto.getRandomValues(new Uint8Array(16)));

async function api<T>(path: string, init?: RequestInit & { json?: unknown }): Promise<T> {
  const res = await fetch(path, {
    ...init, credentials: "same-origin",
    headers: { "content-type": "application/json", ...(init?.headers ?? {}) },
    body: init?.json !== undefined ? JSON.stringify(init.json) : init?.body,
  });
  const data = (await res.json().catch(() => ({}))) as T & { error?: string };
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

export const getProviders = () => api<Providers>("/api/auth/providers").catch(() => ({ google: false, github: false, email: false }));
export const getMe = () => api<{ user: Me | null }>("/api/auth/me").then((r) => r.user).catch(() => null);
export const logout = () => api("/api/auth/logout", { method: "POST" });

/** Use this device's sync key if sync is on; otherwise create one so the new account has a vault from day one. */
async function ensureSyncKey() {
  const k = await getSyncKey();
  if (k) return k;
  const n = newSyncKey();
  await setSyncKey(n);
  return n;
}

export async function signup(email: string, password: string) {
  const salt = newSalt();
  const { authKey, encKey } = await stretch(password, salt);
  const key = await ensureSyncKey();
  await api("/api/auth/signup", { method: "POST", json: { email, salt, authKey, wrapped: await seal(encKey, { k: key }) } });
  await syncNow(key);
}

export async function login(email: string, password: string) {
  const { salt } = await api<{ salt: string }>("/api/auth/salt", { method: "POST", json: { email } });
  const { authKey, encKey } = await stretch(password, salt);
  const { wrapped } = await api<{ wrapped: string | null }>("/api/auth/login", { method: "POST", json: { email, authKey } });
  if (wrapped) await adoptKey((await open<{ k: string }>(encKey, wrapped)).k);
}

/** Switch this device to the account's vault: link, pull, then drop the empty first-run profile. */
export async function adoptKey(key: string) {
  const cur = await getSyncKey();
  if (cur === key) { await syncNow(key); return; }
  await setSyncKey(key);
  const r = await syncNow(key);
  if (r.ok) { await adoptSyncedProfiles(); await syncNow(key); }
}

export const forgot = (email: string) => api<{ ok: true; message: string; devLink?: string }>("/api/auth/forgot", { method: "POST", json: { email, lang: getLang() } });

/**
 * Finish a reset. Your vault survives only if this device still has the sync key or you paste it (the recovery code).
 * Otherwise a fresh key is created: local data on this device is kept and becomes the new vault.
 */
export async function resetPassword(token: string, password: string, recovery?: string) {
  const local = await getSyncKey();
  const typed = recovery && isValidKey(recovery) ? recovery.trim().toUpperCase() : null;
  const key = typed ?? local ?? newSyncKey();
  const rotated = !typed && !local;
  const salt = newSalt();
  const { authKey, encKey } = await stretch(password, salt);
  await api("/api/auth/reset", { method: "POST", json: { token, salt, authKey, wrapped: await seal(encKey, { k: key }), rotated } });
  await adoptKey(key);
  return { rotated };
}

/** Wrap the current sync key with the account password (e.g. after turning sync on later). */
export async function rewrap(email: string, password: string) {
  const key = await getSyncKey();
  if (!key) throw new Error("Turn on sync first");
  const { salt } = await api<{ salt: string }>("/api/auth/salt", { method: "POST", json: { email } });
  const { authKey, encKey } = await stretch(password, salt);
  await api("/api/auth/wrap", { method: "POST", json: { salt, authKey, wrapped: await seal(encKey, { k: key }) } });
}

/**
 * Change the password, or add one to a Google/GitHub-only account. The same sync key is re-wrapped, so the vault and
 * passkeys keep working. With a password, the key comes from the account's wrap (proves the old password too);
 * otherwise from this device.
 */
export async function changePassword(me: Me, current: string | null, next: string) {
  let oldAuthKey: string | undefined, key: string | null = null;
  if (me.hasPassword) {
    if (!current) throw new Error("Enter your current password");
    const { salt } = await api<{ salt: string }>("/api/auth/salt", { method: "POST", json: { email: me.email } });
    const old = await stretch(current, salt);
    oldAuthKey = old.authKey;
    if (me.wrapped) key = (await open<{ k: string }>(old.encKey, me.wrapped).catch(() => { throw new Error("Current password is wrong"); })).k;
  }
  key ??= await getSyncKey();
  if (!key) throw new Error("Unlock your vault on this device first");
  const salt = newSalt();
  const { authKey, encKey } = await stretch(next, salt);
  await api("/api/auth/password", { method: "POST", json: { oldAuthKey, salt, authKey, wrapped: await seal(encKey, { k: key }) } });
}

export const unlinkProvider = (provider: string) => api("/api/auth/identity", { method: "DELETE", json: { provider } });

export const passwordProblem = (pw: string, email = "") =>
  pw.length < 10 ? "Use at least 10 characters" : email && pw.toLowerCase().includes(email.split("@")[0].toLowerCase()) ? "Don't include your email in the password" : null;

/** A delete-everything run that has not finished on this device (it resumes from here). */
export const pendingWipe = () => getMeta<WipeProgress | null>("wipe", null);

/** Delete everything (lib/domain/wipe.ts, docs/feat/delete-everything.md): the circles this user owns, the vault,
 * the account when signed in, then all data on this device. */
export async function wipeEverything(signedIn: boolean, onStep?: (s: WipeStep) => void) {
  // Loaded here: lib/circles.ts imports this module.
  const [{ getCircles, revokeCircle }, { disableReminders }] = await Promise.all([import("./circles"), import("./reminders")]);
  const vaultAuth = async (): Promise<VaultAuth | null> => {
    const k = await getSyncKey();
    return k ? deriveVault(k).then((v) => ({ id: v.vaultId, auth: v.auth })) : null;
  };
  const ok = async (res: Response) => {
    if (res.ok) return;
    const { error } = (await res.json().catch(() => ({}))) as { error?: string };
    throw new Error(error || `Request failed (${res.status})`);
  };
  return deleteEverything({
    progress: async () => {
      const s = await pendingWipe();
      const account = !!s?.account || signedIn;
      return s ? { ...s, vault: s.vault ?? (await vaultAuth()), account } : { vault: await vaultAuth(), account, done: [] };
    },
    save: async (p) => { await setMeta("wipe", p); },
    // Not getMe(): it turns every failure into "signed out". Only a 200 with no user is.
    signedIn: async () => !!(await api<{ user: Me | null }>("/api/auth/me")).user,
    pull: async () => {
      const k = await getSyncKey();
      const r = k ? await syncNow(k) : null;
      // gone: another device already deleted the vault (sync is now off here); its circles are still deleted below.
      if (r && !r.ok && !("gone" in r && r.gone)) throw new Error(r.error);
    },
    ownedCircles: async () => (await getCircles()).filter((c) => c.w).map((c) => c.id),
    deleteCircle: revokeCircle,
    disableReminders: async () => { if ("serviceWorker" in navigator) await disableReminders(); }, // no worker, no push
    syncOff: async () => { await setSyncKey(null); },
    deleteVault: async (v) => ok(await fetch(`/api/vault/${v.id}`, { method: "DELETE", headers: { authorization: `Bearer ${v.auth}` } })),
    deleteAccount: async () => {
      const res = await fetch("/api/auth/me", { method: "DELETE", credentials: "same-origin" });
      if (res.status === 401) return "signed-out";
      await ok(res);
      return "ok";
    },
    clearDevice: () => db.delete(),
  }, onStep);
}

export { api };
