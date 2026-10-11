// OAK Healthy service worker: app-shell precache + stale-while-revalidate for static assets. API is never cached.
const VERSION = "oak-v2.3.0";
const SHELL = ["/", "/stack/", "/insights/", "/settings/", "/account/", "/login/", "/signup/", "/forgot/", "/reset/", "/s/", "/c/", "/manifest.webmanifest", "/icon-192.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin || url.pathname.startsWith("/api/")) return;
  if (e.request.mode === "navigate") {
    const key = url.pathname.startsWith("/s/") ? "/s/" : url.pathname.startsWith("/c/") ? "/c/" : url.pathname;
    e.respondWith(fetch(e.request).then((r) => { const c = r.clone(); caches.open(VERSION).then((ca) => ca.put(key, c)); return r; })
      .catch(() => caches.match(key).then((r) => r || caches.match("/"))));
    return;
  }
  e.respondWith(caches.open(VERSION).then(async (c) => {
    const hit = await c.match(e.request);
    const net = fetch(e.request).then((r) => { if (r.ok) c.put(e.request, r.clone()); return r; }).catch(() => hit);
    return hit || net;
  }));
});
// Reminders: pushes are empty, so read due routines from IndexedDB (Dexie db "oak-healthy") and compose locally.
function idb(store) {
  return new Promise((res) => {
    const r = indexedDB.open("oak-healthy");
    r.onerror = () => res([]);
    r.onsuccess = () => {
      try {
        const q = r.result.transaction(store, "readonly").objectStore(store).getAll();
        q.onsuccess = () => res(q.result || []);
        q.onerror = () => res([]);
      } catch { res([]); }
    };
  });
}
// Snoozed doses live in meta "snoozed" = { ids, at } (set by the notification action below).
async function dueNow() {
  const [routines, meta] = await Promise.all([idb("routines"), idb("meta")]);
  const lang = (meta.find((m) => m.key === "lang") || {}).value === "vi" ? "vi" : "en";
  const snz = (meta.find((m) => m.key === "snoozed") || {}).value;
  const d = new Date(), mins = d.getHours() * 60 + d.getMinutes();
  const woke = snz && snz.at <= Date.now() + 90e3 ? new Set(snz.ids) : null;
  const due = routines.filter((r) => {
    if (r.deletedAt || r.paused) return false;
    if (woke && woke.has(r.id)) return true;
    if (r.remind === false) return false;
    const [h, m] = String(r.time).split(":").map(Number);
    return Math.abs(h * 60 + m - mins) <= 2;
  });
  if (woke) await openDb().then((db) => putMeta(db, "snoozed", null).finally(() => db.close())).catch(() => {});
  return { lang, due };
}
const SNOOZES = [10, 60];
self.addEventListener("push", (e) => {
  e.waitUntil(dueNow().then(({ lang, due }) => {
    const vi = lang === "vi";
    const names = due.map((r) => r.name).slice(0, 4).join(", ");
    const title = due.length ? (vi ? `Đến giờ uống: ${names}` : `Time for ${names}`) : (vi ? "Nhắc nhở OAK" : "OAK reminder");
    const body = due.length ? due.map((r) => `${r.time} · ${r.dose || r.name}`).slice(0, 4).join("\n") : (vi ? "Mở OAK để xem liều hôm nay." : "Open OAK to see today's doses.");
    const actions = due.length ? SNOOZES.map((n) => ({ action: `snooze-${n}`, title: vi ? (n < 60 ? `Báo lại ${n} phút` : "Báo lại 1 giờ") : (n < 60 ? `Snooze ${n} min` : "Snooze 1 hour") })) : [];
    return self.registration.showNotification(title, { body, icon: "/icon-192.png", badge: "/icon-192.png", tag: "oak-dose", renotify: true, actions, data: { url: "/", ids: due.map((r) => r.id) } });
  }));
});
// Background Sync ("oak-sync", registered by the app when offline or a push failed on the network).
// With an OAK tab open, that tab syncs (it owns Dexie). With none, the worker syncs the encrypted vault itself:
// same HKDF/AES-GCM scheme and last-writer-wins merge as lib/sync.ts + lib/db.ts. Throwing makes the browser retry.
const te = new TextEncoder(), td = new TextDecoder();
const b64u = (b) => { const u = new Uint8Array(b); let s = ""; for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode(...u.subarray(i, i + 0x8000)); return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); };
const unb64u = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
const STORES = { profiles: "id", routines: "id", intakes: "key", biomarkers: "id" };
async function vaultKeys(secret) {
  const base = await crypto.subtle.importKey("raw", te.encode(secret.toUpperCase().replace(/[^A-Z0-9]/g, "").replace(/^OAK/, "")), "HKDF", false, ["deriveBits", "deriveKey"]);
  const p = (info) => ({ name: "HKDF", hash: "SHA-256", salt: te.encode("oak-healthy-v1"), info: te.encode(info) });
  return {
    id: b64u(await crypto.subtle.deriveBits(p("vault-id"), base, 128)),
    auth: b64u(await crypto.subtle.deriveBits(p("vault-auth"), base, 256)),
    key: await crypto.subtle.deriveKey(p("vault-enc"), base, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]),
  };
}
const openDb = () => new Promise((res, rej) => { const r = indexedDB.open("oak-healthy"); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
const getAll = (db, store) => new Promise((res) => {
  if (!db.objectStoreNames.contains(store)) return res([]);
  const q = db.transaction(store, "readonly").objectStore(store).getAll();
  q.onsuccess = () => res(q.result || []); q.onerror = () => res([]);
});
function mergeInto(db, snap) {
  const names = Object.keys(STORES).filter((s) => db.objectStoreNames.contains(s));
  return new Promise((res, rej) => {
    const tx = db.transaction(names, "readwrite");
    for (const s of names) {
      const os = tx.objectStore(s);
      for (const item of snap[s] || []) {
        const g = os.get(item[STORES[s]]);
        g.onsuccess = () => { const cur = g.result; if (!cur || item.updatedAt > cur.updatedAt) os.put(item); };
      }
    }
    tx.oncomplete = () => res(); tx.onerror = () => rej(tx.error);
  });
}
const putMeta = (db, key, value) => new Promise((res) => { const tx = db.transaction("meta", "readwrite"); tx.objectStore("meta").put({ key, value }); tx.oncomplete = res; tx.onerror = res; });
async function swSync() {
  const db = await openDb();
  try {
    if (!db.objectStoreNames.contains("meta")) return;
    const secret = ((await getAll(db, "meta")).find((m) => m.key === "syncKey") || {}).value;
    if (!secret) return;
    const v = await vaultKeys(secret);
    const headers = { "content-type": "application/json", authorization: `Bearer ${v.auth}` };
    for (let attempt = 0; attempt < 3; attempt++) {
      const res = await fetch(`/api/vault/${v.id}`, { headers });
      if (res.status === 403) return;
      let rev = 0;
      if (res.ok) {
        const body = await res.json();
        rev = body.rev;
        const [iv, ct] = body.blob.split(".");
        await mergeInto(db, JSON.parse(td.decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64u(iv) }, v.key, unb64u(ct)))));
      } else if (res.status !== 404) throw new Error(`vault ${res.status}`);
      const snap = { v: 1 };
      for (const s of Object.keys(STORES)) snap[s] = await getAll(db, s);
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const blob = b64u(iv) + "." + b64u(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, v.key, te.encode(JSON.stringify(snap))));
      const put = await fetch(`/api/vault/${v.id}`, { method: "PUT", headers, body: JSON.stringify({ baseRev: rev, blob }) });
      if (put.status === 409) continue;
      if (!put.ok) throw new Error(`push ${put.status}`);
      await putMeta(db, "lastSync", Date.now());
      return;
    }
    throw new Error("conflict");
  } finally { db.close(); }
}
self.addEventListener("sync", (e) => {
  if (e.tag !== "oak-sync") return;
  e.waitUntil(self.clients.matchAll({ type: "window" }).then((cs) => {
    const mine = cs.filter((c) => new URL(c.url).origin === location.origin);
    if (mine.length) { mine[0].postMessage({ type: "oak-sync" }); return; }
    return swSync();
  }));
});
async function snooze(ids, n) {
  const sub = await self.registration.pushManager.getSubscription();
  if (!sub || !ids.length) return;
  const db = await openDb();
  try { await putMeta(db, "snoozed", { ids, at: Date.now() + n * 60e3 }); } finally { db.close(); }
  await fetch("/api/push/snooze", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: sub.endpoint, snooze: n }) });
}
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const sn = /^snooze-(\d+)$/.exec(e.action || "");
  if (sn) { e.waitUntil(snooze((e.notification.data && e.notification.data.ids) || [], Number(sn[1])).catch(() => {})); return; }
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((cs) => {
    const c = cs.find((x) => new URL(x.url).origin === location.origin);
    return c ? c.focus() : self.clients.openWindow("/");
  }));
});
