"use client";
import { useT, t as tt } from "@/lib/i18n";
import { LabelScan } from "./label-scan";
import { Copy } from "lucide-react";
import type { LabelGuess } from "@/lib/domain/label";
import { useEffect, useState } from "react";
import { Drawer } from "vaul";
import { toast } from "sonner";
import { Sparkles, Trash2, X } from "lucide-react";
import { blankRoutine, db, saveRoutine, setDeleted } from "@/lib/db";
import { useOak } from "@/lib/use-oak";
import { suggest, findReference } from "@/lib/domain/knowledge";
import type { Category, Routine } from "@/lib/domain/types";

export const openRoutine = (id: string | null) => window.dispatchEvent(new CustomEvent("oak:routine", { detail: id }));
export const CATEGORIES: Category[] = ["vitamin", "mineral", "nootropic", "herb", "rx", "other"];

export function duplicateRoutine(r: Routine) {
  void saveRoutine({ ...r, id: crypto.randomUUID(), name: `${r.name} (${tt("copy")})` });
  toast.success(tt("Duplicated"));
}

export function RoutineDrawer() {
  const { profile } = useOak();
  const t = useT();
  const [open, setOpen] = useState(false);
  const [r, setR] = useState<Routine | null>(null);
  const [isNew, setIsNew] = useState(true);
  const [focus, setFocus] = useState(false);
  const [desktop, setDesktop] = useState(true);

  useEffect(() => {
    const mq = matchMedia("(min-width: 768px)");
    setDesktop(mq.matches);
    const onMq = () => setDesktop(mq.matches);
    mq.addEventListener("change", onMq);
    const h = async (e: Event) => {
      const id = (e as CustomEvent<string | null>).detail;
      const ex = id ? await db.routines.get(id) : undefined;
      const pid = (await db.meta.get("activeProfile"))?.value as string | undefined;
      setR(ex ?? blankRoutine(pid ?? profile?.id ?? ""));
      setIsNew(!ex);
      setOpen(true);
    };
    window.addEventListener("oak:routine", h);
    return () => { window.removeEventListener("oak:routine", h); mq.removeEventListener("change", onMq); };
  }, [profile?.id]);

  if (!r) return null;
  const set = <K extends keyof Routine>(k: K, v: Routine[K]) => setR({ ...r, [k]: v });
  const sugg = focus && isNew ? suggest(r.name).filter((s) => s.name.toLowerCase() !== r.name.toLowerCase()) : [];
  const ref = findReference(r.name);

  const apply = (name: string) => {
    const s = findReference(name);
    if (!s) return;
    setR({ ...r, name: s.name, time: s.time, dose: s.dose ?? r.dose, cycle: { ...s.cycle, durationDays: r.cycle.durationDays ?? null }, category: s.category, note: r.note || t(s.advice) });
    setFocus(false);
  };
  const fromLabel = (g: LabelGuess) => {
    const s = g.ref;
    setR({ ...r, name: s?.name ?? r.name, time: s?.time ?? r.time, dose: g.dose ?? r.dose, category: s?.category ?? r.category,
      cycle: s ? { ...s.cycle, durationDays: r.cycle.durationDays ?? null } : r.cycle, note: r.note || (s ? t(s.advice) : ""),
      stock: g.stock ?? r.stock, perDose: g.perDose ?? r.perDose });
  };
  const save = async () => {
    if (!r.name.trim()) return toast.error(t("Give it a name"));
    await saveRoutine({ ...r, name: r.name.trim() });
    setOpen(false);
    toast.success(isNew ? t("{name} added to your stack", { name: r.name }) : t("Saved"));
  };
  const remove = async () => {
    await setDeleted(r.id, true);
    setOpen(false);
    toast(t("{name} deleted", { name: r.name }), { action: { label: t("Undo"), onClick: () => setDeleted(r.id, false) } });
  };

  return (
    <Drawer.Root open={open} onOpenChange={setOpen} direction={desktop ? "right" : "bottom"}>
      <Drawer.Portal>
        <Drawer.Overlay className="fixed inset-0 z-40 bg-neutral-950/25" />
        <Drawer.Content className={`fixed z-50 flex flex-col bg-white outline-none dark:bg-neutral-900 ${desktop ? "inset-y-2 right-2 w-[440px] rounded-xl border border-neutral-200 dark:border-neutral-800" : "inset-x-0 bottom-0 max-h-[92dvh] rounded-t-2xl"}`}>
          {!desktop && <div className="mx-auto mt-2 h-1 w-10 rounded-full bg-neutral-200" />}
          <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-4 dark:border-neutral-800">
            <Drawer.Title className="font-semibold">{isNew ? t("New routine") : t("Edit {name}", { name: r.name })}</Drawer.Title>
            <Drawer.Description className="sr-only">{t("Supplement routine details")}</Drawer.Description>
            <button aria-label={t("Close")} onClick={() => setOpen(false)} className="flex size-11 items-center justify-center rounded-md text-neutral-400 md:size-8 hover:bg-neutral-100 dark:hover:bg-neutral-800"><X className="size-4" /></button>
          </div>
          <div className="flex-1 space-y-4 overflow-y-auto px-5 py-5">
            {isNew && <LabelScan onResult={fromLabel} />}
            <div className="relative">
              <label className="label" htmlFor="rname">{t("Name")}</label>
              <input id="rname" autoFocus={isNew} className="input" placeholder={t("e.g. Vitamin D3")} value={r.name} autoComplete="off"
                onFocus={() => setFocus(true)} onBlur={() => setTimeout(() => setFocus(false), 150)} onChange={(e) => set("name", e.target.value)} />
              {sugg.length > 0 && (
                <div className="absolute inset-x-0 top-full z-10 mt-1 rounded-lg border border-neutral-200 bg-white p-1 shadow-lg dark:border-neutral-800 dark:bg-neutral-900">
                  {sugg.map((s) => (
                    <button key={s.name} onMouseDown={(e) => e.preventDefault()} onClick={() => apply(s.name)} className="flex min-h-11 w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm md:min-h-0 hover:bg-neutral-100 dark:hover:bg-neutral-800">
                      <Sparkles className="size-3.5 text-taken" /> {s.name}
                      <span className="ml-auto font-mono text-xs text-neutral-400">{s.time}</span>
                    </button>
                  ))}
                </div>
              )}
              {ref && <p className="mt-1.5 text-xs text-neutral-500"><Sparkles className="mr-1 inline size-3 text-taken" />{t(ref.advice)}{ref.source && <> · <a href={ref.source} target="_blank" rel="noreferrer" className="underline underline-offset-2">{t("Source")}</a></>}</p>}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label">{t("Dose")}</label><input className="input" placeholder={t("1 capsule")} value={r.dose} onChange={(e) => set("dose", e.target.value)} /></div>
              <div><label className="label">{t("Time")}</label><input type="time" className="input font-mono" value={r.time} onChange={(e) => set("time", e.target.value)} /></div>
            </div>
            <div>
              <label className="label">{t("Category")}</label>
              <div className="flex flex-wrap gap-1.5">
                {CATEGORIES.map((c) => (
                  <button key={c} onClick={() => set("category", c)} className={`chip chip-btn ${r.category === c ? "border-neutral-900 bg-neutral-900 text-white dark:border-white dark:bg-white dark:text-neutral-900" : "border-neutral-200 text-neutral-600 dark:border-neutral-800 dark:text-neutral-400"}`}>{t(c)}</button>
                ))}
              </div>
            </div>
            <div className="rounded-xl border border-neutral-200 p-3 dark:border-neutral-800">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">{t("Cycle")}</span>
                <label className="flex min-h-11 items-center gap-2 text-sm text-neutral-600 md:min-h-0 md:text-xs"><input type="checkbox" className="size-5 accent-[#1F6B4D] md:size-auto" checked={r.cycle.continuous} onChange={(e) => set("cycle", { ...r.cycle, continuous: e.target.checked })} /> {t("Every day")}</label>
              </div>
              {!r.cycle.continuous && (
                <div className="mt-3 grid grid-cols-2 gap-3">
                  <div><label className="label">{t("Days on")}</label><input type="number" min={1} className="input tabular" value={r.cycle.daysOn} onChange={(e) => set("cycle", { ...r.cycle, daysOn: Math.max(1, +e.target.value || 1) })} /></div>
                  <div><label className="label">{t("Days off")}</label><input type="number" min={0} className="input tabular" value={r.cycle.daysOff} onChange={(e) => set("cycle", { ...r.cycle, daysOff: Math.max(0, +e.target.value || 0) })} /></div>
                </div>
              )}
              <div className="mt-3 grid grid-cols-2 gap-3">
                <div><label className="label">{t("Start")}</label><input type="date" className="input" value={r.startDate} onChange={(e) => set("startDate", e.target.value)} /></div>
                <div><label className="label">{t("Duration (days)")}</label><input type="number" min={0} placeholder={t("Forever")} className="input tabular" value={r.cycle.durationDays ?? ""} onChange={(e) => set("cycle", { ...r.cycle, durationDays: e.target.value ? Math.max(1, +e.target.value) : null })} /></div>
              </div>
            </div>
            <div className="rounded-xl border border-neutral-200 p-3 dark:border-neutral-800">
              <span className="text-sm font-medium">{t("Inventory")}</span>
              <p className="text-xs text-neutral-500">{t("Auto-decrements when you mark a dose Taken.")}</p>
              <div className="mt-3 grid grid-cols-2 gap-3">
                <div><label className="label">{t("Pills left")}</label><input type="number" min={0} placeholder={t("Not tracked")} className="input tabular" value={r.stock ?? ""} onChange={(e) => set("stock", e.target.value === "" ? null : Math.max(0, +e.target.value))} /></div>
                <div><label className="label">{t("Per dose")}</label><input type="number" min={1} className="input tabular" value={r.perDose} onChange={(e) => set("perDose", Math.max(1, +e.target.value || 1))} /></div>
              </div>
            </div>
            <div><label className="label">{t("Note")}</label><textarea rows={2} className="input h-auto py-2" value={r.note ?? ""} onChange={(e) => set("note", e.target.value)} /></div>
            <label className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" className="size-5 accent-[#1F6B4D] md:size-auto" checked={r.paused} onChange={(e) => set("paused", e.target.checked)} /> {t("Paused")}</label>
          </div>
          <div className="flex flex-wrap items-center gap-2 border-t border-neutral-100 px-5 pb-[max(env(safe-area-inset-bottom),12px)] pt-3 dark:border-neutral-800">
            {!isNew && <button onClick={remove} aria-label={t("Delete")} className="btn w-11 px-0 text-missed md:w-auto md:px-3"><Trash2 className="size-4" /> <span className="hidden md:inline">{t("Delete")}</span></button>}
            {!isNew && <button onClick={() => { duplicateRoutine(r); setOpen(false); }} aria-label={t("Duplicate")} className="btn w-11 px-0 md:w-auto md:px-3"><Copy className="size-4" /> <span className="hidden md:inline">{t("Duplicate")}</span></button>}
            <button onClick={() => setOpen(false)} className="btn ml-auto">{t("Cancel")}</button>
            <button onClick={save} className="btn-primary">{isNew ? t("Add routine") : t("Save changes")}</button>
          </div>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}