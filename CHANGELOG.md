# Changelog

All notable changes to OAK Healthy are documented here. The project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses semantic
versioning where practical.

## [Unreleased]

### Added

- P7.1–P8 reliability/maturity work: cross-platform reminder health and repair paths, 7/30/90-day Coach reports, typed sync recovery states, semantic backup integrity manifests, adaptive report/demo layouts, synthetic QA scenarios, and a repository product-readiness CI gate.
- New Android/iOS backup fixtures assert the same canonical SHA-256 digest and reject payload changes before import persistence mutation.
- P5.1A store-neutral commerce lifecycle/replay core on Android and iOS with retry-safe authority-unavailable behavior, fail-closed downgrade states, and unit fixtures; it is not a runtime premium unlock path.
- P5.2–P6 product work for deferred-store development: plan-preview fallback, first-value onboarding guidance, entitlement-gated Coach Overview, overdue recovery CTA, reminder-rebuild visibility, EN/VI copy, and debug-only read-only synthetic demo screens.
- Phase 4 commercial validation/growth gates: machine-checkable real-store evidence, production candidate matching, privacy-safe aggregate KPI reporting, and a P4.1–P4.4 operations runbook/templates.
- Commercial funnel diagnostics now cover store product-load, purchase-result, and restore-result outcomes with allowlisted product/plan/period/store metadata only.
- An explicit iOS SwiftData schema v1/migration-plan baseline plus a regression fixture that writes an existing unversioned store and reopens it through the versioned container without losing client, supplement, or intake data.
- Android Room v2→v6 migration regression coverage and shared Android/iOS fixtures for legacy backup identity compatibility.
- Beta/store release preflight, safe beta/internal defaults, explicit production confirmation, Play billing-key release wiring, launch checklist, listing copy, and signed iOS Crashlytics dSYM upload.
- Privacy-first Firebase Analytics/Crashlytics diagnostics on Android and iOS with explicit opt-in, allowlisted commercial funnel events, health-data field scrubbing, in-app disclosure, and store privacy declaration guidance.
- StoreKit 2 and Google Play Billing 9.1 subscription flows with store-localized pricing, purchase/restore handling, verified entitlement resolution, and fail-closed pending/unverified purchase behavior.
- Cross-platform Free/Pro/Coach entitlement policy, stable subscription product catalog, Plan & Access UI, and monetization delivery roadmap.
- Cross-platform AES-GCM interoperability and revision validation tests.
- In-app sync security guidance, unlink controls, and protected key display.
- Durable iOS notification actions for Taken and Skipped dose events.
- Public contribution, governance, security, and roadmap documentation.

### Changed

- Store execution is explicitly deferred until developer accounts are desired; repository commerce/store gates remain intact and do not block continued product development.
- About/onboarding/recovery copy now consistently positions OAK Healthy as a wellness routine tracker for individuals and coaches without diagnosis/treatment claims.
- Production store promotion now requires an explicit beta-tested candidate commit/version/build and release-specific P4 evidence; iOS reuses that TestFlight build and Android promotes that Play versionCode instead of rebuilding production binaries.
- iOS production bootstrap now constructs its persistent `ModelContainer` from the versioned SwiftData schema and central migration plan.
- Firebase payload and revision reads are now atomic snapshots.
- Firebase writes now use compare-and-set transactions with monotonic revisions.
- Android and iOS sync settings now separate pending Link Code input from the
  active link.
- Three-tab navigation, filters, app icon, and loading identity were redesigned
  consistently across Android and iOS.
- Android card rendering and accessibility semantics were streamlined to reduce
  scroll frame spikes on physical devices.
- iOS simulator Debug builds use ad-hoc signing so Keychain-backed encryption
  tests run under GitHub Actions without changing unsigned Release archives.

### Fixed

- Android consumed one-shot reminders now remove their schedule-registry entry when the broadcast is received, preventing successfully delivered alarms from being reported as stale by reminder-health diagnostics.
- Android Coach Overview no longer imports Compose's internal `weight` symbol, fixing the `defc7bf` compile regression; iOS SwiftData schema version identity is now computed rather than stored as a non-Sendable static value under Swift 6 concurrency checks.
- Android Room v2→v3 now stages intake history before rebuilding the supplement table, preventing foreign-key cascades from deleting legacy history during migration.
- Legacy v1 and ID-less backup payloads now derive the same deterministic supplement identity on Android and iOS, preventing cross-platform duplicate identities after import or sync.
- iOS dose-time and supplement deletion now await notification cleanup before returning, and nil-client autosync requests no longer retain short-lived SwiftData contexts after teardown.
- Android periodic cycle workers now use a WorkManager-compatible constructor.
- Android backup imports now preserve interval/last-taken data and remap cross-profile ID collisions without breaking history links.
- Android background cloud sync now runs without constructing `HomeViewModel`, shares active-client/log persistence services, and retries engine-reported failures instead of silently treating them as success.
- Android startup now uses a dedicated dependency factory and testable theme/splash/notification policies; `MainActivity.onCreate` no longer owns database or ViewModel construction.
- Factory reset now stops sync, clears pending notifications, removes local profile/history data, wipes app preferences and cloud encryption material on both Android and iOS.
- Android reminders fall back safely when exact-alarm access is unavailable and no longer re-enable an in-app notification opt-out.
- Cross-platform sync now preserves newer deletion tombstones, keeps the revision returned by Android conflict retries, and avoids multi-client history truncation on iOS.
- iOS manual and automatic cloud sync now share one serialized engine, including legacy single-bin fallback and stale-error cleanup.
- iOS cloud sync no longer carries the unused JSONBin HTTP transport or secret injection; orchestration now lives in a dedicated coordinator with regression-tested serialization/fallback/conflict handling.
- iOS client-scoped SwiftData reads now scope through the target profile before applying record limits, preventing other profiles from hiding History or sync records and reducing fetch-all work.
- Notification scheduling and dose actions now stay isolated to the active profile on Android and iOS, including profile switches, reboot/time-change rescheduling, and stale notification actions.
- Cloud host/link ownership is now scoped per profile on Android and iOS; legacy global links migrate once to the active profile, profile switches rebind realtime sync, and Android in-flight exports retain their captured profile.
- iOS realtime cloud sync sessions now bind to both profile and manifest identity, reject stale generation completions, and cannot let an old listener clear or mark a newer profile session.
- Android profile create/update/delete flows now commit persistence before changing active-profile or dialog state, preventing duplicate-name or database failures from creating phantom active clients.
- Android alarm receivers now retain their broadcast lifetime while asynchronous reminder validation and rescheduling complete.
- Cloud hosting now cleans up partial uploads, and iOS re-hosting keeps the previous host until the replacement is ready.

### Security

- Cloud/debug telemetry no longer emits client IDs, cloud link IDs, raw server responses, file paths, or raw error text; analytics and crash collection are disabled by default until the user opts in.
- Malformed encryption envelopes now fail closed.
- Firebase identifiers and encryption key IDs are validated before use.
- In-place encryption-key rotation was removed to prevent inaccessible cloud
  data.
- Firebase database rules now validate node shape, payload size, and strictly
  increasing revisions.

[Unreleased]: https://github.com/QuachGia1994/OAK-Healthy/commits/main
