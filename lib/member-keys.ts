// Circle member crypto, pure WebCrypto (browser and node:test). Key hierarchy: docs/feat/circle-accounts.md § Keys.
import { b64u, normalizeKey, open, seal, unb64u } from "./crypto.ts";

const te = new TextEncoder();
const EC = { name: "ECDH", namedCurve: "P-256" } as const;
const AES = { name: "AES-GCM", length: 256 } as const;
const hkdf = (salt: string, info: string): HkdfParams => ({ name: "HKDF", hash: "SHA-256", salt: te.encode(salt), info: te.encode(info) });
const random = (n: number) => crypto.getRandomValues(new Uint8Array(n));

export interface MemberKeys { pub: string; sealed: string; priv: CryptoKey }

async function memberSealKey(syncKey: string) {
  const base = await crypto.subtle.importKey("raw", te.encode(normalizeKey(syncKey)), "HKDF", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(hkdf("oak-healthy-v1", "member-key"), base, AES, false, ["encrypt", "decrypt"]);
}
const importPriv = (jwk: JsonWebKey) => crypto.subtle.importKey("jwk", jwk, EC, false, ["deriveBits"]);
const importPub = (pub: string) => crypto.subtle.importKey("raw", unb64u(pub), EC, false, []);

/** pub: raw uncompressed P-256 point (one canonical encoding, so the join proof MACs exact bytes). */
export async function newMemberKeys(syncKey: string): Promise<MemberKeys> {
  const pair = await crypto.subtle.generateKey(EC, true, ["deriveBits"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  return {
    pub: b64u(await crypto.subtle.exportKey("raw", pair.publicKey)),
    sealed: await seal(await memberSealKey(syncKey), jwk),
    priv: await importPriv(jwk),
  };
}
export const openMemberKey = async (syncKey: string, sealed: string) => importPriv(await open<JsonWebKey>(await memberSealKey(syncKey), sealed));

// Invite secret j: `auth` is shown to the server (it stores only its hash); `mac` never leaves the link holders.
async function joinKeys(j: string) {
  const base = await crypto.subtle.importKey("raw", unb64u(j), "HKDF", false, ["deriveBits", "deriveKey"]);
  const auth = b64u(await crypto.subtle.deriveBits(hkdf("oak-circle-v1", "join-auth"), base, 256));
  const mac = await crypto.subtle.deriveKey(hkdf("oak-circle-v1", "join-mac"), base, { name: "HMAC", hash: "SHA-256", length: 256 }, false, ["sign", "verify"]);
  return { auth, mac };
}
export const newJoinSecret = () => b64u(random(24));
export const joinAuth = async (j: string) => (await joinKeys(j)).auth;
const proofMessage = (circleId: string, pub: string) => te.encode(`${circleId}|${pub}`);

export async function joinProof(j: string, circleId: string, pub: string) {
  return b64u(await crypto.subtle.sign("HMAC", (await joinKeys(j)).mac, proofMessage(circleId, pub)));
}
export async function verifyProof(j: string, circleId: string, pub: string, proof: string) {
  try { return await crypto.subtle.verify("HMAC", (await joinKeys(j)).mac, unb64u(proof), proofMessage(circleId, pub)); } catch { return false; }
}

export interface MemberClaim { userId: string; pub: string; proof: string }
/** The members the owner wraps to: still-listed pinned keys, plus joins whose proof verifies under the current j.
 * A server cannot add or swap a key without j (docs/feat/circle-accounts.md § Threat model). */
export async function trustedMembers(circleId: string, j: string, pinned: Record<string, string>, claims: MemberClaim[]) {
  const out: Record<string, string> = {};
  for (const m of claims) if (pinned[m.userId] === m.pub || (await verifyProof(j, circleId, m.pub, m.proof))) out[m.userId] = m.pub;
  return out;
}

/** Pins to keep across a member removal: every other member still provable under the old j, pinned before j rotates
 * (a pending joiner's proof would not verify under the new j). */
export const pinsAfterRemoval = (circleId: string, oldJ: string, pinned: Record<string, string>, claims: MemberClaim[], removedId: string) =>
  trustedMembers(circleId, oldJ, pinned, claims.filter((m) => m.userId !== removedId));

export const newContentKey = () => random(32);
export const contentKey = (raw: Uint8Array<ArrayBuffer>, use: KeyUsage) => crypto.subtle.importKey("raw", raw, "AES-GCM", false, [use]);

async function wrappingKey(priv: CryptoKey, pub: CryptoKey, circleId: string, epk: string) {
  const bits = await crypto.subtle.deriveBits({ name: "ECDH", public: pub }, priv, 256);
  const base = await crypto.subtle.importKey("raw", bits, "HKDF", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(hkdf("oak-circle-v1", `wrap:${circleId}:${epk}`), base, AES, false, ["encrypt", "decrypt"]);
}

/** ECIES: a one-off ephemeral key per wrap, so the owner needs no keypair of its own. Format: epk.iv.ct */
export async function wrapFor(raw: Uint8Array, memberPub: string, circleId: string) {
  const eph = await crypto.subtle.generateKey(EC, true, ["deriveBits"]);
  const epk = b64u(await crypto.subtle.exportKey("raw", eph.publicKey));
  return `${epk}.${await seal(await wrappingKey(eph.privateKey, await importPub(memberPub), circleId, epk), b64u(raw))}`;
}
export async function unwrapFor(priv: CryptoKey, wrap: string, circleId: string) {
  const [epk, iv, ct] = wrap.split(".");
  const raw = await open<string>(await wrappingKey(priv, await importPub(epk), circleId, epk), `${iv}.${ct}`);
  return contentKey(unb64u(raw), "decrypt");
}
