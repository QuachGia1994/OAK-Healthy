# Schedule history

> updated 2026-10-11 · v2.12.0

Past days follow the schedule that was in force on that day. A dose time, start date, cycle or pause change made on 2.12 or later no longer rewrites the past: Missed rows in the history CSV, past-day stats on the dashboard and insights, and circle views count each day with that day's schedule. Code: `lib/domain/schedule-history.ts` (pure).

## Model
- `Routine.history?: ScheduleSpan[]`, sorted by `from`. A span `{ from, time, startDate, cycle, paused, deleted? }` holds from its local day `from` until the next span's day. startDate and cycle are both copied because the cycle phase counts from startDate.
- No history = the current fields hold on every day (every routine before its first 2.12 change, so nothing changes on upgrade). A history of one span is stored as none.
- Days before the first span use the first span.
- **Invariant:** the effective last span always equals the current fields (`time`, `startDate`, `cycle`, `paused`, deleted = `deletedAt` set). When the stored last span differs (an edit on an older device, or a record that won a sync merge), an implicit span with the current fields holds from the local day of `updatedAt`. So today's list never depends on history.
- The routine record is the only store: no Dexie table or version bump; the worker only writes the implicit span down on the notification's Taken (2.12.2, below). History rides inside the encrypted routine in the vault, the JSON backup and the service worker's merge and push, which all copy whole records.

## Writes
| Write | Effect on history |
|---|---|
| `saveRoutine` (`forSave`: always on top of the stored record) | a changed time, start date, cycle or pause sets a span from today, after the stored spans (also when the edited copy was loaded before a sync changed them); a second change the same day replaces it; a change back merges it away; name, dose, stock, remind, note add nothing |
| `saveRoutine` (Undo, which re-saves the old record) | the old fields replace today's span and merge it away, so the history is as before the edit; an Undo after midnight adds a span with the old fields from that day instead (same schedule from then on) |
| `saveRoutine` (new id: new routine, Duplicate) | no history; a duplicate does not copy the original's spans |
| `setDeleted(id, true)` | closing span `{ from: today, …, deleted: true }` |
| `setDeleted(id, false)` | the closing span is removed |
| `markDose` | the implicit span, if any, is written down before the dose moves `updatedAt` |

A change holds from the day it is made, including that day: a dose time moved at 14:00 from 08:00 to 20:00 plans 20:00 for that day (an 08:00 Taken stays as a record).

## Readers
- `scheduleOn(r, day)`: the schedule that held on `day`. `plannedOn(r, day)`: planned when not deleted, not paused in that span and cycle ON in that span.
- `dosesForDay` (`lib/domain/schedule.ts`) uses both, so dayStats, streak, insights, the circle view and the command menu follow history. The history CSV's Missed rows use the same rule (`docs/feat/history-csv.md`).
- `adherenceOn` (`lib/domain/stack.ts`) ignores pauses, also the ones in history; time and cycle still follow each day's span.
- Unchanged: `planned()` and `takenNext` (`lib/domain/intake-key.ts`), `public/sw.js` and reminders use the current fields only, which is the last span by the invariant.
- The notification's Taken (`public/sw.js` `writeTaken`) writes the implicit span down before it moves `updatedAt`, like `markDose`: `materialize(r, now)` is a self-contained mirror of `withHistory(r, today)` with no edit (parse, implicit span from the `updatedAt` day clamped to today, merge, cap); `tests/pwa.test.ts` runs both on the same fixtures.

## Delete
A deleted routine is planned on no day, with or without a closing span (the same rule as before 2.12). The closing span is recorded data only. Counting a deleted routine's unrecorded past doses as Missed (owner question Q2) would need more than `plannedOn`: deleted routines are also dropped before `plannedOn` is ever reached, in `lib/use-oak.ts:24` (`routinesAll.filter((r) => !r.deletedAt)`, feeds the dashboard and insights), `components/circle-view.tsx:45` and `lib/circles.ts:63` (`payload`, which would then send deleted routines). Only deletions made on 2.12 or later have a closing span; older ones would stay never planned.

## Circles
The circle payload (`lib/circles.ts`) sends `history` cut to the span holding 60 days ago and later ones (`trimHistory`), the same window as intakes; `note` is still blanked. Owner question Q4: trimmed (default) or none.

## Limits
- **Before the first change:** routines have no `createdAt`, so the days before the first 2.12 change keep the current-schedule rule.
- **Sync conflict:** merge is last-writer-wins per whole routine record (`mergeSnapshot`, sw.js `mergeInto`). A schedule change on one device that loses to a later write of the same routine on another, a stock change from a dose included, loses its span too; that past then follows the winner's history.
- **Older devices:** a 2.11 edit keeps the stored history (it spreads the record) but writes no span; the implicit span covers it from the `updatedAt` day. Since 2.12.2 the notification's Taken writes that span down first (above), so its day no longer moves; an older service worker still running before the update can move it once.
- **Clock ahead:** a span with `from` after today is dropped when the history is next written (`parseHistory(x, today)`); reads just do not use it before its day, and the implicit span covers the current fields meanwhile.
- **Size:** at most 64 spans per routine; older spans fold into the first one (earliest `from`, newest folded fields), so only the oldest period loses detail. 64 spans ≈ 12 KB per routine sealed.
- **Untrusted data:** history from the vault, a backup or a circle is checked by `parseHistory` on read; anything that is not a valid span list counts as no history.

Tests: `tests/schedule-history.test.ts`, `tests/history-csv.test.ts`, `tests/domain.test.ts`, `tests/pwa.test.ts`.
