# Security Policy

## Reporting

Report vulnerabilities privately to the maintainer (@QuachGia1994) via GitHub Security Advisories.
Do not include sync keys, share or circle links, health data or screenshots containing them in public issues.

## Model

- All data lives in the browser's IndexedDB. Nothing leaves the device unless sync, share, circles or reminders are used.
- **Sync key**: 120 random bits, shown as `OAK-XXXX-…`. HKDF-SHA-256 derives `vault-id` (public locator), `vault-auth` (bearer secret) and `vault-enc` (AES-256-GCM key). The Worker stores `{id, rev, blob, sha256(auth)}` only. The sync key is also the **recovery code**.
- **Accounts** keep the vault zero-knowledge. The browser runs PBKDF2-SHA-256 (600 000 iterations, 16-byte random salt) over the password and splits 512 bits into `authKey` (sent, stored as `sha256(salt:authKey)`) and `encKey` (never sent) which AES-GCM-wraps the sync key. The server can authenticate you but cannot decrypt the wrapped key or the vault.
- Unknown emails get a deterministic `HMAC(AUTH_SECRET, email)` fake salt, and login/forgot responses are identical for existing and missing accounts (no enumeration).
- **Sessions**: 32-byte random id in an `HttpOnly; Secure; SameSite=Lax` cookie, stored hashed in D1, 30-day expiry. All `/api/auth/*` and `/api/keys` writes require a matching `Origin` header (CSRF).
- **Password reset**: 32-byte token, stored hashed, single use, 30-minute expiry. A successful reset deletes every session and outstanding token. Without the old sync key (on the device or typed as recovery code) the reset creates a fresh vault from this device's data; the old ciphertext stays unreadable.
- **OAuth (Google, GitHub)**: authorization code + PKCE (S256), single-use `state` with 10-minute expiry, exact `redirect_uri`, provider-verified email required. An email that already belongs to another account is refused (`account_exists`); identities are linked only from a signed-in session. OAuth-only accounts unlock the vault with a passkey or the sync key.
- **Passkeys**: WebAuthn PRF output → HKDF → AES-GCM key that wraps the sync key. The server stores the credential id, PRF salt and ciphertext, only for the signed-in user.
- **Rate limits** (fixed window in D1): salt 30 / 15 min / IP, login 10 / 15 min / IP+email and 50 / IP, sign-up 10 / h / IP, forgot 3 / h / email and 10 / IP, reset 20 / h / IP, OAuth start 30 / 15 min / IP.
- **Share links** and **circles**: fresh random AES-128-GCM key per link in the URL `#fragment`. Circles add a 192-bit write token (stored hashed) so only the owner can update or revoke; revocation deletes the ciphertext.
- **Reminders**: the server stores the push endpoint and the UTC minutes to ping. Pushes have no payload; the service worker builds the notification from on-device data.
- Limits: vault ≤ 1.5 MB, share ≤ 200 KB, circle ≤ 400 KB; ids validated by regex; all D1 queries parameterized; 500 responses carry no error details.

## Known gaps

- The sync key is stored in IndexedDB on linked devices; anyone with the unlocked browser profile can read it.
- Circle and share links are bearer links: anyone holding the full URL can read them until revoked / expired.
