# OAK Healthy Beta & Store Launch Checklist

Last reviewed: 2026-08-16

## Release principle

Beta is the default release mode. A tag must never silently upload to production. Production upload requires an explicit `workflow_dispatch` choice and `confirm_production=true`.

## Repository preflight

Run before every store build:

```bash
python3 scripts/release_preflight.py
```

The preflight verifies cross-platform app/version identity, four stable subscription IDs, diagnostics default-off flags, privacy documents, release billing-key wiring, and safe beta/internal workflow defaults.

## GitHub Actions secrets

### iOS / TestFlight

Required:

- `MATCH_GIT_URL`
- `MATCH_PASSWORD`
- `APP_STORE_CONNECT_KEY_ID`
- `APP_STORE_CONNECT_ISSUER_ID`
- `APP_STORE_CONNECT_KEY_CONTENT` (base64 encoded `.p8` content)

Optional when needed:

- `MATCH_SSH_PRIVATE_KEY`
- `IOS_APP_IDENTIFIER` (defaults to `com.phongqk.oakhealthy`)

### Android / Play testing

Required:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
- `PLAY_BILLING_PUBLIC_KEY`

Never commit signing files, API private keys, service-account JSON, or the Play billing public-key property file.

## Store account setup

Before purchase testing:

- Create the app records for `com.phongqk.oakhealthy`.
- Create and activate `oak_pro_monthly`, `oak_pro_annual`, `oak_coach_monthly`, `oak_coach_annual`.
- Configure durations, prices, localizations, availability, and subscription hierarchy/base plans.
- Complete developer agreements, tax/banking/merchant requirements that block test or sale flows.
- Publish `docs/PRIVACY_POLICY.md` at a stable public HTTPS URL.
- Complete App Store privacy declarations, Google Play Data safety, and the Google Play Health apps declaration against the shipping binary.

## Beta purchase matrix

Run on fresh installs and after app relaunch:

| Case | Expected access |
| --- | --- |
| No purchase | Free |
| Pro monthly | Pro |
| Pro annual | Pro |
| Coach monthly | Coach |
| Coach annual | Coach |
| Pro + Coach both active | Coach |
| User cancels checkout | No new entitlement |
| Play purchase pending | No new entitlement until purchased |
| Unverified/invalid purchase | No paid entitlement |
| Restore active purchase | Original active plan restored |
| Expired/refunded/revoked entitlement | Downgrade after store refresh; user data retained |
| Offline app launch | Never fabricate a paid entitlement |

Then verify feature boundaries:

- Free: one client, 7-day history, basic tracking/reminders only.
- Pro: one client, advanced cycles, 90-day history, adherence analytics, encrypted sync, export.
- Coach: multiple clients, 365-day history, all Pro capabilities and coach-oriented access.

## Diagnostics matrix

- Fresh install: Analytics/Crashlytics collection remains OFF.
- Enable **Share anonymous diagnostics**: allowlisted commercial events can be emitted.
- Disable it again: collection is disabled and unsent Crashlytics reports are deleted where supported.
- Verify no custom diagnostics contain client/profile IDs or names, supplement names, doses, intake history/times, sync IDs/codes/keys, file paths, or raw server responses.

## Release workflow

For beta/internal validation, dispatch `.github/workflows/release.yml` with:

- iOS lane: `beta`
- Android track: `internal`
- `confirm_production`: false
- build number: leave blank to use the Release workflow run number, or provide a higher monotonic integer when a store already contains a larger build number

If store secrets are missing, the workflow must still run release preflight and quality gates, then explicitly skip store upload jobs.

For production, use `workflow_dispatch` only, choose the production target(s), and set `confirm_production=true`. Never use a tag as an implicit production approval. The same resolved store build number is injected into Android `versionCode` and the iOS bundle build number for that release run.

## Listing and review material

Use `docs/STORE_LISTING_COPY.md` as the source for initial store copy and screenshot captions. Before submission, replace placeholders with the published privacy/support URLs and capture screenshots from the exact release candidate.

Review notes should state that OAK Healthy is a wellness/routine tracker, not a medical device, explain how reviewers access subscription flows, and provide any sandbox/test account instructions requested by the store.

## Rollback

If a beta build corrupts data, mis-grants entitlement, or exposes sensitive diagnostics:

1. Stop promotion/rollout in the store console.
2. Revoke the affected release from testers where the store supports it.
3. Disable the affected backend/diagnostics feature server-side where possible.
4. Fix on a new branch and rerun Android, iOS, release preflight, and quality gates.
5. Never delete user data merely because a subscription is lost or a release is rolled back.

## Support triage

For purchase issues collect only non-health metadata: platform, app version/build, storefront country if volunteered, product ID, store transaction state/error category, and approximate time. Do not request supplement lists, doses, intake logs, cloud encryption keys, or full raw purchase receipts in ordinary support channels.

## Definition of launch-ready

Repository launch readiness is reached when preflight and all CI/release quality gates pass. Actual TestFlight/Play distribution additionally requires the account-side credentials/configuration above. Public launch is not complete until the stores report the intended build available to its target audience.
