# System boundaries

```
Browser (PWA)                                          Cloudflare Worker (worker/*.ts) + D1
┌───────────────────────────────────────────┐          ┌─────────────────────────────────────────┐
│ app/ + components/ (React UI, i18n en/vi) │          │ /api/vault/:id      ciphertext + rev    │
│ lib/domain/  pure rules, no I/O           │  HTTPS   │ /api/share[/:id]    ciphertext          │
│ lib/db.ts    Dexie / IndexedDB            │ ───────▶ │ /api/circle[/:id]   ciphertext + token  │
│ lib/crypto.ts HKDF, AES-GCM               │ cipher-  │ /api/auth/*         salt, signup, login,│
│ lib/account.ts PBKDF2 → authKey / encKey  │ text,    │   logout, me, forgot, reset, wrap,      │
│ lib/passkey.ts WebAuthn PRF wrap          │ hashes,  │   oauth/:p/start|callback               │
│ lib/sync.ts  pull → merge → push          │ cookie   │ /api/keys[/:id]     passkey wraps       │
│ lib/circles.ts lib/reminders.ts           │          │ /api/push/*         endpoint + minutes  │
│ public/sw.js offline cache + push         │ ◀─────── │ cron * * * * *  → payload-free pushes   │
└───────────────────────────────────────────┘  push    │ D1: vaults shares circles users         │
                                                       │ identities sessions reset_tokens        │
                                                       │ oauth_states rate_limits wrapped_keys   │
                                                       │ push_subs                               │
                                                       └─────────────────────────────────────────┘
```

Rules:

1. `lib/domain/*` is pure TypeScript (no React, IndexedDB or fetch) and owns date, cycle, timing, adherence, knowledge, label-parsing and biomarker formulas. It is unit-tested in `tests/`.
2. Components read through `useOak()` / `useLiveQuery` and write through `lib/db.ts` helpers.
3. Only `lib/sync.ts`, `lib/account.ts`, `lib/passkey.ts`, `lib/circles.ts`, `lib/reminders.ts` and the share helper talk to `/api`. They send ciphertext, hashes or derived keys, never plaintext health data or passwords.
4. The Worker has no knowledge of the health data model. Accounts only gate access to a wrapped key; the vault stays end-to-end encrypted.
