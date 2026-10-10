import type { Category, CycleConfig, Routine } from "./types.ts";
import { slotOf } from "./schedule.ts";

export interface Reference { name: string; time: string; dose?: string; cycle: CycleConfig; category: Category; advice: string; fatSoluble?: boolean; stimulating?: boolean; calming?: boolean }

const C: CycleConfig = { daysOn: 1, daysOff: 0, continuous: true };

// Seeded from the mobile SupplementDictionary, extended with timing traits for the chrono engine.
export const DICTIONARY: Reference[] = [
  { name: "Ashwagandha", time: "21:00", cycle: { daysOn: 28, daysOff: 7, continuous: false }, category: "herb", advice: "Adaptogen; cycle 4 weeks on, 1 off.", calming: true },
  { name: "Boron", time: "08:00", cycle: { daysOn: 14, daysOff: 7, continuous: false }, category: "mineral", advice: "Cycle 2 weeks on, 1 off." },
  { name: "Caffeine", time: "07:30", dose: "200 mg", cycle: C, category: "nootropic", advice: "Avoid after 14:00 to protect sleep.", stimulating: true },
  { name: "Vitamin D3", time: "08:00", dose: "2000 IU", cycle: C, category: "vitamin", advice: "Take with a meal that contains fat.", fatSoluble: true },
  { name: "Vitamin K2", time: "08:00", dose: "100 mcg", cycle: C, category: "vitamin", advice: "Pairs with D3.", fatSoluble: true },
  { name: "Omega-3", time: "12:00", dose: "1 g", cycle: C, category: "other", advice: "With lunch to reduce fishy burps.", fatSoluble: true },
  { name: "CoQ10", time: "12:00", dose: "100 mg", cycle: C, category: "other", advice: "Fat-soluble; can be energizing.", fatSoluble: true, stimulating: true },
  { name: "Creatine", time: "12:00", dose: "5 g", cycle: C, category: "other", advice: "Daily consistency matters more than timing." },
  { name: "NAC", time: "22:00", dose: "600 mg", cycle: C, category: "other", advice: "Take away from meals." },
  { name: "Magnesium", time: "21:00", dose: "200 mg", cycle: C, category: "mineral", advice: "Glycinate form supports sleep.", calming: true },
  { name: "Zinc", time: "21:00", dose: "15 mg", cycle: C, category: "mineral", advice: "Long-term use: balance with copper." },
  { name: "Iron", time: "10:00", dose: "18 mg", cycle: C, category: "mineral", advice: "Take with vitamin C, away from calcium and coffee." },
  { name: "Calcium", time: "20:00", dose: "500 mg", cycle: C, category: "mineral", advice: "Split doses above 500 mg." },
  { name: "Vitamin C", time: "10:00", dose: "500 mg", cycle: C, category: "vitamin", advice: "Boosts non-heme iron absorption." },
  { name: "Vitamin B Complex", time: "08:00", cycle: C, category: "vitamin", advice: "Morning; can disturb sleep at night.", stimulating: true },
  { name: "L-Theanine", time: "21:00", dose: "200 mg", cycle: C, category: "nootropic", advice: "Calming; pairs with caffeine in the morning too.", calming: true },
  { name: "Copper", time: "12:00", dose: "1 mg", cycle: C, category: "mineral", advice: "Balance against long-term zinc." },
];

export function findReference(name: string): Reference | undefined {
  const n = name.trim().toLowerCase();
  return DICTIONARY.find((r) => r.name.toLowerCase() === n);
}

export function suggest(q: string): Reference[] {
  const n = q.trim().toLowerCase();
  if (!n) return [];
  return DICTIONARY.filter((r) => r.name.toLowerCase().includes(n)).slice(0, 6);
}

export type Insight = { level: "clash" | "synergy" | "timing"; title: string; detail: string; ids: string[] };

const CLASHES: [string, string, string][] = [
  ["calcium", "iron", "Calcium blocks iron absorption. Keep them at least 2 hours apart."],
  ["zinc", "copper", "Zinc and copper compete for absorption. Separate them."],
  ["zinc", "iron", "High-dose iron reduces zinc absorption."],
  ["caffeine", "iron", "Coffee/caffeine cuts iron absorption. Wait 1 hour."],
  ["magnesium", "calcium", "Large calcium and magnesium doses compete. Split them."],
];
const SYNERGIES: [string, string, string][] = [
  ["vitamin d3", "vitamin k2", "D3 + K2 work together to route calcium to bones."],
  ["iron", "vitamin c", "Vitamin C boosts iron absorption. Take them together."],
  ["caffeine", "l-theanine", "L-theanine smooths caffeine's jitter."],
];

const has = (r: Routine, n: string) => r.name.toLowerCase().includes(n);

/** Interaction & chrono engine: clashes in the same slot, synergies split apart, and timing advice. */
export function analyze(routines: Routine[]): Insight[] {
  const live = routines.filter((r) => !r.deletedAt && !r.paused);
  const out: Insight[] = [];
  for (const [a, b, detail] of CLASHES) {
    for (const ra of live.filter((r) => has(r, a)))
      for (const rb of live.filter((r) => has(r, b) && r.id !== ra.id))
        if (slotOf(ra.time) === slotOf(rb.time)) out.push({ level: "clash", title: `${ra.name} × ${rb.name}`, detail, ids: [ra.id, rb.id] });
  }
  for (const [a, b, detail] of SYNERGIES) {
    const ra = live.find((r) => has(r, a)), rb = live.find((r) => has(r, b));
    if (ra && rb && slotOf(ra.time) !== slotOf(rb.time)) out.push({ level: "synergy", title: `${ra.name} + ${rb.name}`, detail: `${detail} They are in different slots now.`, ids: [ra.id, rb.id] });
  }
  for (const r of live) {
    const ref = findReference(r.name);
    const hour = Number(r.time.split(":")[0]);
    if ((ref?.stimulating || has(r, "caffeine")) && hour >= 18) out.push({ level: "timing", title: `${r.name} at ${r.time}`, detail: "Stimulating late in the day can hurt sleep. Move it before 14:00.", ids: [r.id] });
    if (ref?.calming && hour < 11) out.push({ level: "timing", title: `${r.name} at ${r.time}`, detail: "Calming. It usually fits better about 45 minutes before bed.", ids: [r.id] });
  }
  return out;
}
