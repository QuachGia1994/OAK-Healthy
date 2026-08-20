# P9.2 Reminder Reliability Matrix

P9.2 keeps reminder recovery separate from intake history. Recovery may rebuild or reconcile OS schedules, but it must never create Taken or Skipped records.

## Android

| Trigger / state | Expected recovery |
| --- | --- |
| App starts with registry entries whose `PendingIntent` is missing | Rebuild active-client schedules |
| `BOOT_COMPLETED` | Rebuild active-client schedules |
| `MY_PACKAGE_REPLACED` | Rebuild active-client schedules |
| `TIMEZONE_CHANGED` | Rebuild active-client schedules |
| `TIME_SET` | Rebuild active-client schedules |
| Exact alarms unavailable | Use existing inexact fallback; report degraded health |
| Battery optimization / system power saver | Report diagnostics; do not alter intake history |
| No future alarm during a valid rest window | Report review/degraded state; do not assume corruption |

## iOS

| Trigger / state | Expected recovery |
| --- | --- |
| App becomes active | Reconcile pending requests against future shadow entries |
| Notification authorization changes | Rebuild only when reminders are active and permission permits scheduling |
| `NSSystemTimeZoneDidChange` | Rebuild active-client schedules |
| `NSSystemClockDidChange` | Rebuild active-client schedules |
| Pending request exists but shadow entry is missing | Repair shadow from OS pending requests |
| Future shadow entry exists but OS pending request is missing | Rebuild schedules |
| Shadow contains a scheduling error | Rebuild schedules |
| Shadow entry is already in the past | Ignore it for missing-schedule detection |
| No future pending request during a valid rest window | Do not assume corruption |

## Safety invariants

- Recovery uses only the active client’s supplements.
- Recovery does not create, update, or delete intake-history records.
- Recovery does not mark a dose Taken or Skipped.
- Permission denial or the user notification toggle being off never forces a rebuild.
- Diagnostics expose platform state only; they do not include supplement names, doses, client IDs, sync keys, or health payloads.
- `scripts/reminder_reliability_gate.py` fails closed if lifecycle hooks or the no-intake-mutation invariant disappear.
