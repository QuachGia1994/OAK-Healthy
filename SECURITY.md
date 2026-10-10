# Security Policy

## Reporting

Report vulnerabilities privately to the maintainer (@QuachGia1994) via GitHub Security Advisories.
Do not include sync keys, share links, health data or screenshots containing them in public issues.

## Model

- All data lives in the browser's IndexedDB. Nothing leaves the device unless sync or share is used.
- **Sync key**: 120 random bits, shown as `OAK-XXXX-…`. HKDF-SHA-256 derives three independent values:
  `vault-id` (128-bit, public locator), `vault-auth` (256-bit bearer secret), `vault-enc` (AES-256-GCM key).
- The Worker stores `{id, rev, blob, sha256(auth)}`. It never sees the key or plaintext. The first write binds the auth hash; later reads and writes must match it.
- Writes use optimistic concurrency (`baseRev` → `rev + 1`); conflicts return 409 and the client pulls, merges (last-writer-wins per record, tombstones for deletes) and retries.
- **Share links**: a fresh random AES-128-GCM key per share, placed in the URL `#fragment` (never sent in HTTP requests). Shares expire after 365 days.
- Losing the sync key means the vault cannot be recovered by anyone. Use JSON export for backups.
- Limits: vault blob ≤ 1.5 MB, share blob ≤ 200 KB, ids validated by regex, all D1 queries parameterized.

## Known gaps

- No per-IP rate limiting yet (add Cloudflare WAF rate-limit rules for `/api/*` in production).
- The sync key is stored in IndexedDB on linked devices; anyone with access to the unlocked browser profile can read it.