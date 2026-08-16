# OAK Healthy Commercial Operations

Last reviewed: 2026-08-16

This runbook continues the monetization roadmap after repository launch-readiness. It deliberately separates source/CI readiness from real App Store and Google Play evidence.

## P3.6 — Beta & Store Launch

Repository-side P3.6 is closed when Android Build, iOS Build, and Release quality gates are green for the candidate commit and the release workflow defaults to TestFlight beta / Play internal rather than production.

A green repository does not prove real subscription products, sandbox purchases, store declarations, screenshots, public URLs, or production availability. Those are P4 gates.

## P4.1 — Store & Beta Validation

1. Copy `docs/commercial/STORE_VALIDATION_EVIDENCE.template.json` to a release-specific evidence file. Keep it non-secret and suitable for the repository: references should point to safe audit material, never receipts, transaction IDs, credentials, health data, or personal tester data. Do not mark a case `pass` without a real store/test result and an evidence reference.
2. Record the exact candidate version, build number, and commit SHA used by testers.
3. Verify all four stable product IDs are active on both stores.
4. Execute the platform-specific matrix on iOS TestFlight/Sandbox and Google Play test tracks/license testers.
5. Keep health data out of screenshots, evidence references, support tickets, and test notes.
6. Validate the completed file:

```bash
python3 scripts/commercial_gate.py --evidence docs/releases/<release>.json --require beta
```

The matrix follows each store's actual subscription states. iOS includes sandbox billing-retry behavior. Android auto-renewing plans include grace period, account hold, and recovery; OAK does not require an Android prepaid-pending case because its monthly/annual products are auto-renewing. Cancellation, restore, revoked/expired/refunded, offline, and invalid/unverified cases remain mandatory. Paid access must never be fabricated from an unverified or unavailable store state.

## P4.2 — Offer & Conversion Strategy

Pricing and eligibility remain store-authoritative. Do not hard-code localized prices or promise a free trial in app copy unless the corresponding store offer is configured and active.

The app may emit only the consented commercial funnel events allowlisted by `DiagnosticsPrivacyPolicy`. Product IDs, commercial plan, billing period, store, and normalized result are permitted. Profile IDs/names, supplement data, dose/intake data, sync identifiers, receipts, transaction IDs, raw errors, and free-form text are forbidden.

For each beta cohort:

1. Export only aggregate counts for Plan & Access views, purchase starts/results, and restore starts/results.
2. Add store-console aggregate subscription counts to a copy of `docs/commercial/METRICS_SNAPSHOT.template.json`.
3. Generate a KPI report:

```bash
python3 scripts/commercial_metrics.py metrics.json --output commercial-kpis.md
```

4. Record the offer decision in the release evidence for each platform: `none`, `introductory`, `trial`, or `discount`, plus a reference to the aggregate beta analysis. The script does not recommend a price or trial automatically; product/offer decisions remain an operator decision based on real conversion and retention data.

## P4.3 — Production Launch

Production release is fail-closed. The release workflow requires all existing production confirmation plus a release-specific commercial evidence file present in the workflow checkout and the exact `candidate_commit` that produced the tested binary. The candidate may be an ancestor of the promotion-workflow commit, allowing non-binary evidence/runbook updates after beta validation without pretending they produced the store build. The production gate verifies:

- the evidence version/build/commit matches the tested candidate commit;
- all four products and P4.1 cases passed on both stores;
- an explicit offer decision exists for each store;
- public privacy and support URLs are HTTPS and not placeholders;
- App Store privacy, Play Data Safety, and Play Health Apps declarations have evidence;
- all six release-candidate screenshot states have evidence separately for iOS and Android;
- rollback, staged-rollout, and purchase-support plans have evidence.

Use the same tested store version/build for production. The iOS production lane selects the already-tested TestFlight app version and build number and submits it for review without uploading a new binary. Android production promotes the tested versionCode from the selected source track to production instead of rebuilding/re-uploading the AAB. If a different binary/build is required, repeat the affected validation rather than carrying forward stale evidence.

Set any non-target platform to `skip` in the production dispatch so promoting one store cannot accidentally create a beta/release on the other. For iOS, production submission enables phased release and keeps automatic release disabled so availability still requires an explicit store-side release decision after review. For Android, choose `android_source_track` and `android_rollout_fraction`; fractions below `1.0` create an in-progress staged rollout, while `1.0` promotes the tested release as completed. The repository does not claim a public launch until the stores show the intended build available to the target audience.

## P4.4 — Post-launch Monetization

Review weekly during the initial launch window, then at least monthly after the funnel stabilizes.

Use app diagnostics only for consented aggregate funnel counts. Use App Store Connect / Google Play aggregate subscription reporting as the source of truth for active subscribers, renewals, cancellations, refunds, and retention/churn analysis. This avoids adding a custom health-linked or user-level analytics identity.

Track at minimum:

- Plan & Access → checkout-start rate;
- checkout success rate;
- restore success rate;
- active subscriber change;
- cancellation and refund rates;
- renewal rate;
- annual-vs-monthly and Coach-vs-Pro mix;
- purchase/restore support tickets per 1,000 active subscribers.

Every pricing or offer experiment must preserve the four stable product identifiers unless a store-side migration is intentionally planned. Subscription loss must never delete local user data.

## STOP conditions

Stop a rollout or promotion when any of these occur:

- paid access is granted without verified store entitlement;
- subscription loss deletes or corrupts user data;
- purchase/restore regression materially increases failures;
- diagnostics include health payloads, identifiers, receipts, transaction IDs, or raw error/server text;
- privacy/store declarations no longer match the shipping binary;
- migration or sync defects can corrupt the tested release candidate.

Return to beta/internal, create a new candidate, rerun quality gates and the affected P4.1 matrix, then replace the evidence file with references for the new build.
