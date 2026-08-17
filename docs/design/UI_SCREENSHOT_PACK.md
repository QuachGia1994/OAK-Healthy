# OAK Healthy — Screenshot & Demo Presentation Pack

This pack is repository-only and does not require App Store Connect, TestFlight, Google Play Console, signing credentials, or live subscriptions.

## Source data
Use only the built-in synthetic Demo Preview and deterministic test fixtures. Do not use a real client name, supplement history, health note, cloud link code, encryption key, notification identifier, purchase token, or analytics payload.

Demo Preview must remain read-only:
- no database writes;
- no intake Taken/Skipped mutation;
- no reminder scheduling;
- no cloud sync request;
- no entitlement override;
- no personal-health-data read.

## Required capture set
Capture these six product stories on Android and iOS:
1. Home — daily completion/progress is the primary signal.
2. Stack — routine overview plus continuous supplement rows.
3. History — completion/trend hero plus continuous date-grouped activity.
4. Coach — aggregate workspace insight and same-client detail.
5. Sync health — readable health/recovery state with technical details collapsed.
6. Settings — client/plan/reminder/theme/data hierarchy.

Optional supporting captures:
- Plan Access current-plan/comparison/store hierarchy;
- Notification reliability health summary;
- Safe Mode recovery surface;
- synthetic Demo Preview itself.

## Variants
Minimum smoke set:
- light theme: all six required screens;
- dark theme: Home, History, Sync, Settings;
- English: all six required screens;
- Vietnamese: Home, Stack, Settings;
- Android compact 360dp and standard 412dp;
- iOS compact/standard iPhone width;
- one accessibility-large-text capture per platform.

## Visual acceptance
- no clipped primary CTA or trailing value;
- no card-per-row fragmentation in Stack/History/Demo routine lists;
- no blur/glass material in core product surfaces;
- no raw IDs, encryption keys or operation logs visible by default;
- no decorative infinite animation required for a capture;
- one dominant insight per screen;
- charts/progress remain more prominent than explanatory copy where applicable;
- EN/VI strings may wrap, but controls and critical values remain usable.

## File naming
Use a deterministic pattern when captures are produced externally:
`<platform>-<screen>-<theme>-<locale>-<size>.png`

Examples:
- `android-home-light-en-412dp.png`
- `ios-history-dark-vi-standard.png`

Screenshot files are optional repository artifacts until a dedicated capture runner is added. The Stage B gate validates the presentation contract and synthetic data source, not pixel snapshots.
