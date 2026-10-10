"use client";
import { useT } from "@/lib/i18n";
import * as DM from "@radix-ui/react-dropdown-menu";
import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { toast } from "sonner";
import { db, PROFILE_COLORS, uid } from "@/lib/db";
import { useOak } from "@/lib/use-oak";

export function ProfileSwitcher({ compact }: { compact?: boolean }) {
  const { profiles, profile, setActive } = useOak();
  const t = useT();
  const add = async () => {
    const name = prompt(t("Profile name (e.g. Mom, Dad, Kid)"))?.trim();
    if (!name) return;
    const now = Date.now();
    const id = uid();
    await db.profiles.add({ id, name, color: PROFILE_COLORS[profiles.length % PROFILE_COLORS.length], createdAt: now, updatedAt: now });
    await setActive(id);
    toast.success(t("Switched to {name}", { name }));
  };
  const initial = (profile?.name ?? "?").slice(0, 1).toUpperCase();
  return (
    <DM.Root>
      <DM.Trigger className={compact ? "flex h-11 max-w-36 items-center gap-1.5 rounded-full border border-neutral-200 bg-white pl-1.5 pr-2 text-xs font-medium dark:border-neutral-800 dark:bg-neutral-900" : "flex h-10 w-full items-center gap-2 rounded-lg px-2 text-left hover:bg-neutral-200/50 dark:hover:bg-neutral-900"}>
        <span className="flex size-6 items-center justify-center rounded-full text-xs font-semibold text-white" style={{ background: profile?.color ?? "#999" }}>{initial}</span>
        <span className="min-w-0 flex-1 truncate text-sm font-medium">{profile?.name ?? "…"}</span>
        <ChevronsUpDown className="size-3.5 text-neutral-400" />
      </DM.Trigger>
      <DM.Portal>
        <DM.Content align="start" sideOffset={6} className="z-50 min-w-52 rounded-xl border border-neutral-200 bg-white p-1 shadow-xl animate-fade-in dark:border-neutral-800 dark:bg-neutral-900">
          <DM.Label className="px-2 py-1.5 text-xs text-neutral-400">{t("Profiles")}</DM.Label>
          {profiles.map((p) => (
            <DM.Item key={p.id} onSelect={() => setActive(p.id)} className="flex h-11 cursor-pointer items-center gap-2 rounded-lg px-2 text-sm outline-none md:h-8 data-[highlighted]:bg-neutral-100 dark:data-[highlighted]:bg-neutral-800">
              <span className="size-2.5 rounded-full" style={{ background: p.color }} /> {p.name}
              {p.id === profile?.id && <Check className="ml-auto size-4" />}
            </DM.Item>
          ))}
          <DM.Separator className="my-1 h-px bg-neutral-100 dark:bg-neutral-800" />
          <DM.Item onSelect={add} className="flex h-11 cursor-pointer items-center gap-2 rounded-lg px-2 text-sm text-neutral-600 md:h-8 outline-none data-[highlighted]:bg-neutral-100 dark:data-[highlighted]:bg-neutral-800">
            <Plus className="size-4" /> {t("Add profile")}
          </DM.Item>
        </DM.Content>
      </DM.Portal>
    </DM.Root>
  );
}