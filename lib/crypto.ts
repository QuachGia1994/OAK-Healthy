// Zero-knowledge vault crypto. The sync key never leaves the device; the server stores ciphertext only.
const enc = new TextEncoder(), dec = new TextDecoder();
export const b64u = (b: ArrayBuffer | Uint8Array) => {
  // Chunked: spreading a large vault (up to 1.5 MB) into one fromCharCode call overflows the stack.
  const u = new Uint8Array(b);
  let s = "";
  for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode(...u.subarray(i, i + 0x8000));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
export const unb64u = (s: string) =>
  Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));

const ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
/** 24 random chars (120 bits) shown as OAK-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX. */
export function newSyncKey(): string {
  const r = crypto.getRandomValues(new Uint8Array(24));
  const s = Array.from(r, (n) => ALPHA[n % 32]).join("");
  return "OAK-" + s.match(/.{4}/g)!.join("-");
}
export const normalizeKey = (k: string) => k.toUpperCase().replace(/[^A-Z0-9]/g, "").replace(/^OAK/, "");
export const isValidKey = (k: string) => normalizeKey(k).length === 24;

async function derive(secret: string) {
  const base = await crypto.subtle.importKey("raw", enc.encode(normalizeKey(secret)), "HKDF", false, ["deriveBits", "deriveKey"]);
  const p = (info: string): HkdfParams => ({ name: "HKDF", hash: "SHA-256", salt: enc.encode("oak-healthy-v1"), info: enc.encode(info) });
  return { base, p };
}

export interface VaultKeys { vaultId: string; auth: string; key: CryptoKey }

export async function deriveVault(secret: string): Promise<VaultKeys> {
  const { base, p } = await derive(secret);
  const id = await crypto.subtle.deriveBits(p("vault-id"), base, 128);
  const auth = await crypto.subtle.deriveBits(p("vault-auth"), base, 256);
  const key = await crypto.subtle.deriveKey(p("vault-enc"), base, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
  return { vaultId: b64u(id), auth: b64u(auth), key };
}

export async function seal(key: CryptoKey, data: unknown): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, enc.encode(JSON.stringify(data)));
  return b64u(iv) + "." + b64u(ct);
}

export async function open<T>(key: CryptoKey, blob: string): Promise<T> {
  const [iv, ct] = blob.split(".");
  const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64u(iv) }, key, unb64u(ct));
  return JSON.parse(dec.decode(pt)) as T;
}

/** One-off key for share links: the key lives in the URL #fragment and is never sent to the server. */
export async function sealShare(data: unknown): Promise<{ blob: string; k: string }> {
  const raw = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt"]);
  return { blob: await seal(key, data), k: b64u(raw) };
}
export async function openShare<T>(blob: string, k: string): Promise<T> {
  const key = await crypto.subtle.importKey("raw", unb64u(k), "AES-GCM", false, ["decrypt"]);
  return open<T>(key, blob);
}