# Health data flow

> updated 2026-10-11 · v2.12.0

## Entities

- **Profile** `{id, name, color}`: a person being tracked.
- **Routine** `{id, clientId → Profile, name, dose, time HH:mm, startDate YYYY-MM-DD, cycle, category, paused, stock, perDose, note, history?}`. `history` = past schedules as effective-from spans `{from, time, startDate, cycle, paused, deleted?}`, at most 64; absent = the current fields on every day (`docs/feat/schedule-history.md`). It rides inside the routine record: encrypted vault, JSON backup, SW merge and push, and the circle payload (cut to the last 60 days, like intakes).
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
| Schedule | `schedule.ts` | doses for a day = not deleted, and unpaused and cycle-ON in the schedule that held that day (`schedule-history.ts`), at that schedule's time; slots Morning < 11, Midday < 15, Evening < 20, Bedtime |
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

Meta `shares` (`{ id, token, expiresAt, title?, createdAt?, refusedAt? }`, contract C1, written by `components/stack-manager.tsx`; `refusedAt`, contract C4, marks a link the server refused to delete: it stays in `shares`, never in `sharesGone`) and meta `sharesGone` (`{ id, at }`, contract C3, written with the removal from `shares` in one transaction by a single delete and by the wipe) ride in the encrypted vault. Neither leaves the device in plaintext; the title is only on devices and in the vault (a pretty link's slug words are public as before).

Merge (`lib/sync.ts` `mergeShareLists`, mirrored by `public/sw.js` `vaultShares`, one fixture `tests/fixtures/vault-circles.ts` for both):
1. Tombstones: this device's and the vault's, union by id with the earliest `at`; `at` is clamped to now, and a tombstone is dropped 400 days after `at`.
2. Entries: union by id. Token from this device's entry; `title`, `createdAt` and `refusedAt` per field, this device's else the vault's (also for an entry only the vault has); the later `expiresAt` (the server's value).
3. Drop every entry whose id has a tombstone, on either side, then every entry 30 days past `expiresAt` (device clock).
- Validation, both sides: an entry needs the worker's id shape (8 to 64 of `A-Za-z0-9_-`), a token of at least 20 characters and a finite `expiresAt`; a title that is not a string, is empty or is over 120 characters (code points), or a non-finite `createdAt` or `refusedAt`, drops only that field (`lib/domain/shares.ts` `cleanShareFields`; `refusedAt` is clamped to now). A tombstone needs that id shape and a finite `at`. Anything else, or a field that is not a list, is ignored.
- Why 400 days is enough: a share lives at most 365 days (`expires_at`, set by the worker) and its entry leaves every list (2.8 and 2.9) 30 days later, so it is gone from every list by 395 days after creation; `at` is never before creation.
- The tab merges both meta keys in one Dexie transaction and pushes the result; the SW pushes the merge without writing meta, and the next tab sync writes it.
- Size: about 40 bytes a tombstone, bounded by the 400-day drop; no cap needed under the vault's 1.5 MB limit.
- Limits: a 2.9 device's push drops `refusedAt`; the row then offers Delete again, and a new try is refused and marks it again (harmless). A 2.8 device's push drops `sharesGone`, titles and `refusedAt`; a deleted entry then shows again until another 2.9 device syncs (its tombstone and titles are kept in its meta and pushed again). If no 2.9 device still holds the tombstone, the entry can stay back for up to about 395 days (harmless: DELETE of a gone share answers 200). A delete with sync off reaches no other device; deleting the same link there again gets 200. Pull comes before every push (a 409 pulls again), so a push cannot skip a tombstone already in the vault.