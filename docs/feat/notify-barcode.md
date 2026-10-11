# Reminder "Taken" action and live barcode scan

> updated 2026-10-11 · v2.1.0

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
- `takenNext`: null unless `planned`; otherwise exactly what `markDose` (`lib/db.ts`) writes for Taken. Intake `{ key: "${id}@${at}", supplementId, clientId, scheduledAt, status: "Taken", updatedAt, deletedAt: null }`; tracked stock drops by `perDose` (floor 0) with the routine's `updatedAt` bumped; already Taken (not tombstoned) → no write, so stock drops once.
- A routine deleted, paused or off-cycle since the push is skipped (`planned` inside `takenNext`).

## Live barcode scan

`components/barcode-scan.tsx`. **Scan barcode** opens a full-screen camera view when `getUserMedia` exists, else the photo picker directly.

- `getUserMedia({ video: { facingMode: { ideal: "environment" } } })`; frames go to the same detector as photos (native `BarcodeDetector`, else `barcode-detector/ponyfill`) every 200 ms (~5/s).
- First usable value (`validGtin`, or any 8 digits: UPC-E's check digit is on its expanded form and the detector already verified it; same rule as photos) stops every track, closes the view and runs the existing Open Food Facts lookup → drawer prefill.
- Focus starts on Cancel; Escape, Cancel, closing the drawer, or unmount always stops the tracks (effect cleanup; a stream that resolves after unmount is stopped at once).
- Permission denied / no camera → the view says so and offers **Take a photo** (photo path unchanged: decode, else typed number).
- Only the barcode number leaves the device (Open Food Facts); frames are decoded locally.
