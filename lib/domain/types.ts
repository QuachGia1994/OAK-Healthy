export type IntakeStatus = "Taken" | "Skipped";
export type Slot = "morning" | "midday" | "evening" | "bedtime";
export type Category = "vitamin" | "mineral" | "nootropic" | "herb" | "rx" | "other";

export interface Profile { id: string; name: string; color: string; createdAt: number; updatedAt: number; deletedAt?: number | null }

export interface CycleConfig {
  daysOn: number;
  daysOff: number;
  continuous: boolean;
  /** Routine stops after this many days from startDate (null = forever). */
  durationDays?: number | null;
}

/** One schedule that held from `from` (local day) until the next span's day (docs/feat/schedule-history.md). */
export interface ScheduleSpan {
  from: string;
  time: string;
  startDate: string;
  cycle: CycleConfig;
  paused: boolean;
  /** Closing span written by a delete; recorded only (a deleted routine is never planned). */
  deleted?: true;
}

export interface Routine {
  id: string;
  clientId: string;
  name: string;
  dose: string;
  /** Local wall-clock time, HH:mm. */
  time: string;
  /** Calendar day, YYYY-MM-DD (never a UTC instant). */
  startDate: string;
  cycle: CycleConfig;
  category: Category;
  paused: boolean;
  /** Push reminder at dose time (undefined = on). */
  remind?: boolean;
  /** Pills left; null = not tracked. */
  stock: number | null;
  perDose: number;
  /** Where to buy more (http/https only). */
  buyUrl?: string;
  /** Calendar day the current bottle was opened, YYYY-MM-DD. */
  openedOn?: string | null;
  /** Days the product stays good after opening (null = not tracked). */
  shelfLifeDays?: number | null;
  note?: string;
  /** Past schedules, sorted by from; absent = the current fields held on every day (lib/domain/schedule-history.ts). */
  history?: ScheduleSpan[];
  updatedAt: number;
  deletedAt?: number | null;
}

export interface Intake {
  /** DoseEventKey: `${supplementId}@${scheduledAt}` */
  key: string;
  supplementId: string;
  clientId: string;
  scheduledAt: number;
  status: IntakeStatus;
  updatedAt: number;
  deletedAt?: number | null;
}

export interface Biomarker {
  id: string;
  clientId: string;
  /** Key into BIOMARKERS reference table, or a custom name. */
  marker: string;
  value: number;
  unit: string;
  /** Calendar day of the lab draw, YYYY-MM-DD. */
  date: string;
  note?: string;
  updatedAt: number;
  deletedAt?: number | null;
}
