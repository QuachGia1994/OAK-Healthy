"use client";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Camera, Loader2 } from "lucide-react";
import { parseLabel, type LabelGuess } from "@/lib/domain/label";
import { useT } from "@/lib/i18n";

/** On-device OCR (tesseract.js, lazy-loaded). The photo is never uploaded. */
export function LabelScan({ onResult }: { onResult: (g: LabelGuess) => void }) {
  const t = useT();
  const file = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [pct, setPct] = useState(0);
  const scan = async (f: File) => {
    setBusy(true); setPct(0);
    try {
      const { createWorker } = await import("tesseract.js");
      const w = await createWorker("eng", 1, { logger: (m: { status: string; progress: number }) => { if (m.status === "recognizing text") setPct(Math.round(m.progress * 100)); } });
      const { data } = await w.recognize(f);
      await w.terminate();
      const g = parseLabel(data.text);
      if (!g.name && !g.dose) toast(t("Couldn't read the label. Try a sharper, well-lit photo of the front."));
      else toast.success(t("Label read"), { description: [g.name, g.dose, g.stock && `${g.stock} ×`].filter(Boolean).join(" · ") });
      onResult(g);
    } catch { toast.error(t("Scanning failed. Check your connection: the OCR engine downloads once.")); }
    finally { setBusy(false); if (file.current) file.current.value = ""; }
  };
  return (
    <>
      <button type="button" disabled={busy} onClick={() => file.current?.click()} className="btn w-full">
        {busy ? <><Loader2 className="size-4 animate-spin" /> {t("Reading label…")} {pct ? `${pct}%` : ""}</> : <><Camera className="size-4" /> {t("Scan label")}</>}
      </button>
      <input ref={file} type="file" accept="image/*" capture="environment" hidden onChange={(e) => e.target.files?.[0] && scan(e.target.files[0])} />
    </>
  );
}
