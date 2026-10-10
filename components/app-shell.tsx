"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect } from "react";
import { Toaster } from "sonner";
import { BarChart3, Command, Layers, LayoutGrid, Plus, Settings } from "lucide-react";
import { CommandMenu, openCommand } from "./command-menu";
import { ProfileSwitcher } from "./profile-switcher";
import { RoutineDrawer, openRoutine } from "./routine-drawer";
import { useHotkey } from "@/lib/use-oak";
import { Logo } from "./ui";
import { SyncAgent } from "./settings";

export const NAV = [
  { href: "/", label: "Today", icon: LayoutGrid },
  { href: "/stack/", label: "Stack", icon: Layers },
  { href: "/insights/", label: "Insights", icon: BarChart3 },
  { href: "/settings/", label: "Settings", icon: Settings },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const isShare = path === "/s" || !!path?.startsWith("/s/");
  const create = useCallback(() => openRoutine(null), []);
  useHotkey("c", create);
  useEffect(() => {
    if ("serviceWorker" in navigator && location.hostname !== "localhost") navigator.serviceWorker.register("/sw.js").catch(() => {});
  }, []);
  const active = (href: string) => (href === "/" ? path === "/" : path?.startsWith(href.replace(/\/$/, "")));

  if (isShare) return (<>{children}<Toaster position="bottom-right" /></>);

  return (
    <div className="flex min-h-dvh bg-neutral-50 dark:bg-neutral-950">
      <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 flex-col border-r border-neutral-200 bg-neutral-50 p-3 md:flex dark:border-neutral-800 dark:bg-neutral-950">
        <Link href="/" className="mb-4 flex items-center gap-2 px-2 pt-1"><Logo /><span className="text-sm font-semibold tracking-tight">OAK Healthy</span></Link>
        <ProfileSwitcher />
        <button onClick={openCommand} className="mt-3 flex h-8 items-center gap-2 rounded-lg border border-neutral-200 bg-white px-2.5 text-sm text-neutral-500 hover:border-neutral-300 dark:border-neutral-800 dark:bg-neutral-900">
          <Command className="size-3.5" /> Search… <span className="ml-auto flex gap-0.5"><span className="kbd">⌘</span><span className="kbd">K</span></span>
        </button>
        <nav className="mt-4 grid gap-0.5">
          {NAV.map(({ href, label, icon: Icon }) => (
            <Link key={href} href={href} className={`flex h-8 items-center gap-2.5 rounded-lg px-2.5 text-sm transition ${active(href) ? "bg-neutral-200/70 font-medium text-neutral-900 dark:bg-neutral-800 dark:text-white" : "text-neutral-600 hover:bg-neutral-200/40 dark:text-neutral-400 dark:hover:bg-neutral-900"}`}>
              <Icon className="size-4" /> {label}
            </Link>
          ))}
        </nav>
        <button onClick={create} className="btn-primary mt-4 w-full"><Plus className="size-4" /> New routine <span className="kbd ml-auto border-white/20 bg-white/10 text-white/70 dark:text-neutral-600">C</span></button>
        <div className="mt-auto rounded-xl border border-neutral-200 bg-white p-3 text-xs text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
          <p className="font-medium text-neutral-800 dark:text-neutral-200">Private by design</p>
          <p className="mt-1">Data lives on this device. Sync is end-to-end encrypted.</p>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-2 border-b border-neutral-200 bg-white/80 px-4 backdrop-blur md:hidden dark:border-neutral-800 dark:bg-neutral-950/80">
          <Logo /><span className="text-sm font-semibold">OAK</span>
          <div className="ml-auto flex items-center gap-2"><ProfileSwitcher compact /><button aria-label="Search" onClick={openCommand} className="btn px-2"><Command className="size-4" /></button></div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-28 pt-6 md:px-8 md:pb-12 md:pt-8">{children}</main>
        <nav className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-neutral-200 bg-white/90 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden dark:border-neutral-800 dark:bg-neutral-950/90">
          {NAV.slice(0, 2).map(({ href, label, icon: Icon }) => <MobileTab key={href} href={href} label={label} Icon={Icon} on={!!active(href)} />)}
          <button onClick={create} aria-label="New routine" className="flex items-center justify-center"><span className="flex size-11 items-center justify-center rounded-full bg-taken text-white shadow-lg shadow-taken/30"><Plus className="size-5" /></span></button>
          {NAV.slice(2).map(({ href, label, icon: Icon }) => <MobileTab key={href} href={href} label={label} Icon={Icon} on={!!active(href)} />)}
        </nav>
      </div>
      <CommandMenu />
      <SyncAgent />
      <RoutineDrawer />
      <Toaster position="bottom-right" toastOptions={{ className: "!rounded-xl !border-neutral-200 !font-sans" }} />
    </div>
  );
}

function MobileTab({ href, label, Icon, on }: { href: string; label: string; Icon: React.ComponentType<{ className?: string }>; on: boolean }) {
  return (
    <Link href={href} className={`flex h-16 flex-col items-center justify-center gap-1 text-[11px] ${on ? "text-neutral-900 dark:text-white" : "text-neutral-400"}`}>
      <Icon className="size-5" />{label}
    </Link>
  );
}