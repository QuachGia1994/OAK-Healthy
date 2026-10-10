import type { Intake, Routine, Slot } from "./types.ts";
import { cycleStatus } from "./cycle.ts";
import { addDays, scheduledAt } from "./day.ts";
import { completionRate } from "./timing.ts";

export const doseKey = (supplementId: string, at: number) => `${supplementId}@${at}`;

export interface Dose { routine: Routine; at: number; key: string; intake?: Intake }

export function slotOf(time: string): Slot {
  const h = Number(time.split(":")[0]);
  if (h < 11) return "morning";
  if (h < 15) return "midday";
  if (h < 20) return "evening";
  return "bedtime";
}

export function dosesForDay(routines: Routine[], intakes: Map<string, Intake>, day: string): Dose[] {
  return routines
    .filter((r) => !r.deletedAt && !r.paused && cycleStatus(r.startDate, r.cycle, day) === "ON")
    .map((r) => {
      const at = scheduledAt(day, r.time);
      const key = doseKey(r.id, at);
      return { routine: r, at, key, intake: intakes.get(key) };
    })
    .sort((a, b) => a.at - b.at);
}

export interface DayStat { day: string; taken: number; skipped: number; planned: number; rate: number | null }

export function dayStats(routines: Routine[], intakes: Map<string, Intake>, endDay: string, n: number): DayStat[] {
  const out: DayStat[] = [];
  for (let i = n - 1; i >= 0; i--) {
    const day = addDays(endDay, -i);
    const doses = dosesForDay(routines, intakes, day);
    let taken = 0, skipped = 0;
    for (const d of doses) {
      if (d.intake?.status === "Taken" && !d.intake.deletedAt) taken++;
      else if (d.intake?.status === "Skipped" && !d.intake.deletedAt) skipped++;
    }
    out.push({ day, taken, skipped, planned: doses.length, rate: completionRate(taken, skipped) });
  }
  return out;
}

/** Consecutive days (ending today, or yesterday if today is not finished) where every planned dose was taken. */
export function streak(stats: DayStat[]): number {
  let s = 0;
  for (let i = stats.length - 1; i >= 0; i--) {
    const d = stats[i];
    if (d.planned === 0) continue;
    if (d.taken === d.planned) s++;
    else if (i === stats.length - 1) continue;
    else break;
  }
  return s;
}

export function daysOfStockLeft(r: Routine): number | null {
  if (r.stock == null || r.perDose <= 0) return null;
  return Math.floor(r.stock / r.perDose);
}
