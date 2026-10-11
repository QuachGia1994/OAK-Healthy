# Health data flow

> updated 2026-10-11 · v2.7.0

## Entities

- **Profile** `{id, name, color}`: a person being tracked.
- **Routine** `{id, clientId → Profile, name, dose, time HH:mm, startDate YYYY-MM-DD, cycle, category, paused, stock, perDose, note}`.
- **Intake** `{key = routineId@scheduledAt, status Taken|Skipped, scheduledAt epoch ms}`: one per planned dose.

All records carry `updatedAt` and an optional `deletedAt` tombstone, so sync can merge without losing deletions.

## Date/time semantics

- `startDate` is a literal local calendar day, never a UTC instant (`lib/domain/day.ts`).
- A day is the half-open local window `[00:00, next 00:00)`; DST days may be 23 or 25 hours. Day distance uses UTC-normalized calendar math.
- `scheduledAt` = local day + routine time, as epoch ms. It is part of the intake key, so the same dose is idempotent across devices.

## Policies (single owners)

| Policy | File | Rule |
| --- | --- | --- |
| Cycle | `cycle.ts` | OFF before start, OFF after `durationDays`, else `elapsed % (on+off) < on` |
| Timing | `timing.ts` | soon = within 20 min before; missed = 2 h after; late-taken = taken > 20 min after |
| Adherence | `timing.ts` | `taken / (taken + skipped)`; null when nothing logged |
| Schedule | `schedule.ts` | doses for a day = active, unpaused, cycle-ON routines; slots Morning < 11, Midday < 15, Evening < 20, Bedtime |
| Inventory | `db.ts#markDose` | Taken −perDose, leaving Taken +perDose, never below 0 |
| Knowledge | `knowledge.ts` | clash in same slot, synergy split across slots, stimulant ≥ 18:00, calming < 11:00 |

## Write path

UI → `markDose` / `saveRoutine` (Dexie transaction) → `oak:changed` event → `SyncAgent` debounces 4 s → `syncNow`: GET vault → decrypt → LWW merge → encrypt snapshot → PUT with `baseRev` (409 → retry). A vault deleted with Delete everything is a server tombstone: GET and PUT answer 410 `gone`, and `syncNow` turns sync off on that device instead of pushing it back (`docs/feat/delete-everything.md`).

## Tombstone purge

- The `ReminderClock` alarm (`worker/push.ts` `purgeDaily`), after `remind()` and with its own catch, deletes vault tombstones whose `deleted_at` is older than 365 days: at most once a day (gate `lastPurge` in Durable Object storage), 500 rows per statement (`DELETE … WHERE id IN (SELECT id … deleted_at IS NOT NULL AND deleted_at < ? LIMIT 500)`). A full batch leaves the day open, so a backlog drains 500 per minute. Live vaults (`deleted_at` NULL) never match. No index on `deleted_at`: one table scan a day.
- The same daily pass deletes expired share rows (`expires_at < now`, which GET already hides), 500 per statement through the `shares_expires` index. Each statement is its own batch; any full batch leaves the day open.
- After the purge the worker answers 404 for that vault id, as if it never existed. 2.8 devices that synced that id before treat that 404 as gone (C2 below). A 2.7 or older device offline for over a year gets 404, PUTs `baseRev 0` and re-creates the vault with its local data (accepted, lead D1). So does a device that never synced that id (the old key typed on a new device: user intent).
- C2 gone rule (`lib/sync.ts` `syncNow`, `public/sw.js` `swSync`): meta `syncedVault` holds the vault id after a 2xx GET or PUT, never after the 404 before a first create. The worker's own JSON 404 `{ error: "not found" }` on that id = gone: the tab turns sync off with the 410 notice, the SW stops and leaves the key. Any other 404 (proxy, captive portal) is a server error (tab) or a throw for a browser retry (SW), never a create. `setSyncKey` (enable, key change, sync off) clears `syncedVault`, so turning sync on again re-creates a restored or lost vault. A device upgraded from 2.7 records the id on its first 2xx; one whose vault was purged before it upgraded re-creates it, as above.
- Known edge: the worker's generic `/api/*` fallback (`worker/index.ts`) also answers `{ error: "not found" }`, so if the vault route path ever changes, an old client would read that 404 as gone and turn sync off. Keep the vault route stable, or give the vault 404 its own error string first.

## Share list in the vault

Meta `shares` (`{ id, token, expiresAt }`, contract C1, written by `components/stack-manager.tsx` and the wipe) rides in the encrypted vault. Pull merges by id: this device's entry and token, the later `expiresAt` (the server's value); an entry on either side is kept only with the worker's id shape (8 to 64 of `A-Za-z0-9_-`), a token of at least 20 characters and a finite numeric `expiresAt`; an entry leaves the list 30 days after `expiresAt` by the device clock. The tab writes the merge into meta; the SW pushes it and the next tab sync writes it. A 2.7 push drops `shares` from the vault; each 2.8 device keeps its own list and puts it back on its next sync. There is no share tombstone: a deleted entry can return from the vault, which is harmless while Delete everything (whose vault step follows) is the only delete path. A per-share Delete needs a share tombstone in the vault first.