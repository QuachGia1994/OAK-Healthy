# Roadmap

OAK Healthy is developed in public. Priorities are ordered by user safety and
cross-platform reliability rather than by a fixed delivery date.

## Current

- Continue product development without requiring paid Apple/Google developer accounts; store execution remains deferred.
- P9.1 Data Recovery 2.0 is implemented locally with its iOS CI compile fix ready for pushed-SHA verification.
- P9.2 Reminder Reliability 3.0 is implemented; iOS CI is green and the Android resource regression found on `7083a53` is fixed locally.
- P9.3 Sync Engine 2.0 is implemented locally: durable dirty-part queue, sanitized operation journal, deterministic conflict preview, local-first stale-delete protection and observable retry/backoff.

## Next

- P9.4 Coach Workspace 3.0: client drill-down, neutral 7/30/90-day comparisons, search/filter/sort, report-ready layout and wellness notes/check-ins.
- P9.5 Activation & Retention 2.0: first-value onboarding, actionable empty/recovery states, pressure-free progress and privacy-safe aggregate funnels.
- P10: architecture decomposition, performance/battery profiling, security hardening and a single automated regression matrix command.

## Deferred

- P11 Launch Readiness and P12 Store Activation remain deferred until work intentionally resumes beyond P10.
- Paid Apple/Google accounts, TestFlight/Play tracks, live subscriptions, P4 evidence and production rollout are outside the active roadmap.

Please use a feature request to propose a user problem. Roadmap items are not a
promise of delivery and may change after security, platform, or user feedback.
