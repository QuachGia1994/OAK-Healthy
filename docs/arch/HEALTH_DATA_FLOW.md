# Health data flow

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