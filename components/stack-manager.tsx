"use client";
import { useT } from "@/lib/i18n";
import { useCallback, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { AlertTriangle, ArrowDown, ArrowUp, ChevronsUpDown, Copy, Hourglass, Pause, Play, Plus, Search, Share2, ShoppingCart } from "lucide-react";
import { haptic } from "@/lib/haptics";
import { inventoryAlert, routineStatus, sortRoutines, type SortDir, type SortKey } from "@/lib/domain/stack";
import { useHotkey, useOak } from "@/lib/use-oak";
import { saveRoutine } from "@/lib/db";
import { updateMeta } from "@/lib/sync";
import { cycleLabel } from "@/lib/domain/cycle";
import { toLocalDay } from "@/lib/domain/day";
import { analyze } from "@/lib/domain/knowledge";
import { b64u, sealShare } from "@/lib/crypto";
import type { ShareRef } from "@/lib/domain/wipe";
import { shareTitle } from "@/lib/domain/shares";
import type { Category, Routine } from "@/lib/domain/types";
import { Empty, PageHeader } from "./ui";
import { CATEGORIES, openRoutine, duplicateRoutine } from "./routine-drawer";

type StatusFilter = "all" | "active" | "off" | "paused";

export { routineStatus };
const STATUS_CLS = {
  active: "border-taken/20 bg-taken-soft text-taken",
  off: "border-due/20 bg-due-soft text-due",
  paused: "border-skipped/20 bg-skipped-soft text-skipped",
  ended: "border-neutral-200 bg-neutral-100 text-neutral-500 dark:border-neutral-800 dark:bg-neutral-800",
};
const STATUS_LABEL = { active: "Active", off: "Off-cycle", paused: "Paused", ended: "Ended" };

const sha256Hex = async (s: string) =>
  [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map((b) => b.toString(16).padStart(2, "0")).join("");

/** Encrypted share. With a handle, the server also issues a readable /s/<handle>/<slug> URL; only the handle,
 * the slug words and the item count are public (used for link previews). The key stays in the #fragment.
 * A delete token (256 bits) is minted here; the server gets only its SHA-256, and meta `shares` keeps the token for
 * Delete everything (docs/feat/delete-everything.md § Share links). */
export async function shareStack(name: string, routines: Routine[], handle?: string) {
  const payload = { v: 1, name, createdAt: Date.now(), items: routines.map(({ name, dose, time, cycle, category, note }) => ({ name, dose, time, cycle, category, note })) };
  const { blob, k } = await sealShare(payload);
  const token = b64u(crypto.getRandomValues(new Uint8Array(32)));
  const meta = handle ? { handle, title: name, items: routines.length } : {};
  const res = await fetch("/api/share", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ blob, deleteHash: await sha256Hex(token), ...meta }) });
  if (!res.ok) throw new Error(`Share failed (${res.status})`);
  // expiresAt is the server's (D7). Only a 2.7 worker sends none: the fallback is then this device's clock, and that
  // worker kept no hash anyway, so the wipe counts the entry as kept (403/405).
  const { id, slug, expiresAt = Date.now() + 365 * 864e5 } = (await res.json()) as { id: string; slug?: string; expiresAt?: number };
  // title: the list name for the Settings card; it stays on devices and in the encrypted vault and is never sent in a
  // DELETE (a pretty link's slug words are public as before).
  const entry: ShareRef = { id, token, expiresAt, title: shareTitle(name), createdAt: Date.now() };
  await updateMeta<ShareRef[]>("shares", [], (cur) => [...cur, entry]);
  window.dispatchEvent(new Event("oak:changed")); // the next sync carries the list to the other devices
  const url = `${location.origin}/s/${slug ?? id}#${k}`;
  await navigator.clipboard?.writeText(url).catch(() => {});
  return url;
}

export function StackManager() {
  const { routines, profile } = useOak();
  const t = useT();
  const [q, setQ] = useState("");
  const [cat, setCat] = useState<Category | "all">("all");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: "time", dir: "asc" });
  const search = useRef<HTMLInputElement>(null);
  const today = toLocalDay(new Date());
  useHotkey("/", useCallback(() => search.current?.focus(), []));
  const clashIds = useMemo(() => new Set(analyze(routines).filter((i) => i.level === "clash").flatMap((i) => i.ids)), [routines]);

  const filtered = routines.filter((r) => {
    if (q && !`${r.name} ${r.dose} ${r.note ?? ""}`.toLowerCase().includes(q.toLowerCase())) return false;
    if (cat !== "all" && r.category !== cat) return false;
    const s = routineStatus(r, today);
    if (status === "active" && s !== "active") return false;
    if (status === "off" && s !== "off" && s !== "ended") return false;
    if (status === "paused" && s !== "paused") return false;
    return true;
  });
  const rows = sortRoutines(filtered, sort.key, sort.dir, today);
  const sortBy = (key: SortKey) => setSort((s) => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" }));
  const th = (k: SortKey, label: string) => (
    <button key={k} onClick={() => sortBy(k)} aria-sort={sort.key === k ? (sort.dir === "asc" ? "ascending" : "descending") : "none"} className={`flex items-center gap-1 text-left hover:text-neutral-900 dark:hover:text-white ${sort.key === k ? "text-neutral-900 dark:text-white" : ""}`}>
      {t(label)} {sort.key === k ? (sort.dir === "asc" ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />) : <ChevronsUpDown className="size-3 opacity-40" />}
    </button>
  );
  const counts = Object.fromEntries(CATEGORIES.map((c) => [c, routines.filter((r) => r.category === c).length]));

  const share = async () => {
    const tid = toast.loading(t("Creating encrypted share link…"));
    try {
      await shareStack(profile?.name ? t("{name}'s stack", { name: profile.name }) : t("My stack"), rows.length ? rows : routines, profile?.name || "oak");
      haptic("success");
      toast.success(t("Share link copied"), { id: tid, description: t("The decryption key is in the link, never on our server.") });
    } catch (e) { toast.error(String((e as Error).message), { id: tid }); }
  };
  const togglePause = async (r: Routine) => {
    await saveRoutine({ ...r, paused: !r.paused });
    haptic();
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
            <div role="group" aria-label={t("Status")} className="flex w-full overflow-x-auto rounded-lg border border-neutral-200 bg-white p-0.5 text-sm sm:w-auto dark:border-neutral-800 dark:bg-neutral-900">
              {(["all", "active", "off", "paused"] as StatusFilter[]).map((s) => (
                <button key={s} type="button" aria-pressed={status === s} onClick={() => setStatus(s)} className={`h-11 flex-1 whitespace-nowrap rounded-md px-2.5 transition md:h-7 md:flex-none ${status === s ? "bg-neutral-100 font-medium dark:bg-neutral-800" : "text-neutral-500 dark:text-neutral-400"}`}>{t(s === "off" ? "Off-cycle" : s === "all" ? "All" : s === "active" ? "Active" : "Paused")}</button>
              ))}
            </div>
            <select aria-label={t("Sort by")} value={`${sort.key}:${sort.dir}`} onChange={(e) => { const [key, dir] = e.target.value.split(":") as [SortKey, SortDir]; setSort({ key, dir }); }} className="input w-full sm:w-auto md:hidden">
              {([["time", "Time"], ["name", "Name"], ["stock", "Inventory"], ["status", "Status"], ["cycle", "Cycle"]] as [SortKey, string][]).flatMap(([k, l]) => (["asc", "desc"] as SortDir[]).map((d) => <option key={k + d} value={`${k}:${d}`}>{t("Sort by")}: {t(l)} {d === "asc" ? "↑" : "↓"}</option>))}
            </select>
          </div>
          <div role="group" aria-label={t("Category")} className="mb-4 flex flex-wrap gap-1.5">
            <button type="button" aria-pressed={cat === "all"} onClick={() => setCat("all")} className={`chip chip-btn ${cat === "all" ? "border-neutral-900 bg-neutral-900 text-white dark:border-white dark:bg-white dark:text-neutral-900" : "border-neutral-200 bg-white text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900"}`}>{t("All")} {routines.length}</button>
            {CATEGORIES.filter((c) => counts[c]).map((c) => (
              <button key={c} type="button" aria-pressed={cat === c} onClick={() => setCat(c)} className={`chip chip-btn ${cat === c ? "border-neutral-900 bg-neutral-900 text-white dark:border-white dark:bg-white dark:text-neutral-900" : "border-neutral-200 bg-white text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900"}`}>{t(c)} <span className="opacity-60">{counts[c]}</span></button>
            ))}
          </div>

          <div className="card overflow-hidden">
            <div className="hidden grid-cols-[1fr_90px_140px_110px_110px_76px] gap-3 border-b border-neutral-100 bg-neutral-50/60 px-4 py-2 text-xs font-medium text-neutral-500 md:grid dark:border-neutral-800 dark:bg-neutral-900">
              {th("name", "Name")}{th("time", "Time")}{th("cycle", "Cycle")}{th("stock", "Inventory")}{th("status", "Status")}<span />
            </div>
            <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
              {rows.map((r) => {
                const s = routineStatus(r, today);
                const inv = inventoryAlert(r, today), left = inv.daysLeft;
                return (
                  <li key={r.id} className="group relative grid cursor-pointer grid-cols-[1fr_auto] items-center gap-3 px-4 py-3 transition hover:bg-neutral-50 md:grid-cols-[1fr_90px_140px_110px_110px_76px] dark:hover:bg-neutral-800/50">
                    {/* The name cell is the row's button; its ::after covers the whole row so a tap anywhere still opens it.
                        Pause, Duplicate, Reorder and the expiry hint (its tooltip) sit above that layer (relative z-10), so no control is nested in another. */}
                    <button type="button" onClick={() => openRoutine(r.id)} className="flex min-w-0 items-center gap-3 rounded-md text-left outline-hidden after:absolute after:inset-0 after:content-[''] focus-visible:after:ring-2 focus-visible:after:ring-inset focus-visible:after:ring-neutral-900 dark:focus-visible:after:ring-white">
                      <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full border border-neutral-200 bg-neutral-50 text-xs font-semibold uppercase text-neutral-600 dark:border-neutral-700 dark:bg-neutral-800">{r.name.slice(0, 2)}</span>
                      <span className="block min-w-0">
                        <span className="flex items-center gap-1.5 truncate text-sm font-medium">{r.name}{clashIds.has(r.id) && <><AlertTriangle className="size-3.5 text-missed" aria-hidden="true" /><span className="sr-only">{t("Interaction")}</span></>}</span>
                        <span className="block truncate text-xs text-neutral-500"><span>{t(r.category)}</span> · {r.dose || "—"} <span className="md:hidden">· {r.time}</span></span>
                      </span>
                    </button>
                    <span className="hidden font-mono text-sm text-neutral-600 md:block dark:text-neutral-400">{r.time}</span>
                    <span className="hidden text-sm text-neutral-600 md:block dark:text-neutral-400">{cycleLabel(r.cycle)}{r.cycle.durationDays ? ` · ${r.cycle.durationDays}d` : ""}</span>
                    <span className="hidden items-center gap-1 text-sm md:flex">
                      {left == null ? <span className="text-neutral-400">—</span> : <span className={`tabular ${inv.low ? "font-medium text-skipped" : "text-neutral-600 dark:text-neutral-400"}`}>{r.stock} · {left}d</span>}
                      {(inv.expiring || inv.expired) && <span role="img" className="relative z-10" aria-label={inv.expired ? t("Expired") : t("Expires in {n}d", { n: inv.expiryDays ?? 0 })} title={inv.expired ? t("Expired") : t("Expires in {n}d", { n: inv.expiryDays ?? 0 })}><Hourglass className={`size-3.5 ${inv.expired ? "text-missed" : "text-skipped"}`} /></span>}
                      {inv.reorderUrl && <a href={inv.reorderUrl} target="_blank" rel="noopener noreferrer" aria-label={t("Reorder")} title={t("Reorder")} className={`relative z-10 flex size-6 items-center justify-center rounded ${inv.low ? "text-skipped" : "text-neutral-400"} hover:bg-neutral-100 dark:hover:bg-neutral-800`}><ShoppingCart className="size-3.5" /></a>}
                    </span>
                    <span className="hidden md:block"><span className={`chip ${STATUS_CLS[s]}`}>{t(STATUS_LABEL[s])}</span></span>
                    <div className="flex justify-end gap-1">
                      <span className={`chip md:hidden ${STATUS_CLS[s]}`}>{t(STATUS_LABEL[s])}</span>
                      <button aria-label={t(r.paused ? "Resume" : "Pause")} onClick={() => togglePause(r)} className="relative z-10 hidden size-8 items-center justify-center rounded-lg text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700 md:flex dark:hover:bg-neutral-800">{r.paused ? <Play className="size-4" /> : <Pause className="size-4" />}</button>
                      <button aria-label={t("Duplicate")} onClick={() => duplicateRoutine(r)} className="relative z-10 hidden size-8 items-center justify-center rounded-lg text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700 md:flex dark:hover:bg-neutral-800"><Copy className="size-4" /></button>
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