# Delete everything

> updated 2026-10-11 · v2.7.0

One action deletes what OAK holds about you, on the server and on this device. It is in Settings and on the Account page, behind an inline confirm ("Yes, delete everything").

Code: `lib/domain/wipe.ts` (order and resume rules), `lib/account.ts` `wipeEverything` (real steps), `components/settings.tsx` `DeleteEverything` (UI), `worker/index.ts` vault route (tombstone). Tests: `tests/wipe.test.ts`.

## Order

1. **Circles you own.** One sync pull first, so circles made on another device that synced are known. Then `DELETE /api/circle/<id>` for every live circle in meta `circles` with a write token `w` (member and bearer circles). Each one stays locally as a tombstone (`lib/circles.ts` `revokeCircle`), and the server drops the circle and its members.
2. **Reminders.** `disableReminders` removes the push subscription (server row and browser).
3. **Sync off, then the vault.** The vault id and auth are derived from the sync key and kept in the progress marker; sync is turned off on this device; then `DELETE /api/vault/<id>`.
4. **Account** (when signed in; if the page did not know yet, GET `/api/auth/me` decides, and a failed check stops the step instead of skipping it). `DELETE /api/auth/me` removes the user, sessions, sign-in methods, passkeys, reset tokens, member key and circle memberships.
5. **This device**, last and in one call: the IndexedDB database is deleted (which also removes the marker) and the app reloads as new.

## Vault tombstone

The vault DELETE empties the blob and sets `deleted_at` (migration `0006_vault_tombstone.sql`); with no vault yet, it creates the tombstone. Only the vault id (derived one-way from the sync key), the hash of its access token and the times stay. Under that auth, GET and PUT answer **410** `{error: "gone"}`, including a first push (`baseRev` 0), so no device can bring the vault back. A different token still gets 403. Another device that still syncs gets 410, turns sync off and shows once: "Your synced data was deleted on another device, so sync is off here." Data on that device stays.

## Failure and retry

The first failing step stops the run; nothing after it is deleted. The card names the step, shows the server's message and offers **Retry**. The marker (meta `wipe`) lists finished steps and is written after each one succeeds, so Retry (also after a reload; the card then shows "Deleting everything didn't finish.") skips them and repeats the rest:

| Step | Why repeating is safe |
|---|---|
| Circles | Deleted circles are tombstones and leave the list; the server answers ok for a circle already gone. |
| Reminders | Turning reminders off twice is a no-op. |
| Vault | The server answers ok on a tombstone and keeps the first deletion time. |
| Account | The marker records that the DELETE was sent. A later 401 counts as done only then (the answer was lost after the server deleted it). A 401 answer to the request itself means the session expired: the marker is reset and the card asks to sign in first. |

## Limits

- A circle created on a device that never synced is unknown to the other devices. Run Delete everything (or Delete circle) on that device too.
- Share links have no owner and no delete route: anyone with the link can open one until it expires after 365 days.
- The tombstone row stays on the server (hashed ids and times only).
- After the first wipe the worker cannot roll back below 2.7: a 2.6 worker would serve tombstones as live (empty) vaults. Fix forward only.
