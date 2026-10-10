// OAK Healthy service worker: app-shell precache + stale-while-revalidate for static assets. API is never cached.
const VERSION = "oak-v2.1.0";
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
async function dueNow() {
  const [routines, meta] = await Promise.all([idb("routines"), idb("meta")]);
  const lang = (meta.find((m) => m.key === "lang") || {}).value === "vi" ? "vi" : "en";
  const d = new Date(), mins = d.getHours() * 60 + d.getMinutes();
  const due = routines.filter((r) => {
    if (r.deletedAt || r.paused) return false;
    const [h, m] = String(r.time).split(":").map(Number);
    return Math.abs(h * 60 + m - mins) <= 2;
  });
  return { lang, due };
}
self.addEventListener("push", (e) => {
  e.waitUntil(dueNow().then(({ lang, due }) => {
    const vi = lang === "vi";
    const names = due.map((r) => r.name).slice(0, 4).join(", ");
    const title = due.length ? (vi ? `Đến giờ uống: ${names}` : `Time for ${names}`) : (vi ? "Nhắc nhở OAK" : "OAK reminder");
    const body = due.length ? due.map((r) => `${r.time} · ${r.dose || r.name}`).slice(0, 4).join("\n") : (vi ? "Mở OAK để xem liều hôm nay." : "Open OAK to see today's doses.");
    return self.registration.showNotification(title, { body, icon: "/icon-192.png", badge: "/icon-192.png", tag: "oak-dose", renotify: true, data: { url: "/" } });
  }));
});
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((cs) => {
    const c = cs.find((x) => new URL(x.url).origin === location.origin);
    return c ? c.focus() : self.clients.openWindow("/");
  }));
});
