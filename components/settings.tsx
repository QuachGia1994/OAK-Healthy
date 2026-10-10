"use client";
import { useEffect, useRef, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { toast } from "sonner";
import Link from "next/link";
import { Bell, Copy, Download, KeyRound, Monitor, RefreshCw, Trash2, Upload, UserRound, Users } from "lucide-react";
import { useT, locale } from "@/lib/i18n";
import { LangToggle } from "./lang-toggle";
import { getMe, type Me } from "@/lib/account";
import { disableReminders, enableReminders, pushReminderTimes, pushSupported, testReminder } from "@/lib/reminders";
import { circleUrl, createCircle, getCircles, publishCircles, revokeCircle, type Circle } from "@/lib/circles";
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
      if (busy || !navigator.onLine) return;
      busy = true;
      const k = await getSyncKey();
      if (k) window.dispatchEvent(new CustomEvent("oak:synced", { detail: await syncNow(k) }));
      await Promise.all([pushReminderTimes().catch(() => {}), publishCircles().catch(() => {})]);
      busy = false;
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
  const t = useT();
  return (
    <section className="card grid gap-4 p-5 md:grid-cols-[260px_1fr]">
      <div><h2 className="text-sm font-semibold">{t(title)}</h2><p className="mt-1 text-xs text-neutral-500">{t(sub)}</p></div>
      <div>{children}</div>
    </section>
  );
}

export function SettingsView() {
  const { profiles, profile } = useOak();
  const t = useT();
  const [me, setMe] = useState<Me | null>(null);
  const [push, setPush] = useState(false);
  const [canPush, setCanPush] = useState(true);
  const [circles, setCircles] = useState<Circle[]>([]);
  const [label, setLabel] = useState("");
  const pushMeta = useLiveQuery(() => db.meta.get("push"), [], undefined)?.value;
  useEffect(() => { setCanPush(pushSupported()); void getMe().then(setMe); void getCircles().then(setCircles); }, []);
  useEffect(() => setPush(!!pushMeta), [pushMeta]);
  const tryIt = async (fn: () => Promise<unknown>, ok: string) => { try { await fn(); toast.success(t(ok)); } catch (e) { toast.error(t((e as Error).message)); } };
  const invite = () => tryIt(async () => {
    if (!profile) return;
    const c = await createCircle(profile.id, label.trim() || t("Family"));
    await navigator.clipboard?.writeText(circleUrl(c)).catch(() => {});
    setLabel(""); setCircles(await getCircles());
  }, "Invite link copied");
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
    if (r.ok) toast.success(t("Synced"), { description: t("Vault revision {rev}", { rev: r.rev }) + (r.pulled ? " · " + t("{n} changes pulled", { n: r.pulled }) : "") });
    else toast.error(t(r.error));
    return r.ok;
  };
  const enable = async () => { const k = newSyncKey(); await setSyncKey(k); setReveal(true); await doSync(k); };
  const join = async () => {
    if (!isValidKey(input)) return toast.error(t("That doesn't look like an OAK sync key"));
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
    try { const n = await mergeSnapshot(JSON.parse(await f.text()) as Snapshot); toast.success(t("Imported {n} records", { n })); }
    catch { toast.error(t("Invalid backup file")); }
  };
  const rename = async (id: string, name: string) => { const p = await db.profiles.get(id); if (p && name.trim()) await db.profiles.put({ ...p, name: name.trim(), updatedAt: Date.now() }); };
  const removeProfile = async (id: string) => {
    if (profiles.length <= 1) return toast.error(t("Keep at least one profile"));
    const p = await db.profiles.get(id);
    if (!p || !confirm(t("Delete profile “{name}”? Its routines stay recoverable from sync history.", { name: p.name }))) return;
    await db.profiles.put({ ...p, deletedAt: Date.now(), updatedAt: Date.now() });
    if (profile?.id === id) await db.meta.put({ key: "activeProfile", value: profiles.find((x) => x.id !== id)!.id });
    window.dispatchEvent(new Event("oak:changed"));
  };
  const theme = (t: "light" | "dark") => { document.documentElement.classList.toggle("dark", t === "dark"); localStorage.setItem("oak-theme", t); };

  return (
    <div className="animate-fade-in space-y-3">
      <PageHeader title={t("Settings")} sub={t("Profiles, encrypted sync and your data")}><LangToggle /></PageHeader>

      <Section title="Account" sub="Optional. Sign in to unlock your vault on any device with email and password, Google or GitHub.">
        <div className="flex flex-wrap items-center gap-2">
          <span className="flex min-w-0 basis-full items-center gap-2 text-sm sm:flex-1 sm:basis-auto"><UserRound className="size-4 shrink-0 text-neutral-400" /><span className="truncate">{me ? me.email : t("Not signed in")}</span></span>
          {me ? <Link href="/account/" className="btn">{t("Manage account")}</Link> : <><Link href="/login/" className="btn">{t("Sign in")}</Link><Link href="/signup/" className="btn-primary">{t("Create account")}</Link></>}
        </div>
      </Section>

      <Section title="Profiles" sub="Track the whole family from one device. Each profile has its own stack and history.">
        <ul className="space-y-2">
          {profiles.map((p) => (
            <li key={p.id} className="flex items-center gap-2">
              <span className="size-3 shrink-0 rounded-full" style={{ background: p.color }} />
              <input defaultValue={p.name} onBlur={(e) => rename(p.id, e.target.value)} className="input md:h-8" />
              <button aria-label={t("Delete {name}", { name: p.name })} onClick={() => removeProfile(p.id)} className="btn w-11 shrink-0 px-0 text-neutral-400 hover:text-missed"><Trash2 className="size-4" /></button>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="Encrypted sync" sub="Zero-knowledge: data is encrypted with AES-256-GCM on this device. The server only sees ciphertext. Lose the key and nobody can recover it.">
        {key ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 rounded-lg border border-taken/20 bg-taken-soft px-3 py-2 text-sm text-taken"><KeyRound className="size-4 shrink-0" /> {t("Sync is on")} {last ? `· ${t("last synced")} ${new Date(last).toLocaleString(locale())}` : ""}</div>
            <div>
              <label className="label">{t("Your sync key (also your recovery code): enter it on another device to link it")}</label>
              <div className="flex flex-wrap gap-2 sm:flex-nowrap">
                <code className="input flex min-w-0 basis-full items-center overflow-x-auto font-mono text-xs sm:basis-auto">{reveal ? key : key.replace(/[A-Z0-9](?=.{5})/g, "•")}</code>
                <button className="btn" onClick={() => setReveal(!reveal)}>{reveal ? t("Hide") : t("Show")}</button>
                <button className="btn" onClick={() => { void navigator.clipboard.writeText(key); toast.success(t("Key copied")); }}>{t("Copy")}</button>
              </div>
            </div>
            <div className="flex gap-2">
              <button disabled={busy} onClick={() => doSync(key)} className="btn-primary"><RefreshCw className={`size-4 ${busy ? "animate-spin" : ""}`} /> {t("Sync now")}</button>
              <button onClick={async () => { if (confirm(t("Turn off sync on this device? Local data stays."))) await setSyncKey(null); }} className="btn">{t("Turn off")}</button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <button disabled={busy} onClick={enable} className="btn-primary"><KeyRound className="size-4" /> {t("Turn on sync")}</button>
            <div>
              <label className="label">{t("Already have a key? Link this device")}</label>
              <div className="flex gap-2"><input value={input} onChange={(e) => setInput(e.target.value)} placeholder="OAK-XXXX-XXXX-…" className="input min-w-0 font-mono" /><button disabled={busy} onClick={join} className="btn">{t("Link")}</button></div>
            </div>
          </div>
        )}
      </Section>

      <Section title="Reminders" sub="Push notifications at each dose time, even when OAK is closed. The server only stores the minutes to ping you, never what you take.">
        {!canPush ? <p className="text-sm text-neutral-500">{t("This browser can't receive push notifications. On iPhone, add OAK to the Home Screen first.")}</p> : (
          <div className="flex flex-wrap gap-2">
            {push ? <>
              <span className="flex min-h-11 items-center gap-2 text-sm text-taken"><Bell className="size-4" /> {t("Reminders are on")}</span>
              <button className="btn" onClick={() => tryIt(testReminder, "Test sent")}>{t("Send test")}</button>
              <button className="btn" onClick={() => tryIt(disableReminders, "Reminders off")}>{t("Turn off")}</button>
            </> : <button className="btn-primary" onClick={() => tryIt(enableReminders, "Reminders on")}><Bell className="size-4" /> {t("Turn on reminders")}</button>}
          </div>
        )}
      </Section>

      <Section title="Circles" sub="Share this profile live and read-only with family or a coach. Each link has its own key; revoke it any time.">
        <div className="space-y-3">
          <div className="flex gap-2"><input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={t("Who is it for? e.g. Mom, Coach")} className="input min-w-0" /><button onClick={invite} className="btn-primary shrink-0"><Users className="size-4" /> {t("Invite")}</button></div>
          <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
            {circles.filter((c) => c.clientId === profile?.id).map((c) => (
              <li key={c.id} className="flex items-center gap-2 py-1.5 text-sm">
                <span className="min-w-0 flex-1 truncate">{c.label}</span>
                <button aria-label={t("Copy link")} className="btn w-11 px-0" onClick={() => { void navigator.clipboard.writeText(circleUrl(c)); toast.success(t("Invite link copied")); }}><Copy className="size-4" /></button>
                <button className="btn text-missed" onClick={() => tryIt(async () => { await revokeCircle(c.id); setCircles(await getCircles()); }, "Access revoked")}>{t("Revoke")}</button>
              </li>
            ))}
          </ul>
        </div>
      </Section>

      <Section title="Backup" sub="Export everything as JSON, or merge a backup back in (newest change wins).">
        <div className="flex flex-wrap gap-2">
          <button onClick={exportJson} className="btn"><Download className="size-4" /> {t("Export JSON")}</button>
          <button onClick={() => file.current?.click()} className="btn"><Upload className="size-4" /> {t("Import")}</button>
          <input ref={file} type="file" accept="application/json" hidden onChange={(e) => e.target.files?.[0] && importJson(e.target.files[0])} />
        </div>
      </Section>

      <Section title="Appearance & app" sub="Install OAK as an app. It works fully offline.">
        <div className="flex flex-wrap gap-2">
          <button onClick={() => theme("light")} className="btn">{t("Light")}</button>
          <button onClick={() => theme("dark")} className="btn">{t("Dark")}</button>
          <LangToggle />
          {installEvt && <button onClick={() => installEvt.prompt()} className="btn-primary"><Monitor className="size-4" /> {t("Install app")}</button>}
        </div>
      </Section>
    </div>
  );
}