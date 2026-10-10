import type { CycleConfig } from "./types.ts";
import { daysBetween } from "./day.ts";

// Port of CalculateCycleUseCase: OFF before start day, OFF after duration, else On/Off cycle.
export function isExpired(startDate: string, cfg: CycleConfig, today: string): boolean {
  const days = cfg.durationDays ?? null;
  if (!days || days <= 0) return false;
  return daysBetween(startDate, today) >= days;
}

export function cycleStatus(startDate: string, cfg: CycleConfig, today: string): "ON" | "OFF" {
  const elapsed = daysBetween(startDate, today);
  if (elapsed < 0) return "OFF";
  if (isExpired(startDate, cfg, today)) return "OFF";
  if (cfg.continuous) return "ON";
  const total = cfg.daysOn + cfg.daysOff;
  if (total <= 0) return "ON";
  return elapsed % total < cfg.daysOn ? "ON" : "OFF";
}

export function cycleLabel(cfg: CycleConfig): string {
  if (cfg.continuous) return "Daily";
  return `${cfg.daysOn} on / ${cfg.daysOff} off`;
}
