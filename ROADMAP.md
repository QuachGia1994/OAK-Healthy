# Roadmap

OAK Healthy is developed in public. Priorities are ordered by user safety and
cross-platform reliability rather than by a fixed delivery date.

## Current

- Continue product development without requiring paid Apple/Google developer accounts; store execution remains deferred.
- P9.1 Data Recovery 2.0 and P9.2 Reminder Reliability 3.0 are implemented and carried by the pushed P9.3 baseline.
- P9.3 Sync Engine 2.0 is pushed on `f14d4b4`: durable dirty-part queue, sanitized operation journal, deterministic conflict preview, local-first stale-delete protection and observable retry/backoff.
- P9.4 Coach Workspace 3.0 and P9.5 Activation & Retention 2.0 are pushed on `db30562` with Android Build, iOS Build and Quality Gates green.
- P10.1 Architecture Decomposition is closed on green baseline `cbdb1ee`.
- P10.2 Performance & Battery and P10.3 Security Hardening are implemented locally with deterministic gates; P10.4 unified regression is ready for pushed-SHA closure.

## Next

- Close P10 on one green Android/iOS/Quality SHA, then execute P11 UX polish, accessibility, failure UX, presentation pack and store-neutral commercial readiness.

## Deferred

- P12 Store Activation remains deferred until developer accounts are intentionally enabled.
- Paid Apple/Google accounts, TestFlight/Play tracks, live subscriptions, P4 evidence and production rollout are outside the active roadmap.

Please use a feature request to propose a user problem. Roadmap items are not a
promise of delivery and may change after security, platform, or user feedback.
