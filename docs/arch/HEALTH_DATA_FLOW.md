# OAK Healthy health-data flow

This document names the canonical domain and the owner of every write path that can affect user wellness data.

## Supported domain

OAK Healthy currently models supplement/wellness routines, not general clinical measurements. The canonical domain is:

- **Client profile** — identity/scope for one person's routines and history. UUID is identity; `ClientNamePolicy` owns trimmed presentation names and duplicate comparison.
- **Supplement routine** — schedule, dose text, recurrence/cycle and start date.
- **Intake history event** — one canonical scheduled dose with `Taken` or `Skipped` state.
- **Backup/import/export** — versioned snapshot and history transfer with preview/integrity/rollback.
- **Derived insight** — completion, late intake, trend, streak and coach summaries derived from persisted history.

`Measurement`, `Workout`, `BodyMetric`, `NutritionLog` and generic `Goal` objects are intentionally absent until the product has real features that own those semantics. Do not add placeholder blobs for them.

## End-to-end ownership

### Android

`UI input` → `ViewModel validation/orchestration` → `domain use case` → `SupplementRepository` → `SupplementRepositoryImpl` → `Room DAO` → persisted entities.

- Routine create/edit: Add flow validates input; persisted edits/deletes route through `SupplementMutationUseCase` before repository persistence.
- Intake action: `RecordDoseUseCase` creates the canonical dose key and persists `IntakeStatus` storage values.
- Local-day conversion: `HealthDayBoundary` owns epoch ↔ local-day boundaries and all persistence queries use half-open `[start, end)` ranges.
- Timing/adherence formulas: `DoseTimingPolicy` owns due-soon, missed, late and completion formulas. `CalculateCycleUseCase` keeps routines `OFF` before their start day.
- History/coach UI consumes persisted records and derived policies; it does not own formulas or persistence.
- Import: `ImportBackupUseCase` validates canonical intake status and recurrence semantics, previews duplicate/orphan/remap impact before `importBackupAtomic`, and rolls back through the persistence boundary.

### iOS

`UI input` → `ViewModel validation/orchestration` → mutation store/service boundary → `ModelContext`/SwiftData → persisted models.

Mutation owners:

- Client profile: `ClientProfileMutationStore`.
- Supplement routine: `SupplementRoutineMutationStore`.
- Intake history and routine soft-delete: `SupplementHistoryMutationStore`.
- Routine field mutations such as dose-time removal: `SupplementRoutineMutationStore`.
- Full backup/import/merge: `SupplementExportCodec` + `BackupRestoreTransaction`.
- Pending Safe Mode import: `PendingImportRecoveryCoordinator`.

Views may query SwiftData and pass `ModelContext` to these owners, but Views do not call `insert`, `delete` or `save` directly.

## Canonical identities

- Client identity: UUID. `ClientNamePolicy` trims persisted names and uses case/diacritic-insensitive canonicalization only for duplicate detection. Safe Mode recovery targets `oakPendingImportClientId` when present; name-only recovery is legacy fallback and must be unambiguous.
- Supplement identity: UUID. Legacy exports without IDs are upgraded to deterministic IDs from the full routine field key; supplements are never merged by name alone.
- Intake identity: `DoseEventKey(supplementId, scheduledAtEpochMs)`. Both platforms use this identity for idempotence and duplicate handling.

## Date/time semantics

- Epoch milliseconds represent instants such as scheduled intake events and update timestamps.
- Date-only fields such as routine start day, weekly anchor and `lastTakenLocalDate` represent a calendar day, not a UTC instant.
- Android stores date-only values as `LocalDate`/ISO date strings and converts query windows with `HealthDayBoundary`.
- iOS uses `LocalDayCodec`; it preserves the literal calendar day in the supplied local calendar/time zone and does not round-trip through UTC. `CycleCalculator` keeps routines `OFF` before their start day.
- Persistence/history windows are half-open `[startInclusive, endExclusive)` so midnight belongs to exactly one day and DST days may be 23 or 25 hours.
- UI presentation formatting is not a persistence codec.

## Status and formulas

Persisted intake states remain schema-compatible strings (`Taken`, `Skipped`), but production logic reads/writes them through `IntakeStatus`.

`DoseTimingPolicy` is the single owner on each platform for:

- due-soon window: 20 minutes before scheduled time;
- missed threshold: 2 hours after scheduled time;
- late-taken threshold: more than 20 minutes after scheduled time;
- completion: `taken / (taken + skipped)` when the denominator is non-zero.

History, Home and Coach consume this policy rather than copying thresholds or formulas.

## Backup/import/recovery

1. Decode supported schema/version, reject unknown intake status/invalid recurrence semantics, and verify integrity when a manifest is present.
2. Build preview before destructive restore.
3. Reject duplicate supplement IDs, duplicate canonical dose identities or orphan history.
4. Resolve cross-client ID collisions deterministically; do not use display names as identity.
5. Snapshot the target client before replacement.
6. Apply import transaction.
7. On failure, rollback explicitly; rollback failure is surfaced rather than swallowed.
8. Rebuild reminders only after persistence succeeds.

Safe Mode recovery additionally uses the stored target client UUID. A legacy name-only target is accepted only when exactly one matching profile exists.

## Migration strategy

- Android Room schema migrations remain the persistence owner for database evolution; new domain policies do not change stored `Taken`/`Skipped` values or existing date columns.
- iOS `OAKSchemaMigration` remains the SwiftData schema owner. This hardening changes mutation/date codecs without introducing a model schema version, so existing stores remain compatible.
- Legacy date-only strings are interpreted literally as the stored calendar day. Older files already written with an incorrect UTC-shifted literal cannot be safely guessed back to the user's original intended day; the importer preserves the literal instead of applying another implicit conversion.

## Verification invariants

Focused tests protect:

- local-day/DST boundaries;
- requested-day history deletion;
- dose timing/completion formulas;
- canonical intake idempotence;
- duplicate client-name rejection at mutation boundary;
- deterministic IDs for legacy same-name routines;
- stable-client-ID Safe Mode recovery;
- corrupt coach check-in persistence surfacing an error;
- existing backup/restore, sync conflict and migration regression suites.
