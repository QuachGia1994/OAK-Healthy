"use client";
import { useMemo } from "react";
import { toast } from "sonner";
import { ArrowRight, CalendarClock, Check } from "lucide-react";
import { saveRoutine } from "@/lib/db";
import { haptic } from "@/lib/haptics";
import { useT } from "@/lib/i18n";
import { suggestSchedule, type ScheduleSuggestion } from "@/lib/domain/stack";
import type { Routine } from "@/lib/domain/types";

/** Chrono-timing: one-tap schedule that fixes timing rules and splits clashing pairs into different slots. */
export function ScheduleSuggest({ routines }: { routines: Routine[] }) {
  const t = useT();
  const list = useMemo(() => suggestSchedule(routines), [routines]);
  const byId = useMemo(() => new Map(routines.map((r) => [r.id, r])), [routines]);
  if (!list.length) return null;

  const apply = async (items: ScheduleSuggestion[]) => {
    const before = items.map((s) => byId.get(s.id)).filter((r): r is Routine => !!r);
    for (const s of items) { const r = byId.get(s.id); if (r) await saveRoutine({ ...r, time: s.to }); }
    haptic("success");
    toast.success(t("Schedule updated ({n})", { n: items.length }), { action: { label: t("Undo"), onClick: () => { for (const r of before) void saveRoutine(r); } } });
  };

  return (
    <div className="card mt-3 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="flex items-center gap-2 text-sm font-medium"><CalendarClock className="size-4 text-neutral-400" /> {t("Suggested schedule")}</p>
          <p className="mt-0.5 text-xs text-neutral-500">{t("Moves doses to slots that respect timing rules and keep clashing supplements apart.")}</p>
        </div>
        <button onClick={() => apply(list)} className="btn-primary"><Check className="size-4" /> {t("Apply all")}</button>
      </div>
      <ul className="mt-3 divide-y divide-neutral-100 dark:divide-neutral-800">
        {list.map((s) => (
          <li key={s.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
            <span className="min-w-0 flex-1 truncate font-medium">{s.name}</span>
            <span className="flex items-center gap-1.5 font-mono text-xs"><span className="text-neutral-400 line-through">{s.from}</span><ArrowRight className="size-3 text-neutral-400" /><span className="text-taken">{s.to}</span></span>
            <span className="basis-full text-xs text-neutral-500 sm:basis-auto">{s.reason === "clash" ? t("Away from {name}", { name: s.partner ?? "" }) : t("Better time of day")}</span>
            <button onClick={() => apply([s])} className="btn ml-auto">{t("Apply")}</button>
          </li>
        ))}
      </ul>
    </div>
  );
}
