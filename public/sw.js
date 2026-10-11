// OAK Healthy service worker: app-shell precache + stale-while-revalidate for static assets. API is never cached.
const VERSION = "oak-v2.5.1";
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
// Mirror of lib/domain/intake-key.ts (doseKey, doseAt, planned, doseWrite, takenNext) and cycle.ts; tests/pwa.test.ts runs both on the same fixtures.
const EARLY_MS = 5 * 60e3;
function doseKey(id, at) { return `${id}@${at}`; }
function doseAt(time, now) {
  const d = new Date(now), [h, m] = String(time).split(":").map(Number);
  const on = (offset) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + offset, h || 0, m || 0).getTime();
  return on(0) - EARLY_MS > now ? on(-1) : on(0);
}
const dayNo = (y, m, d) => Math.round(Date.UTC(y, m - 1, d) / 864e5);
function cycleOn(startDate, cfg, at) {
  const t = new Date(at), [y, m, d] = String(startDate).split("-").map(Number);
  const elapsed = dayNo(t.getFullYear(), t.getMonth() + 1, t.getDate()) - dayNo(y, m, d);
  if (elapsed < 0 || (cfg.durationDays > 0 && elapsed >= cfg.durationDays)) return false;
  const total = cfg.daysOn + cfg.daysOff;
  return cfg.continuous || total <= 0 || elapsed % total < cfg.daysOn;
}
function planned(r, at) { return !r.deletedAt && !r.paused && cycleOn(r.startDate, r.cycle, at); }
const isTaken = (i) => !!i && !i.deletedAt && i.status === "Taken";
function doseWrite(r, at, prev, status, now) {
  const intake = status ? { key: doseKey(r.id, at), supplementId: r.id, clientId: r.clientId, scheduledAt: at, status, updatedAt: now, deletedAt: null }
    : prev ? { ...prev, deletedAt: now, updatedAt: now } : null;
  const willTake = status === "Taken";
  const routine = r.stock != null && isTaken(prev) !== willTake ? { ...r, stock: Math.max(0, r.stock + (willTake ? -r.perDose : r.perDose)), updatedAt: now } : null;
  return { intake, routine };
}
function takenNext(r, at, prev, now) { return !planned(r, at) || isTaken(prev) ? null : doseWrite(r, at, prev, "Taken", now); }
// Mirror of lib/domain/remind.ts (remindable, utcMinutes): the minutes lib/reminders.ts registers, for resubscribe below.
const remindable = (r) => !r.deletedAt && !r.paused && r.remind !== false;
function utcMinutes(rs, offset) {
  const set = new Set();
  for (const r of rs) {
    if (!remindable(r)) continue;
    const [h, m] = r.time.split(":").map(Number);
    if (Number.isFinite(h) && Number.isFinite(m)) set.add((((h * 60 + m + offset) % 1440) + 1440) % 1440);
  }
  return [...set].sort((a, b) => a - b);
}
// Browsers show at most two actions: Taken + one snooze. snooze-<n> stays parsed so older notifications keep working.
const SNOOZE = 10;
self.addEventListener("push", (e) => {
  e.waitUntil(dueNow().then(({ lang, due: candidates }) => {
    const vi = lang === "vi", now = Date.now();
    const due = candidates.filter((r) => planned(r, doseAt(r.time, now)));
    const shown = due.slice(0, 4);
    const names = shown.map((r) => r.name).join(", ");
    const title = due.length ? (vi ? `Đến giờ uống: ${names}` : `Time for ${names}`) : (vi ? "Nhắc nhở OAK" : "OAK reminder");
    const body = due.length ? shown.map((r) => `${r.time} · ${r.dose || r.name}`).join("\n") : (vi ? "Mở OAK để xem liều hôm nay." : "Open OAK to see today's doses.");
    const actions = due.length ? [{ action: "taken", title: vi ? "Đã uống" : "Taken" }, { action: `snooze-${SNOOZE}`, title: vi ? `Báo lại ${SNOOZE} phút` : `Snooze ${SNOOZE} min` }] : [];
    const doses = shown.map((r) => ({ id: r.id, at: doseAt(r.time, now) }));
    return self.registration.showNotification(title, { body, icon: "/icon-192.png", badge: "/icon-192.png", tag: "oak-dose", renotify: true, actions, data: { url: "/", lang, ids: due.map((r) => r.id), doses } });
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
// Circle state (meta "circles"/"circlePins") rides in the vault like lib/sync.ts. The SW cannot run mergeCircles
// (lib/member-keys.ts), so it passes the pulled vault's circles through (local ones if the vault has none) and keeps
// this device's pins over synced ones (sync.ts rule); the next tab sync merges both into meta and pushes the result.
function vaultCircles(remote, meta) {
  const mine = (k, d) => (meta.find((m) => m.key === k) || {}).value || d;
  const circles = remote.circles && remote.circles.length ? remote.circles : mine("circles", []);
  return { circles, circlePins: { ...remote.circlePins, ...mine("circlePins", {}) } };
}
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
      let rev = 0, remote = {};
      if (res.ok) {
        const body = await res.json();
        rev = body.rev;
        const [iv, ct] = body.blob.split(".");
        remote = JSON.parse(td.decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64u(iv) }, v.key, unb64u(ct))));
        await mergeInto(db, remote);
      } else if (res.status !== 404) throw new Error(`vault ${res.status}`);
      const snap = { v: 1 };
      for (const s of Object.keys(STORES)) snap[s] = await getAll(db, s);
      Object.assign(snap, vaultCircles(remote, await getAll(db, "meta")));
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
const ownTabs = () => self.clients.matchAll({ type: "window" }).then((cs) => cs.filter((c) => new URL(c.url).origin === location.origin));
self.addEventListener("sync", (e) => {
  if (e.tag !== "oak-sync") return;
  e.waitUntil(ownTabs().then((mine) => {
    if (mine.length) { mine[0].postMessage({ type: "oak-sync" }); return; }
    return swSync();
  }));
});
// "Taken" action: the same Intake + stock write as markDose (lib/db.ts), in one IndexedDB transaction.
function writeTaken(db, doses, now) {
  return new Promise((res, rej) => {
    const tx = db.transaction(["intakes", "routines"], "readwrite");
    const intakes = tx.objectStore("intakes"), routines = tx.objectStore("routines");
    for (const { id, at } of doses) {
      const gr = routines.get(id);
      gr.onsuccess = () => {
        if (!gr.result) return;
        const gi = intakes.get(doseKey(id, at));
        gi.onsuccess = () => {
          const next = takenNext(gr.result, at, gi.result, now);
          if (!next) return;
          intakes.put(next.intake);
          if (next.routine) routines.put(next.routine);
        };
      };
    }
    tx.oncomplete = () => res(); tx.onerror = () => rej(tx.error); tx.onabort = () => rej(tx.error);
  });
}
// Open tabs re-read Dexie live queries via Dexie's own cross-context channel (dexie 4: "x-storagemutated-1", { all: true }),
// and the first tab syncs; with no tab, Background Sync or swSync pushes the change.
async function markTaken(doses) {
  const db = await openDb();
  try { await writeTaken(db, doses, Date.now()); } finally { db.close(); }
  const bc = new BroadcastChannel("x-storagemutated-1");
  bc.postMessage({ all: true });
  bc.close();
  const mine = await ownTabs();
  if (mine.length) { mine[0].postMessage({ type: "oak-sync" }); return; }
  if (self.registration.sync) return self.registration.sync.register("oak-sync").catch(() => swSync());
  return swSync();
}
async function snooze(ids, n) {
  const sub = await self.registration.pushManager.getSubscription();
  if (!sub || !ids.length) return;
  const db = await openDb();
  try { await putMeta(db, "snoozed", { ids, at: Date.now() + n * 60e3 }); } finally { db.close(); }
  await fetch("/api/push/snooze", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: sub.endpoint, snooze: n }) });
}
// The browser rotated or expired the push subscription (often with no tab open): move the server row (minutes + pending
// snooze) to the new endpoint and drop the old one. Only while reminders are on (meta "push", set by lib/reminders.ts).
// No server row (404) → subscribe the new endpoint with on-device minutes. Throwing lets the browser retry.
async function resubscribe(e) {
  const db = await openDb();
  try {
    const known = ((await getAll(db, "meta")).find((m) => m.key === "push") || {}).value;
    if (!known) return;
    const old = (e.oldSubscription && e.oldSubscription.endpoint) || known.endpoint;
    const pm = self.registration.pushManager;
    const sub = e.newSubscription || (await pm.getSubscription()) || (await fetch("/api/push/key").then((r) => r.json())
      .then(({ key }) => pm.subscribe({ userVisibleOnly: true, applicationServerKey: key })));
    if (sub.endpoint === old) return;
    const res = await fetch("/api/push/move", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: old, to: sub.endpoint }) });
    if (!res.ok && res.status !== 404) throw new Error(`move ${res.status}`);
    // 404: the server already dropped the old row (remind() deletes on 404/410), so register the new endpoint with the
    // minutes from on-device routines (any pending snooze is lost).
    if (res.status === 404) {
      const minutes = utcMinutes(await getAll(db, "routines"), new Date().getTimezoneOffset());
      const put = await fetch("/api/push/subscribe", { method: "PUT", headers: { "content-type": "application/json" }, body: JSON.stringify({ endpoint: sub.endpoint, minutes }) });
      if (!put.ok) throw new Error(`subscribe ${put.status}`);
    }
    await putMeta(db, "push", { endpoint: sub.endpoint });
  } finally { db.close(); }
}
self.addEventListener("pushsubscriptionchange", (e) => e.waitUntil(resubscribe(e)));
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  if (e.action === "taken") {
    const d = e.notification.data || {}, vi = d.lang === "vi";
    e.waitUntil(markTaken(d.doses || []).catch(() => self.registration.showNotification(vi ? "Chưa lưu được" : "Couldn't save", { body: vi ? "Mở OAK để đánh dấu đã uống." : "Open OAK to mark it taken.", icon: "/icon-192.png", tag: "oak-dose", data: { url: "/" } })));
    return;
  }
  const sn = /^snooze-(\d+)$/.exec(e.action || "");
  if (sn) { e.waitUntil(snooze((e.notification.data && e.notification.data.ids) || [], Number(sn[1])).catch(() => {})); return; }
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((cs) => {
    const c = cs.find((x) => new URL(x.url).origin === location.origin);
    return c ? c.focus() : self.clients.openWindow("/");
  }));
});
