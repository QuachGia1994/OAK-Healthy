"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Download, Lock } from "lucide-react";
import { openShare } from "@/lib/crypto";
import { db, ensureSeed, saveRoutine, uid } from "@/lib/db";
import { toLocalDay } from "@/lib/domain/day";
import { cycleLabel } from "@/lib/domain/cycle";
import type { Category, CycleConfig } from "@/lib/domain/types";
import { Logo } from "./ui";

interface Shared { v: 1; name: string; createdAt: number; items: { name: string; dose: string; time: string; cycle: CycleConfig; category: Category; note?: string }[] }

export function ShareView() {
  const params = useSearchParams();
  const router = useRouter();
  const [data, setData] = useState<Shared | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    const id = params.get("id") ?? location.pathname.split("/").filter(Boolean)[1];
    const k = location.hash.slice(1);
    if (!id || !k) { setErr("This link is incomplete."); return; }
    fetch(`/api/share/${id}`).then(async (r) => {
      if (!r.ok) throw new Error(r.status === 404 ? "This stack link has expired or never existed." : `Error ${r.status}`);
      const { blob } = (await r.json()) as { blob: string };
      setData(await openShare<Shared>(blob, k));
    }).catch((e: Error) => setErr(e.message.includes("decrypt") || e.name === "OperationError" ? "Wrong key in link." : e.message));
  }, [params]);

  const importAll = async () => {
    if (!data) return;
    await ensureSeed();
    const pid = (await db.meta.get("activeProfile"))?.value as string;
    for (const it of data.items) await saveRoutine({ id: uid(), clientId: pid, name: it.name, dose: it.dose, time: it.time, cycle: it.cycle, category: it.category, note: it.note, startDate: toLocalDay(new Date()), paused: false, stock: null, perDose: 1, updatedAt: Date.now(), deletedAt: null });
    toast.success(`Added ${data.items.length} routines to your stack`);
    router.push("/stack/");
  };

  return (
    <div className="grid-bg min-h-dvh px-4 py-10">
      <div className="mx-auto max-w-xl">
        <Link href="/" className="mb-8 flex items-center gap-2"><Logo /><span className="text-sm font-semibold">OAK Healthy</span></Link>
        {err && <div className="card p-6 text-sm text-missed">{err}</div>}
        {!err && !data && <div className="card h-64 animate-pulse" />}
        {data && (
          <div className="card animate-fade-in overflow-hidden">
            <div className="border-b border-neutral-100 p-6 dark:border-neutral-800">
              <p className="flex items-center gap-1.5 text-xs text-neutral-500"><Lock className="size-3" /> End-to-end encrypted share</p>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight">{data.name}</h1>
              <p className="mt-1 text-sm text-neutral-500">{data.items.length} routines · shared {new Date(data.createdAt).toLocaleDateString()}</p>
            </div>
            <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
              {[...data.items].sort((a, b) => a.time.localeCompare(b.time)).map((it, i) => (
                <li key={i} className="flex items-center gap-3 px-6 py-3">
                  <span className="w-11 font-mono text-xs text-neutral-500">{it.time}</span>
                  <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{it.name}</p><p className="truncate text-xs text-neutral-500">{it.dose || "—"} · {cycleLabel(it.cycle)}</p></div>
                  <span className="chip border-neutral-200 capitalize text-neutral-500 dark:border-neutral-800">{it.category}</span>
                </li>
              ))}
            </ul>
            <div className="flex justify-end gap-2 border-t border-neutral-100 p-4 dark:border-neutral-800">
              <Link href="/" className="btn">Open OAK</Link>
              <button onClick={importAll} className="btn-primary"><Download className="size-4" /> Copy to my stack</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}