# Delete everything

> updated 2026-10-11 · v2.9.0

One action deletes what OAK holds about you, on the server and on this device. It is in Settings and on the Account page, behind an inline confirm ("Yes, delete everything").

Code: `lib/domain/wipe.ts` (order and resume rules), `lib/account.ts` `wipeEverything` (real steps), `components/settings.tsx` `DeleteEverything` (UI) and `SyncAgent` (notice after the reload), `components/stack-manager.tsx` `shareStack` (share list), `components/settings.tsx` `ShareLinks` and `lib/domain/shares.ts` (Share links card), `worker/index.ts` vault route (tombstone) and share route (delete). Tests: `tests/wipe.test.ts`, `tests/share.test.ts`, `tests/share-list.test.ts`.

## Order

1. **Circles you own.** One sync pull first, so circles made on another device that synced are known. Then `DELETE /api/circle/<id>` for every live circle in meta `circles` with a write token `w` (member and bearer circles). Each one stays locally as a tombstone (`lib/circles.ts` `revokeCircle`), and the server drops the circle and its members.
2. **Reminders.** `disableReminders` removes the push subscription (server row and browser).
3. **Share links.** `DELETE /api/share/<id>` with `Authorization: Bearer <token>` for every entry in meta `shares` (expired ones too: the daily purge may not have removed them yet). Each entry leaves the list (its id goes to meta `sharesGone`, contract C3) once deleted, already gone (200) or refused for good (`shareDeleteOutcome`: 403 wrong or no hash, 405 a 2.7 worker, 400/401 a malformed entry); refused ones are skipped and counted (`sharesKept`), never retried. A network error, 429 or 5xx stops the run before the vault.
4. **Sync off, then the vault.** The vault id and auth are derived from the sync key and kept in the progress marker; sync is turned off on this device; then `DELETE /api/vault/<id>`.
5. **Account** (when signed in; if the page did not know yet, GET `/api/auth/me` decides, and a failed check stops the step instead of skipping it). `DELETE /api/auth/me` removes the user, sessions, sign-in methods, passkeys, reset tokens, member key and circle memberships.
6. **This device**, last and in one call: the IndexedDB database is deleted (which also removes the marker) and the app reloads as new. Just before the reload, sessionStorage `oak:wiped` is set to `sharesKept`; after it, `SyncAgent` (mounted on every app page) reads and removes the flag and shows "Everything deleted" once, with "{n} share links couldn't be deleted. They expire 365 days after they were created." when `sharesKept` > 0.

## Share links

| | |
|---|---|
| Token | `shareStack` mints 32 random bytes, base64url (256 bits). Sent only in the share's DELETE `Authorization` header, never in a URL or query. |
| Server | `POST /api/share` takes `deleteHash` = SHA-256 hex of the token (64 lowercase hex; malformed: 400; absent: NULL, as from 2.7 apps), stores it in `shares.delete_hash` (migration `0007_share_delete.sql`) and answers `expiresAt` (server clock). |
| List | meta `shares` = `[{ id, token, expiresAt, title?, createdAt? }]` (contract C1), appended after the 201 and before the link is copied; it rides in the encrypted vault (union by id), never in Export. `title` is the list name (trimmed, at most 120 characters), `createdAt` this device's clock; 2.8 entries have neither. A bad title or createdAt drops only that field (`cleanShareFields`). The title stays on your devices and in the encrypted vault; a pretty link's words are public as before. It is never in a DELETE, URL, query or log, and the card renders it as text only. |
| Gone | meta `sharesGone` = `[{ id, at }]`, ids only (never a token). Deleting a link, from the card or Delete everything, removes it from `shares` and adds its id here in one transaction (C3, `forgetShareMeta`); an id already there keeps its first `at`. Ids are hidden from the card and dropped from merged lists, so another device's list cannot bring the link back. An entry is dropped 400 days after `at` (`parseSharesGone`: bad ids or times are ignored, a future `at` counts from now). |
| Delete | By id only (the slug form answers 400). No row: 200. Hash mismatch or NULL hash: 403. Match: the row is deleted, expired or not. |
| Rate limit | 120 DELETEs per IP per hour (key `share-del:<ip>`), checked before the id and token, so every call counts; call 121 answers 429 "Too many attempts. Try again later.". |
| Expiry | Once a day the ReminderClock purge deletes share rows with `expires_at` < now, 500 per statement (`docs/arch/HEALTH_DATA_FLOW.md`); GET already hides them before that. |

## Share links card

Settings → **Share links** lists meta `shares` minus `sharesGone` (`shareRows`): newest created first, 2.8 entries (no created date) last. Each row shows the title or "Untitled stack", "Created {date}" when known, and "Expires {date}" ("Expired {date}" once past). Empty: "No share links yet. Use Share on the Stack page to create one."

**Delete** asks inline ("Delete this share link? People with it lose access." Cancel / Yes, delete), then sends the same DELETE as Delete everything (`deleteOneShare`):

| Answer | Result |
|---|---|
| 200 | Removed (C3). Toast "Share link deleted". |
| 400, 401, 403, 405 | Removed too (a retry cannot change it). Toast for 15 s: "This share link couldn't be deleted. It expires {date}." |
| Network error, 5xx | Stays listed. Error toast "Couldn't delete the share link." (no raw fetch or HTTP text). |
| 429 | Stays listed. Error toast "Couldn't delete the share link. Too many attempts. Try again later." |

A link already gone from the list (deleted in another tab) sends nothing.

## Vault tombstone

The vault DELETE empties the blob and sets `deleted_at` (migration `0006_vault_tombstone.sql`); with no vault yet, it creates the tombstone. Only the vault id (derived one-way from the sync key), the hash of its access token and the times stay. Under that auth, GET and PUT answer **410** `{error: "gone"}`, including a first push (`baseRev` 0), so no device can bring the vault back. A different token still gets 403. Another device that still syncs gets 410, turns sync off and shows once: "Your synced data was deleted on another device, so sync is off here." Data on that device stays.

## Failure and retry

The first failing step stops the run; nothing after it is deleted. The card names the step, shows the server's message and offers **Retry**. The marker (meta `wipe`) lists finished steps and is written after each one succeeds, so Retry (also after a reload; the card then shows "Deleting everything didn't finish.") skips them and repeats the rest:

| Step | Why repeating is safe |
|---|---|
| Circles | Deleted circles are tombstones and leave the list; the server answers ok for a circle already gone. |
| Reminders | Turning reminders off twice is a no-op. |
| Share links | Deleted and refused entries leave the list after each call, and the marker keeps `sharesKept`; the server answers 200 for a share already gone. A 2.7 marker (vault done, no `shares` step) runs the share step on its retry. |
| Vault | The server answers ok on a tombstone and keeps the first deletion time. |
| Account | The marker records that the DELETE was sent. A later 401 counts as done only then (the answer was lost after the server deleted it). A 401 answer to the request itself means the session expired: the marker is reset and the card asks to sign in first. |

## Limits

- A circle created on a device that never synced is unknown to the other devices. Run Delete everything (or Delete circle) on that device too.
- With more than 120 share links, Delete everything stops at the share step with "Too many attempts. Try again later."; Retry after the hour continues with the rest (deleted ones already left the list, so nothing is sent or counted twice).
- A 2.8 device's push drops `sharesGone` and titles; a deleted entry then shows again until another 2.9 device syncs.
- Share links created before 2.8 (no `delete_hash`) or on a device that never synced are not deleted: anyone with the link can open one until it expires, 365 days after creation; the daily purge then deletes the row.
- The tombstone row stays on the server (hashed ids and times only) and is deleted by the daily ReminderClock purge once it is 365 days past `deleted_at` (`docs/feat/notify-barcode.md`).
- After that purge, a device still on 2.7 or older that was offline the whole time gets 404, pushes with `baseRev` 0 and re-creates the vault with its local data (accepted, lead D1). 2.8 devices that synced the vault treat that 404 as gone.
- After the first wipe the worker cannot roll back below 2.7: a 2.6 worker would serve tombstones as live (empty) vaults. Fix forward only.
