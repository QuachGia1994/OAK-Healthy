"use client";
// Circles: share one profile live and read-only with family or a coach (docs/feat/circle-accounts.md).
// Member circles: people join with their own OAK account; every publish wraps a fresh content key to each verified
// member, so revoking one member cuts them off while the rest keep reading. Bearer circles (older invites) keep
// their key in the link #fragment. Owner state (write token, invite secret, pinned member keys, signing key) lives in
// meta "circles" and reaches the owner's other devices inside the encrypted vault snapshot (lib/sync.ts).
import { b64u, seal, open, unb64u } from "./crypto";
import { api } from "./account";
import { db, getMeta } from "./db";
import { getSyncKey, syncNow, updateMeta } from "./sync";
import { addDays, toLocalDay } from "./domain/day";
import type { Intake, Profile, Routine } from "./domain/types";
import {
  applyPins, canSign, checkSeq, checkSnapshot, contentKey, joinAuth, joinProof, newContentKey, newJoinSecret, newMemberKeys, newSigner, openMemberKey,
  outOfStep, pinsAfterRemoval, publishEpoch, signSnapshot, STALE_EPOCH, trustedMembers, unwrapFor, wrapFor, type OwnerCircle, type Signer,
} from "./member-keys";

/** k: bearer key (older circles). j: invite secret of a member circle, rotated with epoch. members: pinned userId →
 * public key. sign: the owner's snapshot signing key. A deleted circle stays as a tombstone so sync cannot revive it. */
export interface Circle extends OwnerCircle { k?: string; w: string; label: string; clientId: string }
/** seq: publish number (2.6+), inside the sealed and signed payload; members refuse a lower one (replay guard). */
export interface CircleView { v: 1; profile: Pick<Profile, "name" | "color">; routines: Routine[]; intakes: Intake[]; publishedAt: number; seq?: number }
export interface CircleMember { userId: string; email: string | null; pub: string; proof: string; active: boolean; joinedAt: number }

export const getCircles = async () => (await getMeta<Circle[]>("circles", [])).filter((c) => !c.deletedAt);
/** Atomic edit of one live circle; returns it as stored after the edit (undefined if deleted or unknown). */
const updateCircle = async (id: string, fn: (c: Circle) => Circle) =>
  (await updateMeta<Circle[]>("circles", [], (list) => list.map((x) => (x.id === id && !x.deletedAt ? fn(x) : x)))).find((x) => x.id === id && !x.deletedAt);
const ownerAuth = (c: Circle) => ({ authorization: `Bearer ${c.w}` });

/** Owner write that may meet the server's epoch guard: 409 STALE_EPOCH comes back as a value (with the server's
 * epoch) for the caller to re-base on; any other failure throws, as api() does. */
async function ownerWrite(c: Circle, path: string, method: "PUT" | "POST", json: unknown) {
  const res = await fetch(`/api/circle/${c.id}${path}`, {
    method, credentials: "same-origin", headers: { "content-type": "application/json", ...ownerAuth(c) }, body: JSON.stringify(json),
  });
  const body = (await res.json().catch(() => ({}))) as { error?: string; epoch?: number | null };
  const stale = res.status === 409 && body.error === STALE_EPOCH;
  if (!res.ok && !stale) throw new Error(body.error || `Request failed (${res.status})`);
  return { stale, epoch: body.epoch ?? null };
}
/** Pull (and push) the vault now, so a rotation made on another device arrives, or ours leaves, without waiting. */
const syncCircleState = async () => { const k = await getSyncKey(); if (k) await syncNow(k); };

// Device-local circle meta (not in the vault): a rotation in flight, and the out-of-step marker.
/** A member removal sent to the server: kept from before the POST until its answer is stored, so a lost response
 * (timeout, closed tab) is finished on the next publish instead of losing the new invite secret. */
interface Rotation { userId: string; j: string; from: number; members: Record<string, string> }
const without = <T,>(all: Record<string, T>, id: string) => (id in all ? Object.fromEntries(Object.entries(all).filter(([k]) => k !== id)) : all);
const setRotation = (id: string, r: Rotation | null) =>
  updateMeta<Record<string, Rotation>>("circleRotating", {}, (all) => (r ? { ...all, [id]: r } : without(all, id)));
/** circleBehind: the server epoch this device last had to publish under without holding that epoch's invite. */
const setBehind = (id: string, epoch: number | null) =>
  updateMeta<Record<string, number>>("circleBehind", {}, (all) => (epoch === null ? without(all, id) : all[id] === epoch ? all : { ...all, [id]: epoch }));
/** Settings note: members get updates, but this device's invite link is out of date (lib/member-keys.ts outOfStep). */
export const isOutOfStep = (c: Circle, behind: Record<string, number> | undefined) => !!c.j && outOfStep(c.epoch ?? 0, behind?.[c.id]);

async function payload(clientId: string, seq?: number): Promise<CircleView> {
  const p = await db.profiles.get(clientId);
  const since = new Date(addDays(toLocalDay(new Date()), -60) + "T00:00").getTime();
  const routines = (await db.routines.where("clientId").equals(clientId).toArray()).filter((r) => !r.deletedAt).map((r) => ({ ...r, note: "" }));
  const intakes = (await db.intakes.where("clientId").equals(clientId).toArray()).filter((i) => !i.deletedAt && i.scheduledAt >= since);
  return { v: 1, profile: { name: p?.name ?? "", color: p?.color ?? "#1F6B4D" }, routines, intakes, publishedAt: Date.now(), ...(seq ? { seq } : {}) };
}
const keyOf = (k: string, use: KeyUsage) => crypto.subtle.importKey("raw", unb64u(k), "AES-GCM", false, [use]);

export async function createCircle(clientId: string, label: string) {
  const j = newJoinSecret(), w = b64u(crypto.getRandomValues(new Uint8Array(24))), sign = await newSigner();
  // The first blob has no wraps, so no member can read it; the first publish replaces it with a signed one.
  const blob = await seal(await contentKey(newContentKey(), "encrypt"), await payload(clientId));
  const { id } = await api<{ id: string }>("/api/circle", { method: "POST", headers: { authorization: `Bearer ${w}` }, json: { blob, joinAuth: await joinAuth(j), epoch: 0 } });
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

/** Publishes under `epoch`. Returns the member keys it trusted (to pin), or the server's epoch when refused as stale.
 * Each stored publish takes the next seq (synced with the circle; the vault merge keeps the highest). */
async function publishMembers(c: Circle & { j: string; sign: Signer }, epoch: number) {
  const trusted = await trustedMembers(c.id, c.j, c.members ?? {}, await circleMembers(c));
  const seq = (c.seq ?? 0) + 1, raw = newContentKey();
  const blob = await signSnapshot(c.sign, c.id, await seal(await contentKey(raw, "encrypt"), await payload(c.clientId, seq)));
  const wraps = Object.fromEntries(await Promise.all(Object.entries(trusted).map(async ([uid, pub]) => [uid, await wrapFor(raw, pub, c.id)])));
  const r = await ownerWrite(c, "", "PUT", { blob, wraps, epoch });
  if (r.stale) return { stale: true as const, epoch: r.epoch };
  await updateCircle(c.id, (x) => ((x.seq ?? 0) >= seq ? x : { ...x, seq }));
  return { stale: false as const, trusted };
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
  const redo = await finishRotation(given.id);
  if (redo) { await revokeMember(given.id, redo, true); return; } // it publishes once the removal is stored
  let refused: number | null = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    const c = await ensureSigner(given.id);
    if (!c?.j || !canSign(c)) return; // pending signer: the next sync commits it, then the sync agent publishes
    const local = c.epoch ?? 0, epoch = publishEpoch(local, refused);
    const r = await publishMembers({ ...c, j: c.j, sign: c.sign }, epoch);
    if (!r.stale) {
      await updateCircle(c.id, (x) => applyPins(x, local, r.trusted));
      await setBehind(c.id, epoch > local ? epoch : null);
      return;
    }
    // Another device rotated the invite: take its state from the vault and publish on top of it. Still behind after
    // that (that device has not synced, or lost its rotation's answer): publish under the server's epoch rather than
    // stall (publishEpoch: this cannot undo a removal), and show the out-of-step note. A third try covers a race.
    refused = r.epoch;
    if (attempt === 0) await syncCircleState();
  }
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
export async function revokeMember(id: string, userId: string, finished = false) {
  if (!finished) { const redo = await finishRotation(id); if (redo && redo !== userId) await revokeMember(id, redo, true); }
  let c = (await getCircles()).find((x) => x.id === id);
  for (let attempt = 0; attempt < 2; attempt++) {
    if (!c?.j) throw new Error("This circle was deleted or never existed.");
    const members = await pinsAfterRemoval(c.id, c.j, c.members ?? {}, await circleMembers(c), userId);
    const r = await rotate(c, { userId, j: newJoinSecret(), from: c.epoch ?? 0, members });
    if (r.stale) { c = await rebase(id, r.epoch ?? 0); continue; }
    await syncCircleState(); // hand the new invite to the other owner devices now
    await publishCircle(r.next);
    return r.next;
  }
  // The last refused rotation stays in circleRotating: the next publish retries this removal, so it is not dropped.
  throw new Error("This circle changed on another device at the same time. Try again.");
}
/** Send one rotation; it is stored as in flight first. Applied: the server holds this j at its new epoch, so the vault
 * merge makes it win on every device (unless a later rotation already arrived here). Refused: left in flight. */
async function rotate(c: Circle, r: Rotation) {
  await setRotation(c.id, r);
  const res = await ownerWrite(c, "/revoke", "POST", { userId: r.userId, joinAuth: await joinAuth(r.j), epoch: r.from });
  if (res.stale) return { stale: true as const, epoch: res.epoch };
  const epoch = res.epoch ?? r.from + 1;
  const next = await updateCircle(c.id, (x) => ((x.epoch ?? 0) >= epoch ? x : { ...x, j: r.j, members: r.members, epoch, updatedAt: Date.now() }));
  await setRotation(c.id, null);
  if (!next) throw new Error("This circle was deleted or never existed.");
  return { stale: false as const, next };
}
/** Finish a rotation whose answer never arrived: the same request again. The server answers ok if it already holds
 * that invite (worker revoke: same join_hash) or applies it now; refused means it never landed, so the removal is
 * returned for the caller to redo on top of the current state. A network error keeps it for the next run. */
async function finishRotation(id: string): Promise<string | null> {
  const r = (await getMeta<Record<string, Rotation>>("circleRotating", {}))[id];
  if (!r) return null;
  const c = (await getCircles()).find((x) => x.id === id);
  if (!c?.j) { await setRotation(id, null); return null; }
  const res = await rotate(c, r);
  if (res.stale) return r.userId;
  await syncCircleState();
  return null;
}
/** After a refused rotation: take the other device's rotation from the vault. If it has not arrived (that device has
 * not synced), go on from the server's epoch with this device's pins: this removal sets a new invite anyway, and the
 * other removal stays applied because the server already dropped that member. */
async function rebase(id: string, serverEpoch: number) {
  await syncCircleState();
  const c = (await getCircles()).find((x) => x.id === id);
  return c && { ...c, epoch: serverEpoch };
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
  const seen = (await getMeta<Record<string, number>>("circleSeen", {}))[id];
  const top = checkSeq(seen, view.seq); // throws SnapshotRefused on a replayed older snapshot
  if (top && top !== seen) {
    await updateMeta<Record<string, number>>("circleSeen", {}, (s) => ((s[id] ?? 0) >= top ? s : { ...s, [id]: top }));
    window.dispatchEvent(new Event("oak:changed")); // push the raised floor to the vault, so a reinstall keeps it
  }
  if (!pinned) await pinOwner(id, signer); // joined before 2.5: trust the first signed snapshot, then hold the owner to it
  return view;
}
