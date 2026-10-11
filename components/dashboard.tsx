"use client";
import { motion } from "motion/react";
import { toast } from "sonner";
import { AlertTriangle, Check, Flame, Hourglass, Package, Plus, ShoppingCart, Sparkles, X } from "lucide-react";
import { haptic } from "@/lib/haptics";
import { inventoryAlert } from "@/lib/domain/stack";
import { useNow, useOak } from "@/lib/use-oak";
import { markDose } from "@/lib/db";
import { toLocalDay } from "@/lib/domain/day";
import { dayStats, dosesForDay, slotOf, streak, type Dose } from "@/lib/domain/schedule";
import { completionRate, doseState } from "@/lib/domain/timing";
import { analyze } from "@/lib/domain/knowledge";
import type { Slot } from "@/lib/domain/types";
import { Empty, PageHeader, Ring, STATE_STYLE, Sparkline, StatusBadge } from "./ui";
import { openRoutine } from "./routine-drawer";
import { t as tt, useT, locale } from "@/lib/i18n";

const SLOTS: { id: Slot; label: string; hint: string }[] = [
  { id: "morning", label: "Morning", hint: "before 11:00" },
  { id: "midday", label: "Midday", hint: "11:00–15:00" },
  { id: "evening", label: "Evening", hint: "15:00–20:00" },
  { id: "bedtime", label: "Bedtime", hint: "after 20:00" },
];
const TAKE = "inline-flex h-11 items-center gap-1.5 rounded-lg bg-taken px-4 text-sm font-medium text-white shadow-sm shadow-taken/30 transition hover:brightness-110 active:scale-[0.98] md:h-9";

function fmtIn(ms: number) {
  const m = Math.round(ms / 60000);
  if (m < 60) return tt("{m}m", { m });
  const h = Math.floor(m / 60);
  return tt("{h}h {m}m", { h, m: m % 60 });
}

export async function logDose(d: Dose, status: "Taken" | "Skipped") {
  const prev = await markDose(d.routine, d.at, status);
  haptic(status === "Taken" ? "success" : "warn");
  toast(`${d.routine.name} · ${tt(status)}`, {
    action: { label: tt("Undo"), onClick: () => markDose(d.routine, d.at, prev) },
  });
}

export function Dashboard() {
  const { ready, profile, routines, intakes } = useOak();
  const t = useT();
  const now = useNow();
  const today = toLocalDay(new Date(now));
  const doses = dosesForDay(routines, intakes, today);
  const taken = doses.filter((d) => d.intake?.status === "Taken").length;
  const skipped = doses.filter((d) => d.intake?.status === "Skipped").length;
  const week = dayStats(routines, intakes, today, 7);
  const month = dayStats(routines, intakes, today, 30);
  const wTaken = week.reduce((a, s) => a + s.taken, 0), wSkip = week.reduce((a, s) => a + s.skipped, 0);
  const next = doses.find((d) => !d.intake && doseState(undefined, d.at, now) !== "missed");
  const lowStock = routines.map((r) => ({ r, inv: inventoryAlert(r, today) })).filter(({ inv }) => inv.low || inv.expiring || inv.expired);
  const insights = analyze(routines);
  const hour = new Date(now).getHours();
  const greet = t(hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");

  if (!ready) return <div className="h-96 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900" />;

  return (
    <div className="animate-fade-in">
      <PageHeader title={`${greet}, ${profile?.name}`} sub={new Date(now).toLocaleDateString(locale(), { weekday: "long", month: "long", day: "numeric" })}>
        <button onClick={() => openRoutine(null)} className="btn-primary hidden md:inline-flex"><Plus className="size-4" /> {t("New routine")}</button>
      </PageHeader>

      {routines.length === 0 ? (
        <Empty title={t("Build your stack")} sub={t("Add the supplements, vitamins or meds you take. OAK schedules them by time slot and cycle, then tracks adherence.")}
          action={<button onClick={() => openRoutine(null)} className="btn-primary"><Plus className="size-4" /> {t("Add your first routine")}</button>} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
            <div className="card flex items-center gap-4 p-5 md:col-span-1 md:flex-col md:items-start">
              <Ring value={doses.length ? taken / doses.length : null} label={t("today")} />
              <div className="min-w-0"><p className="text-sm font-medium">{t("{a} of {b} taken", { a: taken, b: doses.length })}</p><p className="text-xs text-neutral-500">{t("{s} skipped · {l} left", { s: skipped, l: doses.length - taken - skipped })}</p></div>
            </div>
            <div className="card relative overflow-hidden p-5 md:col-span-2">
              <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">{t("Next dose")}</p>
              {next ? (
                <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-2xl font-semibold tracking-tight [overflow-wrap:anywhere]">{next.routine.name}</p>
                    <p className="mt-1 text-sm text-neutral-500">{next.routine.dose || t("1 dose")} · <span className="font-mono">{next.routine.time}</span> · {next.at > now ? t("in {d}", { d: fmtIn(next.at - now) }) : t("due now")}</p>
                  </div>
                  <div className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto">
                    <button onClick={() => logDose(next, "Skipped")} className="btn">{t("Skip")}</button>
                    <button onClick={() => logDose(next, "Taken")} className={`${TAKE} justify-center`}><Check className="size-4" /> {t("Take now")}</button>
                  </div>
                </div>
              ) : taken + skipped === doses.length ? <p className="mt-3 text-2xl font-semibold tracking-tight text-taken">{t("All done for today ✓")}</p>
                : <div className="mt-3"><p className="text-2xl font-semibold tracking-tight">{t("No more doses due today")}</p><p className="mt-1 text-sm text-missed">{t("{n} missed: tap ✓ in the timeline if you took them", { n: doses.length - taken - skipped })}</p></div>}
              <div className="pointer-events-none absolute -right-10 -top-10 size-40 rounded-full bg-taken/5" />
            </div>
            <div className="card p-5">
              <div className="flex items-center justify-between"><p className="text-xs font-medium uppercase tracking-wide text-neutral-500">{t("Streak")}</p><Flame className="size-4 text-skipped" /></div>
              <p className="mt-2 text-3xl font-semibold tracking-tight tabular">{streak(month)}<span className="ml-1 text-sm font-normal text-neutral-500">{t("days")}</span></p>
              <p className="text-xs text-neutral-500">{t("Perfect days in a row")}</p>
            </div>
          </div>

          <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-3">
            <div className="card p-5 md:col-span-2">
              <div className="flex items-baseline justify-between">
                <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">{t("7-day adherence")}</p>
                <p className="text-lg font-semibold tabular">{(() => { const r = completionRate(wTaken, wSkip); return r == null ? "—" : `${Math.round(r * 100)}%`; })()}</p>
              </div>
              <div className="mt-3"><Sparkline values={week.map((s) => (s.planned ? s.taken / s.planned : null))} /></div>
              <div className="mt-1 flex justify-between font-mono text-xs text-neutral-500">{week.map((s) => <span key={s.day}>{new Date(s.day + "T00:00").toLocaleDateString(locale(), { weekday: "narrow" })}</span>)}</div>
            </div>
            <div className="card p-5">
              <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">{t("Stack health")}</p>
              <ul className="mt-3 space-y-2 text-sm">
                {lowStock.map(({ r, inv }) => (
                  <li key={r.id} className="flex items-center gap-2">
                    {inv.low ? <Package className="size-4 shrink-0 text-skipped" /> : <Hourglass className={`size-4 shrink-0 ${inv.expired ? "text-missed" : "text-skipped"}`} />}
                    <span className="truncate">{r.name}</span>
                    <span className={`ml-auto shrink-0 text-xs tabular ${inv.expired ? "text-missed" : "text-skipped"}`}>{[inv.low ? t("{d}d left", { d: inv.daysLeft ?? 0 }) : "", inv.expired ? t("Expired") : inv.expiring ? t("Expires in {n}d", { n: inv.expiryDays ?? 0 }) : ""].filter(Boolean).join(" · ")}</span>
                    {inv.reorderUrl && <a href={inv.reorderUrl} target="_blank" rel="noopener noreferrer" className="flex min-h-11 shrink-0 items-center gap-1 rounded-md px-2 text-xs font-medium text-taken hover:bg-taken-soft md:min-h-0 md:py-1"><ShoppingCart className="size-3.5" /> {t("Reorder")}</a>}
                  </li>
                ))}
                {insights.slice(0, 3).map((i) => (
                  <li key={i.title + i.level} className="flex items-start gap-2">
                    {i.level === "clash" ? <AlertTriangle className="mt-0.5 size-4 shrink-0 text-missed" /> : <Sparkles className="mt-0.5 size-4 shrink-0 text-due" />}
                    <span className="min-w-0"><span className="font-medium [overflow-wrap:anywhere]">{i.title}</span><span className="block text-xs text-neutral-500">{t(i.detail)}</span></span>
                  </li>
                ))}
                {lowStock.length === 0 && insights.length === 0 && <li className="text-neutral-500">{t("No clashes, stock is fine. 🌿")}</li>}
              </ul>
            </div>
          </div>

          <h2 className="mb-3 mt-8 text-sm font-semibold">{t("Today’s timeline")}</h2>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {SLOTS.map((slot) => {
              const list = doses.filter((d) => slotOf(d.routine.time) === slot.id);
              return (
                <div key={slot.id} className="card">
                  <div className="flex items-center justify-between border-b border-neutral-100 px-4 py-2.5 dark:border-neutral-800">
                    <span className="text-sm font-medium">{t(slot.label)}</span><span className="text-xs text-neutral-500">{t(slot.hint)}</span>
                  </div>
                  {list.length === 0 ? <p className="px-4 py-4 text-sm text-neutral-500">{t("Nothing scheduled")}</p> : (
                    <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
                      {list.map((d) => {
                        const st = doseState(d.intake?.status, d.at, now);
                        return (
                          <motion.li layout key={d.key} className="flex items-center gap-2 px-3 py-2.5 sm:gap-3 sm:px-4">
                            <span className="w-10 shrink-0 font-mono text-xs text-neutral-500">{d.routine.time}</span>
                            <button onClick={() => openRoutine(d.routine.id)} className="min-h-11 min-w-0 flex-1 text-left">
                              <p className={`flex items-center gap-1.5 truncate text-sm font-medium ${st === "taken" ? "text-neutral-400 line-through decoration-neutral-300" : ""}`}><span className={`size-2 shrink-0 rounded-full min-[400px]:hidden ${STATE_STYLE[st].dot}`} aria-hidden /><span className="truncate">{d.routine.name}</span></p>
                              <p className="truncate text-xs text-neutral-500">{d.routine.dose || "—"}<span className="min-[400px]:hidden"> · {t(STATE_STYLE[st].label)}</span></p>
                            </button>
                            <span className="hidden min-[400px]:inline-flex"><StatusBadge state={st} /></span>
                            <div className="flex shrink-0 gap-1">
                              <button aria-label={t("Skip {name}", { name: d.routine.name })} onClick={() => logDose(d, "Skipped")} className={`flex size-11 items-center md:size-8 justify-center rounded-lg border transition ${st === "skipped" ? "border-skipped bg-skipped text-white" : "border-neutral-200 text-neutral-400 hover:border-skipped hover:text-skipped dark:border-neutral-800"}`}><X className="size-4" /></button>
                              <button aria-label={t("Take {name}", { name: d.routine.name })} onClick={() => logDose(d, "Taken")} className={`flex size-11 items-center md:size-8 justify-center rounded-lg border transition ${st === "taken" ? "border-taken bg-taken text-white" : "border-neutral-200 text-neutral-400 hover:border-taken hover:text-taken dark:border-neutral-800"}`}><Check className="size-4" /></button>
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