"use client";
import { useEffect, useRef, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { toast } from "sonner";
import Link from "next/link";
import { Bell, BellOff, Copy, Download, KeyRound, Loader2, Monitor, RefreshCw, Trash2, Upload, UserRound, Users } from "lucide-react";
import { useT, locale } from "@/lib/i18n";
import { LangToggle } from "./lang-toggle";
import { deleteOneShare, getMe, pendingWipe, wipeEverything, type Me } from "@/lib/account";
import { takeWipedFlag, WIPED_FLAG, type ShareRef, type WipeStep } from "@/lib/domain/wipe";
import { parseSharesGone, shareRows } from "@/lib/domain/shares";
import { disableReminders, enableReminders, pushReminderTimes, pushSupported, testReminder } from "@/lib/reminders";
import { circleMembers, circleUrl, createCircle, ensureSigner, ensureSigners, getCircles, isOutOfStep, publishCircle, publishCircles, revokeCircle, revokeMember, type Circle, type CircleMember } from "@/lib/circles";
import { adoptSyncedProfiles, db, mergeSnapshot, saveRoutine, snapshot, type Snapshot } from "@/lib/db";
import { isValidKey, newSyncKey, normalizeKey } from "@/lib/crypto";
import { safeNext } from "@/lib/domain/safe-next";
import { getSyncKey, requestBackgroundSync, setSyncKey, syncNow, type SyncResult } from "@/lib/sync";
import { DeviceQr } from "./device-qr";
import { useOak } from "@/lib/use-oak";
import { PageHeader } from "./ui";

/** Delete everything on another device left a vault tombstone (410); syncNow has already turned sync off here and
 * carries the notice in r.error. */
const vaultGone = (r: SyncResult): r is Extract<SyncResult, { ok: false }> => !r.ok && r.gone === true;

/** Sync on load, on focus, 4s after local changes, and when the service worker fires a Background Sync.
 * Offline or failed pushes register a one-off "oak-sync" so the browser retries even after the tab closes. */
export function SyncAgent() {
  const tr = useT();
  useEffect(() => {
    // The reload that ends Delete everything (DeleteEverything below). Deferred: the Toaster mounts after this effect.
    const wiped = takeWipedFlag(sessionStorage);
    if (!wiped) return;
    const n = wiped.sharesKept;
    const id = setTimeout(() => toast.success(tr("Everything deleted"), n ? {
      description: tr(n === 1 ? "1 share link couldn't be deleted. It expires 365 days after it was created." : "{n} share links couldn't be deleted. They expire 365 days after they were created.", { n }),
      duration: 15000,
    } : undefined), 0);
    return () => clearTimeout(id);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    let t: ReturnType<typeof setTimeout> | undefined, busy = false;
    const run = async (fromSw = false) => {
      if (busy) return;
      const k = await getSyncKey();
      if (!navigator.onLine) { if (k) void requestBackgroundSync(); return; }
      busy = true;
      try {
        if (k) {
          // Pre-2.5 circles: mint signing keys before the push, so this run's vault carries them (lib/circles.ts).
          await ensureSigners().catch(() => {});
          const r = await syncNow(k);
          // Network failures only (server errors would just loop); the SW-triggered run never re-registers itself.
          if (!r.ok && !fromSw && /fetch|network|load failed/i.test(r.error)) void requestBackgroundSync();
          // Once: sync is off now, so the next run has no key.
          if (vaultGone(r)) toast(tr(r.error), { duration: 15000 });
          window.dispatchEvent(new CustomEvent("oak:synced", { detail: r }));
        }
        await Promise.all([pushReminderTimes().catch(() => {}), publishCircles().catch(() => {})]);
      } finally { busy = false; }
    };
    const now = () => void run();
    const later = () => { clearTimeout(t); t = setTimeout(now, 4000); };
    const onSw = (e: MessageEvent) => { if ((e.data as { type?: string } | null)?.type === "oak-sync") void run(true); };
    now();
    window.addEventListener("oak:changed", later);
    window.addEventListener("focus", now);
    window.addEventListener("online", now);
    navigator.serviceWorker?.addEventListener("message", onSw);
    return () => { clearTimeout(t); window.removeEventListener("oak:changed", later); window.removeEventListener("focus", now); window.removeEventListener("online", now); navigator.serviceWorker?.removeEventListener("message", onSw); };
  }, []);
  return null;
}

function CircleRow({ c, onChange, tryIt }: { c: Circle; onChange: () => Promise<void>; tryIt: (fn: () => Promise<unknown>, ok: string) => Promise<void> }) {
  const t = useT();
  const [members, setMembers] = useState<CircleMember[]>([]);
  const behind = useLiveQuery(() => db.meta.get("circleBehind"), [], undefined)?.value as Record<string, number> | undefined;
  useEffect(() => {
    if (!c.j) return;
    // Circles from before 2.5: create the signing key now, so the copied invite carries it (#o=).
    if (!c.sign) { void ensureSigner(c.id).then(onChange).catch(() => {}); return; }
    // A join waits for a publish; publishing here lets a new member in as soon as the owner opens Settings.
    const load = () => circleMembers(c).then((ms) => { setMembers(ms); return ms; });
    void load().then((ms) => (ms.some((m) => !m.active) ? publishCircle(c).then(load) : ms)).catch(() => {});
  }, [c]);
  const copy = () => { void navigator.clipboard.writeText(circleUrl(c)); toast.success(t("Invite link copied")); };
  return (
    <li className="py-1.5 text-sm">
      <div className="flex items-center gap-2">
        <span className="min-w-0 flex-1 truncate">{c.label}{!c.j && <span className="chip ml-2 text-xs text-neutral-500">{t("Bearer link")}</span>}</span>
        <button aria-label={t("Copy link")} className="btn w-11 px-0" onClick={copy}><Copy className="size-4" /></button>
        <button className="btn text-missed" onClick={() => tryIt(async () => { await revokeCircle(c.id); await onChange(); }, "Access revoked")}>{t(c.j ? "Delete circle" : "Revoke")}</button>
      </div>
      {/* A pre-2.5 circle's new signing key cannot sign until a vault sync carries it (lib/circles.ts ensureSigner). */}
      {c.j && c.sign?.pending && <p className="mt-1 pl-3 text-xs text-neutral-500">{t("Securing this circle. Members see updates after the next sync.")}</p>}
      {/* Published under a newer invite this device lacks (lib/circles.ts publishCircle); clears once the vault brings it. */}
      {isOutOfStep(c, behind) && <p className="mt-1 pl-3 text-xs text-neutral-500">{t("Another device changed this circle's invite and hasn't synced yet. Members still get updates; new members need the invite link from that device.")}</p>}
      {members.length > 0 && <ul className="mt-1 space-y-1 pl-3">
        {members.map((m) => (
          <li key={m.userId} className="flex items-center gap-2 text-xs text-neutral-500">
            <UserRound className="size-3.5 shrink-0" /><span className="min-w-0 flex-1 truncate">{m.email ?? m.userId}</span>
            <span>{t(m.active ? "Can view" : "Waiting for sync")}</span>
            <button className="btn min-h-8 text-missed" onClick={() => tryIt(async () => { await revokeMember(c.id, m.userId); await onChange(); }, "Member removed. The invite link changed.")}>{t("Remove")}</button>
          </li>
        ))}
      </ul>}
    </li>
  );
}

/** Share links card (docs/feat/delete-everything.md § Share links): every link in meta `shares` minus `sharesGone`,
 * newest first. Delete asks inline first; the title is rendered as text only and never leaves the device in the DELETE. */
export function ShareLinks() {
  const t = useT();
  const meta = useLiveQuery(() => db.meta.bulkGet(["shares", "sharesGone"]), [], undefined);
  const [confirm, setConfirm] = useState<string | null>(null), [busy, setBusy] = useState<string | null>(null);
  if (!meta) return null;
  const now = Date.now();
  const list = Array.isArray(meta[0]?.value) ? (meta[0].value as ShareRef[]) : [];
  const rows = shareRows(list, parseSharesGone(meta[1]?.value, now), now);
  const day = (ms: number) => new Date(ms).toLocaleDateString(locale());
  const del = async (id: string) => {
    setConfirm(null); setBusy(id);
    try {
      const r = await deleteOneShare(id);
      if (r.outcome === "deleted") toast.success(t("Share link deleted"));
      // The row stays, marked (C4), so the toast is no longer the only trace: default duration.
      else if (r.outcome === "kept") toast(t("This share link couldn't be deleted. It expires {date}.", { date: day(r.expiresAt) }));
      // No raw fetch/HTTP text: offline and 5xx show the plain message; only the 429 says why.
      else toast.error(r.tooMany ? `${t("Couldn't delete the share link.")} ${t("Too many attempts. Try again later.")}` : t("Couldn't delete the share link."));
    } finally { setBusy(null); }
  };
  if (!rows.length) return <p className="text-sm text-neutral-500">{t("No share links yet. Use Share on the Stack page to create one.")}</p>;
  return (
    <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
      {rows.map((r) => (
        <li key={r.id} className="py-1.5 text-sm">
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1">
              <p className="truncate">{r.title ?? t("Untitled stack")}</p>
              <p className="text-xs text-neutral-500">
                {r.createdAt !== null && <>{t("Created {date}", { date: day(r.createdAt) })} · </>}
                {t(r.expired ? "Expired {date}" : "Expires {date}", { date: day(r.expiresAt) })}
              </p>
            </div>
            {/* C4: the server refused this link's DELETE for good; a retry gets the same answer, so no Delete. */}
            {r.refused ? <span className="chip shrink-0 text-xs text-neutral-500">{t("Couldn't be deleted")}</span>
              : busy === r.id ? <span role="status" className="btn w-11 px-0" aria-label={t("Deleting…")}><Loader2 className="size-4 animate-spin" /></span>
              : confirm !== r.id && <button className="btn text-missed" disabled={!!busy} onClick={() => setConfirm(r.id)}><Trash2 className="size-4" /> {t("Delete")}</button>}
          </div>
          {confirm === r.id && busy !== r.id && (
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <span className="text-xs">{t("Delete this share link? People with it lose access.")}</span>
              <button className="btn" onClick={() => setConfirm(null)}>{t("Cancel")}</button>
              <button className="btn text-missed" onClick={() => void del(r.id)}>{t("Yes, delete")}</button>
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}

const WIPE_DOING: Record<WipeStep, string> = {
  circles: "Deleting your circles…", reminders: "Turning off reminders…", shares: "Deleting your share links…", vault: "Deleting your encrypted vault…", account: "Deleting your account…", device: "Clearing this device…",
};
const WIPE_FAILED: Record<WipeStep, string> = {
  circles: "Couldn't delete your circles.", reminders: "Couldn't turn off reminders.", shares: "Couldn't delete your share links.", vault: "Couldn't delete your encrypted vault.", account: "Couldn't delete your account.", device: "Couldn't clear this device.",
};

/** One action, behind an explicit confirm, deletes the circles you own, reminders, share links, the vault, the account
 * and this device's data, in that order (lib/account.ts wipeEverything). A failure stops there; Retry continues where it stopped. */
export function DeleteEverything({ signedIn }: { signedIn: boolean }) {
  const t = useT();
  const [confirming, setConfirming] = useState(false), [step, setStep] = useState<WipeStep | null>(null);
  const [failed, setFailed] = useState<{ step: WipeStep; error: string } | null>(null), [unfinished, setUnfinished] = useState(false);
  useEffect(() => { void pendingWipe().then((p) => setUnfinished(!!p)); }, []);
  const run = async () => {
    setFailed(null); setConfirming(false);
    const r = await wipeEverything(signedIn, setStep);
    setStep(null);
    if (r.ok) {
      // Shown after the reload by SyncAgent; a toast here would vanish with the page.
      try { sessionStorage.setItem(WIPED_FLAG, String(r.sharesKept)); } catch { /* storage off: no notice */ }
      location.replace("/"); return;
    }
    setFailed(r); setUnfinished(true); toast.error(t(WIPE_FAILED[r.step]));
  };
  return (
    <section className="card space-y-3 p-5">
      <div><h2 className="text-sm font-semibold text-missed">{t("Delete everything")}</h2>
        <p className="mt-1 text-xs text-neutral-500">{t(signedIn
          ? "Deletes the circles you own, your reminders, your share links, your encrypted vault, your account and all data on this device. Members lose access at once. This can't be undone."
          : "Deletes the circles you own, your reminders, your share links, your encrypted vault (if sync is on) and all data on this device. Members lose access at once. This can't be undone.")}</p></div>
      {step ? <p role="status" className="flex min-h-11 items-center gap-2 text-sm"><Loader2 className="size-4 animate-spin" /> {t(WIPE_DOING[step])}</p>
        : failed || unfinished ? (
          <div role="alert" className="space-y-2">
            <p className="text-sm text-missed">{failed ? `${t(WIPE_FAILED[failed.step])} ${t(failed.error)}` : t("Deleting everything didn't finish.")}</p>
            <p className="text-xs text-neutral-500">{t("Retry continues where it stopped.")}</p>
            <button className="btn text-missed" onClick={run}>{t("Retry")}</button>
          </div>
        ) : confirming ? (
          <div className="space-y-2">
            <p className="text-sm font-medium">{t("Delete everything? This can't be undone.")}</p>
            <div className="flex flex-wrap gap-2"><button className="btn" onClick={() => setConfirming(false)}>{t("Cancel")}</button><button className="btn text-missed" onClick={run}><Trash2 className="size-4" /> {t("Yes, delete everything")}</button></div>
          </div>
        ) : <button className="btn text-missed" onClick={() => setConfirming(true)}><Trash2 className="size-4" /> {t("Delete everything")}</button>}
    </section>
  );
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
  const mine = useLiveQuery(() => db.routines.where("clientId").equals(profile?.id ?? "").toArray(), [profile?.id], [])
    .filter((r) => !r.deletedAt && !r.paused).sort((a, b) => a.time.localeCompare(b.time));
  useEffect(() => { setCanPush(pushSupported()); void getMe().then(setMe); void getCircles().then(setCircles); }, []);
  // Circle state also arrives from the owner's other devices through sync.
  useEffect(() => {
    const reload = () => void getCircles().then(setCircles);
    window.addEventListener("oak:synced", reload);
    return () => window.removeEventListener("oak:synced", reload);
  }, []);
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
  // QR handoff: another device's QR opens /settings/#link=<key>. Strip it from the address bar right away.
  useEffect(() => {
    // next= (old-domain move, components/domain-move.tsx): the page the visitor asked for, same-origin paths only.
    const m = location.hash.match(/^#link=([^&]+)(?:&next=(.+))?$/);
    if (!m) return;
    history.replaceState(null, "", location.pathname + location.search);
    let next = "";
    try { next = m[2] ? decodeURIComponent(m[2]) : ""; } catch { /* malformed next= is ignored */ }
    const go = () => { const to = safeNext(next, location.origin, location.pathname); if (to) location.replace(to); };
    let k = "";
    try { k = decodeURIComponent(m[1]).trim().toUpperCase(); } catch { /* malformed key falls to the invalid-key toast */ }
    if (!isValidKey(k)) { toast.error(t("That doesn't look like an OAK sync key")); return go(); }
    void (async () => {
      const cur = await getSyncKey();
      if (cur && normalizeKey(cur) === normalizeKey(k)) { toast.success(t("This device is already linked")); return go(); }
      if (cur) { toast.error(t("This device already syncs with another key. Turn off sync first.")); return go(); }
      if (!confirm(t("Link this device to the encrypted vault from your other device?"))) return go();
      await setSyncKey(k);
      if (await doSync(k)) { await adoptSyncedProfiles(); await syncNow(k); }
      go();
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
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
            <DeviceQr syncKey={key} />
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
              {mine.length > 0 && <div className="basis-full">
                <p className="label mt-2">{t("Remind me for")}</p>
                <div className="flex flex-wrap gap-1.5">
                  {mine.map((r) => { const on = r.remind !== false; return (
                    <button key={r.id} aria-pressed={on} onClick={() => void saveRoutine({ ...r, remind: !on })}
                      className={`chip chip-btn gap-1.5 ${on ? "border-taken/30 bg-taken-soft text-taken" : "border-neutral-200 text-neutral-500 line-through dark:border-neutral-800"}`}>
                      {on ? <Bell className="size-3.5" /> : <BellOff className="size-3.5" />}<span className="font-mono text-xs">{r.time}</span> {r.name}
                    </button>); })}
                </div>
                <p className="mt-2 text-xs text-neutral-500">{t("Each notification has Taken and Snooze 10 min buttons.")}</p>
              </div>}
            </> : <button className="btn-primary" onClick={() => tryIt(enableReminders, "Reminders on")}><Bell className="size-4" /> {t("Turn on reminders")}</button>}
          </div>
        )}
      </Section>

      <Section title="Circles" sub="Share this profile live and read-only with family or a coach. People join with their own OAK account; remove anyone any time and the others keep access.">
        <div className="space-y-3">
          <div className="flex gap-2"><input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={t("Who is it for? e.g. Mom, Coach")} className="input min-w-0" /><button onClick={invite} className="btn-primary shrink-0"><Users className="size-4" /> {t("Invite")}</button></div>
          <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
            {circles.filter((c) => c.clientId === profile?.id).map((c) => (
              <CircleRow key={`${c.id}:${c.j ?? ""}`} c={c} tryIt={tryIt} onChange={async () => setCircles(await getCircles())} />
            ))}
          </ul>
        </div>
      </Section>

      <Section title="Share links" sub="Links you made with Share on the Stack page. Delete one to make it stop working for anyone who has it.">
        <ShareLinks />
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

      <DeleteEverything signedIn={!!me} />
    </div>
  );
}