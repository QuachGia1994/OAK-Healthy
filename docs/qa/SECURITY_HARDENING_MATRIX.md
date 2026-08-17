# P10.3 Security Hardening Matrix

Updated 2026-08-17 · v1.0.1

## Cloud sync encryption

- Android cloud key material is wrapped by an AndroidKeyStore AES-GCM master key before persistence.
- iOS cloud key material is stored in Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.
- Both platforms accept only bounded key identifiers and 256-bit imported keys.
- Encrypted sync uses AES-256-GCM and rejects invalid envelope headers, nonce sizes, missing tags, wrong/missing keys and plaintext downgrade when local encryption is enabled.
- Cross-platform tests include ciphertext-tamper rejection.

## Firebase transport data

Realtime Database rules must keep all of the following true:
- reads require authenticated Firebase sessions;
- writes require authenticated Firebase sessions;
- revision numbers are strictly increasing;
- payload is a non-empty string capped at 1 MiB.

App Check remains part of application bootstrap/transport hardening; database rules are still fail-closed on authentication if App Check behavior changes.

## Local backup / device transfer

Android OS cloud backup and device transfer must exclude:
- `supplement_db`;
- `oak_settings`;
- `oak_settings_recovery`;
- `oak_db_encryption`.

User-controlled OAK export/import remains the supported portable-data path and continues to use the existing integrity/preview/rollback checks.

## Diagnostics and analytics

Collection remains opt-in and disabled by default. Custom analytics fields are event allowlisted. The allowlists must never include client identity, supplement, dose, notes, health payloads or sync keys.

Cloud-sync operational diagnostics remain coarse metadata only (error type/status code and bounded operational counters), never raw server bodies, encryption keys or health content.

## Repository secret scan

The security gate scans Git-tracked source/config/documentation for strong private-secret signatures such as private-key blocks and common long-lived access-token prefixes. Firebase client configuration files are deliberately excluded because those identifiers are client configuration, not server credentials; access is protected by Firebase authentication/rules/App Check rather than secrecy of those files.

## Exit criteria

- Security hardening gate passes.
- AES-GCM tamper tests exist on Android and iOS.
- Existing data-recovery, sync local-first and privacy gates remain green.
- No Store credential or developer-account secret is introduced into the repository.
