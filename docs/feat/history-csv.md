# Dose history as CSV

> updated 2026-10-11 · v2.11.0

Settings → Backup → **Download history (CSV)** saves the dose history as a spreadsheet file for a doctor or coach. The file is built on the device (`lib/domain/history-csv.ts`, `historyCsv`) and saved with a local download; no request leaves the device, and nothing is added to meta, the vault or the worker.

## Range
| Choice | Rows from | Rows to |
|---|---|---|
| Last 30 days (default) | local midnight of today − 29 days | now |
| Last 90 days | local midnight of today − 89 days | now |
| All history | the earliest live record or the start date of a live routine | now |

File name: `oak-healthy-history-YYYY-MM-DD.csv`, the local date of the download.

## Rows
One row per dose, every profile on the device (deleted profiles too, so their history keeps a name).

| Status | When |
|---|---|
| Taken / Skipped | a record (intake) without `deletedAt`, at its own scheduled time; kept even when the routine is now paused, deleted or rescheduled |
| Missed | no live record for a slot the dashboard plans (`planned`, `lib/domain/intake-key.ts`) and already shows as missed (`isMissed`, `lib/domain/timing.ts`: more than 2 h past) |

- **Missed uses the routine's current schedule.** No pause or edit history is stored, so a routine paused or deleted today has no Missed rows at all, and cycle off-days and days before its start date have none.
- A slot not yet 2 h past and not recorded is not listed (the dashboard still shows it as due). Future slots are never listed.
- Records match slots by exact time (dose key). After a dose time change (08:00 → 09:00), an earlier day can show Taken at 08:00 and Missed at 09:00.
- A record whose routine is gone from the device keeps its row with Person, Supplement and Dose empty.
- A record synced from another device with a status this version does not know shows that status as stored, escaped like user text (empty when it is not text).

## Columns
Date (YYYY-MM-DD, local) · Time (HH:mm, local) · Person (profile name) · Supplement (routine name) · Dose · Status · Recorded at (last change): the record's `updatedAt`, local `YYYY-MM-DD HH:mm`, empty for Missed.

Headers and status words follow the UI language. Sorted by date, time, then person and supplement in the UI language's order.

## Format
- RFC 4180: a cell with `"` `,` CR or LF is quoted, inner `"` doubled; CRLF line ends, the last row too; UTF-8 with a BOM so Excel shows Vietnamese names right.
- CSV injection guard (OWASP): Person, Supplement, Dose or Status starting with `=` `+` `-` `@` tab or CR, or with spaces, tabs, CR or LF and then `=` `+` `-` `@`, gets a leading `'` before quoting; the text after it is unchanged. Excel, Google Sheets and LibreOffice then show it as text instead of running it as a formula. A dose like `-5 mg` shows as `'-5 mg`, ` =1+1` as `' =1+1`. Dates, times, Taken / Skipped / Missed and headers come from the app, not from user text.
- An empty range gives the header row only.

## Privacy
The CSV is plaintext health data, like the JSON export: it stays where the user saves it. Making it sends nothing to the server (zero-knowledge holds).

Tests: `tests/history-csv.test.ts`.
