# Roadmap

## Shipped in 2.1

- Mobile layout pass for iPhone 16 Plus (430×932) and Meizu Lucky 08 (360×800, 412×915): 44 px targets, ≥ 12 px text, single-column cards, safe-area insets.
- Accounts: sign up, sign in, forgot / reset password, Google + GitHub OAuth (PKCE), zero-knowledge key wrapping.
- Web Push reminders (VAPID, Worker cron, payload-free pushes).
- Label scan (on-device OCR with tesseract.js).
- Passkey vault unlock (WebAuthn PRF).
- Biomarkers with typical ranges and trends.
- Family & coach circles (per-invite keys, revocable).
- Expanded knowledge base: 37 entries, aliases, food timing, sources, more clashes.
- Vietnamese UI (auto-detected, toggle in Settings and on auth pages).

## Shipped in 2.0 (web)

- Cloud web app / PWA replacing native Android & iOS; zero-knowledge sync; clash / synergy / timing engine; inventory; share links.

## Next

1. Link a second sign-in method from Account (password ↔ Google/GitHub) and change password while signed in.
2. Circle members with their own accounts (per-member public keys) instead of bearer links.
3. Reminder snooze and per-routine reminder toggles.
4. Biomarker ↔ supplement correlation charts.
5. Barcode lookup to complement OCR.
