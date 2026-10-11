"use client";
// Circles: share one profile live and read-only with family or a coach (docs/feat/circle-accounts.md).
// Member circles: people join with their own OAK account; every publish wraps a fresh content key to each verified
// member, so revoking one member cuts them off while the rest keep reading. Bearer circles (older invites) keep
// their key in the link #fragment. Owner state (write token, invite secret, pinned member keys) stays on this device.
import { b64u, seal, open, unb64u } from "./crypto";
import { api } from "./account";
import { db, getMeta, setMeta } from "./db";
import { getSyncKey } from "./sync";
import { addDays, toLocalDay } from "./domain/day";
import type { Intake, Profile, Routine } from "./domain/types";
import {
  contentKey, joinAuth, joinProof, newContentKey, newJoinSecret, newMemberKeys, openMemberKey, pinsAfterRemoval, trustedMembers, unwrapFor, wrapFor,
} from "./member-keys";

/** k: bearer key (older circles). j: invite secret of a member circle. members: pinned userId → public key. */
export interface Circle { id: string; k?: string; j?: string; members?: Record<string, string>; w: string; label: string; clientId: string; createdAt: number }
export interface CircleView { v: 1; profile: Pick<Profile, "name" | "color">; routines: Routine[]; intakes: Intake[]; publishedAt: number }
export interface CircleMember { userId: string; email: string | null; pub: string; proof: string; active: boolean; joinedAt: number }

export const getCircles = () => getMeta<Circle[]>("circles", []);
const saveCircle = async (c: Circle) => setMeta("circles", (await getCircles()).map((x) => (x.id === c.id ? c : x)));
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
  const j = newJoinSecret(), w = b64u(crypto.getRandomValues(new Uint8Array(24)));
  const blob = await seal(await contentKey(newContentKey(), "encrypt"), await payload(clientId));
  const { id } = await api<{ id: string }>("/api/circle", { method: "POST", headers: { authorization: `Bearer ${w}` }, json: { blob, joinAuth: await joinAuth(j) } });
  const c: Circle = { id, j, w, label, clientId, createdAt: Date.now(), members: {} };
  await setMeta("circles", [...(await getCircles()), c]);
  return c;
}
export const circleUrl = (c: Circle) => `${location.origin}/c/${c.id}#${c.j ? `j=${c.j}` : c.k}`;

export const circleMembers = (c: Circle) => api<{ members: CircleMember[] }>(`/api/circle/${c.id}/members`, { headers: ownerAuth(c) }).then((r) => r.members);

/** Returns the member keys this publish trusted, to pin on the circle. */
async function publishMembers(c: Circle & { j: string }) {
  const trusted = await trustedMembers(c.id, c.j, c.members ?? {}, await circleMembers(c));
  const raw = newContentKey();
  const blob = await seal(await contentKey(raw, "encrypt"), await payload(c.clientId));
  const wraps = Object.fromEntries(await Promise.all(Object.entries(trusted).map(async ([uid, pub]) => [uid, await wrapFor(raw, pub, c.id)])));
  await api(`/api/circle/${c.id}`, { method: "PUT", headers: ownerAuth(c), json: { blob, wraps } });
  return trusted;
}
async function publishBearer(c: Circle & { k: string }) {
  const blob = await seal(await keyOf(c.k, "encrypt"), await payload(c.clientId));
  await api(`/api/circle/${c.id}`, { method: "PUT", headers: ownerAuth(c), json: { blob } });
}

/** Publish one circle now; pins newly verified members. */
export async function publishCircle(c: Circle) {
  if (c.k) return publishBearer({ ...c, k: c.k });
  if (!c.j) return;
  const members = await publishMembers({ ...c, j: c.j });
  if (JSON.stringify(members) !== JSON.stringify(c.members ?? {})) await saveCircle({ ...c, members });
}

/** Re-publish every circle (called by the sync agent after local changes). */
export async function publishCircles() {
  for (const c of await getCircles()) await publishCircle(c).catch(() => {}); // sequential: each may rewrite the list
}

export async function revokeCircle(id: string) {
  const list = await getCircles();
  const c = list.find((x) => x.id === id);
  if (c) await api(`/api/circle/${c.id}`, { method: "DELETE", headers: ownerAuth(c) });
  await setMeta("circles", list.filter((x) => x.id !== id));
}

/** Remove one member, replace the invite link (the old one could re-admit them) and re-publish with a new key.
 * Reads the stored circle and the server's member list first, so everyone else stays trusted after j rotates. */
export async function revokeMember(id: string, userId: string) {
  const c = (await getCircles()).find((x) => x.id === id);
  if (!c?.j) throw new Error("This circle was deleted or never existed.");
  const members = await pinsAfterRemoval(c.id, c.j, c.members ?? {}, await circleMembers(c), userId);
  const j = newJoinSecret();
  await api(`/api/circle/${c.id}/revoke`, { method: "POST", headers: ownerAuth(c), json: { userId, joinAuth: await joinAuth(j) } });
  const next: Circle = { ...c, j, members };
  await saveCircle(next);
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

export async function joinCircle(id: string, j: string) {
  memberKey = loadMemberKey(true);
  const { pub } = await memberKey.catch((e) => { memberKey = null; throw e; });
  await api(`/api/circle/${id}/join`, { method: "POST", json: { joinAuth: await joinAuth(j), pub, proof: await joinProof(j, id, pub) } });
}

/** null = joined, waiting for the owner's next publish to include this account. */
export async function readMemberCircle(id: string): Promise<CircleView | null> {
  const r = await api<{ blob?: string; wrap?: string }>(`/api/circle/${id}/me`);
  if (!r.blob || !r.wrap) return null;
  const { priv } = await myMemberKey();
  return open<CircleView>(await unwrapFor(priv, r.wrap, id), r.blob);
}
