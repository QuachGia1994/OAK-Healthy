# OAK Healthy Monetization Roadmap

## Product position

OAK Healthy is a supplement cycle and adherence system for individual power users and coaches. It should not compete as a generic medication reminder.

## Plans

### Free
- Basic supplement tracking
- Reminder notifications
- Recent history
- One client profile

### Pro
- Everything in Free
- Advanced cycle scheduling
- Unlimited history
- Adherence analytics
- Encrypted cloud sync
- Data export
- One client profile

### Coach
- Everything in Pro
- Multi-client management
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
- Gate advanced cycles, unlimited history, analytics, encrypted cloud sync and export behind Pro.
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

## Monetization safety rules

1. Purchases and entitlements are store-authoritative; local storage is cache only.
2. No ads targeted from health/supplement data.
3. No health payload is sent to analytics or purchase telemetry.
4. A failed entitlement refresh must never silently grant paid access.
5. Subscription loss never deletes user data; paid features become read-only or unavailable until access returns.
6. Pricing and offer text displayed to users come from the store APIs once P3.2/P3.3 are connected.
