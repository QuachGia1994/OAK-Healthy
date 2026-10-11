# Roadmap

## Shipped in 2.3

- Change password while signed in; add a password to OAuth-only accounts; link/unlink Google and GitHub from Account.
- Reminder snooze (10 min / 1 hour) and per-routine reminder toggles.
- Barcode lookup (on-device decode + Open Food Facts) next to label OCR.

## Shipped in 2.2

- Readable share URLs `/s/<handle>/<slug>` with link previews (public metadata only).
- Background Sync for offline edits.
- Sortable stack table and haptic feedback.
- Inventory reorder link and shelf life after opening.
- Automatic time-slot suggestions (timing rules + clash separation).
- Biomarker ↔ adherence correlation chart.
- Device-to-device QR handoff for the sync key.

## Shipped in 2.1

- Mobile layout pass for iPhone 16 Plus (430×932) and Meizu Lucky 08 (360×800, 412×915): 44 px targets, ≥ 12 px text, single-column cards, safe-area insets.
- Accounts: sign up, sign in, zero-knowledge key wrapping, passkeys. Built, pending owner secrets: reset email (`RESEND_API_KEY`), Google + GitHub OAuth (PKCE, browser-bound state).
- Web Push reminders (VAPID, Durable Object alarm each minute — no cron trigger, payload-free pushes; Free plan cap 45 pushes/min).
- Label scan (on-device OCR with tesseract.js).
- Passkey vault unlock (WebAuthn PRF).
- Biomarkers with typical ranges and trends.
- Family & coach circles (per-invite keys, revocable).
- Expanded knowledge base: 37 entries, aliases, food timing, sources, more clashes.
- Vietnamese UI (auto-detected, toggle in Settings and on auth pages).

## Shipped in 2.0 (web)

- Cloud web app / PWA replacing native Android & iOS; zero-knowledge sync; clash / synergy / timing engine; inventory; share links.

## Next

1. Circle members with their own accounts (per-member public keys) instead of bearer links.
2. “Mark taken” action on the reminder notification.
3. Live camera barcode scanning (continuous) in addition to photos.
