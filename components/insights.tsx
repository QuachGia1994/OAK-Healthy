"use client";
import { useMemo } from "react";
import { AlertTriangle, Clock, Sparkles } from "lucide-react";
import { useOak } from "@/lib/use-oak";
import { addDays, parseLocalDay, toLocalDay } from "@/lib/domain/day";
import { dayStats, dosesForDay } from "@/lib/domain/schedule";
import { completionRate, isLateTaken } from "@/lib/domain/timing";
import { analyze } from "@/lib/domain/knowledge";
import { Empty, PageHeader } from "./ui";

const shade = (rate: number | null, planned: number) => {
  if (!planned) return "bg-neutral-100 dark:bg-neutral-800/60";
  if (rate == null) return "bg-neutral-200 dark:bg-neutral-700";
  if (rate >= 0.99) return "bg-[#1F6B4D]";
  if (rate >= 0.75) return "bg-[#1F6B4D]/70";
  if (rate >= 0.5) return "bg-[#1F6B4D]/45";
  if (rate > 0) return "bg-[#1F6B4D]/25";
  return "bg-missed/40";
};

export function Insights() {
  const { routines, intakes, intakeList } = useOak();
  const today = toLocalDay(new Date());
  const weeks = 53;
  const start = useMemo(() => { const d = parseLocalDay(today); return addDays(today, -(weeks - 1) * 7 - d.getDay()); }, [today]);
  const total = useMemo(() => {
    const n = Math.round((parseLocalDay(today).getTime() - parseLocalDay(start).getTime()) / 864e5) + 1;
    return dayStats(routines, intakes, today, n);
  }, [routines, intakes, today, start]);
  const live = intakeList.filter((i) => !i.deletedAt);
  const taken = live.filter((i) => i.status === "Taken"), skipped = live.filter((i) => i.status === "Skipped");
  const rate = completionRate(taken.length, skipped.length);
  const late = taken.filter((i) => isLateTaken(i.status, i.scheduledAt, i.updatedAt)).length;
  const hours = Array.from({ length: 24 }, (_, h) => taken.filter((i) => new Date(i.updatedAt).getHours() === h).length);
  const maxH = Math.max(1, ...hours);
  const last30 = total.slice(-30);
  const perRoutine = routines.map((r) => {
    let t = 0, p = 0;
    for (let i = 0; i < 30; i++) { const day = addDays(today, -i); for (const d of dosesForDay([r], intakes, day)) { p++; if (d.intake?.status === "Taken") t++; } }
    return { r, t, p };
  }).filter((x) => x.p > 0).sort((a, b) => a.t / a.p - b.t / b.p);
  const insights = analyze(routines);
  const byId = new Map(routines.map((r) => [r.id, r]));
  const history = [...live].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 25);

  if (!routines.length) return (<div><PageHeader title="Insights" /><Empty title="Nothing to analyze yet" sub="Add routines and log a few doses. Your heatmap, timing patterns and interaction checks appear here." /></div>);

  return (
    <div className="animate-fade-in">
      <PageHeader title="Insights" sub="Adherence, timing patterns and interaction checks" />
      <div className="grid gap-3 sm:grid-cols-4">
        {[
          ["All-time adherence", rate == null ? "—" : `${Math.round(rate * 100)}%`],
          ["Doses taken", String(taken.length)],
          ["Skipped", String(skipped.length)],
          ["Taken late (>20m)", String(late)],
        ].map(([k, v]) => <div key={k} className="card p-4"><p className="text-xs text-neutral-500">{k}</p><p className="mt-1 text-2xl font-semibold tracking-tight tabular">{v}</p></div>)}
      </div>

      <div className="card mt-3 p-5">
        <div className="flex items-center justify-between"><p className="text-sm font-medium">Last 12 months</p>
          <div className="flex items-center gap-1 text-[10px] text-neutral-400">Less {[0.1, 0.3, 0.6, 0.8, 1].map((v) => <span key={v} className={`size-2.5 rounded-[3px] ${shade(v, 1)}`} />)} More</div></div>
        <div className="mt-4 overflow-x-auto pb-1">
          <div className="grid w-max grid-flow-col grid-rows-7 gap-[3px]">
            {total.map((s) => <div key={s.day} title={`${s.day}: ${s.taken}/${s.planned} taken`} className={`size-[11px] rounded-[3px] ${shade(s.planned ? s.taken / s.planned : null, s.planned)}`} />)}
          </div>
        </div>
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <div className="card p-5">
          <p className="flex items-center gap-2 text-sm font-medium"><Clock className="size-4 text-neutral-400" /> When you actually take doses</p>
          <div className="mt-4 flex h-28 items-end gap-[3px]">
            {hours.map((n, h) => <div key={h} title={`${h}:00 · ${n}`} className="flex-1 rounded-t bg-due/80" style={{ height: `${Math.max(2, (n / maxH) * 100)}%`, opacity: n ? 1 : 0.15 }} />)}
          </div>
          <div className="mt-1 flex justify-between font-mono text-[10px] text-neutral-400"><span>0h</span><span>6h</span><span>12h</span><span>18h</span><span>23h</span></div>
        </div>
        <div className="card p-5">
          <p className="text-sm font-medium">30-day adherence by routine</p>
          <ul className="mt-3 space-y-2.5">
            {perRoutine.slice(0, 8).map(({ r, t, p }) => (
              <li key={r.id} className="text-sm">
                <div className="flex justify-between"><span className="truncate">{r.name}</span><span className="tabular text-neutral-500">{t}/{p}</span></div>
                <div className="mt-1 h-1.5 rounded-full bg-neutral-100 dark:bg-neutral-800"><div className="h-full rounded-full bg-taken" style={{ width: `${(t / p) * 100}%` }} /></div>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <div className="card p-5">
          <p className="text-sm font-medium">Interaction & timing check</p>
          {insights.length === 0 ? <p className="mt-3 text-sm text-neutral-500">No clashes found in your current stack.</p> : (
            <ul className="mt-3 space-y-3">
              {insights.map((i) => (
                <li key={i.level + i.title} className="flex gap-2.5 text-sm">
                  {i.level === "clash" ? <AlertTriangle className="mt-0.5 size-4 shrink-0 text-missed" /> : <Sparkles className={`mt-0.5 size-4 shrink-0 ${i.level === "synergy" ? "text-taken" : "text-due"}`} />}
                  <div><p className="font-medium">{i.title} <span className="ml-1 text-xs font-normal capitalize text-neutral-400">{i.level}</span></p><p className="text-neutral-500">{i.detail}</p></div>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-4 text-[11px] text-neutral-400">General information, not medical advice.</p>
        </div>
        <div className="card overflow-hidden">
          <p className="border-b border-neutral-100 px-5 py-3 text-sm font-medium dark:border-neutral-800">History</p>
          <ul className="max-h-80 divide-y divide-neutral-100 overflow-y-auto dark:divide-neutral-800">
            {history.map((i) => (
              <li key={i.key} className="flex items-center gap-3 px-5 py-2.5 text-sm">
                <span className={`size-2 rounded-full ${i.status === "Taken" ? "bg-taken" : "bg-skipped"}`} />
                <span className="truncate">{byId.get(i.supplementId)?.name ?? "Deleted routine"}</span>
                <span className="ml-auto font-mono text-xs text-neutral-400">{new Date(i.scheduledAt).toLocaleDateString(undefined, { month: "short", day: "numeric" })} {new Date(i.scheduledAt).toTimeString().slice(0, 5)}</span>
              </li>
            ))}
            {history.length === 0 && <li className="px-5 py-6 text-sm text-neutral-500">No doses logged yet.</li>}
          </ul>
        </div>
      </div>
      <p className="mt-6 text-xs text-neutral-400">Last 30 days: {last30.reduce((a, s) => a + s.taken, 0)} taken of {last30.reduce((a, s) => a + s.planned, 0)} planned.</p>
    </div>
  );
}