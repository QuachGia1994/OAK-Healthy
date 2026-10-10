"use client";
import { useEffect, useRef, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { toast } from "sonner";
import { Download, KeyRound, Monitor, RefreshCw, Trash2, Upload } from "lucide-react";
import { adoptSyncedProfiles, db, mergeSnapshot, snapshot, type Snapshot } from "@/lib/db";
import { isValidKey, newSyncKey } from "@/lib/crypto";
import { getSyncKey, setSyncKey, syncNow } from "@/lib/sync";
import { useOak } from "@/lib/use-oak";
import { PageHeader } from "./ui";

/** Background sync: on load, on focus, and 4s after local changes. */
export function SyncAgent() {
  useEffect(() => {
    let t: ReturnType<typeof setTimeout> | undefined, busy = false;
    const run = async () => {
      const k = await getSyncKey();
      if (!k || busy || !navigator.onLine) return;
      busy = true;
      const r = await syncNow(k);
      busy = false;
      window.dispatchEvent(new CustomEvent("oak:synced", { detail: r }));
    };
    const later = () => { clearTimeout(t); t = setTimeout(run, 4000); };
    void run();
    window.addEventListener("oak:changed", later);
    window.addEventListener("focus", run);
    window.addEventListener("online", run);
    return () => { clearTimeout(t); window.removeEventListener("oak:changed", later); window.removeEventListener("focus", run); window.removeEventListener("online", run); };
  }, []);
  return null;
}

function Section({ title, sub, children }: { title: string; sub: string; children: React.ReactNode }) {
  return (
    <section className="card grid gap-4 p-5 md:grid-cols-[260px_1fr]">
      <div><h2 className="text-sm font-semibold">{title}</h2><p className="mt-1 text-xs text-neutral-500">{sub}</p></div>
      <div>{children}</div>
    </section>
  );
}

export function SettingsView() {
  const { profiles, profile } = useOak();
  const key = useLiveQuery(() => db.meta.get("syncKey"), [], undefined)?.value as string | null | undefined;
  const last = useLiveQuery(() => db.meta.get("lastSync"), [], undefined)?.value as number | undefined;
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [reveal, setReveal] = useState(false);
  const file = useRef<HTMLInputElement>(null);
  const [installEvt, setInstallEvt] = useState<(Event & { prompt: () => Promise<void> }) | null>(null);
  useEffect(() => {
    const h = (e: Event) => { e.preventDefault(); setInstallEvt(e as Event & { prompt: () => Promise<void> }); };
    window.addEventListener("beforeinstallprompt", h);
    return () => window.removeEventListener("beforeinstallprompt", h);
  }, []);

  const doSync = async (k: string) => {
    setBusy(true);
    const r = await syncNow(k);
    setBusy(false);
    if (r.ok) toast.success("Synced", { description: `Vault revision ${r.rev}${r.pulled ? ` · ${r.pulled} changes pulled` : ""}` });
    else toast.error(r.error);
    return r.ok;
  };
  const enable = async () => { const k = newSyncKey(); await setSyncKey(k); setReveal(true); await doSync(k); };
  const join = async () => {
    if (!isValidKey(input)) return toast.error("That doesn’t look like an OAK sync key");
    const k = input.trim().toUpperCase();
    await setSyncKey(k);
    if (await doSync(k)) { setInput(""); await adoptSyncedProfiles(); await syncNow(k); }
  };
  const exportJson = async () => {
    const blob = new Blob([JSON.stringify(await snapshot(), null, 2)], { type: "application/json" });
    const a = Object.assign(document.createElement("a"), { href: URL.createObjectURL(blob), download: `oak-healthy-${new Date().toISOString().slice(0, 10)}.json` });
    a.click(); URL.revokeObjectURL(a.href);
  };
  const importJson = async (f: File) => {
    try { const n = await mergeSnapshot(JSON.parse(await f.text()) as Snapshot); toast.success(`Imported ${n} records`); }
    catch { toast.error("Invalid backup file"); }
  };
  const rename = async (id: string, name: string) => { const p = await db.profiles.get(id); if (p && name.trim()) await db.profiles.put({ ...p, name: name.trim(), updatedAt: Date.now() }); };
  const removeProfile = async (id: string) => {
    if (profiles.length <= 1) return toast.error("Keep at least one profile");
    const p = await db.profiles.get(id);
    if (!p || !confirm(`Delete profile “${p.name}”? Its routines stay recoverable from sync history.`)) return;
    await db.profiles.put({ ...p, deletedAt: Date.now(), updatedAt: Date.now() });
    if (profile?.id === id) await db.meta.put({ key: "activeProfile", value: profiles.find((x) => x.id !== id)!.id });
    window.dispatchEvent(new Event("oak:changed"));
  };
  const theme = (t: "light" | "dark") => { document.documentElement.classList.toggle("dark", t === "dark"); localStorage.setItem("oak-theme", t); };

  return (
    <div className="animate-fade-in space-y-3">
      <PageHeader title="Settings" sub="Profiles, encrypted sync and your data" />

      <Section title="Profiles" sub="Track the whole family from one device. Each profile has its own stack and history.">
        <ul className="space-y-2">
          {profiles.map((p) => (
            <li key={p.id} className="flex items-center gap-2">
              <span className="size-3 shrink-0 rounded-full" style={{ background: p.color }} />
              <input defaultValue={p.name} onBlur={(e) => rename(p.id, e.target.value)} className="input h-8" />
              <button aria-label={`Delete ${p.name}`} onClick={() => removeProfile(p.id)} className="btn px-2 text-neutral-400 hover:text-missed"><Trash2 className="size-4" /></button>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="Encrypted sync" sub="Zero-knowledge: data is encrypted with AES-256-GCM on this device. The server only sees ciphertext. Lose the key and nobody can recover it.">
        {key ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 rounded-lg border border-taken/20 bg-taken-soft px-3 py-2 text-sm text-taken"><KeyRound className="size-4" /> Sync is on {last ? `· last synced ${new Date(last).toLocaleString()}` : ""}</div>
            <div>
              <label className="label">Your sync key: enter it on another device to link it</label>
              <div className="flex gap-2">
                <code className="input flex items-center overflow-x-auto font-mono text-xs">{reveal ? key : key.replace(/[A-Z0-9](?=.{5})/g, "•")}</code>
                <button className="btn" onClick={() => setReveal(!reveal)}>{reveal ? "Hide" : "Show"}</button>
                <button className="btn" onClick={() => { void navigator.clipboard.writeText(key); toast.success("Key copied"); }}>Copy</button>
              </div>
            </div>
            <div className="flex gap-2">
              <button disabled={busy} onClick={() => doSync(key)} className="btn-primary"><RefreshCw className={`size-4 ${busy ? "animate-spin" : ""}`} /> Sync now</button>
              <button onClick={async () => { if (confirm("Turn off sync on this device? Local data stays.")) await setSyncKey(null); }} className="btn">Turn off</button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <button disabled={busy} onClick={enable} className="btn-primary"><KeyRound className="size-4" /> Turn on sync</button>
            <div>
              <label className="label">Already have a key? Link this device</label>
              <div className="flex gap-2"><input value={input} onChange={(e) => setInput(e.target.value)} placeholder="OAK-XXXX-XXXX-…" className="input font-mono" /><button disabled={busy} onClick={join} className="btn">Link</button></div>
            </div>
          </div>
        )}
      </Section>

      <Section title="Backup" sub="Export everything as JSON, or merge a backup back in (newest change wins).">
        <div className="flex flex-wrap gap-2">
          <button onClick={exportJson} className="btn"><Download className="size-4" /> Export JSON</button>
          <button onClick={() => file.current?.click()} className="btn"><Upload className="size-4" /> Import</button>
          <input ref={file} type="file" accept="application/json" hidden onChange={(e) => e.target.files?.[0] && importJson(e.target.files[0])} />
        </div>
      </Section>

      <Section title="Appearance & app" sub="Install OAK as an app. It works fully offline.">
        <div className="flex flex-wrap gap-2">
          <button onClick={() => theme("light")} className="btn">Light</button>
          <button onClick={() => theme("dark")} className="btn">Dark</button>
          {installEvt && <button onClick={() => installEvt.prompt()} className="btn-primary"><Monitor className="size-4" /> Install app</button>}
        </div>
      </Section>
    </div>
  );
}