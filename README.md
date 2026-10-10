# OAK Healthy

Offline-first, end-to-end encrypted supplement & routine tracker on the web.
Installable as a PWA, synced across devices with a zero-knowledge vault.

**Live:** https://oak-healthy.kim-phong619.workers.dev

> v2.0 replaces the native Android/iOS apps with a single cloud web app.
> The mobile code stays in git history (tag/commit before `v2.0.0`) but is no longer maintained.

## Features

- **Today**: bento dashboard with adherence ring, next-dose countdown with one-tap *Take now*, 7-day sparkline, streak and a Morning / Midday / Evening / Bedtime timeline. Every action has an **Undo** toast.
- **Stack**: links-table style manager. Press `/` to search, `C` to create, filter by category and status (Active / Off-cycle / Paused), edit in a side drawer (bottom sheet on mobile).
- **Insights**: 12-month heatmap, when-you-actually-take-it histogram, per-routine 30-day adherence, history log.
- **Smart stack engine**: clash detection (iron × calcium, zinc × copper, caffeine × iron…), synergy hints (D3 + K2, iron + vitamin C), chrono-timing warnings (stimulants late, calming supplements in the morning). Dictionary autofill for time, dose and cycle.
- **Inventory**: pills-left auto-decrements on *Taken*, restores on Undo, low-stock warning at ≤ 7 days.
- **Cycles**: N days on / M days off, fixed duration, pause.
- **Profiles**: track family members from one device.
- **Encrypted sync**: AES-256-GCM on device, server stores ciphertext only (see [SECURITY.md](SECURITY.md)).
- **Shareable stacks**: `/s/<id>#<key>` links; the key lives in the URL fragment and never reaches the server. Recipients can copy the stack into their own.
- **Command menu**: `⌘K` / `Ctrl+K` to log doses, navigate, edit routines, switch profile, toggle dark mode.
- **PWA**: install, works fully offline (IndexedDB + service worker), JSON export / import.

## Stack

Next.js 15 (static export) · React 19 · Tailwind CSS v4 · Radix · cmdk · vaul · sonner · Motion · Geist ·
Dexie (IndexedDB) · Web Crypto · Cloudflare Workers (static assets + API) · D1.

## Develop

```bash
npm install
npm run dev        # http://localhost:3000 (UI only, no API)
npm test           # domain unit tests (Node ≥ 22.6)
npm run preview    # build + wrangler dev with local D1 (UI + API)
```

First-time local D1: `npx wrangler d1 execute oak-healthy --local --file worker/schema.sql`.

## Deploy

```bash
npx wrangler d1 create oak-healthy        # once; put the id in wrangler.jsonc
npm run db:init                           # apply worker/schema.sql remotely
npm run deploy                            # next build && wrangler deploy
```

## Layout

```
app/            routes: / (Today), /stack, /insights, /settings, /s (share viewer)
components/     UI (shell, command menu, drawer, dashboard, tables, charts)
lib/domain/     pure domain logic: day codec, cycles, dose timing policy, schedule, knowledge engine
lib/            IndexedDB store, crypto, sync
worker/         Cloudflare Worker API (vault + share) and D1 schema
tests/          node:test suites for lib/domain
```

See [docs/arch/system-boundaries.md](docs/arch/system-boundaries.md) and [docs/arch/HEALTH_DATA_FLOW.md](docs/arch/HEALTH_DATA_FLOW.md).

OAK Healthy is not a medical device and does not give medical advice.