"use client";
// Passkey vault unlock via the WebAuthn PRF extension: the authenticator derives a 32-byte secret for our salt,
// which (through HKDF) becomes an AES key wrapping the sync key. The server stores only the wrapped blob.
import { b64u, open, seal, unb64u } from "./crypto";
import { api } from "./account";
import { getSyncKey } from "./sync";

const te = new TextEncoder();
type PrfResults = { prf?: { enabled?: boolean; results?: { first?: ArrayBuffer } } };
export interface PasskeyWrap { id: string; credId: string; salt: string; blob: string; label: string; createdAt: number }

export const passkeySupported = () => typeof window !== "undefined" && !!window.PublicKeyCredential && !!navigator.credentials;

async function prfKey(secret: ArrayBuffer) {
  const base = await crypto.subtle.importKey("raw", secret, "HKDF", false, ["deriveKey"]);
  return crypto.subtle.deriveKey({ name: "HKDF", hash: "SHA-256", salt: te.encode("oak-healthy-prf-v1"), info: te.encode("vault-wrap") }, base, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}

async function evaluate(credId: Uint8Array, salt: Uint8Array) {
  const a = (await navigator.credentials.get({
    publicKey: {
      challenge: crypto.getRandomValues(new Uint8Array(32)), userVerification: "required", timeout: 60_000,
      allowCredentials: [{ type: "public-key", id: credId as BufferSource }],
      extensions: { prf: { eval: { first: salt as BufferSource } } } as AuthenticationExtensionsClientInputs,
    },
  })) as PublicKeyCredential | null;
  const first = (a?.getClientExtensionResults() as PrfResults | undefined)?.prf?.results?.first;
  if (!first) throw new Error("This passkey doesn't support vault unlock (PRF). Try a newer device or browser.");
  return first;
}

export const listPasskeys = () => api<{ keys: PasskeyWrap[] }>("/api/keys").then((r) => r.keys);
export const removePasskey = (id: string) => api(`/api/keys/${id}`, { method: "DELETE" });

/** Create a passkey for this account and store a PRF-wrapped copy of the current sync key. */
export async function addPasskey(email: string, label: string) {
  const key = await getSyncKey();
  if (!key) throw new Error("Turn on sync first");
  const salt = crypto.getRandomValues(new Uint8Array(32));
  const cred = (await navigator.credentials.create({
    publicKey: {
      rp: { name: "OAK Healthy", id: location.hostname },
      user: { id: crypto.getRandomValues(new Uint8Array(16)), name: email, displayName: email },
      challenge: crypto.getRandomValues(new Uint8Array(32)),
      pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -257 }],
      authenticatorSelection: { residentKey: "preferred", userVerification: "required" },
      timeout: 60_000,
      extensions: { prf: { eval: { first: salt } } } as AuthenticationExtensionsClientInputs,
    },
  })) as PublicKeyCredential | null;
  if (!cred) throw new Error("Passkey was not created");
  const ext = cred.getClientExtensionResults() as PrfResults;
  if (ext.prf?.enabled === false) throw new Error("This passkey doesn't support vault unlock (PRF). Try a newer device or browser.");
  const rawId = new Uint8Array(cred.rawId);
  // Some authenticators only return PRF output on get(), so evaluate once more when create() didn't.
  const secret = ext.prf?.results?.first ?? (await evaluate(rawId, salt));
  const blob = await seal(await prfKey(secret), { k: key });
  await api("/api/keys", { method: "POST", json: { credId: b64u(rawId), salt: b64u(salt), blob, label } });
}

/** Unlock the vault on a signed-in device with any registered passkey. Returns the sync key. */
export async function unlockWithPasskey(w: PasskeyWrap) {
  const secret = await evaluate(unb64u(w.credId), unb64u(w.salt));
  return (await open<{ k: string }>(await prfKey(secret), w.blob)).k;
}
