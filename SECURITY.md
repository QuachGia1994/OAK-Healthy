# Security Policy

## Supported versions

Security fixes are applied to the latest commit on `main`. Until the first
stable release, older commits and locally modified builds are not supported.

## Reporting a vulnerability

Please use [GitHub private vulnerability reporting](https://github.com/QuachGia1994/OAK-Healthy/security/advisories/new).
Do not open a public issue for suspected vulnerabilities and do not include
Link Codes, Sync Keys, Firebase credentials, health data, or screenshots that
contain private information.

The maintainer will acknowledge a complete report within seven days, assess
severity, and coordinate a fix before public disclosure. Include affected
platforms, reproduction steps, impact, and a minimal proof of concept when it
is safe to do so.

## Security model

- Health records are local by default and are uploaded only after the user
  creates or joins a sync link.
- Optional cloud encryption uses AES-256-GCM. The Sync Key must be transferred
  separately from the Link Code.
- Android wraps the Sync Key with Android Keystore. iOS stores it in Keychain
  with `WhenUnlockedThisDeviceOnly` accessibility.
- Firebase writes use transactions with monotonic revisions. Database rules
  validate the payload shape, size, and revision increase.
- Firebase Anonymous Authentication limits access to authenticated app clients.
  App Check enforcement must also be enabled in the production Firebase
  console to reject untrusted clients.
- A Link Code is an unguessable capability, not a password. Anyone who obtains
  it may attempt to read, replace, or delete its cloud record; AES-GCM protects
  confidentiality but cannot prevent denial of service.
- Local application databases rely on the operating-system sandbox and device
  file protection. OAK Healthy does not currently claim SQLCipher protection.

## Deployment checklist

1. Enable Firebase App Check enforcement for Realtime Database.
2. Deploy [`firebase/database.rules.json`](firebase/database.rules.json).
3. Keep signing keys, service-account credentials, `Secrets.xcconfig`, and
   `keystore.properties` outside the repository.
4. Review dependency alerts and GitHub Actions results before every release.

Firebase client configuration files contain public client identifiers. They do
not grant server-admin access, but their associated services must still be
restricted in Firebase and Google Cloud consoles.
