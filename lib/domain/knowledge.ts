import type { Category, CycleConfig, Routine } from "./types.ts";
import { slotOf } from "./schedule.ts";

export type Food = "with" | "empty" | "any";
export interface Reference { name: string; time: string; dose?: string; cycle: CycleConfig; category: Category; advice: string; fatSoluble?: boolean; stimulating?: boolean; calming?: boolean; food?: Food; aliases?: string[]; source?: string }

const C: CycleConfig = { daysOn: 1, daysOff: 0, continuous: true };
const ODS = (slug: string) => `https://ods.od.nih.gov/factsheets/${slug}-HealthProfessional/`;

// Timing traits feed the chrono engine; `source` points at a public fact sheet (NIH ODS where one exists).
export const DICTIONARY: Reference[] = [
  { name: "Ashwagandha", time: "21:00", cycle: { daysOn: 28, daysOff: 7, continuous: false }, category: "herb", advice: "Adaptogen; cycle 4 weeks on, 1 off.", calming: true, food: "any", aliases: ["withania"], source: ODS("Ashwagandha") },
  { name: "Boron", time: "08:00", cycle: { daysOn: 14, daysOff: 7, continuous: false }, category: "mineral", advice: "Cycle 2 weeks on, 1 off.", food: "with", source: ODS("Boron") },
  { name: "Caffeine", time: "07:30", dose: "200 mg", cycle: C, category: "nootropic", advice: "Avoid after 14:00 to protect sleep.", stimulating: true, food: "any", aliases: ["coffee"] },
  { name: "Vitamin D3", time: "08:00", dose: "2000 IU", cycle: C, category: "vitamin", advice: "Take with a meal that contains fat.", fatSoluble: true, food: "with", aliases: ["cholecalciferol", "vitamin d"], source: ODS("VitaminD") },
  { name: "Vitamin K2", time: "08:00", dose: "100 mcg", cycle: C, category: "vitamin", advice: "Pairs with D3.", fatSoluble: true, food: "with", aliases: ["menaquinone", "mk-7", "mk7"], source: ODS("VitaminK") },
  { name: "Omega-3", time: "12:00", dose: "1 g", cycle: C, category: "other", advice: "With lunch to reduce fishy burps.", fatSoluble: true, food: "with", aliases: ["fish oil", "epa", "dha"], source: ODS("Omega3FattyAcids") },
  { name: "CoQ10", time: "12:00", dose: "100 mg", cycle: C, category: "other", advice: "Fat-soluble; can be energizing.", fatSoluble: true, stimulating: true, food: "with", aliases: ["ubiquinol", "coenzyme q10"] },
  { name: "Creatine", time: "12:00", dose: "5 g", cycle: C, category: "other", advice: "Daily consistency matters more than timing.", food: "any", aliases: ["creatine monohydrate"] },
  { name: "NAC", time: "22:00", dose: "600 mg", cycle: C, category: "other", advice: "Take away from meals.", food: "empty", aliases: ["n-acetyl cysteine", "n-acetylcysteine"] },
  { name: "Magnesium", time: "21:00", dose: "200 mg", cycle: C, category: "mineral", advice: "Glycinate form supports sleep.", calming: true, food: "any", aliases: ["magnesium glycinate", "magnesium citrate"], source: ODS("Magnesium") },
  { name: "Zinc", time: "21:00", dose: "15 mg", cycle: C, category: "mineral", advice: "Long-term use: balance with copper.", food: "with", aliases: ["zinc picolinate"], source: ODS("Zinc") },
  { name: "Iron", time: "10:00", dose: "18 mg", cycle: C, category: "mineral", advice: "Take with vitamin C, away from calcium and coffee.", food: "empty", aliases: ["ferrous", "iron bisglycinate"], source: ODS("Iron") },
  { name: "Calcium", time: "20:00", dose: "500 mg", cycle: C, category: "mineral", advice: "Split doses above 500 mg.", food: "with", aliases: ["calcium carbonate", "calcium citrate"], source: ODS("Calcium") },
  { name: "Vitamin C", time: "10:00", dose: "500 mg", cycle: C, category: "vitamin", advice: "Boosts non-heme iron absorption.", food: "any", aliases: ["ascorbic acid"], source: ODS("VitaminC") },
  { name: "Vitamin B Complex", time: "08:00", cycle: C, category: "vitamin", advice: "Morning; can disturb sleep at night.", stimulating: true, food: "with", aliases: ["b complex", "b-complex"] },
  { name: "L-Theanine", time: "21:00", dose: "200 mg", cycle: C, category: "nootropic", advice: "Calming; pairs with caffeine in the morning too.", calming: true, food: "any", aliases: ["theanine"] },
  { name: "Copper", time: "12:00", dose: "1 mg", cycle: C, category: "mineral", advice: "Balance against long-term zinc.", food: "with", source: ODS("Copper") },
  { name: "Melatonin", time: "22:00", dose: "0.5 mg", cycle: C, category: "other", advice: "Low dose 30–60 minutes before bed works for most people.", calming: true, food: "any", source: "https://www.nccih.nih.gov/health/melatonin-what-you-need-to-know" },
  { name: "Vitamin B12", time: "08:00", dose: "500 mcg", cycle: C, category: "vitamin", advice: "Morning; important on plant-based diets.", stimulating: true, food: "any", aliases: ["methylcobalamin", "cyanocobalamin", "b12"], source: ODS("VitaminB12") },
  { name: "Folate", time: "08:00", dose: "400 mcg", cycle: C, category: "vitamin", advice: "Methylfolate is the active form.", food: "any", aliases: ["folic acid", "methylfolate", "5-mthf"], source: ODS("Folate") },
  { name: "Vitamin A", time: "12:00", cycle: C, category: "vitamin", advice: "Fat-soluble; avoid high doses in pregnancy.", fatSoluble: true, food: "with", aliases: ["retinol", "beta-carotene"], source: ODS("VitaminA") },
  { name: "Vitamin E", time: "12:00", cycle: C, category: "vitamin", advice: "Fat-soluble; high doses thin the blood.", fatSoluble: true, food: "with", aliases: ["tocopherol"], source: ODS("VitaminE") },
  { name: "Selenium", time: "12:00", dose: "100 mcg", cycle: C, category: "mineral", advice: "2 Brazil nuts ≈ 100 mcg; do not exceed 400 mcg/day.", food: "with", source: ODS("Selenium") },
  { name: "Iodine", time: "08:00", dose: "150 mcg", cycle: C, category: "mineral", advice: "Thyroid support; check with a doctor if you have thyroid disease.", food: "any", source: ODS("Iodine") },
  { name: "Potassium", time: "12:00", cycle: C, category: "mineral", advice: "Take with food and water.", food: "with", source: ODS("Potassium") },
  { name: "Probiotic", time: "07:30", cycle: C, category: "other", advice: "Before breakfast; keep 2 hours away from antibiotics.", food: "empty", aliases: ["lactobacillus", "bifidobacterium"], source: ODS("Probiotics") },
  { name: "Psyllium", time: "19:00", dose: "5 g", cycle: C, category: "other", advice: "Fiber; drink a full glass of water and keep 2 hours from other pills.", food: "any", aliases: ["fiber", "fibre", "metamucil"] },
  { name: "Turmeric", time: "12:00", dose: "500 mg", cycle: C, category: "herb", advice: "Curcumin absorbs better with fat and black pepper.", fatSoluble: true, food: "with", aliases: ["curcumin"], source: "https://www.nccih.nih.gov/health/turmeric" },
  { name: "Rhodiola", time: "08:00", dose: "200 mg", cycle: { daysOn: 42, daysOff: 14, continuous: false }, category: "herb", advice: "Energizing adaptogen; take in the morning.", stimulating: true, food: "empty", aliases: ["rhodiola rosea"] },
  { name: "Glycine", time: "22:00", dose: "3 g", cycle: C, category: "other", advice: "Before bed for sleep quality.", calming: true, food: "any" },
  { name: "Collagen", time: "08:00", dose: "10 g", cycle: C, category: "other", advice: "Pair with vitamin C.", food: "any", aliases: ["collagen peptides"] },
  { name: "Lion's Mane", time: "08:00", dose: "1 g", cycle: C, category: "nootropic", advice: "Morning, with or without food.", food: "any", aliases: ["lions mane", "hericium"] },
  { name: "Ginkgo", time: "08:00", dose: "120 mg", cycle: C, category: "herb", advice: "May thin the blood; avoid with anticoagulants.", food: "with", aliases: ["ginkgo biloba"], source: "https://www.nccih.nih.gov/health/ginkgo" },
  { name: "Green Tea Extract", time: "10:00", cycle: C, category: "herb", advice: "Contains caffeine; take with food to protect the liver.", stimulating: true, food: "with", aliases: ["egcg", "green tea"], source: "https://www.nccih.nih.gov/health/green-tea" },
  { name: "Berberine", time: "12:00", dose: "500 mg", cycle: C, category: "herb", advice: "With meals; may lower blood sugar.", food: "with" },
  { name: "Biotin", time: "08:00", cycle: C, category: "vitamin", advice: "Stop 2–3 days before blood tests: it skews many lab results.", food: "any", aliases: ["vitamin b7"], source: ODS("Biotin") },
  { name: "Quercetin", time: "12:00", dose: "500 mg", cycle: C, category: "other", advice: "With a meal for better absorption.", food: "with" },
];

const norm = (s: string) => s.trim().toLowerCase().replace(/[’']/g, "'");
export function findReference(name: string): Reference | undefined {
  const n = norm(name);
  return DICTIONARY.find((r) => norm(r.name) === n || r.aliases?.some((a) => a === n));
}

export function suggest(q: string): Reference[] {
  const n = norm(q);
  if (!n) return [];
  return DICTIONARY.filter((r) => norm(r.name).includes(n) || r.aliases?.some((a) => a.includes(n))).slice(0, 6);
}

/** Find dictionary entries mentioned anywhere in free text (e.g. OCR output of a supplement label). */
export function matchInText(text: string): Reference[] {
  const n = " " + norm(text).replace(/[^a-z0-9' -]+/g, " ").replace(/\s+/g, " ") + " ";
  const hit = (w: string) => n.includes(" " + w + " ") || n.includes(" " + w.replace(/-/g, " ") + " ");
  return DICTIONARY.filter((r) => hit(norm(r.name)) || r.aliases?.some(hit));
}

export type Insight = { level: "clash" | "synergy" | "timing"; title: string; detail: string; ids: string[] };

const CLASHES: [string, string, string][] = [
  ["calcium", "iron", "Calcium blocks iron absorption. Keep them at least 2 hours apart."],
  ["zinc", "copper", "Zinc and copper compete for absorption. Separate them."],
  ["zinc", "iron", "High-dose iron reduces zinc absorption."],
  ["caffeine", "iron", "Coffee/caffeine cuts iron absorption. Wait 1 hour."],
  ["magnesium", "calcium", "Large calcium and magnesium doses compete. Split them."],
  ["calcium", "zinc", "Calcium can reduce zinc absorption. Separate them."],
  ["green tea", "iron", "Green tea catechins bind iron. Keep them apart."],
  ["psyllium", "", "Fiber can trap other supplements and medicines. Keep 2 hours apart."],
  ["caffeine", "melatonin", "Caffeine works against melatonin."],
  ["ginkgo", "vitamin e", "Both thin the blood; ask a clinician before combining."],
  ["ginkgo", "omega-3", "Both thin the blood at high doses; ask a clinician before combining."],
];
const SYNERGIES: [string, string, string][] = [
  ["vitamin d3", "vitamin k2", "D3 + K2 work together to route calcium to bones."],
  ["iron", "vitamin c", "Vitamin C boosts iron absorption. Take them together."],
  ["caffeine", "l-theanine", "L-theanine smooths caffeine's jitter."],
  ["collagen", "vitamin c", "Vitamin C is needed to build collagen."],
  ["turmeric", "omega-3", "Curcumin absorbs better alongside dietary fat."],
];

const has = (r: Routine, n: string) => r.name.toLowerCase().includes(n);

/** Interaction & chrono engine: clashes in the same slot, synergies split apart, and timing advice.
 * `detail` is always a fixed English source string so the UI can translate it. */
export function analyze(routines: Routine[]): Insight[] {
  const live = routines.filter((r) => !r.deletedAt && !r.paused);
  const out: Insight[] = [];
  for (const [a, b, detail] of CLASHES) {
    for (const ra of live.filter((r) => has(r, a)))
      for (const rb of live.filter((r) => r.id !== ra.id && (b ? has(r, b) : !has(r, a))))
        if (slotOf(ra.time) === slotOf(rb.time) && !out.some((o) => o.level === "clash" && o.ids.includes(ra.id) && o.ids.includes(rb.id)))
          out.push({ level: "clash", title: `${ra.name} × ${rb.name}`, detail, ids: [ra.id, rb.id] });
  }
  for (const [a, b, detail] of SYNERGIES) {
    const ra = live.find((r) => has(r, a)), rb = live.find((r) => has(r, b));
    if (ra && rb && slotOf(ra.time) !== slotOf(rb.time)) out.push({ level: "synergy", title: `${ra.name} + ${rb.name}`, detail, ids: [ra.id, rb.id] });
  }
  for (const r of live) {
    const ref = findReference(r.name);
    const hour = Number(r.time.split(":")[0]);
    if ((ref?.stimulating || has(r, "caffeine")) && hour >= 18) out.push({ level: "timing", title: `${r.name} · ${r.time}`, detail: "Stimulating late in the day can hurt sleep. Move it before 14:00.", ids: [r.id] });
    if (ref?.calming && hour < 11) out.push({ level: "timing", title: `${r.name} · ${r.time}`, detail: "Calming. It usually fits better about 45 minutes before bed.", ids: [r.id] });
    if (ref?.food === "with" && (hour >= 22 || hour < 6)) out.push({ level: "timing", title: `${r.name} · ${r.time}`, detail: "Absorbs best with a meal. Move it next to breakfast, lunch or dinner.", ids: [r.id] });
  }
  return out;
}
