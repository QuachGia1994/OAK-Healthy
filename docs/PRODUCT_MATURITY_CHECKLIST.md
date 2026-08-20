# OAK Healthy — P7/P8 Product Maturity Checklist

Store distribution remains deferred. This checklist is repository/device-test work and does not require paid Apple or Google developer accounts.

## P7.1 Reminder Reliability 2.0

- Android audits future schedule registry entries against their actual `PendingIntent` registrations and distinguishes stale/missing alarms from platform limitations.
- Consumed Android one-shot alarms remove their registry entry at broadcast receipt, so normal delivered reminders do not become false stale alarms on the next health check.
- iOS compares actual pending notification requests with the shadow registry and can rebuild the complete schedule set.
- A zero-future-reminder state is reviewable but is not automatically treated as corruption because a valid routine may be in a long rest phase.
- Repair actions rebuild reminders only; they never write intake history.

## P7.2 Coach Reports 2.0

- 7/30/90-day report windows use the same local intake history on both platforms.
- Reports expose aggregate completion, Taken/Skipped counts, recent trend buckets, search and sort.
- Android caches the loaded Coach source when switching windows; iOS builds one report per selected window/render.
- “Check-in” remains a neutral workflow signal, not a medical risk score or diagnosis.

## P7.3 Sync Recovery & Conflict UX

- Sync health separates unlinked, idle, healthy, pending, missing-key, retryable and action-required states.
- Retryable recovery only triggers the existing safe sync engine.
- Missing-key guidance requires the matching encryption key before retrying.
- Recovery UI must never delete or overwrite local data merely because remote sync failed.

## P7.4 Backup & Data Portability

- New backups carry an optional semantic SHA-256 integrity manifest with schema, algorithm and item counts.
- Android and iOS use the same canonical semantic representation and a shared fixed digest fixture.
- A manifest mismatch fails before import persistence mutation.
- Legacy backups without a manifest remain readable; existing deterministic identity/collision handling remains in force.

## P7.5 Performance, Accessibility & Large Text

- Coach window changes avoid unnecessary Android repository refetches.
- iOS Coach reporting avoids repeated full relationship traversal within one render.
- Adaptive/weighted summary layouts keep key metrics readable with Vietnamese and larger text sizes.
- Main guide, Coach, reminder-health and sync-recovery copy is normalized across EN/VI; technical tier/store names remain intentional where they are product/platform terminology.
- New diagnostic/report cards combine related accessibility content without hiding actionable controls.

## P8 Product Maturity

- `docs/qa/PRODUCT_MATURITY_SCENARIOS.json` contains synthetic-only cross-platform regression scenarios.
- `scripts/product_readiness.py` validates scenario coverage, P7 core files, demo debug guards and non-medical positioning.
- Quality Gates run the readiness check and its unit tests before platform coverage jobs are considered complete.
- Debug synthetic demo content never persists to Room/SwiftData and is absent from release navigation.

## STOP conditions

Stop a rollout or release-candidate promotion if any validation shows data loss, an integrity mismatch accepted as valid, reminder repair mutating intake history, an unverified premium entitlement, raw health/identifier telemetry, or a migration/sync flow that overwrites newer local data without an explicit merge decision.
