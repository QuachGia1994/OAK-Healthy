"use client";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Loader2, ScanBarcode } from "lucide-react";
import { fromProduct, validGtin, type LabelGuess, type OffProduct } from "@/lib/domain/label";
import { useT } from "@/lib/i18n";

const FORMATS = ["ean_13", "ean_8", "upc_a", "upc_e"];
type Detector = { detect: (img: ImageBitmapSource) => Promise<{ rawValue: string }[]> };
type DetectorCtor = { new (o: { formats: string[] }): Detector; getSupportedFormats?: () => Promise<string[]> };

/** Barcode from a photo, decoded on this device (native BarcodeDetector, else a lazy-loaded ZXing WebAssembly build). */
async function decode(f: File) {
  let D = (globalThis as { BarcodeDetector?: DetectorCtor }).BarcodeDetector;
  if (!D || !(await D.getSupportedFormats?.().catch(() => [] as string[]))?.includes("ean_13")) D = (await import("barcode-detector/ponyfill")).BarcodeDetector as unknown as DetectorCtor;
  const codes = await new D({ formats: FORMATS }).detect(await createImageBitmap(f));
  return codes.map((c) => c.rawValue.trim()).find((v) => validGtin(v) || /^\d{8}$/.test(v)) ?? null;
}

/** Only the barcode number goes to Open Food Facts (an open product database), straight from this browser. */
async function lookup(code: string) {
  const r = await fetch(`https://world.openfoodfacts.org/api/v2/product/${code}.json?fields=product_name,generic_name,brands,quantity,serving_size`, { headers: { accept: "application/json" } });
  if (r.status === 404) return null;
  if (!r.ok) throw new Error("lookup");
  const j = (await r.json()) as { status?: number; product?: OffProduct };
  return j.status === 1 && j.product ? j.product : null;
}

export function BarcodeScan({ onResult }: { onResult: (g: LabelGuess) => void }) {
  const t = useT();
  const file = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const find = async (code: string) => {
    const p = await lookup(code);
    if (!p) { toast(t("Barcode {code} isn't in the open product database yet. Try Scan label.", { code })); return; }
    const g = fromProduct(p, code);
    toast.success(t("Product found"), { description: [g.name, g.dose, g.stock && `${g.stock} ×`].filter(Boolean).join(" · ") });
    onResult(g);
  };
  const scan = async (f: File) => {
    setBusy(true);
    try {
      let code = await decode(f).catch(() => null);
      if (!code) {
        const typed = prompt(t("No barcode found in the photo. Type the number under the bars:"))?.replace(/\D/g, "") ?? "";
        if (!typed) return;
        if (!validGtin(typed)) { toast.error(t("That barcode number doesn't look right")); return; }
        code = typed;
      }
      await find(code);
    } catch { toast.error(t("Barcode lookup failed. Check your connection.")); }
    finally { setBusy(false); if (file.current) file.current.value = ""; }
  };
  return (
    <>
      <button type="button" disabled={busy} onClick={() => file.current?.click()} className="btn w-full">
        {busy ? <><Loader2 className="size-4 animate-spin" /> {t("Looking up…")}</> : <><ScanBarcode className="size-4" /> {t("Scan barcode")}</>}
      </button>
      <input ref={file} type="file" accept="image/*" capture="environment" hidden onChange={(e) => e.target.files?.[0] && scan(e.target.files[0])} />
    </>
  );
}
