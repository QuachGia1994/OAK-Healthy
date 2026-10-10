import type { Biomarker } from "./types.ts";

export interface MarkerRef { key: string; name: string; unit: string; low: number; high: number; hint: string; supplements?: string[] }

// Typical adult reference ranges (they vary by lab, age and sex; the UI says so).
export const BIOMARKERS: MarkerRef[] = [
  { key: "vitd", name: "Vitamin D (25-OH)", unit: "ng/mL", low: 30, high: 100, hint: "Below 20 is deficient.", supplements: ["vitamin d3"] },
  { key: "ferritin", name: "Ferritin", unit: "ng/mL", low: 30, high: 300, hint: "Iron stores.", supplements: ["iron"] },
  { key: "hb", name: "Hemoglobin", unit: "g/dL", low: 12, high: 17.5, hint: "Oxygen-carrying capacity." },
  { key: "b12", name: "Vitamin B12", unit: "pg/mL", low: 200, high: 900, hint: "Low on plant-based diets.", supplements: ["vitamin b12"] },
  { key: "mg", name: "Magnesium (serum)", unit: "mg/dL", low: 1.7, high: 2.2, hint: "Serum level misses tissue stores.", supplements: ["magnesium"] },
  { key: "hba1c", name: "HbA1c", unit: "%", low: 4, high: 5.6, hint: "3-month blood sugar average." },
  { key: "glucose", name: "Fasting glucose", unit: "mg/dL", low: 70, high: 99, hint: "After 8 hours without food." },
  { key: "ldl", name: "LDL cholesterol", unit: "mg/dL", low: 0, high: 100, hint: "Lower is better for heart health." },
  { key: "hdl", name: "HDL cholesterol", unit: "mg/dL", low: 40, high: 90, hint: "Higher is generally better." },
  { key: "tg", name: "Triglycerides", unit: "mg/dL", low: 0, high: 150, hint: "Omega-3 can lower them.", supplements: ["omega-3"] },
  { key: "tsh", name: "TSH", unit: "mIU/L", low: 0.4, high: 4, hint: "Thyroid signal. Biotin skews this test." },
  { key: "crp", name: "hs-CRP", unit: "mg/L", low: 0, high: 1, hint: "Inflammation marker." },
  { key: "hcy", name: "Homocysteine", unit: "µmol/L", low: 5, high: 15, hint: "B12 and folate lower it.", supplements: ["folate", "vitamin b12"] },
  { key: "o3i", name: "Omega-3 index", unit: "%", low: 8, high: 12, hint: "EPA+DHA in red cells.", supplements: ["omega-3"] },
];

export const markerRef = (key: string) => BIOMARKERS.find((m) => m.key === key);
export type MarkerStatus = "low" | "normal" | "high" | "unknown";

export function markerStatus(b: Pick<Biomarker, "marker" | "value" | "unit">): MarkerStatus {
  const ref = markerRef(b.marker);
  if (!ref || ref.unit !== b.unit || !Number.isFinite(b.value)) return "unknown";
  return b.value < ref.low ? "low" : b.value > ref.high ? "high" : "normal";
}

/** Latest reading per marker (newest date, then newest edit) plus the series in date order. */
export function byMarker(list: Biomarker[]) {
  const m = new Map<string, Biomarker[]>();
  for (const b of list) if (!b.deletedAt) m.set(b.marker, [...(m.get(b.marker) ?? []), b]);
  return [...m.entries()].map(([marker, rows]) => {
    const series = rows.sort((a, b) => a.date.localeCompare(b.date) || a.updatedAt - b.updatedAt);
    return { marker, latest: series[series.length - 1], series };
  }).sort((a, b) => b.latest.date.localeCompare(a.latest.date));
}
