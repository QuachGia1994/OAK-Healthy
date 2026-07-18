# Contributing to OAK Healthy

Thank you for helping make private, cross-platform supplement tracking more
reliable. Small, focused changes are preferred over broad rewrites.

## Before you start

1. Search existing issues before opening a new one.
2. Use a bug report for reproducible defects and a feature request for a
   concrete user problem.
3. Discuss changes that alter sync payloads, encryption, migrations, health
   behavior, or platform support before implementation.
4. Never post real health records, Link Codes, Sync Keys, signing material, or
   service-account credentials.

## Development setup

Android requires JDK 17 and the Android SDK:

```powershell
cd Android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

iOS requires macOS, Xcode 16.4 or newer, and XcodeGen:

```bash
cd iOS
xcodegen generate
xcodebuild -project SupplementTracker.xcodeproj \
  -scheme SupplementTracker \
  -destination 'platform=iOS Simulator,name=iPhone 16' test
```

## Pull requests

- Keep one user-visible concern per pull request.
- Explain the root cause and behavior change, not only the files changed.
- Add the smallest test that would fail if non-trivial logic regresses.
- Update localized strings and documentation when behavior changes.
- Confirm Android and iOS interoperability for sync format changes.
- Do not use `try!`, silent failure paths, or unbounded cloud identifiers.
- Ensure all relevant GitHub Actions checks pass.

By submitting a contribution, you agree that it is licensed under the
[Apache License 2.0](LICENSE).
