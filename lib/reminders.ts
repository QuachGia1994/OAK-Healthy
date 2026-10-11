"use client";
// Web Push reminders. The server gets an endpoint plus the UTC minutes to ping — never supplement names.
import { db, getMeta, setMeta } from "./db";
import { utcMinutes } from "./domain/remind";

export const pushSupported = () => typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;

/** Local HH:mm of active routines for every profile → UTC minute-of-day, for today's offset. */
export async function reminderMinutes() {
  return utcMinutes(await db.routines.toArray(), new Date().getTimezoneOffset());
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

/** The browser may rotate the push subscription (or the server may have dropped it): re-read it and re-register. */
async function freshEndpoint() {
  const p = await getMeta<{ endpoint: string } | null>("push", null);
  if (!p) throw new Error("Reminders are off");
  const reg = await navigator.serviceWorker.getRegistration();
  let sub = await reg?.pushManager.getSubscription();
  if (!sub && reg) {
    const { key } = (await fetch("/api/push/key").then((r) => r.json())) as { key?: string };
    if (key) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  }
  if (sub && sub.endpoint !== p.endpoint) await setMeta("push", { endpoint: sub.endpoint });
  await pushReminderTimes();
  return sub?.endpoint ?? p.endpoint;
}

async function sendTest(endpoint: string) {
  const res = await fetch("/api/push/test", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint }) });
  return (await res.json().catch(() => ({ status: res.status }))) as { ok?: boolean; status?: number; error?: string };
}

export async function testReminder() {
  let r = await sendTest(await freshEndpoint());
  // 403/404/410 = the subscription is stale or was made with an old key: drop it, subscribe again, retry once.
  if (!r.ok && (r.status === 403 || r.status === 404 || r.status === 410 || r.error === "not subscribed")) {
    const reg = await navigator.serviceWorker.getRegistration();
    await (await reg?.pushManager.getSubscription())?.unsubscribe();
    r = await sendTest(await freshEndpoint());
  }
  if (!r.ok) {
    console.warn("push test", r);
    throw new Error("Push service rejected the test");
  }
}

export async function disableReminders() {
  const p = await getMeta<{ endpoint: string } | null>("push", null);
  if (p) await fetch("/api/push/subscribe", { method: "DELETE", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: p.endpoint }) }).catch(() => {});
  const reg = await navigator.serviceWorker.getRegistration();
  await (await reg?.pushManager.getSubscription())?.unsubscribe();
  await setMeta("push", null);
}
