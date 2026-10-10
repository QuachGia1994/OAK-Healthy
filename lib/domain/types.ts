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
  /** Pills left; null = not tracked. */
  stock: number | null;
  perDose: number;
  note?: string;
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
