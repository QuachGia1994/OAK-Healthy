# OAK Healthy

Offline-first, end-to-end encrypted supplement & routine tracker on the web.
Installable as a PWA, synced across devices with a zero-knowledge vault. English and Vietnamese UI.

**Live:** https://oak-healthy.kim-phong619.workers.dev

## Features

- **Today**: bento dashboard with adherence ring, next-dose countdown with one-tap *Take now*, 7-day sparkline, streak and a Morning / Midday / Evening / Bedtime timeline. Every action has an **Undo** toast. Laid out for phones from 360 px (Meizu Lucky 08) to 430 px (iPhone 16 Plus) with 44 px touch targets.
- **Stack**: search `/`, create `C`, filter by category and status, edit in a drawer (bottom sheet on mobile), **scan a label** to prefill name, dose and pill count (on-device OCR).
- **Insights**: 12-month heatmap, time-of-day histogram, per-routine adherence, history, **biomarkers** (vitamin D, ferritin, B12, HbA1c, lipids… with typical ranges and trends).
- **Smart stack engine**: 37-entry knowledge base with aliases, food timing and sources (NIH ODS / NCCIH); clash, synergy and chrono-timing checks.
- **Accounts (optional)**: email + password sign-up, sign-in, forgot / reset password, Google and GitHub sign-in. Zero-knowledge: the password only unwraps your sync key on the device.
- **Passkeys**: unlock the vault on a new device with Face ID / Touch ID / security key (WebAuthn PRF).
- **Reminders**: Web Push at each dose time, even when the app is closed.
- **Circles**: live, read-only, encrypted view of one profile for family or a coach; revoke any time.
- **Encrypted sync**, **share links**, **profiles**, **inventory**, **cycles**, `⌘K` command menu, offline PWA, JSON export / import.

## Stack

Next.js 15 (static export) · React 19 · Tailwind CSS v4 · Radix · cmdk · vaul · sonner · Motion · Geist · Dexie ·
Web Crypto · tesseract.js (lazy) · Cloudflare Workers (assets + API + cron) · D1.

## Develop

```bash
npm install
npm run dev        # http://localhost:3000 (UI only, no API)
npm test           # domain unit tests (Node ≥ 22.6)
npm run preview    # build + wrangler dev with local D1 (UI + API)
```

First-time local D1: `npx wrangler d1 execute oak-healthy --local --file worker/schema.sql`.
Local secrets go in `.dev.vars` (git-ignored), e.g. `AUTH_SECRET=…` and `DEV_MAIL=1` to show reset links on screen.

## Deploy

```bash
npm run db:init                           # apply worker/schema.sql remotely (idempotent, expand-only)
npm run deploy                            # next build && wrangler deploy
```

Secrets (`npx wrangler secret put <NAME>`):

| Name | Needed for |
|---|---|
| `AUTH_SECRET` | fake-salt HMAC (account enumeration protection) — required |
| `VAPID_PUBLIC`, `VAPID_PRIVATE_JWK` | Web Push reminders |
| `RESEND_API_KEY`, `MAIL_FROM` | password reset emails |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google sign-in (redirect URI `https://<host>/api/auth/oauth/google/callback`) |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | GitHub sign-in (callback `https://<host>/api/auth/oauth/github/callback`) |

Social buttons stay visible; until a provider's secrets exist they explain that sign-in is not set up yet.

## Layout

```
app/            routes: / /stack /insights /settings /account /login /signup /forgot /reset /s (share) /c (circle)
components/     UI (shell, auth, drawer, dashboard, biomarkers, label scan, circles, charts)
lib/domain/     pure logic: day codec, cycles, timing, schedule, knowledge base, label parser, biomarkers
lib/            IndexedDB store, crypto, sync, account, passkey, reminders, circles, i18n (en/vi)
worker/         Cloudflare Worker API (vault, share, auth, keys, push, circles, cron) and D1 schema
tests/          node:test suites for lib/domain
```

See [docs/arch/system-boundaries.md](docs/arch/system-boundaries.md) and [docs/arch/HEALTH_DATA_FLOW.md](docs/arch/HEALTH_DATA_FLOW.md).

OAK Healthy is not a medical device and does not give medical advice.
