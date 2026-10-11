"use client";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Camera, Loader2, ScanBarcode, X } from "lucide-react";
import { fromProduct, validGtin, type LabelGuess, type OffProduct } from "@/lib/domain/label";
import { useT } from "@/lib/i18n";

const FORMATS = ["ean_13", "ean_8", "upc_a", "upc_e"];
const FRAME_MS = 200;
type Detector = { detect: (img: ImageBitmapSource) => Promise<{ rawValue: string }[]> };
type DetectorCtor = { new (o: { formats: string[] }): Detector; getSupportedFormats?: () => Promise<string[]> };

let detector: Promise<Detector> | null = null;
/** Decoding stays on this device: native BarcodeDetector, else a lazy-loaded ZXing WebAssembly build. */
function getDetector() {
  detector ??= (async () => {
    let D = (globalThis as { BarcodeDetector?: DetectorCtor }).BarcodeDetector;
    if (!D || !(await D.getSupportedFormats?.().catch(() => [] as string[]))?.includes("ean_13")) D = (await import("barcode-detector/ponyfill")).BarcodeDetector as unknown as DetectorCtor;
    return new D({ formats: FORMATS });
  })().catch((e) => { detector = null; throw e; });
  return detector;
}
/** UPC-E's check digit is computed on its expanded UPC-A form, so any 8-digit read (already verified by the detector) is kept. */
const usable = (codes: { rawValue: string }[]) => codes.map((c) => c.rawValue.trim()).find((v) => validGtin(v) || /^\d{8}$/.test(v));

async function decodePhoto(f: File) {
  return usable(await (await getDetector()).detect(await createImageBitmap(f))) ?? null;
}

/** Only the barcode number goes to Open Food Facts (an open product database), straight from this browser. */
async function lookup(code: string) {
  const r = await fetch(`https://world.openfoodfacts.org/api/v2/product/${code}.json?fields=product_name,generic_name,brands,quantity,serving_size`, { headers: { accept: "application/json" } });
  if (r.status === 404) return null;
  if (!r.ok) throw new Error("lookup");
  const j = (await r.json()) as { status?: number; product?: OffProduct };
  return j.status === 1 && j.product ? j.product : null;
}

/** Rear camera, ~5 frames/s through the same detector; the first usable code stops every track. Unmount always stops them. */
function LiveScan({ onCode, onPhoto, onClose }: { onCode: (code: string) => void; onPhoto: () => void; onClose: () => void }) {
  const t = useT();
  const video = useRef<HTMLVideoElement>(null);
  const found = useRef(onCode);
  found.current = onCode;
  const [state, setState] = useState<"starting" | "scanning" | "unavailable">("starting");
  useEffect(() => {
    let live = true, timer = 0, stream: MediaStream | null = null;
    const stop = () => { live = false; clearTimeout(timer); stream?.getTracks().forEach((tr) => tr.stop()); };
    const tick = async (d: Detector) => {
      const v = video.current;
      const code = v && v.readyState >= 2 ? usable(await d.detect(v).catch(() => [])) : undefined;
      if (!live) return;
      if (code) { stop(); found.current(code); return; }
      timer = window.setTimeout(() => tick(d), FRAME_MS);
    };
    (async () => {
      try {
        const s = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" } }, audio: false });
        if (!live) { s.getTracks().forEach((tr) => tr.stop()); return; }
        stream = s;
        const v = video.current!;
        v.srcObject = s;
        await v.play();
        const d = await getDetector();
        if (!live) return;
        setState("scanning");
        void tick(d);
      } catch { if (live) { stop(); setState("unavailable"); } }
    })();
    return stop;
  }, []);
  const hint = state === "unavailable" ? t("Camera unavailable. Take a photo of the barcode instead.") : state === "starting" ? t("Starting camera…") : t("Point the camera at the barcode");
  return (
    <div role="dialog" aria-modal="true" aria-label={t("Scan barcode")} onKeyDown={(e) => { if (e.key === "Escape") onClose(); }} className="fixed inset-0 z-50 flex flex-col bg-black text-white">
      <div className="relative flex min-h-0 flex-1 items-center justify-center">
        <video ref={video} muted playsInline className={`size-full object-cover ${state === "unavailable" ? "hidden" : ""}`} />
        {state === "scanning" && <div aria-hidden className="pointer-events-none absolute inset-x-6 top-1/2 h-28 -translate-y-1/2 rounded-xl border-2 border-white/80" />}
        <p role="status" className={`px-6 text-center text-sm ${state === "unavailable" ? "" : "absolute inset-x-0 bottom-4"}`}>{hint}</p>
      </div>
      <div className="grid grid-cols-2 gap-2 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <button type="button" autoFocus onClick={onClose} className="btn min-h-11"><X className="size-4" /> {t("Cancel")}</button>
        <button type="button" onClick={onPhoto} className="btn min-h-11"><Camera className="size-4" /> {t("Take a photo")}</button>
      </div>
    </div>
  );
}

export function BarcodeScan({ onResult }: { onResult: (g: LabelGuess) => void }) {
  const t = useT();
  const file = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState(false);
  const find = async (code: string) => {
    const p = await lookup(code);
    if (!p) { toast(t("Barcode {code} isn't in the open product database yet. Try Scan label.", { code })); return; }
    const g = fromProduct(p, code);
    toast.success(t("Product found"), { description: [g.name, g.dose, g.stock && `${g.stock} ×`].filter(Boolean).join(" · ") });
    onResult(g);
  };
  const codeFromPhoto = async (f: File) => {
    const code = await decodePhoto(f).catch(() => null);
    if (code) return code;
    const typed = prompt(t("No barcode found in the photo. Type the number under the bars:"))?.replace(/\D/g, "") ?? "";
    if (!typed) return null;
    if (!validGtin(typed)) { toast.error(t("That barcode number doesn't look right")); return null; }
    return typed;
  };
  const run = async (getCode: () => Promise<string | null>) => {
    setBusy(true);
    try {
      const code = await getCode();
      if (code) await find(code);
    } catch { toast.error(t("Barcode lookup failed. Check your connection.")); }
    finally { setBusy(false); if (file.current) file.current.value = ""; }
  };
  const pickPhoto = () => { setLive(false); file.current?.click(); };
  const start = () => ("mediaDevices" in navigator ? setLive(true) : file.current?.click());
  return (
    <>
      <button type="button" disabled={busy} onClick={start} className="btn min-h-11 w-full">
        {busy ? <><Loader2 className="size-4 animate-spin" /> {t("Looking up…")}</> : <><ScanBarcode className="size-4" /> {t("Scan barcode")}</>}
      </button>
      <input ref={file} type="file" accept="image/*" capture="environment" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) void run(() => codeFromPhoto(f)); }} />
      {live && <LiveScan onCode={(code) => { setLive(false); void run(async () => code); }} onPhoto={pickPhoto} onClose={() => setLive(false)} />}
    </>
  );
}
