# Data Recovery Matrix

P9.1 keeps restore local-first and fail-closed. A restore is a two-step operation:
preview/dry-run, then explicit confirmation. The dry-run performs no persistence
mutation.

## Supported payloads

| Source schema | Fixture | Expected supplements | Expected history |
| --- | --- | ---: | ---: |
| Legacy array | `legacy_array.json` | 1 | 0 |
| Export v1 | `export_v1.json` | 1 | 0 |
| OAK 1.1 | `oak_v1_1.json` | 1 | 1 |
| OAK 2.0 Android→iOS | `android_to_ios_v2.json` | 2 | 3 |
| OAK 2.0 iOS→Android | `ios_to_android_v2.json` | 2 | 2 |

Unknown/future schemas, duplicate stable IDs, orphan history and partial recurrence
configurations block restore. Foreign-profile supplement IDs are deterministically
remapped while history links remain attached to the target client. Legacy exports
without supplement IDs are upgraded to deterministic IDs from the complete routine
field key; display name alone is never a merge identity.

## Persistence migrations

| Platform | Supported source | Target |
| --- | --- | --- |
| Android Room | 2, 3, 4, 5 | 6 |
| iOS SwiftData | legacy-default, 1.0.0 | 1.0.0 |

The Android matrix test opens every supported source version through the production
migration chain. The iOS migration fixture reopens the legacy default store through
the versioned schema plan.

## Rollback invariant

Before replacement, the app captures the target client's supplements and complete
history. If persistence fails, that snapshot is restored. The restore does not
create Taken or Skipped records; those statuses only come from the imported payload.

Safe Mode recovery on iOS targets the stored client UUID when available. Name-only
resolution exists only for older pending-import state and is accepted only when one
profile matches. A rollback persistence failure is surfaced as a recovery error; it
is not swallowed.

Date-only fields preserve the literal local calendar day. History query windows are
half-open `[startInclusive, endExclusive)`, preventing midnight from belonging to two
days and preserving DST-short/long days.

Run the repository fixture gate with:

```bash
python scripts/data_recovery_gate.py
python -m unittest scripts.tests.test_data_recovery_gate -q
```

The gate rejects count drift, orphan history, incomplete schema coverage and an
incomplete database migration matrix.
