"use client";
import { useMemo, useState } from "react";
import { useOak } from "@/lib/use-oak";
import { correlationLabel, markerAdherence } from "@/lib/domain/stack";
import type { Biomarker } from "@/lib/domain/types";
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

/** Adherence of one routine (30 days before each lab draw) against the marker value over time. */
function Correlation({ groups }: { groups: { marker: string; series: Biomarker[] }[] }) {
  const t = useT();
  const { routines, intakes } = useOak();
  const usable = groups.filter((g) => g.series.length >= 2);
  const [mk, setMk] = useState<string>("");
  const g = usable.find((x) => x.marker === mk) ?? usable[0];
  const ref = g ? markerRef(g.marker) : undefined;
  const guess = routines.find((r) => ref?.supplements?.some((s) => r.name.toLowerCase().includes(s)));
  const [rid, setRid] = useState<string>("");
  const routine = routines.find((r) => r.id === rid) ?? guess ?? routines[0];
  const data = useMemo(() => (g && routine ? markerAdherence(g.series, routine, intakes, 30) : null), [g, routine, intakes]);
  if (!usable.length || !routines.length || !g || !routine || !data) return null;

  const W = 320, H = 120, P = 8;
  const ts = data.points.map((p) => new Date(p.date + "T00:00").getTime());
  const t0 = Math.min(...ts), t1 = Math.max(...ts);
  const vals = data.points.map((p) => p.value), lo = Math.min(...vals, ref?.low ?? Infinity), hi = Math.max(...vals, ref?.high ?? -Infinity);
  const x = (i: number) => P + ((ts[i] - t0) / (t1 - t0 || 1)) * (W - 2 * P);
  const yv = (v: number) => H - P - ((v - lo) / (hi - lo || 1)) * (H - 2 * P);
  const ya = (a: number) => H - P - a * (H - 2 * P);
  const path = (pts: [number, number][]) => pts.map(([px, py], i) => `${i ? "L" : "M"}${px.toFixed(1)},${py.toFixed(1)}`).join(" ");
  const adh = data.points.map((p, i) => [p.adherence, i] as const).filter(([a]) => a != null).map(([a, i]) => [x(i), ya(a!)] as [number, number]);
  const label = correlationLabel(data.r);

  return (
    <div className="mt-4 rounded-xl border border-neutral-200 p-3 dark:border-neutral-800">
      <p className="text-sm font-medium">{t("Adherence vs marker")}</p>
      <div className="mt-2 grid gap-2 sm:grid-cols-2">
        <select aria-label={t("Marker")} value={g.marker} onChange={(e) => setMk(e.target.value)} className="input">{usable.map((u) => <option key={u.marker} value={u.marker}>{t(markerRef(u.marker)?.name ?? u.marker)}</option>)}</select>
        <select aria-label={t("Routine")} value={routine.id} onChange={(e) => setRid(e.target.value)} className="input">{routines.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}</select>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} className="mt-3 h-32 w-full" role="img" aria-label={t("Adherence vs marker")}>
        {[0, 0.5, 1].map((a) => <line key={a} x1={P} x2={W - P} y1={ya(a)} y2={ya(a)} className="stroke-neutral-200 dark:stroke-neutral-800" strokeDasharray="2 3" />)}
        {adh.length > 0 && <path d={path(adh)} fill="none" stroke="#1F6B4D" strokeWidth="2" />}
        {adh.map(([px, py], i) => <circle key={`a${i}`} cx={px} cy={py} r="2.5" fill="#1F6B4D" />)}
        <path d={path(data.points.map((p, i) => [x(i), yv(p.value)]))} fill="none" stroke="#466A8D" strokeWidth="2" strokeDasharray="5 3" />
        {data.points.map((p, i) => <circle key={`v${i}`} cx={x(i)} cy={yv(p.value)} r="2.5" fill="#466A8D" />)}
      </svg>
      <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-neutral-500">
        <span className="flex items-center gap-1.5"><span className="h-0.5 w-4 bg-[#1F6B4D]" /> {t("30-day adherence")} (0–100%)</span>
        <span className="flex items-center gap-1.5"><span className="h-0.5 w-4 border-t-2 border-dashed border-[#466A8D]" /> {t(ref?.name ?? g.marker)} ({ref?.unit ?? g.series[0].unit})</span>
        <span className="ml-auto font-medium text-neutral-700 dark:text-neutral-300">{data.r == null ? t("Need 3+ results with doses logged") : `r = ${data.r.toFixed(2)} · ${t(label)} ${t(data.r >= 0 ? "positive" : "negative")}`}</span>
      </div>
      <p className="mt-1 text-[12px] text-neutral-500">{t("Correlation is not causation. Diet, season and lab differences matter too.")}</p>
    </div>
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
      <Correlation groups={groups} />
      <p className="mt-3 text-[12px] text-neutral-500">{t("Ranges vary by lab, age and sex. General information, not medical advice.")}</p>
    </div>
  );
}
