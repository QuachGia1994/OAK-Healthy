import type { IntakeStatus } from "./types.ts";

// Port of DoseTimingPolicy — single owner of timing/adherence formulas.
export const SOON_WINDOW_MS = 20 * 60 * 1000;
export const MISSED_AFTER_MS = 2 * 60 * 60 * 1000;

export const isDueSoon = (at: number, now: number) => at > now && at - now <= SOON_WINDOW_MS;
export const isMissed = (at: number, now: number) => at > 0 && now > at + MISSED_AFTER_MS;
export const isLateTaken = (status: IntakeStatus | undefined, at: number, updatedAt: number) =>
  status === "Taken" && updatedAt > 0 && updatedAt > at + SOON_WINDOW_MS;

export function completionRate(taken: number, skipped: number): number | null {
  const total = taken + skipped;
  return total <= 0 ? null : taken / total;
}

export type DoseState = "taken" | "skipped" | "missed" | "due" | "soon" | "upcoming";

export function doseState(status: IntakeStatus | undefined, at: number, now: number): DoseState {
  if (status === "Taken") return "taken";
  if (status === "Skipped") return "skipped";
  if (isMissed(at, now)) return "missed";
  if (now >= at) return "due";
  if (isDueSoon(at, now)) return "soon";
  return "upcoming";
}
