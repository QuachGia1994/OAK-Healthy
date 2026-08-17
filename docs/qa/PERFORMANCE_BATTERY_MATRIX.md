# P10.2 Performance & Battery Matrix

Updated 2026-08-17 · v1.0.1

The P10.2 gate uses deterministic source/data-work budgets rather than wall-clock CI benchmarks.

## Persistence/query budgets

| Path | Budget / invariant | Regression evidence |
|---|---|---|
| Android History | Query only the entitlement-visible date range | `HistoryViewModel` uses `getRecordsByDateRange` |
| Android Coach | Query only current + previous 90-day windows (180 days) | `PerformanceBudgets.COACH_HISTORY_DAYS` |
| Android sync dirty check | SQL existence query, no full snapshot materialization | DAO `SELECT EXISTS` + repository overrides |
| Android sync payload | At most 5,000 recent intake rows | Existing sync query limit remains 5,000 |
| iOS history/sync | SwiftData predicate + sort + `fetchLimit` | `ClientScopedStore` direct descriptors |
| iOS dirty check | Fetch at most one matching model | `fetchLimit = 1` |

## Background / wakeup budgets

| Path | Budget | Rationale |
|---|---|---|
| Android periodic cloud sync | 30 minutes, network connected | User edits still request foreground/one-off sync; periodic work is fallback reliability |
| iOS active realtime fallback poll | 30 seconds minimum | Firebase listener and local dirty triggers are primary; polling is fallback |
| iOS medium idle poll | 120 seconds | Avoid tight wakeups after recent activity |
| iOS idle poll | 600 seconds | Keep fallback recovery without constant wakeups |

## Stress fixtures

Repository logic must remain correct with at least:
- 100 synthetic clients for pure Coach aggregation/filter tests.
- 5,000 sync history records (current payload cap).
- 180 days of Coach history per client.
- Dense routine schedules without increasing periodic worker frequency.

Stress fixtures must use synthetic identifiers/content only. They must not use production user data or introduce telemetry fields.

## Exit criteria

- Machine gate passes without timing-sensitive assertions.
- Existing local-first, conflict, notification, backup and privacy gates remain green.
- Android/iOS platform builds on the P10 close SHA confirm query syntax and type safety.
