# P9.4 Coach Workspace 3.0 QA Matrix

## Product invariants

- Every comparison is client-scoped: current 7/30/90-day window vs the immediately preceding window for the same client.
- Completion is descriptive adherence data only. It is not a medical, clinical, disease-risk, or diagnosis score.
- Routine check-ins capture only subjective maintainability (`comfortable`, `okay`, `difficult`) plus an optional local note.
- Check-ins remain local-only and are not sent to cloud sync, analytics, Crashlytics, Firebase diagnostics, or entitlement telemetry.
- Notes are trimmed to 500 characters and the local journal is bounded to 20 entries per client.
- The report export boundary accepts a renderer-neutral `CoachReportDocument`; future PDF renderers must not fetch Room/SwiftData or UI state directly.

## Cross-platform scenarios

| Scenario | Android | iOS | Expected |
|---|---:|---:|---|
| 7-day current vs previous | `CoachWorkspaceTest` | `CoachWorkspaceTests` | Same-client previous 7 days only |
| 30/90-day selection | `CoachWorkspaceBuilder` | `CoachWorkspaceBuilder` | Supported windows only |
| No previous records | unit test | unit test | Delta is unavailable, never fabricated |
| Search + sort + filter | `CoachOverviewScreen` | `CoachOverviewView` | All / follow-up / active / no activity |
| Client drill-down | inline detail card | navigation detail view | Current/previous stats + trend |
| Routine check-in | local SharedPreferences store | local UserDefaults store | Bounded local journal |
| Report boundary | `CoachReportRenderer` | `CoachReportRenderer` | Renderer consumes document snapshot |
| Medical language regression | `coach_workspace_gate.py` | `coach_workspace_gate.py` | Fail closed on diagnostic/clinical scoring terms |
| Telemetry regression | `coach_workspace_gate.py` | `coach_workspace_gate.py` | No analytics/Firebase/Crashlytics calls from workspace core |

## Manual review

- Verify long client names and Dynamic Type/large font do not hide the comparison or check-in controls.
- Verify a client with no current records shows em-dash completion rather than 0%.
- Verify saving a check-in does not create or modify any Taken/Skipped intake record.
- Verify changing the report window rebuilds detail from the same client only.
- Verify report-ready counts match the detail trend and recent local check-ins.
