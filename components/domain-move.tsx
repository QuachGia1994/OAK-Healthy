"use client";
import { useEffect, useState } from "react";
import { db, getMeta } from "@/lib/db";
import { getSyncKey, syncNow } from "@/lib/sync";
import { disableReminders } from "@/lib/reminders";
import { useT } from "@/lib/i18n";

export const OLD_HOST = "oak-healthy.kim-phong619.workers.dev";
export const NEW_ORIGIN = "https://healthy.oakgatekeeper.uk";

/** True when this device holds anything the new origin could not get back without the old one. The app seeds a
 * default profile on first load, so profiles alone do not count. */
async function hasLocalData() {
  const counts = await Promise.all(db.tables.filter((x) => x.name !== "meta" && x.name !== "profiles").map((x) => x.count()));
  const circles = await getMeta<unknown[]>("circles", []);
  const pins = await getMeta<Record<string, string>>("circlePins", {});
  return counts.some((n) => n > 0) || circles.length > 0 || Object.keys(pins).length > 0;
}

/** On-device data is per origin, so the old address never just redirects: a synced device pushes first, turns
 * off its old-origin reminders and hands its key to the new address (the #link flow, which then opens the page
 * the visitor asked for via next=); an empty device goes straight over; a device with local-only data stays. */
export function DomainMove() {
  const t = useT();
  const [stuck, setStuck] = useState<"" | "nosync" | "failed">("");
  useEffect(() => {
    if (location.hostname !== OLD_HOST) return;
    const here = location.pathname + location.search + location.hash;
    void (async () => {
      const key = await getSyncKey();
      if (key) {
        if (!(await syncNow(key)).ok) return setStuck("failed");
        await disableReminders().catch(() => {});
        return location.replace(`${NEW_ORIGIN}/settings/#link=${encodeURIComponent(key)}&next=${encodeURIComponent(here)}`);
      }
      if (!(await hasLocalData())) return location.replace(NEW_ORIGIN + here);
      setStuck("nosync");
    })();
  }, []);
  if (!stuck) return null;
  return (
    <div role="status" className="fixed inset-x-0 top-0 z-50 border-b border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
      {stuck === "failed"
        ? t("OAK Healthy moved to healthy.oakgatekeeper.uk, but this device couldn't sync first. Check sync in Settings, then reload this page.")
        : t("OAK Healthy moved to healthy.oakgatekeeper.uk. Turn on sync in Settings to take your data along, then reload this page.")}
    </div>
  );
}
