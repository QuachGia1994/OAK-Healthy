"use client";
import { useEffect, useMemo, useState } from "react";
import qrcode from "qrcode-generator";
import { QrCode, X } from "lucide-react";
import { useT } from "@/lib/i18n";

const SHOW_SECONDS = 60;

/** Device-to-device handoff: the QR opens /settings/#link=<key> on the other device. The key rides in the URL
 * fragment, so it never reaches the server; the code auto-hides after a minute. */
export function DeviceQr({ syncKey }: { syncKey: string }) {
  const t = useT();
  const [left, setLeft] = useState(0);
  useEffect(() => {
    if (left <= 0) return;
    const id = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(id);
  }, [left]);
  const qr = useMemo(() => {
    if (left <= 0) return null;
    const q = qrcode(0, "M");
    q.addData(`${location.origin}/settings/#link=${encodeURIComponent(syncKey)}`);
    q.make();
    const n = q.getModuleCount();
    let d = "";
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (q.isDark(r, c)) d += `M${c},${r}h1v1h-1z`;
    return { n, d };
  }, [left > 0, syncKey]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!qr) return <button className="btn" onClick={() => setLeft(SHOW_SECONDS)}><QrCode className="size-4" /> {t("Link a device with QR")}</button>;
  return (
    <div className="rounded-xl border border-neutral-200 p-3 dark:border-neutral-800">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium">{t("Scan with the other device's camera")}</p>
        <button aria-label={t("Hide")} onClick={() => setLeft(0)} className="flex size-11 shrink-0 items-center justify-center rounded-md text-neutral-400 hover:bg-neutral-100 md:size-8 dark:hover:bg-neutral-800"><X className="size-4" /></button>
      </div>
      <svg viewBox={`-2 -2 ${qr.n + 4} ${qr.n + 4}`} className="mx-auto my-3 size-56 rounded-lg bg-white" shapeRendering="crispEdges" role="img" aria-label={t("Link a device with QR")}>
        <path d={qr.d} fill="#0a0a0a" />
      </svg>
      <p className="text-xs text-neutral-500">{t("It opens OAK and links that device to this vault. Anyone who scans it can read your data, so only show it to your own devices.")} {t("Hides in {s}s.", { s: left })}</p>
    </div>
  );
}
