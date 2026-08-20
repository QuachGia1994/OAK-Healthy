# P9.3 Sync Engine 2.0 QA Matrix

## Invariants

- Local Room/SwiftData remains the source of truth for queued changes; the queue stores only dirty part + timestamp.
- A successful sync clears only queue entries created at or before that sync attempt started.
- A local edit created during an in-flight sync remains queued for the next attempt.
- Remote data applies only when its effective timestamp is strictly newer than local state.
- Equal timestamps deterministically keep local state.
- A stale remote tombstone cannot erase a newer local edit.
- Automatic/realtime/background sync respects exponential backoff; explicit user Sync Now may retry immediately.
- Operation journals contain event/timing/count metadata only, never supplement names, doses, intake payloads, encryption keys or raw cloud data.

## Conflict preview

Before retrying a 409/412 conflict, both platforms classify material local-vs-remote differences into:

- `remoteWins`: remote effective timestamp is newer.
- `localWins`: local effective timestamp is newer.
- `tieLocalWins`: timestamps are equal and local is retained.

The preview is informational and does not change the deterministic merge rule.

## Backoff

Automatic retry delay starts at 15 seconds, doubles per consecutive failure, and caps at 10 minutes. A successful sync clears retry state.

## Platform matrix

| Scenario | Android | iOS | Expected |
| --- | --- | --- | --- |
| Local stack dirty while offline | `SyncMutationQueue` | `SyncMutationQueueStore` | Dirty stack intent survives failed sync |
| Local history dirty while offline | `SyncMutationQueue` | `SyncMutationQueueStore` | Dirty history intent survives failed sync |
| Mutation during running sync | cutoff-based clear | cutoff-based clear | New mutation remains queued |
| Remote newer field | timestamp policy | timestamp policy | Remote applies |
| Local newer field | timestamp policy | timestamp policy | Local remains |
| Equal timestamps | tie-local policy | tie-local policy | Local remains |
| Stale remote deletion | effective timestamp guard | existing effective timestamp guard | Newer local edit survives |
| 409/412 retry | preview + one retry | preview + one retry | Conflict counts are observable |
| Repeated transient failure | exponential backoff | exponential backoff | Automatic request storm avoided |
| Manual retry | `force=true` | direct `syncNow` | User may retry immediately |

## Machine checks

Run `python scripts/sync_engine_gate.py` and the Android/iOS `SyncOperationPolicyTests` before closing P9.3.
