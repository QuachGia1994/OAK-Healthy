# P10 Unified Regression Matrix

Updated 2026-08-17 · v1.0.1

Run from repository root:

```text
python scripts/oak_regression.py
```

The command fails on the first broken invariant and covers:

- P7/P8 product maturity, localization/accessibility fixtures and static readiness.
- P9.1 migration/data-recovery fixtures, preview/integrity/rollback contracts.
- P9.2 reminder reconciliation and no-intake-mutation invariant.
- P9.3 durable sync queue, conflict/local-first and retry/backoff contracts.
- P9.4 Coach same-client report/check-in boundaries and non-medical positioning.
- P9.5 activation/retention privacy and pressure-free recovery contracts.
- P10.1 architecture ownership boundaries.
- P10.2 bounded query/background-work budgets.
- P10.3 encryption/tamper/Firebase/privacy/secret-scan contracts.
- Release preflight and all `scripts/tests` unit tests.

Platform executable validation remains separate because Android and iOS compilers are authoritative for Kotlin/Room and Swift/SwiftData syntax. P10 closes only when Android Build, iOS Build and Quality Gates all pass on the same pushed SHA containing this matrix.
