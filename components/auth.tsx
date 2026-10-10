"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ArrowLeft, Eye, EyeOff, KeyRound, Loader2, LogOut, Mail, ShieldCheck, Trash2 } from "lucide-react";
import { deleteAccount, forgot, getMe, getProviders, login, logout, passwordProblem, resetPassword, rewrap, signup, adoptKey, type Me, type Providers } from "@/lib/account";
import { addPasskey, listPasskeys, passkeySupported, removePasskey, unlockWithPasskey, type PasskeyWrap } from "@/lib/passkey";
import { getSyncKey } from "@/lib/sync";
import { isValidKey } from "@/lib/crypto";
import { useT } from "@/lib/i18n";
import { Logo, PageHeader } from "./ui";
import { LangToggle } from "./lang-toggle";

function Shell({ title, sub, children, foot }: { title: string; sub?: string; children: React.ReactNode; foot?: React.ReactNode }) {
  const t = useT();
  return (
    <div className="flex min-h-dvh flex-col bg-neutral-50 px-4 pb-[max(env(safe-area-inset-bottom),24px)] pt-[max(env(safe-area-inset-top),16px)] dark:bg-neutral-950">
      <div className="mx-auto flex w-full max-w-sm items-center justify-between py-2">
        <Link href="/" className="flex min-h-11 items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400"><ArrowLeft className="size-4" /> {t("Back to app")}</Link>
        <LangToggle />
      </div>
      <main className="mx-auto mt-6 w-full max-w-sm animate-fade-in sm:mt-12">
        <div className="mb-6 flex flex-col items-center text-center"><Logo size={40} /><h1 className="mt-4 text-2xl font-semibold tracking-tight">{title}</h1>{sub && <p className="mt-1.5 text-sm text-neutral-500">{sub}</p>}</div>
        <div className="card p-5">{children}</div>
        {foot && <div className="mt-5 text-center text-sm text-neutral-600 dark:text-neutral-400">{foot}</div>}
        <p className="mt-6 flex items-start justify-center gap-1.5 text-center text-xs text-neutral-500"><ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-taken" />{t("Your password never leaves this device. It unlocks your encrypted vault; we can't read your data.")}</p>
      </main>
    </div>
  );
}

function Field({ label, ...p }: React.InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label className="block"><span className="label">{label}</span><input {...p} className="input" /></label>;
}
function Password({ label, value, onChange, autoComplete }: { label: string; value: string; onChange: (v: string) => void; autoComplete: string }) {
  const [show, setShow] = useState(false);
  const t = useT();
  return (
    <label className="block"><span className="label">{label}</span>
      <span className="relative block">
        <input type={show ? "text" : "password"} value={value} onChange={(e) => onChange(e.target.value)} autoComplete={autoComplete} required minLength={10} className="input pr-12" />
        <button type="button" onClick={() => setShow(!show)} aria-label={show ? t("Hide password") : t("Show password")} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-neutral-400 hover:text-neutral-700">{show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}</button>
      </span>
    </label>
  );
}
const Submit = ({ busy, children }: { busy: boolean; children: React.ReactNode }) => (
  <button type="submit" disabled={busy} className="btn-primary w-full">{busy && <Loader2 className="size-4 animate-spin" />}{children}</button>
);

const GoogleIcon = () => (<svg viewBox="0 0 24 24" className="size-4" aria-hidden><path fill="#EA4335" d="M12 10.2v3.9h5.5c-.2 1.3-1.6 3.9-5.5 3.9-3.3 0-6-2.7-6-6.1s2.7-6.1 6-6.1c1.9 0 3.2.8 3.9 1.5l2.7-2.6C16.9 3.1 14.7 2 12 2 6.5 2 2 6.5 2 12s4.5 10 10 10c5.8 0 9.6-4.1 9.6-9.8 0-.7-.1-1.2-.2-1.7H12z" /></svg>);
const GithubIcon = () => (<svg viewBox="0 0 24 24" className="size-4" aria-hidden fill="currentColor"><path d="M12 .5a11.5 11.5 0 0 0-3.6 22.4c.6.1.8-.3.8-.6v-2c-3.2.7-3.9-1.5-3.9-1.5-.5-1.3-1.3-1.7-1.3-1.7-1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1 1.8 2.8 1.3 3.5 1 .1-.8.4-1.3.7-1.6-2.6-.3-5.3-1.3-5.3-5.7 0-1.3.5-2.3 1.2-3.1-.1-.3-.5-1.5.1-3.1 0 0 1-.3 3.2 1.2a11 11 0 0 1 5.8 0c2.2-1.5 3.2-1.2 3.2-1.2.6 1.6.2 2.8.1 3.1.8.8 1.2 1.9 1.2 3.1 0 4.4-2.7 5.4-5.3 5.7.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A11.5 11.5 0 0 0 12 .5Z" /></svg>);

function Social() {
  const t = useT();
  const [p, setP] = useState<Providers | null>(null);
  useEffect(() => { void getProviders().then(setP); }, []);
  const btn = (id: "google" | "github", label: string, Icon: () => React.ReactElement) => (
    <a key={id} href={p?.[id] ? `/api/auth/oauth/${id}/start` : undefined} aria-disabled={!p?.[id]}
      onClick={(e) => { if (!p?.[id]) { e.preventDefault(); toast(t("{p} sign-in isn't set up on this server yet", { p: label })); } }}
      className={`btn w-full ${p?.[id] ? "" : "opacity-60"}`}><Icon /> {t("Continue with {p}", { p: label })}</a>
  );
  return (
    <div className="grid gap-2">
      {btn("google", "Google", GoogleIcon)}
      {btn("github", "GitHub", GithubIcon)}
      <div className="my-2 flex items-center gap-3 text-xs text-neutral-400"><span className="h-px flex-1 bg-neutral-200 dark:bg-neutral-800" />{t("or with email")}<span className="h-px flex-1 bg-neutral-200 dark:bg-neutral-800" /></div>
    </div>
  );
}

const OAUTH_ERR: Record<string, string> = {
  account_exists: "An account with this email already exists. Sign in with your password, then link the provider from Account.",
  email_unverified: "Your provider didn't confirm a verified email.",
  provider_off: "That sign-in provider isn't set up on this server yet.",
  oauth_state: "The sign-in took too long. Please try again.",
  oauth_denied: "Sign-in was cancelled.",
  oauth_token: "The provider rejected the sign-in. Please try again.",
  rate: "Too many attempts. Try again later.",
};

export function LoginView() {
  const t = useT();
  const router = useRouter();
  const [email, setEmail] = useState(""), [pw, setPw] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { const e = new URLSearchParams(location.search).get("error"); if (e) toast.error(t(OAUTH_ERR[e] ?? "Sign-in failed")); }, []); // eslint-disable-line
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true);
    try { await login(email, pw); toast.success(t("Signed in")); router.push("/"); }
    catch (err) { toast.error(t((err as Error).message)); }
    finally { setBusy(false); }
  };
  return (
    <Shell title={t("Sign in to OAK")} sub={t("Sync your stack across devices")} foot={<>{t("New here?")} <Link href="/signup/" className="inline-flex min-h-11 items-center font-medium text-taken underline-offset-4 hover:underline">{t("Create an account")}</Link></>}>
      <Social />
      <form onSubmit={submit} className="grid gap-3">
        <Field label={t("Email")} type="email" autoComplete="username" inputMode="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Password label={t("Password")} value={pw} onChange={setPw} autoComplete="current-password" />
        <div className="-mt-1 text-right"><Link href="/forgot/" className="inline-flex min-h-11 items-center text-sm text-neutral-600 underline-offset-4 hover:underline dark:text-neutral-400">{t("Forgot password?")}</Link></div>
        <Submit busy={busy}>{busy ? t("Unlocking vault…") : t("Sign in")}</Submit>
      </form>
    </Shell>
  );
}

export function SignupView() {
  const t = useT();
  const router = useRouter();
  const [email, setEmail] = useState(""), [pw, setPw] = useState(""), [pw2, setPw2] = useState(""), [busy, setBusy] = useState(false);
  const problem = pw ? passwordProblem(pw, email) : null;
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (problem) return toast.error(t(problem));
    if (pw !== pw2) return toast.error(t("Passwords don't match"));
    setBusy(true);
    try { await signup(email, pw); toast.success(t("Account created")); router.push("/account/?new=1"); }
    catch (err) { toast.error(t((err as Error).message)); }
    finally { setBusy(false); }
  };
  return (
    <Shell title={t("Create your account")} sub={t("Your current data on this device comes with you")} foot={<>{t("Already have an account?")} <Link href="/login/" className="inline-flex min-h-11 items-center font-medium text-taken underline-offset-4 hover:underline">{t("Sign in")}</Link></>}>
      <Social />
      <form onSubmit={submit} className="grid gap-3">
        <Field label={t("Email")} type="email" autoComplete="username" inputMode="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Password label={t("Password")} value={pw} onChange={setPw} autoComplete="new-password" />
        <p className={`-mt-1 text-xs ${problem ? "text-missed" : "text-neutral-500"}`}>{t(problem ?? "At least 10 characters. A passphrase of 3–4 words works well.")}</p>
        <Password label={t("Confirm password")} value={pw2} onChange={setPw2} autoComplete="new-password" />
        <Submit busy={busy}>{busy ? t("Encrypting…") : t("Create account")}</Submit>
        <p className="text-xs text-neutral-500">{t("By continuing you agree to the privacy policy. OAK is not a medical device.")}</p>
      </form>
    </Shell>
  );
}

export function ForgotView() {
  const t = useT();
  const [email, setEmail] = useState(""), [busy, setBusy] = useState(false), [sent, setSent] = useState<string | null>(null), [dev, setDev] = useState<string | null>(null);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true);
    try { const r = await forgot(email); setSent(r.message); setDev(r.devLink ?? null); }
    catch (err) { toast.error(t((err as Error).message)); }
    finally { setBusy(false); }
  };
  return (
    <Shell title={t("Reset your password")} sub={t("We'll email you a link that works for 30 minutes")} foot={<Link href="/login/" className="inline-flex min-h-11 items-center font-medium text-taken underline-offset-4 hover:underline">{t("Back to sign in")}</Link>}>
      {sent ? (
        <div className="space-y-3 text-sm">
          <p className="flex items-start gap-2"><Mail className="mt-0.5 size-4 shrink-0 text-taken" />{t(sent)}</p>
          <p className="text-neutral-500">{t("Keep your OAK sync key handy: it is your recovery code and keeps your encrypted data after the reset.")}</p>
          {dev && <a href={dev} className="btn w-full">Dev: open reset link</a>}
        </div>
      ) : (
        <form onSubmit={submit} className="grid gap-3">
          <Field label={t("Email")} type="email" autoComplete="username" inputMode="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <Submit busy={busy}>{t("Send reset link")}</Submit>
        </form>
      )}
    </Shell>
  );
}

export function ResetView() {
  const t = useT();
  const router = useRouter();
  const [token, setToken] = useState(""), [pw, setPw] = useState(""), [pw2, setPw2] = useState(""), [rec, setRec] = useState(""), [busy, setBusy] = useState(false), [hasLocal, setHasLocal] = useState(false);
  useEffect(() => { setToken(new URLSearchParams(location.hash.slice(1)).get("t") ?? ""); void getSyncKey().then((k) => setHasLocal(!!k)); }, []);
  const problem = pw ? passwordProblem(pw) : null;
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (problem) return toast.error(t(problem));
    if (pw !== pw2) return toast.error(t("Passwords don't match"));
    if (rec && !isValidKey(rec)) return toast.error(t("That doesn't look like an OAK sync key"));
    setBusy(true);
    try {
      const r = await resetPassword(token, pw, rec || undefined);
      toast.success(t("Password updated"), { description: r.rotated ? t("A new encrypted vault was created from the data on this device.") : undefined });
      history.replaceState(null, "", "/reset/");
      router.push("/account/");
    } catch (err) { toast.error(t((err as Error).message)); }
    finally { setBusy(false); }
  };
  return (
    <Shell title={t("Choose a new password")} foot={<Link href="/login/" className="inline-flex min-h-11 items-center font-medium text-taken underline-offset-4 hover:underline">{t("Back to sign in")}</Link>}>
      {!token ? <p className="text-sm text-missed">{t("This reset link is invalid or has expired.")}</p> : (
        <form onSubmit={submit} className="grid gap-3">
          <Password label={t("New password")} value={pw} onChange={setPw} autoComplete="new-password" />
          <Password label={t("Confirm password")} value={pw2} onChange={setPw2} autoComplete="new-password" />
          {hasLocal ? <p className="rounded-lg bg-taken-soft p-3 text-xs text-taken">{t("This device has your sync key, so your encrypted data is kept.")}</p> : (
            <label className="block"><span className="label">{t("Recovery code (your OAK sync key)")}</span>
              <input value={rec} onChange={(e) => setRec(e.target.value)} placeholder="OAK-XXXX-XXXX-…" autoComplete="off" className="input font-mono" />
              <span className="mt-1 block text-xs text-neutral-500">{t("Without it, your cloud data can't be decrypted. We'll start a fresh vault from this device's data.")}</span>
            </label>
          )}
          <Submit busy={busy}>{t("Update password")}</Submit>
        </form>
      )}
    </Shell>
  );
}

export function AccountView() {
  const t = useT();
  const router = useRouter();
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [keys, setKeys] = useState<PasskeyWrap[]>([]);
  const [hasKey, setHasKey] = useState(false);
  const [pw, setPw] = useState("");
  const [busy, setBusy] = useState(false);
  const load = async () => {
    const u = await getMe(); setMe(u); setHasKey(!!(await getSyncKey()));
    if (u) setKeys(await listPasskeys().catch(() => []));
  };
  useEffect(() => { void load(); }, []);
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try { await fn(); toast.success(t(ok)); await load(); } catch (e) { toast.error(t((e as Error).message)); } finally { setBusy(false); }
  };
  if (me === undefined) return <div className="h-64 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900" />;
  if (!me) return (
    <div className="animate-fade-in">
      <PageHeader title={t("Account")} sub={t("Optional. OAK works fully without an account.")} />
      <div className="card flex flex-col gap-3 p-5 sm:flex-row sm:items-center">
        <p className="flex-1 text-sm text-neutral-600 dark:text-neutral-400">{t("Sign in to unlock your encrypted vault on any device with just your email and password.")}</p>
        <div className="flex gap-2"><Link href="/login/" className="btn flex-1 sm:flex-none">{t("Sign in")}</Link><Link href="/signup/" className="btn-primary flex-1 sm:flex-none">{t("Create account")}</Link></div>
      </div>
    </div>
  );
  return (
    <div className="animate-fade-in space-y-3">
      <PageHeader title={t("Account")} sub={me.email}>
        <button className="btn" onClick={() => run(async () => { await logout(); router.push("/"); }, "Signed out")}><LogOut className="size-4" /> {t("Sign out")}</button>
      </PageHeader>
      <section className="card space-y-3 p-5">
        <h2 className="text-sm font-semibold">{t("Vault")}</h2>
        {hasKey ? <p className="flex items-center gap-2 text-sm text-taken"><KeyRound className="size-4" />{t("Unlocked on this device")}</p> : <p className="text-sm text-skipped">{t("Locked on this device. Unlock with a passkey or paste your sync key in Settings.")}</p>}
        {me.hasPassword && !me.wrapped && hasKey && (
          <div className="flex flex-col gap-2 sm:flex-row"><input type="password" value={pw} onChange={(e) => setPw(e.target.value)} placeholder={t("Password")} autoComplete="current-password" className="input" />
            <button disabled={busy || !pw} className="btn-primary" onClick={() => run(() => rewrap(me.email, pw), "Vault linked to your password")}>{t("Link vault to password")}</button></div>
        )}
        <p className="text-xs text-neutral-500">{t("Signed in with")}: {[me.hasPassword && t("password"), ...me.providers.map((p) => (p === "google" ? "Google" : "GitHub"))].filter(Boolean).join(", ")}</p>
      </section>
      <section className="card space-y-3 p-5">
        <div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="text-sm font-semibold">{t("Passkeys")}</h2><p className="text-xs text-neutral-500">{t("Unlock your vault with Face ID, Touch ID or a security key (needs WebAuthn PRF).")}</p></div>
          {passkeySupported() && hasKey && <button disabled={busy} className="btn" onClick={() => run(() => addPasskey(me.email, navigator.platform || "Passkey"), "Passkey added")}>{t("Add passkey")}</button>}</div>
        <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
          {keys.map((k) => (
            <li key={k.id} className="flex items-center gap-2 py-2 text-sm">
              <span className="flex-1 truncate">{k.label} <span className="text-xs text-neutral-500">· {new Date(k.createdAt).toLocaleDateString()}</span></span>
              {!hasKey && <button className="btn" disabled={busy} onClick={() => run(async () => adoptKey(await unlockWithPasskey(k)), "Vault unlocked")}>{t("Unlock")}</button>}
              <button aria-label={t("Remove")} className="btn px-0 w-11" onClick={() => run(() => removePasskey(k.id), "Passkey removed")}><Trash2 className="size-4" /></button>
            </li>
          ))}
          {keys.length === 0 && <li className="py-2 text-sm text-neutral-500">{t("No passkeys yet.")}</li>}
        </ul>
      </section>
      <section className="card flex flex-wrap items-center justify-between gap-3 p-5">
        <div><h2 className="text-sm font-semibold text-missed">{t("Delete account")}</h2><p className="text-xs text-neutral-500">{t("Removes your login and passkeys. Data on this device and your encrypted vault stay until you delete them in Settings.")}</p></div>
        <button className="btn text-missed" onClick={() => { if (confirm(t("Delete your account?"))) void run(async () => { await deleteAccount(); router.push("/"); }, "Account deleted"); }}>{t("Delete")}</button>
      </section>
    </div>
  );
}
