# OAK Healthy system boundaries

Updated 2026-08-18 · v1.0.1

This document records current ownership boundaries after the health-data integrity hardening pass. It describes implementation structure, not a future roadmap. See also [`HEALTH_DATA_FLOW.md`](HEALTH_DATA_FLOW.md).

## Sync

- Android `CloudSyncEngine` owns transport/merge/retry behavior.
- Android `CloudSyncStatusReader` owns persisted sync status/metrics reads. `HomeViewModel` maps the typed snapshot to UI state and no longer knows metric preference keys.
- Android `CloudSyncLogStore` owns sync-log JSON persistence and parsing. Sync Center receives typed log entries.
- iOS `CloudSyncCoordinator` owns transport/merge/retry behavior.
- iOS `SyncCenterStatusReader` owns UserDefaults-backed status/metrics reads and pending-change observation.
- iOS `CloudSyncLogStore` owns bounded sync-log persistence. `SyncCenterView` only filters/formats typed entries.

## Notifications

- Android `AndroidNotificationDiagnosticsSource` is the platform diagnostics boundary for permission, exact alarms, power state, schedule registry and PendingIntent audit. Notification UI does not call platform alarm/power APIs directly.
- Existing scheduling/recovery behavior remains in `NotificationScheduleEngine`, `NotificationSchedulerImpl` and the P9.2 reliability policy.
- iOS `NotificationScheduleLifecycleCoordinator` owns active-client supplement fetch + reschedule/reconcile orchestration for foreground, profile and clock/time-zone lifecycle events.

## Backup and recovery

- Backup codecs remain responsible for payload validation, integrity and persistence semantics.
- Android `ImportBackupUseCase` owns preview/validation and delegates the final replacement to repository atomic import.
- iOS `PendingImportRecoveryCoordinator` owns Safe Mode apply orchestration: stable-preview check, stable client-ID resolution, import, link restoration, notification reschedule and explicit rollback of a newly created client on import failure.
- Name-only iOS recovery is legacy fallback only and fails when more than one profile matches.
- `SafeModeView` owns only UI state, file selection/lifecycle and user messaging.

## App bootstrap

- iOS `AppBootstrapper` owns SwiftData container creation, active-client validation and construction of notification/entitlement/billing dependencies.
- `SafeBootView` owns crash-recovery decision, splash timing and presentation of bootstrap failure/retry.

## Health persistence and derived policy

- Android persistence mutations go through `SupplementRepository`; Room DAO details do not leak into UI/domain code.
- iOS client mutations are owned by `ClientProfileMutationStore`; routine mutations by `SupplementRoutineMutationStore`; intake/tombstone mutations by `SupplementHistoryMutationStore`. Views do not directly insert/delete/save SwiftData models.
- `IntakeStatus` is the schema-compatible owner of persisted Taken/Skipped terminology; `ClientNamePolicy` owns trimmed/canonical profile-name validation while UUID remains profile identity.
- `HealthDayBoundary` (Android) and `LocalDayCodec` (iOS) own date-only/day-window semantics.
- `DoseTimingPolicy` owns due-soon, missed, late and completion formulas on each platform.

## Coach

- Cross-platform report/trend/check-in semantics remain in the P9.4 Coach workspace domain services.
- Android `CoachWorkspaceSourceProvider` owns repository reads and conversion into Coach snapshots. `HistoryViewModel` owns only report state/window selection.

## Invariants

- This hardening does not change stored model schema versions; existing `Taken`/`Skipped` strings and stored date columns remain compatible.
- Local databases remain the source of truth for routines and intake history.
- Sync conflict policy remains local-first on equal timestamps and rejects stale remote deletion over newer local state.
- Reminder recovery never creates Taken/Skipped intake records.
- Safe Mode import still revalidates the preview immediately before persistence mutation and targets a stable client UUID when available.
- History day windows are half-open `[startInclusive, endExclusive)`; midnight belongs to exactly one day.
- Architecture boundaries must remain enforced by `scripts/architecture_boundaries_gate.py`.
