# P11.1 UX Polish Matrix

## Scope

P11.1 standardizes loading, empty, error, no-match, and recovery feedback without changing data ownership or Store behavior.

| Surface | Android | iOS | Expected behavior |
| --- | --- | --- | --- |
| Home / no client | Actionable create-client state | Actionable create-client state | User always has a next action. |
| Home / no routine | First-value action | First-value action | Empty dashboard points to adding a routine. |
| History / loading | Progress indicator | Progress indicator | Existing data is not shown as an error while loading. |
| History / load failure | Shared feedback card + Retry | Shared feedback card + Retry | Failure does not mutate local data; retry is explicit. |
| History / true empty | Shared neutral feedback | Shared neutral feedback | Empty means there are no records, not that loading failed. |
| History / filtered empty | Dedicated no-match copy | Dedicated no-match copy | Search/filter mismatch is distinct from true empty. |
| History / no client | Settings recovery action | Settings recovery action | User can reach client management. |
| Coach / locked | Shared feedback + plan action | Shared feedback + plan navigation | No local paid-access bypass. |
| Coach / load failure | Shared feedback + Retry | N/A: local SwiftData query is synchronous | Android can retry repository loading; no data mutation on failure. |
| Coach / empty | Shared neutral feedback | Shared neutral feedback | No diagnostic or medical scoring language. |
| Settings / long label + value | Flexible label + single-line trailing value | Flexible row layout | Long EN/VI text does not collapse timestamps/status into character columns. |
| Sync / recoverable failure | Health summary + action/hint | Health summary + retry action/hint | Retry remains local-first and does not delete local data. |

## Large-text checks

- Shared feedback components do not impose body `maxLines`.
- Settings trailing values remain readable while labels wrap instead of stealing all trailing width.
- Coach summary metrics already switch to stacked presentation at large Android font scale; iOS uses adaptive layout from earlier maturity work.

## Privacy invariants

- History diagnostics use only aggregate client presence, never a raw client UUID.
- History load failure reports only the error type, not raw error text or record content.
- Feedback copy never contains supplement names, doses, notes, sync keys, or client identifiers.

## Exit gate

Run:

```text
python scripts/ux_polish_gate.py
python -m unittest scripts.tests.test_ux_polish_gate -q
```

P11.1 remains local until Android/iOS compile verification is available on a pushed SHA or equivalent executable local evidence.
