# Stage B — Final UI/UX Release Candidate Matrix

Stage B is the final presentation pass after Stage A. It does not change dose persistence, cycle calculation, reminder semantics, backup/import, sync conflict resolution, billing verification, entitlements, analytics consent, or store execution.

## Cross-screen consistency
- Android and iOS use the same wellness hierarchy: background → one primary insight/surface → continuous rows → progressive technical details.
- Shared card APIs expose paper/surface semantics only; legacy glass variants and unused hero/elevation tokens are removed.
- Splash/loading surfaces use the same neutral wellness palette as the product instead of a separate decorative gradient language.
- Stack, Coach, Settings, Plan Access, Sync, Notifications, Demo Preview and Safe Mode stay aligned with the Stage A presentation contract.

## Accessibility and device stress
Android review matrix:
- widths: 320dp, 360dp, 412dp, 600dp;
- fontScale: 1.0, 1.3, 2.0;
- Stack overview/actions, Coach chip groups, Settings theme selector and purchase rows must stack at compact/large-text breakpoints;
- touch targets remain at least 48dp and critical status text may wrap rather than compress into narrow trailing columns.

iOS review matrix:
- compact iPhone, standard iPhone, Max-width iPhone and wide/iPad-class width;
- Dynamic Type: default, accessibility1 and accessibility3;
- Stack metrics/actions, Settings theme picker, Plan Access headers/purchase rows and Demo Preview routine rows must fall back to vertical layouts;
- shared interactive controls retain at least 44pt targets.

## Motion / Reduce Motion
- Android Home keeps short status feedback but respects system animator disablement.
- Android splash uses one finite progress transition and becomes static when animator duration is disabled; no infinite transition remains.
- Android expandable Settings content is state-driven without decorative reveal animation.
- iOS Home respects Reduce Motion and launch branding uses finite reveal/ring animation only; no `repeatForever` launch animation remains.
- No decorative infinite animation is permitted in the final UI release candidate.

## Render/performance and dead presentation cleanup
- No per-row blur/glass material is used in core product screens.
- Synthetic demo routine lists use one continuous surface instead of card-per-row rendering.
- Legacy Android `Glass`, `OakElevation`, unused chart/badge/insight tokens and iOS `.glass`/hero gradient tokens are removed.
- Existing P10 performance/battery budgets and bounded History/Coach/Sync work remain part of the unified regression matrix.

## Screenshot/demo readiness
- Demo Preview is synthetic/read-only and contains fixed fixture values only.
- Screenshot-ready surfaces mirror the real Stage A hierarchy rather than a separate demo visual language.
- Capture set and privacy constraints are defined in `docs/design/UI_SCREENSHOT_PACK.md`.
- No screenshot preparation may write personal health data, change entitlements, trigger sync, schedule reminders, or create intake history.

## Final gate
Stage B is closed only when all of the following are green on the same pushed SHA:
- `scripts/stage_b_ui_rc_gate.py`;
- `scripts/oak_regression.py`;
- Android Build;
- iOS Build/unsigned IPA;
- Quality Gates including Android/iOS coverage thresholds.

Store/TestFlight/Play Console execution remains deferred unless developer accounts are intentionally enabled.
