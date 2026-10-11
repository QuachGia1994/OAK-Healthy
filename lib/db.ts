"use client";
import Dexie, { type Table } from "dexie";
import type { Biomarker, Intake, Profile, Routine } from "./domain/types";
import { toLocalDay } from "./domain/day";
import { doseKey } from "./domain/schedule";
import { doseWrite, type DoseStatus } from "./domain/intake-key";

export interface Meta { key: string; value: unknown }

class OakDB extends Dexie {
  profiles!: Table<Profile, string>;
  routines!: Table<Routine, string>;
  intakes!: Table<Intake, string>;
  meta!: Table<Meta, string>;
  biomarkers!: Table<Biomarker, string>;
  constructor() {
    super("oak-healthy");
    this.version(1).stores({
      profiles: "id, updatedAt",
      routines: "id, clientId, updatedAt",
      intakes: "key, clientId, supplementId, scheduledAt, updatedAt",
      meta: "key",
    });
    this.version(2).stores({ biomarkers: "id, clientId, marker, date, updatedAt" });
  }
}

export const db = new OakDB();
export const uid = () => crypto.randomUUID();
export const PROFILE_COLORS = ["#1F6B4D", "#466A8D", "#9A661F", "#B5473F", "#6B4D8F", "#3F7F86"];
const changed = () => window.dispatchEvent(new Event("oak:changed"));

export async function getMeta<T>(key: string, fallback: T): Promise<T> {
  const m = await db.meta.get(key);
  return (m?.value as T) ?? fallback;
}
export const setMeta = (key: string, value: unknown) => db.meta.put({ key, value });

let seeding: Promise<void> | null = null;
/** Idempotent first-run seed; a single shared promise + rw transaction prevents duplicate default profiles. */
export function ensureSeed(): Promise<void> {
  seeding ??= db.transaction("rw", db.profiles, db.meta, async () => {
    if ((await db.profiles.count()) > 0) return;
    const now = Date.now();
    const p: Profile = { id: uid(), name: "Me", color: PROFILE_COLORS[0], createdAt: now, updatedAt: now };
    await db.profiles.add(p);
    await db.meta.put({ key: "activeProfile", value: p.id });
  });
  return seeding;
}

export async function saveRoutine(r: Routine) {
  await db.routines.put({ ...r, updatedAt: Date.now() });
  changed();
}

export async function setDeleted(id: string, deleted: boolean) {
  const r = await db.routines.get(id);
  if (r) await db.routines.put({ ...r, deletedAt: deleted ? Date.now() : null, updatedAt: Date.now() });
  changed();
}

/** Record a dose with the rule the notification's Taken uses (doseWrite). Returns the previous status for Undo. */
export async function markDose(r: Routine, at: number, status: DoseStatus) {
  const prev = await db.transaction("rw", db.intakes, db.routines, async () => {
    const before = await db.intakes.get(doseKey(r.id, at));
    const cur = await db.routines.get(r.id);
    const w = doseWrite(cur ?? { ...r, stock: null }, at, before, status, Date.now());
    if (w.intake) await db.intakes.put(w.intake);
    if (w.routine) await db.routines.put(w.routine);
    return before;
  });
  changed();
  return prev && !prev.deletedAt ? prev.status : null;
}

export function blankRoutine(clientId: string): Routine {
  return {
    id: uid(), clientId, name: "", dose: "", time: "08:00", startDate: toLocalDay(new Date()),
    cycle: { daysOn: 1, daysOff: 0, continuous: true, durationDays: null }, category: "other",
    paused: false, stock: null, perDose: 1, note: "", updatedAt: Date.now(), deletedAt: null,
  };
}

export interface Snapshot { v: 1; profiles: Profile[]; routines: Routine[]; intakes: Intake[]; biomarkers?: Biomarker[] }

export async function snapshot(): Promise<Snapshot> {
  return { v: 1, profiles: await db.profiles.toArray(), routines: await db.routines.toArray(), intakes: await db.intakes.toArray(), biomarkers: await db.biomarkers.toArray() };
}

export async function saveBiomarker(b: Biomarker) {
  await db.biomarkers.put({ ...b, updatedAt: Date.now() });
  changed();
}
export async function deleteBiomarker(id: string, deleted = true) {
  const b = await db.biomarkers.get(id);
  if (b) await db.biomarkers.put({ ...b, deletedAt: deleted ? Date.now() : null, updatedAt: Date.now() });
  changed();
}

const newer = <T extends { updatedAt: number }>(a: T | undefined, b: T) => !a || b.updatedAt > a.updatedAt;

/** Last-writer-wins merge by updatedAt; tombstones (deletedAt) win when newer. */
export async function mergeSnapshot(s: Snapshot): Promise<number> {
  let n = 0;
  await db.transaction("rw", [db.profiles, db.routines, db.intakes, db.biomarkers], async () => {
    for (const p of s.profiles ?? []) if (newer(await db.profiles.get(p.id), p)) { await db.profiles.put(p); n++; }
    for (const r of s.routines ?? []) if (newer(await db.routines.get(r.id), r)) { await db.routines.put(r); n++; }
    for (const i of s.intakes ?? []) if (newer(await db.intakes.get(i.key), i)) { await db.intakes.put(i); n++; }
    for (const b of s.biomarkers ?? []) if (newer(await db.biomarkers.get(b.id), b)) { await db.biomarkers.put(b); n++; }
  });
  if (n) changed();
  return n;
}
/** After linking a device: drop this device's empty first-run profile and switch to the synced one. */
export async function adoptSyncedProfiles() {
  const active = (await db.meta.get("activeProfile"))?.value as string | undefined;
  const profiles = (await db.profiles.toArray()).filter((p) => !p.deletedAt);
  if (!active || profiles.length < 2) return;
  const empty = (await db.routines.where("clientId").equals(active).count()) === 0 && (await db.intakes.where("clientId").equals(active).count()) === 0;
  if (!empty) return;
  const target = profiles.find((p) => p.id !== active)!;
  const seed = profiles.find((p) => p.id === active)!;
  await db.profiles.put({ ...seed, deletedAt: Date.now(), updatedAt: Date.now() });
  await db.meta.put({ key: "activeProfile", value: target.id });
  changed();
}
