# Governance

## Maintainer

[QuachGia1994](https://github.com/QuachGia1994) is the founder, primary
maintainer, release manager, and security contact for OAK Healthy.

The maintainer is responsible for issue triage, pull-request review, release
decisions, dependency and security updates, Firebase schema compatibility, and
Android/iOS interoperability.

## Decision process

Routine fixes use review discussion and passing CI as the decision record.
Changes to encrypted payloads, sync schemas, migrations, privacy behavior,
supported platforms, or licensing require a public design issue before code is
merged. When consensus is not available, the maintainer makes the final call
and records the tradeoff in the issue or pull request.

## Releases

Releases follow semantic versioning where practical. A release requires:

1. Passing Android and iOS build workflows.
2. Passing sync interoperability fixtures on both platforms.
3. Updated user guidance and `CHANGELOG.md` for behavior changes.
4. Review of open security and dependency alerts.

Additional maintainers may be invited after sustained, constructive
contributions and successful reviews across at least one platform area.
