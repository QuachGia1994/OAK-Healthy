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

// Signed snapshots (§ Signatures). ECIES gives members no sender authentication: anyone holding a member's public
// key (the server) could wrap its own content key. The owner signs every member snapshot with a per-circle ECDSA key
// whose public half the member pins at join (invite #o=) or, for members who joined before 2.5, on first sight.
const ECDSA = { name: "ECDSA", namedCurve: "P-256" } as const;
const SIG = { name: "ECDSA", hash: "SHA-256" } as const;
/** pending: made on this device for a circle that may exist elsewhere (pre-2.5), not yet in a pushed vault. */
export interface Signer { pub: string; priv: JsonWebKey; createdAt: number; pending?: true }
export const UNSIGNED = "This circle update isn't signed by its owner, so it isn't shown.";
export const TAMPERED = "This circle update failed its signature check, so it isn't shown. Ask the owner for a new invite.";
export class SnapshotRefused extends Error { constructor(message: string) { super(message); this.name = "SnapshotRefused"; } }
const sigMessage = (circleId: string, pub: string, sealed: string) => te.encode(`oak-circle-sig-v1|${circleId}|${pub}|${sealed}`);

export async function newSigner(): Promise<Signer> {
  const pair = await crypto.subtle.generateKey(ECDSA, true, ["sign", "verify"]);
  return { pub: b64u(await crypto.subtle.exportKey("raw", pair.publicKey)), priv: await crypto.subtle.exportKey("jwk", pair.privateKey), createdAt: Date.now() };
}
/** Member blob format: iv.ct.signerPub.sig (bearer blobs stay iv.ct). The signature binds circle, signer and ciphertext. */
export async function signSnapshot(signer: Signer, circleId: string, sealed: string) {
  const key = await crypto.subtle.importKey("jwk", signer.priv, ECDSA, false, ["sign"]);
  return `${sealed}.${signer.pub}.${b64u(await crypto.subtle.sign(SIG, key, sigMessage(circleId, signer.pub, sealed)))}`;
}
/** Returns the sealed payload and its signer, or throws SnapshotRefused: unsigned, signer ≠ pinned, or bad signature. */
export async function checkSnapshot(circleId: string, blob: string, pinned?: string) {
  const parts = blob.split(".");
  if (parts.length !== 4) throw new SnapshotRefused(UNSIGNED);
  const [iv, ct, pub, sig] = parts, sealed = `${iv}.${ct}`;
  if (pinned && pub !== pinned) throw new SnapshotRefused(TAMPERED);
  const ok = await crypto.subtle.importKey("raw", unb64u(pub), ECDSA, false, ["verify"])
    .then((k) => crypto.subtle.verify(SIG, k, unb64u(sig), sigMessage(circleId, pub, sealed))).catch(() => false);
  if (!ok) throw new SnapshotRefused(TAMPERED);
  return { sealed, signer: pub };
}

// Owner state merge (§ Owner devices). Circles travel in the encrypted vault snapshot; these rules keep two owner
// devices and in-flight publishes from undoing each other.
/** The synced fields of an owner circle. epoch counts invite rotations (the server's count since 2.6); j and pins
 * belong to one epoch. seq: the last publish number, sealed into each snapshot for the members' replay guard. */
export interface OwnerCircle {
  id: string; j?: string; epoch?: number; seq?: number; members?: Record<string, string>; sign?: Signer;
  createdAt: number; updatedAt?: number; deletedAt?: number | null;
}
const stamp = (c: OwnerCircle) => c.updatedAt ?? c.createdAt;
const epochOf = (c: OwnerCircle) => c.epoch ?? 0;

/** a = this device, b = the pulled vault. Tombstones win; j moves only to a higher epoch; pins of one epoch are
 * unioned; other fields: newer edit wins. Signer: the vault's wins. Vault pushes are linear (baseRev), so the first
 * signer pushed is the one every device adopts, and nothing signs before its signer is pushed (canSign). */
export function mergeCircle<T extends OwnerCircle>(a: T, b: T): T {
  if (a.deletedAt || b.deletedAt) return a.deletedAt ? a : b;
  const [hi, lo] = stamp(b) > stamp(a) ? [b, a] : [a, b];
  const ea = epochOf(a), eb = epochOf(b), key = ea === eb ? hi : ea > eb ? a : b;
  const members = ea === eb ? { ...lo.members, ...hi.members } : key.members;
  const seq = Math.max(a.seq ?? 0, b.seq ?? 0) || undefined;
  return { ...hi, j: key.j, epoch: key.epoch, seq, members, sign: b.sign ?? a.sign, updatedAt: Math.max(stamp(a), stamp(b)) };
}
/** Only a signer that reached the vault may sign: a second owner device can no longer mint a competing one and
 * get members to pin it (it adopts the pushed signer on its next pull instead). */
export const canSign = (c: OwnerCircle): c is OwnerCircle & { sign: Signer } => !!c.sign && !c.sign.pending;
/** After a successful vault push: signers that were in the pushed state are committed. Same list when unchanged. */
export function commitSigners<T extends OwnerCircle>(list: T[], pushed: OwnerCircle[]): T[] {
  const sent = new Set(pushed.map((c) => c.sign?.pub));
  if (!list.some((c) => c.sign?.pending && sent.has(c.sign.pub))) return list;
  return list.map((c) => (c.sign?.pending && sent.has(c.sign.pub) ? committed(c) : c));
}
const committed = <T extends OwnerCircle>(c: T): T => (c.sign?.pending ? { ...c, sign: { pub: c.sign.pub, priv: c.sign.priv, createdAt: c.sign.createdAt } } : c);
export function mergeCircles<T extends OwnerCircle>(local: T[], remote: T[]): T[] {
  const byId = new Map(local.map((c) => [c.id, c]));
  for (const r of remote) { const l = byId.get(r.id); byId.set(r.id, l ? mergeCircle(l, r) : r); }
  return [...byId.values()];
}
/** The circle fields of the encrypted vault blob, stored under the same meta keys. lib/sync.ts pulls and pushes them
 * with the two functions below; public/sw.js vaultCircles mirrors the push (tests/fixtures/vault-circles.ts drives both).
 * circles: owner state. circlePins: owner keys this account pinned as a member. circleSeen: the member's replay floor. */
export interface VaultCircles<C extends OwnerCircle = OwnerCircle> { circles: C[]; circlePins: Record<string, string>; circleSeen: Record<string, number> }
/** This device's circle meta after a pull. A vault from an older app may lack a field: that field stays as it is here
 * (a 2.6 vault has no circleSeen, so it adds no floor). A pin set on this device wins over a synced one. */
export function pullCircleState<C extends OwnerCircle>(local: VaultCircles<C>, remote: Partial<VaultCircles<C>>): VaultCircles<C> {
  return {
    circles: remote.circles?.length ? mergeCircles(local.circles, remote.circles) : local.circles,
    circlePins: remote.circlePins ? { ...remote.circlePins, ...local.circlePins } : local.circlePins,
    circleSeen: mergeSeen(local.circleSeen, remote.circleSeen),
  };
}
/** What a vault push carries. A signer is committed once it is in the vault, so the pushed copy drops `pending`. */
export const pushCircleState = <C extends OwnerCircle>(local: VaultCircles<C>): VaultCircles<C> => ({ ...local, circles: local.circles.map(committed) });

/** Store pins a publish verified, onto the circle as stored now. A publish that started before an invite rotation
 * carries the old epoch and is dropped, so it can never write the old j back (the 2.4 flow saved {...staleCircle, members}). */
export function applyPins<T extends OwnerCircle>(stored: T, publishedEpoch: number, pins: Record<string, string>, now = Date.now()): T {
  if (stored.deletedAt || epochOf(stored) !== publishedEpoch || JSON.stringify(pins) === JSON.stringify(stored.members ?? {})) return stored;
  return { ...stored, members: pins, updatedAt: now };
}

// Server epoch (§ Epoch). Shared by worker/circles.ts and the client, so both apply one rule.
export const STALE_EPOCH = "This circle changed on another device.";
export const UPDATE_APP = "Update the app to change this circle.";
export const isEpoch = (n: unknown): n is number => Number.isSafeInteger(n) && (n as number) >= 0;
export type EpochGate = { ok: true; base: number | null } | { ok: false; epoch: number | null; error: string };
/** Owner writes (publish, rotation). server: circles.epoch, null until a 2.6 client first writes. sent: body.epoch.
 * - no epoch (2.5 client): allowed only while the server epoch is unset; after that → refused, "update the app";
 * - first 2.6 write: adopts the client's count (base = sent), so circles from before 2.6 keep their numbering;
 * - later: forward only. sent >= server is accepted and moves the server to sent; a lower one is refused with the
 *   current epoch so the client re-bases. A higher count is the newer owner state by construction (the vault merge
 *   moves j only to a higher epoch), e.g. a device that kept a pre-2.6 rotation after a behind device wrote first. */
export function epochGate(server: number | null, sent?: number): EpochGate {
  if (sent === undefined) return server === null ? { ok: true, base: null } : { ok: false, epoch: server, error: UPDATE_APP };
  return server === null || sent >= server ? { ok: true, base: sent } : { ok: false, epoch: server, error: STALE_EPOCH };
}
/** The epoch to publish under. refused: the server's epoch from a 409 after this device synced. Still behind it means
 * the invite of that epoch is on a device that has not synced (or its rotation response was lost): publish under the
 * server's epoch anyway instead of stalling. That cannot undo a removal: a removal is the server deleting the member's
 * row and rotating join_hash, a publish changes neither, and wraps land only on member rows that exist. The cost is
 * that members who joined with the invite this device lacks get no wrap until a device holding it publishes. */
export const publishEpoch = (local: number, refused?: number | null) => (refused != null && refused > local ? refused : local);
/** The out-of-step note: this device last had to publish under a server epoch it has no invite for. Clears by itself
 * once the vault brings that epoch's invite (local catches up). */
export const outOfStep = (local: number, publishedUnder?: number) => publishedUnder !== undefined && publishedUnder > local;

// Replay guard (§ Replay). seq sits inside the sealed payload, which the 2.5 signature already covers, so the signed
// string and blob format stay the same and 2.5 members keep verifying 2.6 snapshots.
export const REPLAYED = "This circle update is older than one already shown, so it isn't shown.";
/** seen: highest seq this member accepted for the circle. Returns the new highest, or throws SnapshotRefused.
 * Once a numbered snapshot was seen, an unnumbered one can only be a replay: the first numbered publish also set the
 * server epoch, which shuts out the 2.5 owner apps that publish without a number. */
export function checkSeq(seen: number | undefined, seq: number | undefined) {
  if (!seen) return seq || undefined;
  if (!seq || seq < seen) throw new SnapshotRefused(REPLAYED);
  return seq;
}
/** Replay floors (meta circleSeen, per circle) from two devices: the highest wins, so a fresh install that pulls the
 * vault starts from the account's floor. Only positive integers count: anything else in a pulled vault adds no floor. */
export function mergeSeen(local: Record<string, number>, remote?: Record<string, number>) {
  const raised = Object.entries(remote ?? {}).filter(([id, n]) => Number.isSafeInteger(n) && n > 0 && n > (local[id] ?? 0));
  return raised.length ? { ...local, ...Object.fromEntries(raised) } : local;
}
