"use client";
import { motion } from "motion/react";
import { toast } from "sonner";
import { AlertTriangle, Check, Flame, Package, Plus, Sparkles, X } from "lucide-react";
import { useNow, useOak } from "@/lib/use-oak";
import { markDose } from "@/lib/db";
import { toLocalDay } from "@/lib/domain/day";
import { dayStats, daysOfStockLeft, dosesForDay, slotOf, streak, type Dose } from "@/lib/domain/schedule";
import { completionRate, doseState } from "@/lib/domain/timing";
import { analyze } from "@/lib/domain/knowledge";
import type { Slot } from "@/lib/domain/types";
import { Empty, PageHeader, Ring, Sparkline, StatusBadge } from "./ui";
import { openRoutine } from "./routine-drawer";

const SLOTS: { id: Slot; label: string; hint: string }[] = [
  { id: "morning", label: "Morning", hint: "before 11:00" },
  { id: "midday", label: "Midday", hint: "11:00–15:00" },
  { id: "evening", label: "Evening", hint: "15:00–20:00" },
  { id: "bedtime", label: "Bedtime", hint: "after 20:00" },
];

function fmtIn(ms: number) {
  const m = Math.round(ms / 60000);
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  return `${h}h ${m % 60}m`;
}

export async function logDose(d: Dose, status: "Taken" | "Skipped") {
  const prev = await markDose(d.routine, d.at, status);
  toast(`${d.routine.name} · ${status}`, {
    action: { label: "Undo", onClick: () => markDose(d.routine, d.at, prev) },
  });
}

export function Dashboard() {
  const { ready, profile, routines, intakes } = useOak();
  const now = useNow();
  const today = toLocalDay(new Date(now));
  const doses = dosesForDay(routines, intakes, today);
  const taken = doses.filter((d) => d.intake?.status === "Taken").length;
  const skipped = doses.filter((d) => d.intake?.status === "Skipped").length;
  const week = dayStats(routines, intakes, today, 7);
  const month = dayStats(routines, intakes, today, 30);
  const wTaken = week.reduce((a, s) => a + s.taken, 0), wSkip = week.reduce((a, s) => a + s.skipped, 0);
  const next = doses.find((d) => !d.intake && doseState(undefined, d.at, now) !== "missed");
  const lowStock = routines.filter((r) => { const d = daysOfStockLeft(r); return d != null && d <= 7; });
  const insights = analyze(routines);
  const hour = new Date(now).getHours();
  const greet = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  if (!ready) return <div className="h-96 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900" />;

  return (
    <div className="animate-fade-in">
      <PageHeader title={`${greet}, ${profile?.name}`} sub={new Date(now).toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}>
        <button onClick={() => openRoutine(null)} className="btn-primary"><Plus className="size-4" /> New routine</button>
      </PageHeader>

      {routines.length === 0 ? (
        <Empty title="Build your stack" sub="Add the supplements, vitamins or meds you take. OAK schedules them by time slot and cycle, then tracks adherence."
          action={<button onClick={() => openRoutine(null)} className="btn-primary"><Plus className="size-4" /> Add your first routine</button>} />
      ) : (
        <>
          <div className="grid gap-3 md:grid-cols-4">
            <div className="card flex items-center gap-4 p-5 md:col-span-1 md:flex-col md:items-start">
              <Ring value={doses.length ? taken / doses.length : null} label="today" />
              <div><p className="text-sm font-medium">{taken} of {doses.length} taken</p><p className="text-xs text-neutral-500">{skipped} skipped · {doses.length - taken - skipped} left</p></div>
            </div>
            <div className="card relative overflow-hidden p-5 md:col-span-2">
              <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">Next dose</p>
              {next ? (
                <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
                  <div>
                    <p className="text-2xl font-semibold tracking-tight">{next.routine.name}</p>
                    <p className="mt-1 text-sm text-neutral-500">{next.routine.dose || "1 dose"} · <span className="font-mono">{next.routine.time}</span> · {next.at > now ? `in ${fmtIn(next.at - now)}` : "due now"}</p>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => logDose(next, "Skipped")} className="btn">Skip</button>
                    <button onClick={() => logDose(next, "Taken")} className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-taken px-4 text-sm font-medium text-white shadow-sm shadow-taken/30 transition hover:brightness-110 active:scale-[0.98]"><Check className="size-4" /> Take now</button>
                  </div>
                </div>
              ) : <p className="mt-3 text-2xl font-semibold tracking-tight text-taken">All done for today ✓</p>}
              <div className="pointer-events-none absolute -right-10 -top-10 size-40 rounded-full bg-taken/5" />
            </div>
            <div className="card p-5">
              <div className="flex items-center justify-between"><p className="text-xs font-medium uppercase tracking-wide text-neutral-500">Streak</p><Flame className="size-4 text-skipped" /></div>
              <p className="mt-2 text-3xl font-semibold tracking-tight tabular">{streak(month)}<span className="ml-1 text-sm font-normal text-neutral-500">days</span></p>
              <p className="text-xs text-neutral-500">Perfect days in a row</p>
            </div>
          </div>

          <div className="mt-3 grid gap-3 md:grid-cols-3">
            <div className="card p-5 md:col-span-2">
              <div className="flex items-baseline justify-between">
                <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">7-day adherence</p>
                <p className="text-lg font-semibold tabular">{(() => { const r = completionRate(wTaken, wSkip); return r == null ? "—" : `${Math.round(r * 100)}%`; })()}</p>
              </div>
              <div className="mt-3"><Sparkline values={week.map((s) => (s.planned ? s.taken / s.planned : null))} /></div>
              <div className="mt-1 flex justify-between font-mono text-[10px] text-neutral-400">{week.map((s) => <span key={s.day}>{new Date(s.day + "T00:00").toLocaleDateString(undefined, { weekday: "narrow" })}</span>)}</div>
            </div>
            <div className="card p-5">
              <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">Stack health</p>
              <ul className="mt-3 space-y-2 text-sm">
                {lowStock.map((r) => <li key={r.id} className="flex items-center gap-2"><Package className="size-4 text-skipped" /> <span className="truncate">{r.name}</span><span className="ml-auto text-xs text-skipped tabular">{daysOfStockLeft(r)}d left</span></li>)}
                {insights.slice(0, 3).map((i) => (
                  <li key={i.title + i.level} className="flex items-start gap-2">
                    {i.level === "clash" ? <AlertTriangle className="mt-0.5 size-4 shrink-0 text-missed" /> : <Sparkles className="mt-0.5 size-4 shrink-0 text-due" />}
                    <span className="min-w-0"><span className="font-medium">{i.title}</span><span className="block text-xs text-neutral-500">{i.detail}</span></span>
                  </li>
                ))}
                {lowStock.length === 0 && insights.length === 0 && <li className="text-neutral-500">No clashes, stock is fine. 🌿</li>}
              </ul>
            </div>
          </div>

          <h2 className="mb-3 mt-8 text-sm font-semibold">Today’s timeline</h2>
          <div className="grid gap-3 md:grid-cols-2">
            {SLOTS.map((slot) => {
              const list = doses.filter((d) => slotOf(d.routine.time) === slot.id);
              return (
                <div key={slot.id} className="card">
                  <div className="flex items-center justify-between border-b border-neutral-100 px-4 py-2.5 dark:border-neutral-800">
                    <span className="text-sm font-medium">{slot.label}</span><span className="text-xs text-neutral-400">{slot.hint}</span>
                  </div>
                  {list.length === 0 ? <p className="px-4 py-4 text-sm text-neutral-400">Nothing scheduled</p> : (
                    <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
                      {list.map((d) => {
                        const st = doseState(d.intake?.status, d.at, now);
                        return (
                          <motion.li layout key={d.key} className="flex items-center gap-3 px-4 py-3">
                            <span className="w-11 font-mono text-xs text-neutral-500">{d.routine.time}</span>
                            <button onClick={() => openRoutine(d.routine.id)} className="min-w-0 flex-1 text-left">
                              <p className={`truncate text-sm font-medium ${st === "taken" ? "text-neutral-400 line-through decoration-neutral-300" : ""}`}>{d.routine.name}</p>
                              <p className="truncate text-xs text-neutral-500">{d.routine.dose || "—"}</p>
                            </button>
                            <StatusBadge state={st} />
                            <div className="flex gap-1">
                              <button aria-label={`Skip ${d.routine.name}`} onClick={() => logDose(d, "Skipped")} className={`flex size-8 items-center justify-center rounded-lg border transition ${st === "skipped" ? "border-skipped bg-skipped text-white" : "border-neutral-200 text-neutral-400 hover:border-skipped hover:text-skipped dark:border-neutral-800"}`}><X className="size-4" /></button>
                              <button aria-label={`Take ${d.routine.name}`} onClick={() => logDose(d, "Taken")} className={`flex size-8 items-center justify-center rounded-lg border transition ${st === "taken" ? "border-taken bg-taken text-white" : "border-neutral-200 text-neutral-400 hover:border-taken hover:text-taken dark:border-neutral-800"}`}><Check className="size-4" /></button>
                            </div>
                          </motion.li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}