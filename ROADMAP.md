# Roadmap

OAK Healthy is developed in public. Priorities are ordered by user safety and
cross-platform reliability rather than by a fixed delivery date.

## Current

- Continue product development without requiring paid Apple/Google developer accounts; store execution remains deferred.
- P9.1 Data Recovery 2.0 and P9.2 Reminder Reliability 3.0 are implemented and carried by the pushed P9.3 baseline.
- P9.3 Sync Engine 2.0 is pushed on `f14d4b4`: durable dirty-part queue, sanitized operation journal, deterministic conflict preview, local-first stale-delete protection and observable retry/backoff.
- P9.4 Coach Workspace 3.0 and P9.5 Activation & Retention 2.0 are pushed on `db30562` with Android Build, iOS Build and Quality Gates green.
- P10 is closed on green baseline `7f5945c`: Android Build, iOS Build and Quality Gates all pass, including fail-closed verification for the known xcodebuild post-suite exit anomaly.
- P11.1 UX Polish 4.0 plus the Warm Editorial native redesign are closed on green baseline `f96a826` across Android Build, iOS Build and Quality Gates.
- P11.2–P11.10 are implemented as the pre-store completion batch: corrected dark contrast, accessibility/touch targets, reduced-motion and responsive behavior, failure-safe Sync UX, denser History/Coach signals, progressive technical diagnostics, post-redesign render-cost cleanup and explicit synthetic presentation fixtures.
- P11-CLOSE is defined by `scripts/p11_completion_gate.py`, the unified P8–P11 regression matrix and exact-SHA Android/iOS/Quality CI.

## Current store activation

- P12 repository activation is implemented: Android API 36/AGP 8.10.1/Gradle 8.11.1, current-stable Xcode with an enforced Xcode 26+ floor, explicit no-upload readiness mode, fail-closed credential checks, stable subscription catalog validation and P12 CI gates.
- P12-CLOSE requires Android Build, iOS Build, Quality Gates and the Release readiness-only workflow to be green on the same candidate commit.

## Active design checkpoint

- UI-R1 Health & Wellness foundation is closed on green baseline `5b3789c`: shared tokens, flat shell, progress-led Home and continuous tracking form are aligned cross-platform.
- UI-R2 History & Trends Scanability is closed on green baseline `1325897`: completion/trend hierarchy, unified chart styling and continuous History rows are aligned cross-platform.
- Stage A Complete Product UI Redesign is locally complete: Stack, Coach, Settings/Profile, Onboarding, Plan Access, Sync/Recovery, notification diagnostics and Safe Mode now share the same wellness hierarchy without changing health/business behavior.
- Stage A closes only after Android Build, iOS Build and Quality Gates are green on the same pushed candidate SHA.

## Next

- Stage B — Final UI/UX Release Candidate: one final cross-screen consistency, accessibility/device stress, motion, render/performance, dead-style cleanup and screenshot-ready pass. No further small UI stages are planned.
- Store execution remains at the external gate unless developer accounts are intentionally enabled. When enabled, run TestFlight/Sandbox + Play Internal and complete real P4.1 evidence before any production promotion.
- After real store activation, production commerce hardening remains the next commerce stage: Google Play server-side purchase-token verification/acknowledgement plus RTDN lifecycle processing.

## External gate

- Apple Developer Program / App Store Connect credentials are not fabricated by repository work.
- Google Play Console enrollment, app signing/service account and billing credentials are not fabricated by repository work.
- TestFlight/Play purchase evidence, live subscriptions and production rollout remain external execution until those accounts are enabled.

Please use a feature request to propose a user problem. Roadmap items are not a
promise of delivery and may change after security, platform, or user feedback.
