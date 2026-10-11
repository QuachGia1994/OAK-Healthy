// Dose writes shared by the dashboard (markDose, lib/db.ts) and the reminder notification. public/sw.js mirrors
// doseAt/planned/doseWrite/takenNext in plain JS; tests/pwa.test.ts runs both on the same fixtures (docs/feat/notify-barcode.md).
import type { Intake, Routine } from "./types.ts";
import { doseKey } from "./schedule.ts";
import { cycleStatus } from "./cycle.ts";
import { addDays, scheduledAt, toLocalDay } from "./day.ts";

/** A reminder can be shown this long before its dose time (push jitter); a time further ahead than that is yesterday's dose. */
export const EARLY_MS = 5 * 60_000;

/** scheduledAt of the latest occurrence of a HH:mm dose at `now`, so a dose snoozed past midnight keeps yesterday's key. */
export function doseAt(time: string, now: number): number {
  const day = toLocalDay(new Date(now));
  const at = scheduledAt(day, time);
  return at - EARLY_MS > now ? scheduledAt(addDays(day, -1), time) : at;
}

type Planned = Pick<Routine, "deletedAt" | "paused" | "startDate" | "cycle">;
/** The dashboard plans this dose on its own local day (dosesForDay's filter, schedule.ts): active, not paused, cycle ON. */
export const planned = (r: Planned, at: number) => !r.deletedAt && !r.paused && cycleStatus(r.startDate, r.cycle, toLocalDay(new Date(at))) === "ON";

type Stocked = Pick<Routine, "id" | "clientId" | "stock" | "perDose" | "updatedAt">;
export type DoseStatus = "Taken" | "Skipped" | null;
export interface DoseWrite<R> { intake: Intake | null; routine: R | null }
const isTaken = (i: Intake | undefined) => !!i && !i.deletedAt && i.status === "Taken";

/** Records for setting a dose's status (null = clear it to a tombstone). Stock moves only when Taken changes: −perDose on, +perDose off, floor 0. */
export function doseWrite<R extends Stocked>(r: R, at: number, prev: Intake | undefined, status: DoseStatus, now: number): DoseWrite<R> {
  const intake: Intake | null = status ? { key: doseKey(r.id, at), supplementId: r.id, clientId: r.clientId, scheduledAt: at, status, updatedAt: now, deletedAt: null }
    : prev ? { ...prev, deletedAt: now, updatedAt: now } : null;
  const willTake = status === "Taken";
  const routine = r.stock != null && isTaken(prev) !== willTake ? { ...r, stock: Math.max(0, r.stock + (willTake ? -r.perDose : r.perDose)), updatedAt: now } : null;
  return { intake, routine };
}

/** The notification's Taken: doseWrite("Taken"), or null when the dashboard does not plan the dose or it is already Taken (stock drops once). */
export function takenNext<R extends Stocked & Planned>(r: R, at: number, prev: Intake | undefined, now: number): DoseWrite<R> | null {
  return !planned(r, at) || isTaken(prev) ? null : doseWrite(r, at, prev, "Taken", now);
}
