# P12 — Store Activation

P12 closes the repository-side store activation path without fabricating App Store Connect or Google Play results.

## P12.1 — 2026 store toolchain

- Android app and baseline-profile modules compile/target API 36.
- Android Gradle Plugin is 8.10.1 and Gradle is 8.11.1.
- iOS build, quality and store-release workflows select the current stable Xcode and enforce Xcode 26+ / iOS 26+ SDK tooling at runtime.

## P12.2 — Explicit execution boundary

The Release workflow defaults to readiness-only mode. `execute_store_uploads=false` runs commercial tooling, release preflight and quality gates without uploading to either store.

Setting `execute_store_uploads=true` is an explicit operator action. If a selected store is missing required credentials, the workflow fails closed rather than reporting a successful skipped release.

## P12.3 — Subscription/catalog contract

The stable product IDs remain identical on both platforms:

- `oak_pro_monthly`
- `oak_pro_annual`
- `oak_coach_monthly`
- `oak_coach_annual`

Prices remain store-authoritative. Pending, cancelled, unavailable and unverified states do not grant paid access. Android refresh clears stale purchase offers before rebuilding the current store offer map.

## P12.4 — Beta/internal activation path

When developer accounts are enabled:

1. Configure App Store Connect and Play Console with the four stable product IDs.
2. Configure the required repository secrets documented by the Release workflow.
3. Dispatch Release with `execute_store_uploads=true`, iOS `beta`, Android `internal`, and a monotonic build number.
4. Record real store results in a release-specific copy of `docs/commercial/STORE_VALIDATION_EVIDENCE.template.json`.
5. Run `python3 scripts/commercial_gate.py --evidence <file> --require beta`.

Do not mark evidence as passed without a real sandbox/TestFlight/Play result.

## P12.5 — Production promotion boundary

Production remains a promotion of the already-tested candidate. It requires explicit production confirmation, exact candidate commit/version/build and production-level commercial evidence. iOS submits the tested TestFlight build; Android promotes the tested Play version code.

## P12.6 — External gates

Repository P12 can be closed before paid accounts are purchased. The following are external execution gates and must remain reported as `NOT EXECUTED` until the owner enables them:

- Apple Developer Program enrollment and App Store Connect credentials.
- Google Play Console enrollment, app signing/service account and billing key.
- TestFlight/Sandbox and Play Internal purchase/restore matrix.
- Store privacy/data-safety/health declarations and public support/privacy URLs.
- Real screenshots/evidence tied to the tested store build.
- Production review/promotion and staged rollout.

## Post-activation hardening

Before broad paid scale, move Google Play purchase-token verification and acknowledgement to a secure backend using the Google Play Developer API and add Real-time Developer Notifications. This is a security/operations hardening stage; the current client verifier remains fail-closed for activation testing.

## P12-CLOSE

Repository P12 is closed when:

- `scripts/store_activation_gate.py` passes.
- `scripts/release_preflight.py` passes.
- Unified repository regression passes.
- Android Build, iOS Build and Quality Gates are green on the same pushed commit.
- Release workflow readiness-only dispatch succeeds with no store upload.

A repository P12-CLOSE does not claim that an external store upload occurred.
