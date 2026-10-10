# Roadmap

## Shipped in 2.0 (web)

- Cloud web app / PWA replacing native Android & iOS.
- Dashboard, Stack manager, Insights, Settings, share viewer.
- Zero-knowledge encrypted sync (Cloudflare Workers + D1).
- Clash / synergy / chrono-timing engine, inventory tracking, shareable stacks.

## Next

1. **Web Push reminders**: VAPID push from a Worker cron so reminders fire with the tab closed.
2. **Label scan (OCR)**: photograph a bottle, extract name and dose on device (Tesseract.js / Shape Detection API).
3. **Passkey vault unlock**: WebAuthn PRF extension to derive the sync key, so no key to copy.
4. **Biomarkers**: log lab values (vitamin D, ferritin, B12…) and chart them next to adherence.
5. **Family & coach circles**: invite-only shared views of a profile with per-member encryption keys.
6. **Expanded knowledge base**: more interactions, food timing (with fat, empty stomach), sources per rule.
7. **i18n**: Vietnamese UI.