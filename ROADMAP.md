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

## Next

- Keep P12 Store Activation deferred. Product work after P11 should be driven by device testing and user feedback rather than store enrollment.

## Deferred

- P12 Store Activation remains deferred until developer accounts are intentionally enabled.
- Paid Apple/Google accounts, TestFlight/Play tracks, live subscriptions, P4 evidence and production rollout are outside the active roadmap.

Please use a feature request to propose a user problem. Roadmap items are not a
promise of delivery and may change after security, platform, or user feedback.
