# OAK Healthy system boundaries

Updated 2026-08-17 · v1.0.1

This document records current ownership boundaries after P10.1. It describes implementation structure, not a future roadmap.

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
- iOS `PendingImportRecoveryCoordinator` owns Safe Mode apply orchestration: stable-preview check, client resolution, import, link restoration, notification reschedule and rollback of a newly created client on import failure.
- `SafeModeView` owns only UI state, file selection/lifecycle and user messaging.

## App bootstrap

- iOS `AppBootstrapper` owns SwiftData container creation, active-client validation and construction of notification/entitlement/billing dependencies.
- `SafeBootView` owns crash-recovery decision, splash timing and presentation of bootstrap failure/retry.

## Coach

- Cross-platform report/trend/check-in semantics remain in the P9.4 Coach workspace domain services.
- Android `CoachWorkspaceSourceProvider` owns repository reads and conversion into Coach snapshots. `HistoryViewModel` owns only report state/window selection.

## Invariants

- No schema or migration behavior is changed by P10.1.
- Local databases remain the source of truth for routines and intake history.
- Sync conflict policy remains local-first on equal timestamps and rejects stale remote deletion over newer local state.
- Reminder recovery never creates Taken/Skipped intake records.
- Safe Mode import still revalidates the preview immediately before persistence mutation.
- Architecture boundaries must remain enforced by `scripts/architecture_boundaries_gate.py`.
