"use client";
import { useT } from "@/lib/i18n";
import { useCallback, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { AlertTriangle, Copy, Pause, Play, Plus, Search, Share2 } from "lucide-react";
import { useHotkey, useOak } from "@/lib/use-oak";
import { saveRoutine } from "@/lib/db";
import { cycleLabel, cycleStatus, isExpired } from "@/lib/domain/cycle";
import { toLocalDay } from "@/lib/domain/day";
import { daysOfStockLeft } from "@/lib/domain/schedule";
import { analyze } from "@/lib/domain/knowledge";
import { sealShare } from "@/lib/crypto";
import type { Category, Routine } from "@/lib/domain/types";
import { Empty, PageHeader } from "./ui";
import { CATEGORIES, openRoutine, duplicateRoutine } from "./routine-drawer";

type StatusFilter = "all" | "active" | "off" | "paused";

export function routineStatus(r: Routine, today: string): "active" | "off" | "paused" | "ended" {
  if (r.paused) return "paused";
  if (isExpired(r.startDate, r.cycle, today)) return "ended";
  return cycleStatus(r.startDate, r.cycle, today) === "ON" ? "active" : "off";
}
const STATUS_CLS = {
  active: "border-taken/20 bg-taken-soft text-taken",
  off: "border-due/20 bg-due-soft text-due",
  paused: "border-skipped/20 bg-skipped-soft text-skipped",
  ended: "border-neutral-200 bg-neutral-100 text-neutral-500 dark:border-neutral-800 dark:bg-neutral-800",
};
const STATUS_LABEL = { active: "Active", off: "Off-cycle", paused: "Paused", ended: "Ended" };

export async function shareStack(name: string, routines: Routine[]) {
  const payload = { v: 1, name, createdAt: Date.now(), items: routines.map(({ name, dose, time, cycle, category, note }) => ({ name, dose, time, cycle, category, note })) };
  const { blob, k } = await sealShare(payload);
  const res = await fetch("/api/share", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ blob }) });
  if (!res.ok) throw new Error(`Share failed (${res.status})`);
  const { id } = (await res.json()) as { id: string };
  const url = `${location.origin}/s/${id}#${k}`;
  await navigator.clipboard?.writeText(url).catch(() => {});
  return url;
}

export function StackManager() {
  const { routines, profile } = useOak();
  const t = useT();
  const [q, setQ] = useState("");
  const [cat, setCat] = useState<Category | "all">("all");
  const [status, setStatus] = useState<StatusFilter>("all");
  const search = useRef<HTMLInputElement>(null);
  const today = toLocalDay(new Date());
  useHotkey("/", useCallback(() => search.current?.focus(), []));
  const clashIds = useMemo(() => new Set(analyze(routines).filter((i) => i.level === "clash").flatMap((i) => i.ids)), [routines]);

  const rows = routines.filter((r) => {
    if (q && !`${r.name} ${r.dose} ${r.note ?? ""}`.toLowerCase().includes(q.toLowerCase())) return false;
    if (cat !== "all" && r.category !== cat) return false;
    const s = routineStatus(r, today);
    if (status === "active" && s !== "active") return false;
    if (status === "off" && s !== "off" && s !== "ended") return false;
    if (status === "paused" && s !== "paused") return false;
    return true;
  });
  const counts = Object.fromEntries(CATEGORIES.map((c) => [c, routines.filter((r) => r.category === c).length]));

  const share = async () => {
    const tid = toast.loading(t("Creating encrypted share link…"));
    try {
      await shareStack(profile?.name ? t("{name}'s stack", { name: profile.name }) : t("My stack"), rows.length ? rows : routines);
      toast.success(t("Share link copied"), { id: tid, description: t("The decryption key is in the link, never on our server.") });
    } catch (e) { toast.error(String((e as Error).message), { id: tid }); }
  };
  const togglePause = async (r: Routine) => {
    await saveRoutine({ ...r, paused: !r.paused });
    toast(`${r.name} · ${t(r.paused ? "resumed" : "paused")}`, { action: { label: t("Undo"), onClick: () => saveRoutine({ ...r }) } });
  };

  return (
    <div className="animate-fade-in">
      <PageHeader title={t("Stack")} sub={t("{n} routines · {a} active today", { n: routines.length, a: routines.filter((r) => routineStatus(r, today) === "active").length })}>
        <button onClick={share} disabled={!routines.length} className="btn"><Share2 className="size-4" /> {t("Share")}</button>
        <button onClick={() => openRoutine(null)} className="btn-primary hidden md:inline-flex"><Plus className="size-4" /> {t("Create")} <span className="kbd border-white/20 bg-white/10 text-white/70 dark:text-neutral-600">C</span></button>
      </PageHeader>

      {routines.length === 0 ? (
        <Empty title={t("No routines yet")} sub={t("Press C anywhere to add one. Try “Vitamin D3” or “Magnesium” to auto-fill the best time and cycle.")} action={<button onClick={() => openRoutine(null)} className="btn-primary"><Plus className="size-4" /> {t("Create routine")}</button>} />
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <div className="relative w-full sm:w-72">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-neutral-400" />
              <input ref={search} value={q} onChange={(e) => setQ(e.target.value)} placeholder={t("Search routines")} className="input pl-9 pr-8" />
              <span className="kbd absolute hidden md:inline-flex right-2 top-1/2 -translate-y-1/2">/</span>
            </div>
            <div className="flex w-full overflow-x-auto rounded-lg border border-neutral-200 bg-white p-0.5 text-sm sm:w-auto dark:border-neutral-800 dark:bg-neutral-900">
              {(["all", "active", "off", "paused"] as StatusFilter[]).map((s) => (
                <button key={s} onClick={() => setStatus(s)} className={`h-11 flex-1 whitespace-nowrap rounded-md px-2.5 transition md:h-7 md:flex-none ${status === s ? "bg-neutral-100 font-medium dark:bg-neutral-800" : "text-neutral-500"}`}>{t(s === "off" ? "Off-cycle" : s === "all" ? "All" : s === "active" ? "Active" : "Paused")}</button>
              ))}
            </div>
          </div>
          <div className="mb-4 flex flex-wrap gap-1.5">
            <button onClick={() => setCat("all")} className={`chip chip-btn ${cat === "all" ? "border-neutral-900 bg-neutral-900 text-white dark:border-white dark:bg-white dark:text-neutral-900" : "border-neutral-200 bg-white text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900"}`}>{t("All")} {routines.length}</button>
            {CATEGORIES.filter((c) => counts[c]).map((c) => (
              <button key={c} onClick={() => setCat(c)} className={`chip chip-btn ${cat === c ? "border-neutral-900 bg-neutral-900 text-white dark:border-white dark:bg-white dark:text-neutral-900" : "border-neutral-200 bg-white text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900"}`}>{t(c)} <span className="opacity-60">{counts[c]}</span></button>
            ))}
          </div>

          <div className="card overflow-hidden">
            <div className="hidden grid-cols-[1fr_90px_140px_110px_110px_76px] gap-3 border-b border-neutral-100 bg-neutral-50/60 px-4 py-2 text-xs font-medium text-neutral-500 md:grid dark:border-neutral-800 dark:bg-neutral-900">
              <span>{t("Name")}</span><span>{t("Time")}</span><span>{t("Cycle")}</span><span>{t("Inventory")}</span><span>{t("Status")}</span><span />
            </div>
            <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
              {rows.map((r) => {
                const s = routineStatus(r, today);
                const left = daysOfStockLeft(r);
                return (
                  <li key={r.id} onClick={() => openRoutine(r.id)} className="group grid cursor-pointer grid-cols-[1fr_auto] items-center gap-3 px-4 py-3 transition hover:bg-neutral-50 md:grid-cols-[1fr_90px_140px_110px_110px_76px] dark:hover:bg-neutral-800/50">
                    <div className="flex min-w-0 items-center gap-3">
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-full border border-neutral-200 bg-neutral-50 text-xs font-semibold uppercase text-neutral-600 dark:border-neutral-700 dark:bg-neutral-800">{r.name.slice(0, 2)}</span>
                      <div className="min-w-0">
                        <p className="flex items-center gap-1.5 truncate text-sm font-medium">{r.name}{clashIds.has(r.id) && <AlertTriangle className="size-3.5 text-missed" aria-label={t("Interaction")} />}</p>
                        <p className="truncate text-xs text-neutral-500"><span>{t(r.category)}</span> · {r.dose || "—"} <span className="md:hidden">· {r.time}</span></p>
                      </div>
                    </div>
                    <span className="hidden font-mono text-sm text-neutral-600 md:block dark:text-neutral-400">{r.time}</span>
                    <span className="hidden text-sm text-neutral-600 md:block dark:text-neutral-400">{cycleLabel(r.cycle)}{r.cycle.durationDays ? ` · ${r.cycle.durationDays}d` : ""}</span>
                    <span className="hidden text-sm md:block">{left == null ? <span className="text-neutral-400">—</span> : <span className={`tabular ${left <= 7 ? "font-medium text-skipped" : "text-neutral-600 dark:text-neutral-400"}`}>{r.stock} · {left}d</span>}</span>
                    <span className="hidden md:block"><span className={`chip ${STATUS_CLS[s]}`}>{t(STATUS_LABEL[s])}</span></span>
                    <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
                      <span className={`chip md:hidden ${STATUS_CLS[s]}`}>{t(STATUS_LABEL[s])}</span>
                      <button aria-label={t(r.paused ? "Resume" : "Pause")} onClick={() => togglePause(r)} className="hidden size-8 items-center justify-center rounded-lg text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700 md:flex dark:hover:bg-neutral-800">{r.paused ? <Play className="size-4" /> : <Pause className="size-4" />}</button>
                      <button aria-label={t("Duplicate")} onClick={() => duplicateRoutine(r)} className="hidden size-8 items-center justify-center rounded-lg text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700 md:flex dark:hover:bg-neutral-800"><Copy className="size-4" /></button>
                    </div>
                  </li>
                );
              })}
              {rows.length === 0 && <li className="px-4 py-10 text-center text-sm text-neutral-500">{t("No routines match these filters.")}</li>}
            </ul>
          </div>
        </>
      )}
    </div>
  );
}