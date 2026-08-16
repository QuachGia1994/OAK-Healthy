# OAK Healthy — Deferred Store Development Mode

Last reviewed: 2026-08-16

OAK Healthy is intentionally continuing product development without requiring an Apple Developer Program or Google Play Console developer-account purchase. This is a product-owner choice, not a launch-readiness failure.

## Deferred until explicitly resumed

The following execution work is deferred:

- TestFlight / App Store Connect distribution and subscription activation.
- Google Play test-track / production distribution and subscription activation.
- Real-store P4.1 purchase-matrix evidence.
- Store-derived P4.2 conversion/offer evidence.
- P4.3 production promotion and P4.4 live subscriber operations.

Repository scaffolding for these stages remains intact and must not be replaced with fake purchases, local premium preferences, fabricated store results, hard-coded prices, or test receipts committed to source control.

## Active development scope

### P5.1A — Offline commerce architecture hardening

- Store-neutral lifecycle model for active, grace, hold, expiry, revoke, refund and unverified states.
- Replay/idempotency ledger with retry-safe handling when the authority is unavailable.
- Verifier protocols/interfaces that can later be backed by App Store / Play server verification.
- Unit fixtures only; no runtime debug unlock path.

### P5.2 — Activation and plan-preview UX

- Plan comparison remains usable when store products are unavailable.
- Paid access remains locked until a verified store authority is connected.
- Onboarding ends with a concrete first-value action: create the first supplement routine and reminder.

### P5.3 — Coach value expansion

- Coach Overview summarizes local 7-day adherence by client.
- Summary includes active clients, completion, last activity and a neutral check-in signal.
- It does not expose diagnosis, treatment advice or supplement details in the overview.
- Access remains protected by the existing Coach entitlement.

### P5.4 — Retention

- Home surfaces a recovery card when routine items are overdue.
- The user chooses whether to record Taken or Skip; the app does not auto-resolve missed items.
- Wording is wellness/routine oriented and avoids medical-pressure language.

### P5.5 — Reliability and scale

- Reminder scheduling records the last successful full rebuild timestamp.
- Settings surfaces this timestamp beside existing permission/exact-alarm/battery/diagnostic controls.
- Existing migration, offline, notification and sync regression coverage remains part of the quality gate.

### P6 — Product polish

- EN/VI copy remains aligned for new flows.
- About copy uses wellness/non-medical positioning.
- Coach and demo cards are responsive/accessibility-aware.
- Debug builds expose a read-only synthetic demo preview for screenshots/UX review; it never writes synthetic records into user data.

## Resume-store gate

When the owner decides to pay for developer accounts, resume at `P4.1-EXEC` using the existing commercial evidence template. Do not redesign the entitlement contract simply because distribution was deferred.
