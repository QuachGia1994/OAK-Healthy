# Contributing

Thanks for helping improve OAK Healthy.

## Setup

Requires Node.js 22.6+ (tests use `--experimental-strip-types`).

```bash
npm install
npm test
npm run build
```

`npm run preview` runs the static build plus the Worker API locally with a local D1.

## Pull requests

- Keep changes small and focused; domain rules belong in `lib/domain/` with a test in `tests/`.
- Do not change the vault blob format or key derivation without a migration path. Existing users must still decrypt their data.
- Never log or send plaintext health data to the server. The Worker must only see ciphertext.
- Run `npm test` and `npm run build` before opening a PR.