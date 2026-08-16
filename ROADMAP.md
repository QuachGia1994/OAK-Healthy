# Roadmap

OAK Healthy is developed in public. Priorities are ordered by user safety and
cross-platform reliability rather than by a fixed delivery date.

## Current

- Continue product development without requiring paid Apple/Google developer accounts; store execution is deferred, not removed.
- P7.1: reconcile reminder registrations and expose repair-safe notification health.
- P7.2: provide local 7/30/90-day Coach reports, trend/search/sort and neutral check-in workflow signals.
- P7.3: expose actionable sync health while preserving local-first recovery semantics.
- P7.4: protect new backups with a cross-platform semantic integrity manifest while preserving legacy import compatibility.
- P7.5/P8: keep report rendering efficient, large-text friendly, synthetic-QA driven and covered by repository readiness gates.

## Next

- Execute the P8 synthetic scenario catalog on a wider physical-device/OS matrix.
- Extend report/export presentation only after the current integrity and recovery gates stay green.
- Add backend commerce verification only when distribution/account work is intentionally resumed or paid acquisition requires it.

## Later

- Resume P4.1 store execution when developer accounts are desired, using the existing store evidence gates rather than redesigning entitlements.
- Evaluate end-to-end encrypted multi-device recovery without placing keys on the application server.
- Evaluate import/export formats that preserve user ownership and portability.

Please use a feature request to propose a user problem. Roadmap items are not a
promise of delivery and may change after security, platform, or user feedback.
