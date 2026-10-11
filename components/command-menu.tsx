"use client";
import { useT } from "@/lib/i18n";
import { Command } from "cmdk";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { BarChart3, Check, Layers, LayoutGrid, Moon, Plus, Settings, User } from "lucide-react";
import { useNow, useOak } from "@/lib/use-oak";
import { markDose } from "@/lib/db";
import { haptic } from "@/lib/haptics";
import { dosesForDay } from "@/lib/domain/schedule";
import { toLocalDay } from "@/lib/domain/day";
import { openRoutine } from "./routine-drawer";

export const openCommand = () => window.dispatchEvent(new Event("oak:cmdk"));

export function CommandMenu() {
  const [open, setOpen] = useState(false);
  const t = useT();
  const router = useRouter();
  const { routines, intakes, profiles, setActive } = useOak();
  const now = useNow();
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key.toLowerCase() === "k" && (e.metaKey || e.ctrlKey)) { e.preventDefault(); setOpen((o) => !o); } };
    const o = () => setOpen(true);
    window.addEventListener("keydown", k); window.addEventListener("oak:cmdk", o);
    return () => { window.removeEventListener("keydown", k); window.removeEventListener("oak:cmdk", o); };
  }, []);
  const run = (fn: () => void) => { setOpen(false); fn(); };
  const pending = dosesForDay(routines, intakes, toLocalDay(new Date(now))).filter((d) => !d.intake);
  const item = "flex h-11 md:h-9 cursor-pointer items-center gap-2.5 rounded-lg px-2.5 text-sm text-neutral-700 data-[selected=true]:bg-neutral-100 dark:text-neutral-300 dark:data-[selected=true]:bg-neutral-800";
  const group = "px-1.5 pb-1 [&_[cmdk-group-heading]]:px-2.5 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:text-neutral-400";

  return (
    <Command.Dialog open={open} onOpenChange={setOpen} label="Command menu"
      overlayClassName="fixed inset-0 z-40 bg-neutral-950/20 backdrop-blur-[2px]"
      contentClassName="fixed left-1/2 top-[14vh] z-50 w-[min(560px,92vw)] -translate-x-1/2 overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-2xl animate-fade-in dark:border-neutral-800 dark:bg-neutral-900">
      <Command.Input autoFocus placeholder={t("Type a command or search…")} className="h-12 w-full border-b border-neutral-200 bg-transparent px-4 text-base md:text-sm outline-none dark:border-neutral-800" />
      <Command.List className="max-h-[50vh] overflow-y-auto py-2">
        <Command.Empty className="py-8 text-center text-sm text-neutral-500">{t("No results.")}</Command.Empty>
        {pending.length > 0 && (
          <Command.Group heading={t("Log a dose")} className={group}>
            {pending.slice(0, 6).map((d) => (
              <Command.Item key={d.key} value={`take ${d.routine.name} ${d.routine.time}`} className={item}
                onSelect={() => run(async () => { await markDose(d.routine, d.at, "Taken"); haptic("success"); toast.success(`${d.routine.name} · ${t("Taken")}`); })}>
                <Check className="size-4 text-taken" /> {t("Take {name}", { name: d.routine.name })} <span className="ml-auto font-mono text-xs text-neutral-400">{d.routine.time}</span>
              </Command.Item>
            ))}
          </Command.Group>
        )}
        <Command.Group heading={t("Navigate")} className={group}>
          <Command.Item className={item} onSelect={() => run(() => router.push("/"))}><LayoutGrid className="size-4" /> {t("Today")}</Command.Item>
          <Command.Item className={item} onSelect={() => run(() => router.push("/stack/"))}><Layers className="size-4" /> {t("Stack")}</Command.Item>
          <Command.Item className={item} onSelect={() => run(() => router.push("/insights/"))}><BarChart3 className="size-4" /> {t("Insights")}</Command.Item>
          <Command.Item className={item} onSelect={() => run(() => router.push("/settings/"))}><Settings className="size-4" /> {t("Settings")}</Command.Item>
          <Command.Item className={item} onSelect={() => run(() => router.push("/account/"))}><User className="size-4" /> {t("Account")}</Command.Item>
        </Command.Group>
        <Command.Group heading={t("Actions")} className={group}>
          <Command.Item className={item} onSelect={() => run(() => openRoutine(null))}><Plus className="size-4" /> {t("New routine")} <span className="kbd ml-auto">C</span></Command.Item>
          {routines.map((r) => (
            <Command.Item key={r.id} value={`edit ${r.name}`} className={item} onSelect={() => run(() => openRoutine(r.id))}>
              <Layers className="size-4 text-neutral-400" /> {t("Edit {name}", { name: r.name })}
            </Command.Item>
          ))}
          <Command.Item className={item} onSelect={() => run(() => {
            const d = document.documentElement.classList.toggle("dark");
            localStorage.setItem("oak-theme", d ? "dark" : "light");
          })}><Moon className="size-4" /> {t("Toggle dark mode")}</Command.Item>
        </Command.Group>
        {profiles.length > 1 && (
          <Command.Group heading={t("Switch profile")} className={group}>
            {profiles.map((p) => (
              <Command.Item key={p.id} value={`profile ${p.name}`} className={item} onSelect={() => run(() => setActive(p.id))}>
                <User className="size-4" style={{ color: p.color }} /> {p.name}
              </Command.Item>
            ))}
          </Command.Group>
        )}
      </Command.List>
    </Command.Dialog>
  );
}