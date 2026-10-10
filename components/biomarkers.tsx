"use client";
import { useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { toast } from "sonner";
import { FlaskConical, Plus, Trash2 } from "lucide-react";
import { db, deleteBiomarker, saveBiomarker, uid } from "@/lib/db";
import { BIOMARKERS, byMarker, markerRef, markerStatus, type MarkerStatus } from "@/lib/domain/biomarkers";
import { toLocalDay } from "@/lib/domain/day";
import { useT, locale } from "@/lib/i18n";

const ST: Record<MarkerStatus, string> = {
  low: "border-skipped/20 bg-skipped-soft text-skipped", high: "border-missed/20 bg-missed-soft text-missed",
  normal: "border-taken/20 bg-taken-soft text-taken", unknown: "border-neutral-200 text-neutral-500 dark:border-neutral-800",
};

function Trend({ values, low, high }: { values: number[]; low?: number; high?: number }) {
  const w = 100, h = 32, lo = Math.min(...values, low ?? Infinity), hi = Math.max(...values, high ?? -Infinity);
  const y = (v: number) => h - 3 - ((v - lo) / (hi - lo || 1)) * (h - 6);
  const x = (i: number) => (values.length < 2 ? w / 2 : (i / (values.length - 1)) * w);
  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="h-8 w-full" aria-hidden>
      {low != null && high != null && <rect x="0" width={w} y={y(high)} height={Math.max(1, y(low) - y(high))} className="fill-taken/10" />}
      <path d={values.map((v, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ")} fill="none" stroke="#466A8D" strokeWidth="1.6" vectorEffect="non-scaling-stroke" />
      {values.map((v, i) => <circle key={i} cx={x(i)} cy={y(v)} r="1.8" fill="#466A8D" />)}
    </svg>
  );
}

export function Biomarkers({ clientId }: { clientId: string }) {
  const t = useT();
  const list = useLiveQuery(() => db.biomarkers.where("clientId").equals(clientId).toArray(), [clientId], []);
  const [open, setOpen] = useState(false);
  const [marker, setMarker] = useState(BIOMARKERS[0].key), [value, setValue] = useState(""), [date, setDate] = useState(toLocalDay(new Date()));
  const groups = byMarker(list);
  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    const v = Number(value.replace(",", "."));
    if (!Number.isFinite(v)) return toast.error(t("Enter a number"));
    const ref = markerRef(marker)!;
    await saveBiomarker({ id: uid(), clientId, marker, value: v, unit: ref.unit, date, updatedAt: Date.now(), deletedAt: null });
    setValue(""); setOpen(false);
    toast.success(t("Saved"));
  };
  return (
    <div className="card mt-3 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-center gap-2 text-sm font-medium"><FlaskConical className="size-4 text-neutral-400" /> {t("Biomarkers")}</p>
        <button onClick={() => setOpen(!open)} className="btn"><Plus className="size-4" /> {t("Add lab result")}</button>
      </div>
      {open && (
        <form onSubmit={add} className="mt-4 grid gap-3 rounded-xl border border-neutral-200 p-3 sm:grid-cols-[1fr_120px_150px_auto] sm:items-end dark:border-neutral-800">
          <label><span className="label">{t("Marker")}</span><select value={marker} onChange={(e) => setMarker(e.target.value)} className="input">{BIOMARKERS.map((m) => <option key={m.key} value={m.key}>{t(m.name)} ({m.unit})</option>)}</select></label>
          <label><span className="label">{t("Value")} ({markerRef(marker)?.unit})</span><input inputMode="decimal" required value={value} onChange={(e) => setValue(e.target.value)} className="input tabular" /></label>
          <label><span className="label">{t("Date")}</span><input type="date" required value={date} onChange={(e) => setDate(e.target.value)} className="input" /></label>
          <button type="submit" className="btn-primary">{t("Save")}</button>
        </form>
      )}
      {groups.length === 0 ? <p className="mt-3 text-sm text-neutral-500">{t("Log blood test results to see trends against typical ranges.")}</p> : (
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {groups.map(({ marker: k, latest, series }) => {
            const ref = markerRef(k), st = markerStatus(latest);
            return (
              <li key={k} className="rounded-xl border border-neutral-200 p-3 dark:border-neutral-800">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0"><p className="truncate text-sm font-medium">{t(ref?.name ?? k)}</p><p className="text-xs text-neutral-500">{new Date(latest.date + "T00:00").toLocaleDateString(locale())}{ref ? ` · ${t("typical")} ${ref.low}–${ref.high}` : ""}</p></div>
                  <span className={`chip shrink-0 ${ST[st]}`}>{t(st === "unknown" ? "custom" : st)}</span>
                </div>
                <p className="mt-2 text-2xl font-semibold tracking-tight tabular">{latest.value}<span className="ml-1 text-xs font-normal text-neutral-500">{latest.unit}</span></p>
                <Trend values={series.map((s) => s.value)} low={ref?.low} high={ref?.high} />
                <div className="mt-1 flex items-center justify-between gap-2">
                  <p className="min-w-0 text-xs text-neutral-500">{ref && t(ref.hint)}</p>
                  <button aria-label={t("Delete")} onClick={() => deleteBiomarker(latest.id).then(() => toast(t("Deleted"), { action: { label: t("Undo"), onClick: () => deleteBiomarker(latest.id, false) } }))} className="flex size-11 shrink-0 items-center justify-center rounded-lg text-neutral-400 hover:text-missed"><Trash2 className="size-4" /></button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <p className="mt-3 text-[12px] text-neutral-500">{t("Ranges vary by lab, age and sex. General information, not medical advice.")}</p>
    </div>
  );
}
