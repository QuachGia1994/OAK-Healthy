"use client";
// Circles: share one profile live and read-only with family or a coach. Each invite has its own random key (in the
// link #fragment) and a write token kept on this device; revoking deletes the server copy.
import { b64u, seal, open, unb64u } from "./crypto";
import { db, getMeta, setMeta } from "./db";
import { addDays, toLocalDay } from "./domain/day";
import type { Intake, Profile, Routine } from "./domain/types";

export interface Circle { id: string; k: string; w: string; label: string; clientId: string; createdAt: number }
export interface CircleView { v: 1; profile: Pick<Profile, "name" | "color">; routines: Routine[]; intakes: Intake[]; publishedAt: number }

export const getCircles = () => getMeta<Circle[]>("circles", []);

async function payload(clientId: string): Promise<CircleView> {
  const p = await db.profiles.get(clientId);
  const since = new Date(addDays(toLocalDay(new Date()), -60) + "T00:00").getTime();
  const routines = (await db.routines.where("clientId").equals(clientId).toArray()).filter((r) => !r.deletedAt).map((r) => ({ ...r, note: "" }));
  const intakes = (await db.intakes.where("clientId").equals(clientId).toArray()).filter((i) => !i.deletedAt && i.scheduledAt >= since);
  return { v: 1, profile: { name: p?.name ?? "", color: p?.color ?? "#1F6B4D" }, routines, intakes, publishedAt: Date.now() };
}
const keyOf = (k: string, use: KeyUsage) => crypto.subtle.importKey("raw", unb64u(k), "AES-GCM", false, [use]);

export async function createCircle(clientId: string, label: string) {
  const k = b64u(crypto.getRandomValues(new Uint8Array(16))), w = b64u(crypto.getRandomValues(new Uint8Array(24)));
  const blob = await seal(await keyOf(k, "encrypt"), await payload(clientId));
  const res = await fetch("/api/circle", { method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${w}` }, body: JSON.stringify({ blob }) });
  if (!res.ok) throw new Error(`Invite failed (${res.status})`);
  const { id } = (await res.json()) as { id: string };
  const c: Circle = { id, k, w, label, clientId, createdAt: Date.now() };
  await setMeta("circles", [...(await getCircles()), c]);
  return c;
}
export const circleUrl = (c: Circle) => `${location.origin}/c/${c.id}#${c.k}`;

/** Re-publish every invite (called by the sync agent after local changes). */
export async function publishCircles() {
  const list = await getCircles();
  await Promise.all(list.map(async (c) => {
    const blob = await seal(await keyOf(c.k, "encrypt"), await payload(c.clientId));
    await fetch(`/api/circle/${c.id}`, { method: "PUT", headers: { "content-type": "application/json", authorization: `Bearer ${c.w}` }, body: JSON.stringify({ blob }) }).catch(() => {});
  }));
}

export async function revokeCircle(id: string) {
  const list = await getCircles();
  const c = list.find((x) => x.id === id);
  if (c) await fetch(`/api/circle/${c.id}`, { method: "DELETE", headers: { authorization: `Bearer ${c.w}` } });
  await setMeta("circles", list.filter((x) => x.id !== id));
}

export async function readCircle(id: string, k: string) {
  const res = await fetch(`/api/circle/${id}`);
  if (!res.ok) throw new Error(res.status === 404 ? "This circle link was revoked or never existed." : `Error ${res.status}`);
  const { blob } = (await res.json()) as { blob: string };
  return open<CircleView>(await keyOf(k, "decrypt"), blob);
}
