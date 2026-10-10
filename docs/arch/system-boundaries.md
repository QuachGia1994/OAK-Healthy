# System boundaries

```
Browser (PWA)                                         Cloudflare
┌──────────────────────────────────────────┐          ┌───────────────────────────────┐
│ app/ + components/  (React UI)           │          │ Worker  worker/index.ts       │
│        │ hooks (lib/use-oak.ts)          │  HTTPS   │  /api/vault/:id  GET/PUT/DEL  │
│ lib/domain/  pure rules, no I/O          │ ───────▶ │  /api/share      POST         │
│ lib/db.ts    Dexie / IndexedDB           │ cipher-  │  /api/share/:id  GET          │
│ lib/crypto.ts Web Crypto (HKDF, AES-GCM) │ text     │  /s/:id → /s/ (static viewer) │
│ lib/sync.ts  pull → merge → push         │ only     │  everything else → assets     │
│ public/sw.js offline cache               │          │ D1: vaults, shares            │
└──────────────────────────────────────────┘          └───────────────────────────────┘
```

Rules:

1. `lib/domain/*` is pure TypeScript (no React, no IndexedDB, no fetch) and is the only owner of date, cycle, timing and adherence formulas. It is unit-tested in `tests/`.
2. Components read through `useOak()` and write through `lib/db.ts` helpers (`saveRoutine`, `markDose`, `setDeleted`, `mergeSnapshot`). Components never call `fetch` for user data except share and sync helpers.
3. Only `lib/sync.ts` and the share helpers talk to `/api`; they send ciphertext only.
4. The Worker has no knowledge of the data model. It stores opaque blobs with revisions.