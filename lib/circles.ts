"use client";
// Circles: share one profile live and read-only with family or a coach (docs/feat/circle-accounts.md).
// Member circles: people join with their own OAK account; every publish wraps a fresh content key to each verified
// member, so revoking one member cuts them off while the rest keep reading. Bearer circles (older invites) keep
// their key in the link #fragment. Owner state (write token, invite secret, pinned member keys, signing key) lives in
// meta "circles" and reaches the owner's other devices inside the encrypted vault snapshot (lib/sync.ts).
import { b64u, seal, open, unb64u } from "./crypto";
import { api } from "./account";
import { db, getMeta } from "./db";
import { getSyncKey, updateMeta } from "./sync";
import { addDays, toLocalDay } from "./domain/day";
import type { Intake, Profile, Routine } from "./domain/types";
import {
  applyPins, canSign, checkSnapshot, contentKey, joinAuth, joinProof, newContentKey, newJoinSecret, newMemberKeys, newSigner, openMemberKey, pinsAfterRemoval,
  signSnapshot, trustedMembers, unwrapFor, wrapFor, type OwnerCircle, type Signer,
} from "./member-keys";

/** k: bearer key (older circles). j: invite secret of a member circle, rotated with epoch. members: pinned userId →
 * public key. sign: the owner's snapshot signing key. A deleted circle stays as a tombstone so sync cannot revive it. */
export interface Circle extends OwnerCircle { k?: string; w: string; label: string; clientId: string }
export interface CircleView { v: 1; profile: Pick<Profile, "name" | "color">; routines: Routine[]; intakes: Intake[]; publishedAt: number }
export interface CircleMember { userId: string; email: string | null; pub: string; proof: string; active: boolean; joinedAt: number }

export const getCircles = async () => (await getMeta<Circle[]>("circles", [])).filter((c) => !c.deletedAt);
/** Atomic edit of one live circle; returns it as stored after the edit (undefined if deleted or unknown). */
const updateCircle = async (id: string, fn: (c: Circle) => Circle) =>
  (await updateMeta<Circle[]>("circles", [], (list) => list.map((x) => (x.id === id && !x.deletedAt ? fn(x) : x)))).find((x) => x.id === id && !x.deletedAt);
const ownerAuth = (c: Circle) => ({ authorization: `Bearer ${c.w}` });

async function payload(clientId: string): Promise<CircleView> {
  const p = await db.profiles.get(clientId);
  const since = new Date(addDays(toLocalDay(new Date()), -60) + "T00:00").getTime();
  const routines = (await db.routines.where("clientId").equals(clientId).toArray()).filter((r) => !r.deletedAt).map((r) => ({ ...r, note: "" }));
  const intakes = (await db.intakes.where("clientId").equals(clientId).toArray()).filter((i) => !i.deletedAt && i.scheduledAt >= since);
  return { v: 1, profile: { name: p?.name ?? "", color: p?.color ?? "#1F6B4D" }, routines, intakes, publishedAt: Date.now() };
}
const keyOf = (k: string, use: KeyUsage) => crypto.subtle.importKey("raw", unb64u(k), "AES-GCM", false, [use]);

export async function createCircle(clientId: string, label: string) {
  const j = newJoinSecret(), w = b64u(crypto.getRandomValues(new Uint8Array(24))), sign = await newSigner();
  // The first blob has no wraps, so no member can read it; the first publish replaces it with a signed one.
  const blob = await seal(await contentKey(newContentKey(), "encrypt"), await payload(clientId));
  const { id } = await api<{ id: string }>("/api/circle", { method: "POST", headers: { authorization: `Bearer ${w}` }, json: { blob, joinAuth: await joinAuth(j) } });
  const now = Date.now(), c: Circle = { id, j, epoch: 0, w, label, clientId, createdAt: now, updatedAt: now, members: {}, sign };
  await updateMeta<Circle[]>("circles", [], (list) => [...list, c]);
  return c;
}
/** Member invite: #j=<invite secret>&o=<owner signing key>, so the joiner pins the owner key from the owner, not the
 * server. A pending key is left out (it may still lose to another device's); the joiner then pins on first sight. */
export const circleUrl = (c: Circle) => `${location.origin}/c/${c.id}#${c.j ? `j=${c.j}${canSign(c) ? `&o=${c.sign.pub}` : ""}` : c.k}`;

/** Circles made before 2.5 get a signing key here, before the sync agent's vault push (ensureSigners runs ahead of
 * syncNow). With sync on, another owner device may hold the same circle, so the key stays `pending` and cannot sign
 * until a vault push carried it; if another device pushed its key first, the pull adopts that one (mergeCircle).
 * Without sync there is no other device, so the key is usable at once. */
export async function ensureSigner(id: string) {
  const c = (await getCircles()).find((x) => x.id === id);
  if (!c?.j || c.sign) return c;
  const sign = { ...(await newSigner()), ...((await getSyncKey()) ? { pending: true as const } : {}) };
  const next = await updateCircle(id, (x) => (x.sign ? x : { ...x, sign, updatedAt: Date.now() }));
  window.dispatchEvent(new Event("oak:changed")); // schedule the push that commits it
  return next;
}
export async function ensureSigners() {
  for (const c of await getCircles()) if (c.j && !c.sign) await ensureSigner(c.id);
}

export const circleMembers = (c: Circle) => api<{ members: CircleMember[] }>(`/api/circle/${c.id}/members`, { headers: ownerAuth(c) }).then((r) => r.members);

/** Returns the member keys this publish trusted, to pin on the circle. */
async function publishMembers(c: Circle & { j: string; sign: Signer }) {
  const trusted = await trustedMembers(c.id, c.j, c.members ?? {}, await circleMembers(c));
  const raw = newContentKey();
  const blob = await signSnapshot(c.sign, c.id, await seal(await contentKey(raw, "encrypt"), await payload(c.clientId)));
  const wraps = Object.fromEntries(await Promise.all(Object.entries(trusted).map(async ([uid, pub]) => [uid, await wrapFor(raw, pub, c.id)])));
  await api(`/api/circle/${c.id}`, { method: "PUT", headers: ownerAuth(c), json: { blob, wraps } });
  return trusted;
}
async function publishBearer(c: Circle & { k: string }) {
  const blob = await seal(await keyOf(c.k, "encrypt"), await payload(c.clientId));
  await api(`/api/circle/${c.id}`, { method: "PUT", headers: ownerAuth(c), json: { blob } });
}

/** Publish one circle now, from its stored state (the caller's copy may be stale); pins newly verified members.
 * Pins are written back with applyPins: only onto the same invite epoch, never the whole circle, so a publish that
 * races a member removal cannot restore the old invite secret. */
export async function publishCircle(given: Circle) {
  if (given.k) return publishBearer({ ...given, k: given.k });
  const c = await ensureSigner(given.id);
  if (!c?.j || !canSign(c)) return; // pending signer: the next sync commits it, then the sync agent publishes
  const members = await publishMembers({ ...c, j: c.j, sign: c.sign });
  await updateCircle(c.id, (x) => applyPins(x, c.epoch ?? 0, members));
}

/** Re-publish every circle (called by the sync agent after local changes). */
export async function publishCircles() {
  for (const c of await getCircles()) await publishCircle(c).catch(() => {});
}

export async function revokeCircle(id: string) {
  const c = (await getCircles()).find((x) => x.id === id);
  if (c) await api(`/api/circle/${c.id}`, { method: "DELETE", headers: ownerAuth(c) });
  const now = Date.now(); // tombstone without secrets
  await updateMeta<Circle[]>("circles", [], (list) => list.map((x) => (x.id === id ? { id, w: "", label: "", clientId: x.clientId, createdAt: x.createdAt, updatedAt: now, deletedAt: now } : x)));
}

/** Remove one member, replace the invite link (the old one could re-admit them) and re-publish with a new key.
 * Reads the stored circle and the server's member list first, so everyone else stays trusted after j rotates. */
export async function revokeMember(id: string, userId: string) {
  const c = (await getCircles()).find((x) => x.id === id);
  if (!c?.j) throw new Error("This circle was deleted or never existed.");
  const members = await pinsAfterRemoval(c.id, c.j, c.members ?? {}, await circleMembers(c), userId);
  const j = newJoinSecret();
  await api(`/api/circle/${c.id}/revoke`, { method: "POST", headers: ownerAuth(c), json: { userId, joinAuth: await joinAuth(j) } });
  // The server now holds this j, so it must win over anything stored meanwhile: next epoch above both.
  const next = await updateCircle(id, (x) => ({ ...x, j, members, epoch: Math.max(x.epoch ?? 0, c.epoch ?? 0) + 1, updatedAt: Date.now() }));
  if (!next) throw new Error("This circle was deleted or never existed.");
  await publishCircle(next);
  return next;
}

export async function readCircle(id: string, k: string) {
  const res = await fetch(`/api/circle/${id}`);
  if (!res.ok) throw new Error(res.status === 404 ? "This circle link was revoked or never existed." : `Error ${res.status}`);
  const { blob } = (await res.json()) as { blob: string };
  return open<CircleView>(await keyOf(k, "decrypt"), blob);
}

interface StoredMemberKey { pub: string; sealed: string }
let memberKey: Promise<{ pub: string; priv: CryptoKey }> | null = null;

/** This account's member keypair: the stored one if this sync key opens it. Only an explicit join may create or
 * replace it; reading never rewrites the account's identity key. */
async function loadMemberKey(mayCreate: boolean) {
  const syncKey = await getSyncKey();
  if (!syncKey) throw new Error("Turn on sync first");
  const { key } = await api<{ key: StoredMemberKey | null }>("/api/circle-key");
  const opened = key && (await openMemberKey(syncKey, key.sealed).catch(() => null));
  if (key && opened) return { pub: key.pub, priv: opened };
  if (!mayCreate) throw new Error("This device can't open the circle key. Ask the owner for a new invite.");
  const fresh = await newMemberKeys(syncKey);
  const { key: saved } = await api<{ key: StoredMemberKey }>("/api/circle-key", { method: "PUT", json: { pub: fresh.pub, sealed: fresh.sealed, replace: !!key } });
  return saved.pub === fresh.pub ? fresh : { pub: saved.pub, priv: await openMemberKey(syncKey, saved.sealed) };
}
const myMemberKey = () => (memberKey ??= loadMemberKey(false).catch((e) => { memberKey = null; throw e; }));

/** Owner signing keys this account pinned as a member, per circle (synced in the vault). */
const ownerPins = () => getMeta<Record<string, string>>("circlePins", {});
const pinOwner = (id: string, pub: string) => updateMeta<Record<string, string>>("circlePins", {}, (p) => (p[id] === pub ? p : { ...p, [id]: pub }));

/** owner: the signing key from the invite's #o=, pinned so later snapshots must carry the owner's signature. */
export async function joinCircle(id: string, j: string, owner?: string | null) {
  memberKey = loadMemberKey(true);
  const { pub } = await memberKey.catch((e) => { memberKey = null; throw e; });
  await api(`/api/circle/${id}/join`, { method: "POST", json: { joinAuth: await joinAuth(j), pub, proof: await joinProof(j, id, pub) } });
  if (owner) await pinOwner(id, owner);
}

/** null = joined, waiting for the owner's next publish to include this account. Throws SnapshotRefused (with a
 * user-facing message) when the snapshot is unsigned or not signed by the pinned owner key. */
export async function readMemberCircle(id: string): Promise<CircleView | null> {
  const r = await api<{ blob?: string; wrap?: string }>(`/api/circle/${id}/me`);
  if (!r.blob || !r.wrap) return null;
  const pinned = (await ownerPins())[id];
  const { sealed, signer } = await checkSnapshot(id, r.blob, pinned);
  const { priv } = await myMemberKey();
  const view = await open<CircleView>(await unwrapFor(priv, r.wrap, id), sealed);
  if (!pinned) await pinOwner(id, signer); // joined before 2.5: trust the first signed snapshot, then hold the owner to it
  return view;
}
