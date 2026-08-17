# P11 Completion Matrix

This matrix closes the ten post-redesign stages without enabling App Store or Play Store execution.

## P11.2 — Accessibility & Interaction Quality
- Android interactive shared feedback actions use a 48dp minimum target.
- iOS shared feedback actions use a 44pt minimum target.
- Home status controls expose selected/status semantics and localized accessibility labels.
- Android respects system animator disablement; iOS respects Reduce Motion.

## P11.3 — Responsive Layout & Device Matrix
- Android Home switches to stacked status metrics at <=360dp or fontScale >=1.3 and uses wider padding on >=600dp.
- iOS Home uses ViewThatFits for status metrics and Coach uses a responsive metric layout.
- EN/VI long copy may wrap vertically; critical values are not forced into narrow trailing columns.

Device review matrix: Android 320/360/412/600dp widths, font scale 1.0/1.3/2.0; iPhone compact/standard/Max widths and Dynamic Type default/accessibility.

## P11.4 — Offline & Failure UX 2.0
- Sync failures show a user-facing message that local data remains on-device.
- Recovery action remains Sync now; encryption/link failures retain their specific recovery hints.
- Backup/import remains preview-first and rollback-safe from P9.1.
- Reminder recovery remains non-destructive from P9.2.

## P11.5 — Motion & Interaction Polish
- Taken/Skipped status change keeps short native feedback.
- Android animation duration becomes zero when system animator scale is disabled.
- iOS skips pulse/snappy transitions when Reduce Motion is enabled.
- No decorative infinite animation is introduced.

## P11.6 — History & Analytics 4.0
- History keeps bounded 7/30-day analytics and the existing report/detail flow.
- A visible window signal states window length, completion percentage, and late count before the chart.
- Empty, no-match, and load-failure states remain distinct.

## P11.7 — Coach Workspace 4.0
- Workspace surfaces the number of clients that may need routine follow-up.
- Current-vs-previous comparison remains per-client only.
- Check-ins remain local-only and non-medical.
- iOS summary metrics use the shared responsive metric layout; Android keeps large-text stacking.

## P11.8 — Sync, Backup & Diagnostics UX
- Sync health/recovery remains visible first.
- Queue, conflict, journal, transfer timing, manifest IDs and raw error detail are hidden behind technical-details disclosure.
- Link codes and encryption keys remain hidden by default.
- Import preview remains mandatory before destructive restore.

## P11.9 — Performance After Redesign
- Core History queries remain bounded by existing P10 budgets.
- Status/Coach trends remain bounded.
- Sync log rows use paper surfaces instead of per-row blur material on iOS.
- Reduced-motion paths avoid unnecessary animation work.
- Existing P10 performance/battery gate remains part of the unified regression matrix.

## P11.10 — Product Presentation & Demo Pack
- Android and iOS synthetic demo screens use fixed fixture values only.
- Demo surfaces explicitly say SYNTHETIC / LOCAL PREVIEW and state that personal health data is not read or written.
- Presentation data never changes entitlement, database, sync, reminder or history state.
- Screenshot set: Home, Stack, History, Coach, Sync health, Settings; light/dark where relevant, EN/VI smoke coverage.

## P11-CLOSE — Pre-Store Release Candidate
- `scripts/p11_completion_gate.py` must pass.
- Unified repository regression, release preflight, Android Build, iOS Build and Quality Gates must pass on one pushed SHA.
- Store upload, TestFlight, Play Internal, live billing and production promotion remain deferred until P12.
