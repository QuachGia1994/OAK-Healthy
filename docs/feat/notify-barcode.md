# Reminder "Taken" action and live barcode scan

> updated 2026-10-11 · v2.4.0

## Taken on the reminder notification

Actions (browsers show at most two): **Taken** (`taken`) and **Snooze 10 min** (`snooze-10`). `notificationclick` still parses any `snooze-<n>`, so notifications shown before this change keep working (the Worker accepts 5–180 min).

| Step | Where | What |
| --- | --- | --- |
| Push | `public/sw.js` `push` | Due routines from IndexedDB, kept only when `planned(r, doseAt(time, now))`; the first 4 are shown and only those go in `data.doses = [{ id, at }]`; `data.ids` (all due) stays for snooze; `data.lang` for the failure notice |
| Tap Taken | `markTaken` → `writeTaken` | One readwrite transaction over `intakes` + `routines`: `takenNext(routine, at, prevIntake, now)`; on failure a "Couldn't save · Open OAK to mark it taken" notification |
| Refresh UI | `BroadcastChannel("x-storagemutated-1")` `{ all: true }` | Dexie 4's own cross-context channel; open tabs re-run live queries |
| Sync | open tab → `postMessage({ type: "oak-sync" })`; none → `registration.sync.register("oak-sync")`, else `swSync()` | Same LWW vault sync as any local edit |

Rules (`lib/domain/intake-key.ts`, mirrored in `sw.js`, agreement proven by `tests/pwa.test.ts`):
- `doseAt(time, now)`: the dose's `scheduledAt` (`lib/domain/day.ts`) today, or yesterday when today's time is more than `EARLY_MS` (5 min) ahead, so a dose snoozed past midnight keeps its key.
- `planned(r, at)`: the dashboard's plan filter (`dosesForDay`, `lib/domain/schedule.ts`) on the dose's local day: not deleted, not paused, `cycleStatus(...) === "ON"` (start date, duration, on/off cycle). The test checks it against `dosesForDay` itself.
- `doseWrite(routine, at, prev, status, now)`: the one dose rule, used by the dashboard's `markDose` (`lib/db.ts`, inside its rw transaction over `intakes` + `routines`) and by `takenNext`. Status `Taken`/`Skipped` → intake `{ key: "${id}@${at}", supplementId, clientId, scheduledAt, status, updatedAt, deletedAt: null }`; `null` (Undo/clear) → the previous intake tombstoned (`deletedAt = updatedAt = now`), or nothing if none. Tracked stock moves only when "is Taken" changes: −`perDose` on, +`perDose` off (floor 0), routine `updatedAt` bumped; untracked (`stock: null`) → routine untouched.
- `takenNext`: null unless `planned` and not already Taken (not tombstoned), so stock drops once; otherwise `doseWrite(..., "Taken", now)`.
- A routine deleted, paused or off-cycle since the push is skipped (`planned` inside `takenNext`).

## Push subscription rotation

Browsers may rotate or expire a push subscription and fire `pushsubscriptionchange` in the service worker, often with no OAK tab open. `sw.js` `resubscribe`:

1. Only when reminders are on: meta `push` (set by `lib/reminders.ts`) is non-null; otherwise nothing is sent.
2. Old endpoint = `event.oldSubscription.endpoint`, else meta `push.endpoint`. New subscription = `event.newSubscription`, else `pushManager.getSubscription()`, else `subscribe({ userVisibleOnly: true, applicationServerKey })` with the key from `GET /api/push/key`. Same endpoint → done.
3. `POST /api/push/move { endpoint: old, to: new }`. The Worker checks both with `okEndpoint`, copies `minutes` and any pending `snooze_at` from the old row to the new id (`sha256(endpoint)`, upsert) and deletes the old row in one D1 batch; no old row → 404 `not subscribed`.
4. 404 (the minute clock already deleted the old row after a 404/410 from the push service) → `PUT /api/push/subscribe { endpoint: new, minutes }`, minutes from on-device routines via `utcMinutes` (`lib/domain/remind.ts`, mirrored in `sw.js`, same as `pushReminderTimes`); a pending snooze is lost in this case only.
5. Success → meta `push = { endpoint: new }`. Any other failure throws so the browser can retry; meta keeps the old endpoint.

The endpoint URL is the only credential, as for subscribe/snooze/delete. Covered by `tests/pwa.test.ts` (fake IndexedDB, fetch and `pushManager`).

## Vault sync from the service worker

`swSync` (Background Sync / no tab) pushes the same vault fields as `lib/sync.ts`: `profiles`, `routines`, `intakes`, `biomarkers` plus `circles`, `circlePins` and `circleSeen` (`vaultCircles`). The SW cannot run `mergeCircles`, so it passes the pulled vault's `circles` through (this device's when the vault has none), keeps this device's pins over synced ones and pushes the higher `circleSeen` floor per circle (`mergeSeen`); the next tab sync merges all three into meta and pushes the result. A 410 on the GET or PUT (the vault was deleted on another device) ends `swSync` without a PUT and without throwing, so the browser does not retry; it leaves the sync key. The next tab `syncNow` gets the same 410, turns sync off and the sync agent shows "Your synced data was deleted on another device, so sync is off here." once.

## Live barcode scan

`components/barcode-scan.tsx`. **Scan barcode** opens a full-screen camera view when `getUserMedia` exists, else the photo picker directly.

- `getUserMedia({ video: { facingMode: { ideal: "environment" } } })`; frames go to the same detector as photos (native `BarcodeDetector`, else `barcode-detector/ponyfill`) every 200 ms (~5/s).
- First usable value (`validGtin`, or any 8 digits: UPC-E's check digit is on its expanded form and the detector already verified it; same rule as photos) stops every track, closes the view and runs the existing Open Food Facts lookup → drawer prefill.
- Focus starts on Cancel; Escape, Cancel, closing the drawer, or unmount always stops the tracks (effect cleanup; a stream that resolves after unmount is stopped at once).
- Permission denied / no camera → the view says so and offers **Take a photo** (photo path unchanged: decode, else typed number).
- Only the barcode number leaves the device (Open Food Facts); frames are decoded locally.
