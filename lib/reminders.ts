"use client";
// Web Push reminders. The server gets an endpoint plus the UTC minutes to ping — never supplement names.
import { db, getMeta, setMeta } from "./db";

export const pushSupported = () => typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;

/** Local HH:mm of active routines for every profile → UTC minute-of-day, for today's offset. */
export async function reminderMinutes() {
  const off = new Date().getTimezoneOffset();
  const rs = (await db.routines.toArray()).filter((r) => !r.deletedAt && !r.paused);
  const set = new Set<number>();
  for (const r of rs) { const [h, m] = r.time.split(":").map(Number); set.add((((h * 60 + m + off) % 1440) + 1440) % 1440); }
  return [...set].sort((a, b) => a - b);
}

async function registration() {
  const reg = await navigator.serviceWorker.register("/sw.js");
  await navigator.serviceWorker.ready;
  return reg;
}

export async function enableReminders() {
  if (!pushSupported()) throw new Error("This browser can't receive push notifications. On iPhone, add OAK to the Home Screen first.");
  if ((await Notification.requestPermission()) !== "granted") throw new Error("Notifications are blocked for this site");
  const { key } = (await fetch("/api/push/key").then((r) => r.json())) as { key?: string };
  if (!key) throw new Error("Push is not configured on this server");
  const reg = await registration();
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key }));
  await setMeta("push", { endpoint: sub.endpoint });
  await pushReminderTimes();
}

export async function pushReminderTimes() {
  const p = await getMeta<{ endpoint: string } | null>("push", null);
  if (!p) return;
  const res = await fetch("/api/push/subscribe", { method: "PUT", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: p.endpoint, minutes: await reminderMinutes() }) });
  if (!res.ok) throw new Error(`Reminder sync failed (${res.status})`);
}

export async function testReminder() {
  const p = await getMeta<{ endpoint: string } | null>("push", null);
  if (!p) throw new Error("Reminders are off");
  const r = (await fetch("/api/push/test", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: p.endpoint }) }).then((x) => x.json())) as { ok?: boolean };
  if (!r.ok) throw new Error("Push service rejected the test");
}

export async function disableReminders() {
  const p = await getMeta<{ endpoint: string } | null>("push", null);
  if (p) await fetch("/api/push/subscribe", { method: "DELETE", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: p.endpoint }) }).catch(() => {});
  const reg = await navigator.serviceWorker.getRegistration();
  await (await reg?.pushManager.getSubscription())?.unsubscribe();
  await setMeta("push", null);
}
