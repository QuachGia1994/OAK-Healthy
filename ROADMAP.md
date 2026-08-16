# Roadmap

OAK Healthy is developed in public. Priorities are ordered by user safety and
cross-platform reliability rather than by a fixed delivery date.

## Current

- Continue product development without requiring paid Apple/Google developer accounts; store execution is deferred, not removed.
- Harden commerce authority boundaries and replay/idempotency behavior without adding a local premium unlock.
- Expand Coach value with local, entitlement-gated adherence summaries.
- Improve recovery, reminder reliability visibility, accessibility, EN/VI consistency, and debug-only synthetic preview tooling.
- Keep Android/iOS sync, persistence migrations, encrypted fixtures, and fail-closed entitlement behavior regression-safe.

## Next

- Validate notification and background-sync behavior on a wider device/OS matrix.
- Expand Coach reports with privacy-safe aggregate views and export-ready presentation.
- Continue responsive/accessibility review across compact and large-screen layouts.
- Add backend commerce verification only when distribution/account work is intentionally resumed or paid acquisition requires it.

## Later

- Resume P4.1 store execution when developer accounts are desired, using the existing store evidence gates rather than redesigning entitlements.
- Evaluate end-to-end encrypted multi-device recovery without placing keys on the application server.
- Evaluate import/export formats that preserve user ownership and portability.

Please use a feature request to propose a user problem. Roadmap items are not a
promise of delivery and may change after security, platform, or user feedback.
