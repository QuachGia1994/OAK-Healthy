"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Eye, Lock, Users } from "lucide-react";
import { joinCircle, readCircle, readMemberCircle, type CircleView as CV } from "@/lib/circles";
import { getMe, type Me } from "@/lib/account";
import { toLocalDay } from "@/lib/domain/day";
import { dayStats, dosesForDay } from "@/lib/domain/schedule";
import { doseState } from "@/lib/domain/timing";
import { cycleLabel } from "@/lib/domain/cycle";
import type { Intake } from "@/lib/domain/types";
import { useT, locale } from "@/lib/i18n";
import { Logo, Ring, Sparkline, StatusBadge } from "./ui";
import { LangToggle } from "./lang-toggle";

const circleId = () => new URLSearchParams(location.search).get("id") ?? location.pathname.split("/").filter(Boolean)[1];

/** /c/<id>#<key>: bearer link. /c/<id>#j=<secret>&o=<owner key>: invite to join with an account (o pins the owner's
 * signing key; invites from before 2.5 lack it). /c/<id>: member read via session; refused snapshots show their reason. */
export function CircleView() {
  const t = useT();
  const [data, setData] = useState<CV | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [invite, setInvite] = useState<{ j: string; o: string | null } | null>(null);
  const [pending, setPending] = useState(false);
  const [joins, setJoins] = useState(0);
  useEffect(() => {
    const id = circleId(), frag = location.hash.slice(1);
    if (!id) { setErr("This link is incomplete."); return; }
    if (frag.startsWith("j=")) { const q = new URLSearchParams(frag); setInvite({ j: q.get("j") ?? "", o: q.get("o") }); return; }
    const read = frag ? () => readCircle(id, frag) : () => readMemberCircle(id);
    const keyError = frag ? "Wrong key in link." : "This device can't open the circle key. Ask the owner for a new invite.";
    const load = () => read().then((v) => { setPending(!v); if (v) { setData(v); setErr(null); } })
      .catch((e: Error) => setErr(e.name === "OperationError" ? keyError : e.message));
    void load();
    const iv = setInterval(load, 60_000);
    return () => clearInterval(iv);
  }, [joins]);
  const joined = () => {
    history.replaceState(null, "", location.pathname + location.search);
    setInvite(null); setErr(null); setJoins((n) => n + 1);
  };
  const now = Date.now(), today = toLocalDay(new Date(now));
  const intakes = new Map<string, Intake>((data?.intakes ?? []).map((i) => [i.key, i]));
  const routines = (data?.routines ?? []).filter((r) => !r.deletedAt).sort((a, b) => a.time.localeCompare(b.time));
  const doses = dosesForDay(routines, intakes, today);
  const taken = doses.filter((d) => d.intake?.status === "Taken").length;
  const week = dayStats(routines, intakes, today, 7);
  return (
    <div className="grid-bg min-h-dvh px-4 pb-10 pt-[max(env(safe-area-inset-top),16px)]">
      <div className="mx-auto max-w-xl">
        <div className="mb-6 flex items-center justify-between"><Link href="/" className="flex min-h-11 items-center gap-2"><Logo /><span className="text-sm font-semibold">OAK Healthy</span></Link><LangToggle /></div>
        {err && <div className="card p-6 text-sm text-missed">{t(err)}</div>}
        {invite && <JoinCard invite={invite} onJoined={joined} />}
        {!err && !invite && pending && <div className="card p-6 text-sm text-neutral-500">{t("You joined. This circle appears after the owner's app syncs next.")}</div>}
        {!err && !invite && !pending && !data && <div className="card h-64 animate-pulse" />}
        {data && (
          <div className="space-y-3 animate-fade-in">
            <div className="card p-5">
              <p className="flex items-center gap-1.5 text-xs text-neutral-500"><Lock className="size-3" /> {t("End-to-end encrypted circle")} · <Eye className="size-3" /> {t("read-only")}</p>
              <h1 className="mt-2 flex items-center gap-2 text-2xl font-semibold tracking-tight"><span className="size-3 rounded-full" style={{ background: data.profile.color }} />{data.profile.name}</h1>
              <p className="mt-1 text-sm text-neutral-500">{t("Updated {when}", { when: new Date(data.publishedAt).toLocaleString(locale()) })}</p>
              <div className="mt-4 flex items-center gap-4">
                <Ring value={doses.length ? taken / doses.length : null} size={88} stroke={8} label={t("today")} />
                <div className="min-w-0 flex-1"><p className="text-sm font-medium">{t("{a} of {b} taken", { a: taken, b: doses.length })}</p><div className="mt-2"><Sparkline values={week.map((s) => (s.planned ? s.taken / s.planned : null))} /></div><p className="text-xs text-neutral-500">{t("7-day adherence")}</p></div>
              </div>
            </div>
            <div className="card overflow-hidden">
              <p className="border-b border-neutral-100 px-5 py-3 text-sm font-medium dark:border-neutral-800">{t("Today’s timeline")}</p>
              <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
                {doses.map((d) => (
                  <li key={d.key} className="flex items-center gap-3 px-5 py-3">
                    <span className="w-11 font-mono text-xs text-neutral-500">{d.routine.time}</span>
                    <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{d.routine.name}</p><p className="truncate text-xs text-neutral-500">{d.routine.dose || "—"} · {cycleLabel(d.routine.cycle)}</p></div>
                    <StatusBadge state={doseState(d.intake?.status, d.at, now)} />
                  </li>
                ))}
                {doses.length === 0 && <li className="px-5 py-6 text-sm text-neutral-500">{t("Nothing scheduled")}</li>}
              </ul>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function JoinCard({ invite, onJoined }: { invite: { j: string; o: string | null }; onJoined: () => void }) {
  const t = useT();
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => { void getMe().then(setMe); }, []);
  const join = async () => {
    setBusy(true); setErr(null);
    try { await joinCircle(circleId(), invite.j, invite.o); onJoined(); } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  };
  return (
    <div className="card space-y-3 p-6">
      <p className="flex items-center gap-2 text-lg font-semibold tracking-tight"><Users className="size-5" /> {t("You're invited to a circle")}</p>
      <p className="text-sm text-neutral-500">{t("Join with your OAK account to see this profile live and read-only. The owner will see your account email.")}</p>
      {me === undefined && <div className="h-11 animate-pulse rounded-lg bg-neutral-100 dark:bg-neutral-800" />}
      {me === null && <>
        <Link href="/login/" className="btn-primary w-fit">{t("Sign in to join")}</Link>
        <p className="text-xs text-neutral-500">{t("Then open this invite link again.")}</p>
      </>}
      {me && <button disabled={busy} onClick={join} className="btn-primary w-fit"><Users className="size-4" /> {t("Join with my account")}</button>}
      {err && <p className="text-sm text-missed">{t(err)}</p>}
    </div>
  );
}
