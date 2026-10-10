import { matchInText, type Reference } from "./knowledge.ts";

export interface LabelGuess { ref?: Reference; name?: string; dose?: string; stock?: number; perDose?: number }

/** Turn OCR text from a supplement label into routine prefill values. Pure, so it is unit-tested. */
export function parseLabel(text: string): LabelGuess {
  const t = text.replace(/\u00b5/g, "µ").replace(/[|]/g, " ");
  const refs = matchInText(t);
  const ref = refs[0];
  const doseRe = /(\d+(?:[.,]\d+)?)\s?(mcg|µg|ug|mg|iu|g)\b/gi;
  let dose: string | undefined;
  for (const m of t.matchAll(doseRe)) {
    const unit = m[2].toLowerCase().replace("ug", "mcg").replace("µg", "mcg").replace("iu", "IU");
    const num = /^\d{1,3},\d{3}$/.test(m[1]) ? m[1].replace(",", "") : m[1].replace(",", ".");
    dose = `${num} ${unit}`;
    break;
  }
  const count = t.match(/(\d{2,4})\s*(capsules|caps|tablets|tabs|softgels|gummies|viên|vien)\b/i);
  const serving = t.match(/(?:serving size|take|liều dùng)[:\s]*(\d)\s*(capsule|tablet|softgel|gumm|viên)/i);
  return {
    ref,
    name: ref?.name,
    dose: dose ?? ref?.dose,
    stock: count ? Number(count[1]) : undefined,
    perDose: serving ? Number(serving[1]) : undefined,
  };
}
