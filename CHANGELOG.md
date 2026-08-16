# Changelog

All notable changes to OAK Healthy are documented here. The project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses semantic
versioning where practical.

## [Unreleased]

### Added

- Cross-platform AES-GCM interoperability and revision validation tests.
- In-app sync security guidance, unlink controls, and protected key display.
- Durable iOS notification actions for Taken and Skipped dose events.
- Public contribution, governance, security, and roadmap documentation.

### Changed

- Firebase payload and revision reads are now atomic snapshots.
- Firebase writes now use compare-and-set transactions with monotonic revisions.
- Android and iOS sync settings now separate pending Link Code input from the
  active link.
- Three-tab navigation, filters, app icon, and loading identity were redesigned
  consistently across Android and iOS.
- Android card rendering and accessibility semantics were streamlined to reduce
  scroll frame spikes on physical devices.
- iOS simulator Debug builds use ad-hoc signing so Keychain-backed encryption
  tests run under GitHub Actions without changing unsigned Release archives.

### Fixed

- Android periodic cycle workers now use a WorkManager-compatible constructor.
- Android backup imports now preserve interval/last-taken data and remap cross-profile ID collisions without breaking history links.
- Android background cloud sync now runs without constructing `HomeViewModel`, shares active-client/log persistence services, and retries engine-reported failures instead of silently treating them as success.
- Android startup now uses a dedicated dependency factory and testable theme/splash/notification policies; `MainActivity.onCreate` no longer owns database or ViewModel construction.
- Factory reset now stops sync, clears pending notifications, removes local profile/history data, wipes app preferences and cloud encryption material on both Android and iOS.
- Android reminders fall back safely when exact-alarm access is unavailable and no longer re-enable an in-app notification opt-out.
- Cross-platform sync now preserves newer deletion tombstones, keeps the revision returned by Android conflict retries, and avoids multi-client history truncation on iOS.
- iOS manual and automatic cloud sync now share one serialized engine, including legacy single-bin fallback and stale-error cleanup.
- iOS cloud sync no longer carries the unused JSONBin HTTP transport or secret injection; orchestration now lives in a dedicated coordinator with regression-tested serialization/fallback/conflict handling.
- Cloud hosting now cleans up partial uploads, and iOS re-hosting keeps the previous host until the replacement is ready.

### Security

- Malformed encryption envelopes now fail closed.
- Firebase identifiers and encryption key IDs are validated before use.
- In-place encryption-key rotation was removed to prevent inaccessible cloud
  data.
- Firebase database rules now validate node shape, payload size, and strictly
  increasing revisions.

[Unreleased]: https://github.com/QuachGia1994/OAK-Healthy/commits/main
