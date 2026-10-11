import { matchInText, type Reference } from "./knowledge.ts";

export interface LabelGuess { ref?: Reference; name?: string; dose?: string; stock?: number; perDose?: number; barcode?: string }

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

/** EAN-8 / UPC-A / EAN-13 / GTIN-14 with a valid mod-10 check digit. */
export function validGtin(code: string) {
  if (!/^(\d{8}|\d{12,14})$/.test(code)) return false;
  const d = [...code].map(Number), check = d.pop()!;
  const sum = d.reverse().reduce((s, n, i) => s + n * (i % 2 === 0 ? 3 : 1), 0);
  return (10 - (sum % 10)) % 10 === check;
}

/** Open Food Facts product fields we read (they also list many supplements). */
export interface OffProduct { product_name?: string; generic_name?: string; brands?: string; quantity?: string; serving_size?: string }

/** Product record → routine prefill: known supplement first, else the product name; count/dose via parseLabel. */
export function fromProduct(p: OffProduct, barcode?: string): LabelGuess {
  const title = [p.product_name || p.generic_name, p.brands?.split(",")[0]?.trim()].filter(Boolean).join(" · ");
  const g = parseLabel([p.product_name, p.generic_name, p.quantity, p.serving_size && `serving size ${p.serving_size}`].filter(Boolean).join("\n"));
  const own = (p.product_name || p.generic_name || "").trim().slice(0, 80);
  return { ...g, name: g.ref?.name ?? (own || title || undefined), barcode };
}
