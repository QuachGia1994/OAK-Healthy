# OAK Healthy Monetization Roadmap

## Product position

OAK Healthy is a supplement cycle and adherence system for individual power users and coaches. It should not compete as a generic medication reminder.

## Plans

### Free
- Basic supplement tracking
- Reminder notifications
- Recent history (7 days)
- One client profile

### Pro
- Everything in Free
- Advanced cycle scheduling
- Extended history (90 days)
- Adherence analytics
- Encrypted cloud sync
- Data export
- One client profile

### Coach
- Everything in Pro
- Multi-client management
- Extended history (365 days)
- Coach reports and client-oriented workflows

Store prices are never hard-coded into entitlement logic. App Store and Google Play are the source of truth for localized price and offer data.

## Stable product identifiers

- `oak_pro_monthly`
- `oak_pro_annual`
- `oak_coach_monthly`
- `oak_coach_annual`

Changing these identifiers after launch requires store-side migration, so they are treated as stable public identifiers.

## Delivery gates

### P3.1 — Entitlement & Paywall Foundation
- Cross-platform Free / Pro / Coach domain model.
- Stable product catalog and feature policy.
- App-wide entitlement state defaults fail-closed to Free.
- Plan & Access screen on Android and iOS.
- Settings entry point and regression tests.
- No fake purchase or local premium unlock path.

### P3.2 — iOS StoreKit 2
- Load App Store products by stable identifiers.
- Purchase and restore purchases.
- Verify transactions and derive entitlement only from verified StoreKit state.
- Handle upgrades, downgrades, revocations, expiration, billing retry and offline cached display.
- StoreKit configuration tests plus GitHub Xcode CI.

### P3.3 — Google Play Billing
- Integrate current Google Play Billing Library.
- Query subscriptions/base plans and localized offers.
- Purchase acknowledgement and restore/query owned subscriptions.
- Derive entitlement from verified Play purchase state; no preference-based premium unlock.
- Unit tests plus Android CI and APK artifact.

### P3.4 — Commercial Feature Enforcement
- Enforce one-client limit for Free/Pro without deleting existing user data.
- Gate advanced cycles, extended history, analytics, encrypted cloud sync and export behind Pro.
- Gate multi-client/coach workflows behind Coach.
- Existing users above a new limit remain readable and receive an upgrade path instead of destructive migration.
- Deep links and background workers fail closed when entitlement is insufficient.

### P3.5 — Analytics, Reliability & Compliance
- Privacy-preserving product funnel events with no supplement names, health payloads or free-form health text.
- Crash reporting with health-data scrubbing.
- Privacy policy and in-app wellness/non-medical disclaimer.
- App Store privacy labels and Google Play Data Safety / Health Apps declaration review.
- Purchase funnel metrics: paywall view, product load, checkout start, purchase success/failure, restore success.

### P3.6 — Beta & Store Launch
- TestFlight and Play closed testing purchase matrix.
- Store screenshots, ASO copy and subscription disclosure.
- Trial/intro-offer decision based on beta conversion data.
- Release checklist, rollback plan and support flow.
- Launch first to supplement-heavy fitness/biohacker users and coaches.

Repository implementation for P3.6 is complete when release preflight, Android CI, iOS CI, and the safe beta/internal release workflow are green. Real store validation continues in Phase 4 and must not be inferred from repository CI.

## Phase 4 — Commercial Validation & Growth

> Execution status: **deferred by product-owner choice** until developer accounts are desired. Repository readiness remains preserved; see `DEFERRED_STORE_DEVELOPMENT.md`.

### P4.1 — Store & Beta Validation
- Activate and verify all four stable products in App Store Connect and Google Play Console.
- Execute platform-accurate subscription matrices: shared Free/paid/highest-plan/cancel/invalid/restore/expiry-refund-revocation/offline cases, iOS Sandbox billing-retry behavior, and Android auto-renew grace-period/account-hold/recovery behavior.
- Record exact release candidate version/build/commit and an evidence reference for every passed case.
- Validate release evidence with `scripts/commercial_gate.py`; no generated or assumed PASS results.

### P4.2 — Offer & Conversion Strategy
- Extend consented commercial telemetry through product load, checkout result, and restore result without health/user payloads.
- Compare monthly/annual and Pro/Coach funnel performance using store-authoritative product IDs and aggregate metrics only.
- Record an explicit per-store offer decision (`none`, introductory, trial, or discount) backed by beta evidence.
- Never hard-code localized prices or advertise an offer that is not active in the store.

### P4.3 — Production Launch
- Require a release-specific commercial evidence file before any production upload.
- Require public HTTPS privacy/support URLs, completed store declarations, release-candidate screenshots, rollback/support evidence, and staged-rollout plans.
- Production version/build and evidence commit must match the tested `candidate_commit`; the promotion workflow itself may run from a later evidence/automation-only descendant commit.
- Public launch is complete only when the intended build is available to the target audience in both selected stores.

### P4.4 — Post-launch Monetization
- Review consented aggregate funnel metrics plus store-console subscriber/renewal/cancellation/refund metrics weekly during initial rollout and monthly after stabilization.
- Track checkout and restore reliability, active subscriber change, annual/Coach mix, churn/refund signals, and purchase-support load.
- Use `scripts/commercial_metrics.py` for a vendor-neutral aggregate KPI snapshot; do not introduce a custom health-linked analytics identity.
- Iterate pricing/offers without changing the four stable product identifiers unless a deliberate store-side migration is planned.

## Phase 5 — Product Value Without Store Dependency

### P5.1A — Offline Commerce Architecture Hardening
- Store-neutral lifecycle model and authoritative-verifier boundary.
- Bounded replay/idempotency ledger; unavailable authority remains retryable.
- Active/grace can resolve verified products; hold/expiry/revoke/refund/unverified fail closed.
- Unit fixtures only; no debug/local premium unlock path.

### P5.2 — Activation & Plan Preview
- Keep Free/Pro/Coach comparison useful while store products are unavailable.
- Never display invented prices or offers.
- End onboarding with a concrete first-value action: add the first supplement and reminder.

### P5.3 — Coach Value Expansion
- Add Coach Overview with local seven-day client adherence summaries.
- Keep Coach access entitlement-gated and use neutral check-in language.
- Avoid diagnosis/treatment claims and avoid exposing supplement details in the overview.

### P5.4 — Retention
- Surface overdue recovery actions without auto-resolving or shaming missed routine items.
- Preserve existing streak/progress behavior as optional feedback, not a medical recommendation.

### P5.5 — Reliability & Scale
- Surface the last successful reminder-schedule rebuild alongside existing notification diagnostics.
- Continue migration/offline/background-sync/device-matrix hardening independently of store distribution.

## Phase 6 — Product Polish

- Keep new UI copy aligned across EN/VI.
- Use wellness/non-medical positioning consistently.
- Maintain responsive and accessibility-aware Coach/recovery/preview states.
- Provide a debug-only, read-only synthetic demo preview for screenshots and UX review without contaminating user data.

## Phase 7 — Reliability, Reporting & Portability

### P7.1 — Reminder Reliability 2.0
- Reconcile reminder registry state with platform scheduling state where platform APIs allow it.
- Separate valid zero-future schedules from confirmed stale/missing registrations.
- Offer schedule rebuild without mutating intake history.

### P7.2 — Coach Reports 2.0
- Support local 7/30/90-day aggregates, trend buckets, search and sort.
- Cache/reuse report source data where practical and keep check-in language non-medical.

### P7.3 — Sync Recovery & Conflict UX
- Classify unlinked, healthy, pending, missing-key, retryable and action-required states.
- Keep recovery local-first: retry/import-key/check-link actions do not delete local data.

### P7.4 — Backup & Data Portability
- Add an optional versioned SHA-256 semantic integrity manifest to new backups.
- Keep legacy backups readable and retain deterministic cross-profile collision handling.
- Require Android/iOS to match the same fixed canonical digest fixture.

### P7.5 — Performance, Accessibility & Large Text
- Avoid needless Coach refetch/recompute during report-window changes.
- Keep new summary/demo/report layouts readable with large text and EN/VI content.

## Phase 8 — Product Maturity

- Maintain a synthetic-only QA scenario catalog covering reminders, Coach, sync, backup, accessibility, demo and migration behavior.
- Run `scripts/product_readiness.py` and its tests in Quality Gates.
- Treat accepted integrity corruption, reminder-repair data mutation, local-data loss, identifier/health telemetry leaks, or migration regressions as STOP conditions.
- Store execution remains deferred until developer accounts are intentionally resumed.

## Monetization safety rules

1. Purchases and entitlements are store-authoritative; local storage is cache only.
2. No ads targeted from health/supplement data.
3. No health payload is sent to analytics or purchase telemetry.
4. A failed entitlement refresh must never silently grant paid access.
5. Subscription loss never deletes user data; paid features become read-only or unavailable until access returns.
6. Pricing and offer text displayed to users come from the store APIs once P3.2/P3.3 are connected.
