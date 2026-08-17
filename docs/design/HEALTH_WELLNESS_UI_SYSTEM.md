# OAK Healthy — Health & Wellness UI System

Status: Stage B final UI/UX release-candidate checkpoint

## Direction

Reference weighting for design decisions:

- shadcn/ui — 60%: restrained surfaces, reusable primitives, clear borders, typography-led hierarchy and low visual noise.
- UI/UX Pro Max — 30%: mobile-first hierarchy, accessibility, responsive layouts and complete interaction states.
- Huashu Design — 10%: critique discipline, motion restraint and avoidance of decorative UI that competes with content.

OAK Healthy is a wellness/routine product, not a hospital dashboard. The interface should feel calm, precise and premium while keeping routine actions fast.

## Visual debt audit

1. Home / Overview — highest debt. The previous top area split one daily signal across four small status cards plus separate recovery and activation cards.
2. Tracking / Add Supplement — three large section cards created a form-inside-dashboard feeling and excessive container nesting.
3. History / Trends — useful charts exist, but card-per-record treatment and mixed chart styling reduce scan speed.
4. Settings — long list with material/glass grouping and inconsistent visual emphasis.
5. Detail / onboarding — lower frequency surfaces that should inherit the shared system after the core flows settle.

## Unified tokens

### Color

Light:
- background/paper: `#F6F7F5`
- raised surface: `#FFFFFF`
- muted surface: `#EEF1EE`
- primary ink: `#111513`
- secondary ink: `#59615C`
- hairline: `#DCE2DD`
- accent: `#1F6B4D`

Dark:
- background/paper: `#0B0F0D`
- raised surface: `#111714`
- muted surface: `#18201C`
- primary ink: `#F2F5F3`
- secondary ink: `#AAB5AE`
- hairline: `#405047`
- accent: `#7DD3A8`

Health semantic colors stay distinct from decorative color:
- Taken / success — green
- Due — blue
- Skipped / caution — amber
- Missed / error — red

### Spacing

`4 / 8 / 12 / 16 / 24 / 32`, with `28` for major section separation.

### Radius

`10 / 14 / 18 / 22`; pill only for chips/status controls. Large page-level rounded containers are not the default.

### Typography

- System sans for body, labels, controls and forms.
- Serif display only for high-value numbers and selected section headlines.
- Screen title 30, hero number 42, section title 20, metric 24, body 16, caption 13.

### Elevation

Default elevation is flat. Raised surfaces use a hairline and at most 1dp-equivalent elevation. Shadow/material blur is not a primary hierarchy tool.

## Component rules

- One dominant insight surface per overview screen.
- Metric controls may have selected backgrounds, but should not become individual cards.
- Form fields and chips can use local control containers; whole form sections should not each become a card.
- Recovery information should sit near the insight it explains rather than as a second dashboard card.
- Bottom navigation is a stable flat shell, not a floating glass capsule.
- Empty/loading/error/success states retain explicit actions and semantic feedback.
- Tablet/desktop widths expand content and spacing without multiplying panels.

## UI-R1 checkpoint

Implemented:
- cross-platform color, spacing, radius and type-scale tokens;
- flat app shell/navigation treatment;
- Home daily overview consolidated into one hero with progress and inline semantic metrics;
- overdue recovery consolidated into the Home insight surface;
- Add/Edit Supplement consolidated into one continuous form surface with divided sections;
- dark theme palette updated for stronger neutral contrast;
- existing dose, recurrence, reminder, entitlement and persistence logic left unchanged.

## UI-R2 checkpoint

Implemented:
- History completion percentage promoted to the dominant 7/30-day visual signal;
- total recorded and late counts moved to supporting metrics around the completion ring;
- trend line and 7-day activity chart now share the OAK wellness accent/semantic status language;
- History search/filter controls use muted token surfaces rather than glass/material containers;
- log history changed from card-per-record to continuous date-grouped rows with hairline dividers and compact status cues;
- visible record count and per-day counts improve scan speed without altering filtering or persistence logic;
- iOS History navigation and search no longer use blur material; Android segmented controls no longer depend on translucent white styling.

## Stage A — Complete Product UI Redesign

Implemented across Android and iOS:
- Stack uses one routine overview, compact Sync/Guide actions, actionable empty state, and continuous supplement rows instead of card-per-item treatment;
- Coach combines completion, client/attention metrics and trend into one workspace insight, while client lists and detail comparisons use continuous/flat hierarchy;
- Settings separates branding from client management, reduces oversized containers, uses solid token surfaces and consistent section spacing;
- onboarding is a three-step flow on both platforms; Android keeps notification permission, exact-alarm and battery reliability actions inside one Reminders step;
- client/profile editing removes material blur, decorative gradients and oversized sheet decoration;
- Plan Access uses current-plan hero + one comparison surface + one store purchase surface rather than a card for every plan/product;
- Sync Center keeps health/recovery first and moves operation logs, IDs, keys and diagnostics behind progressive disclosure;
- notification reliability presents one readable health summary; raw diagnostics are hidden by default and scheduled reminders use continuous rows;
- Safe Boot/Safe Mode share the wellness background and paper surfaces; destructive recovery remains explicit while debug tools stay collapsed by default;
- legacy glass/material treatments left in high-frequency Home helper surfaces were removed so core screens share one visual language.

No Stage A presentation change alters dose persistence, cycles, reminder scheduling semantics, backup/import behavior, sync conflict policy, commercial verification or entitlement decisions.

## Stage B — Final UI/UX Release Candidate

Completed locally:
- compact/large-text fallbacks now cover Stage A Stack actions/metrics, Coach chip groups, Settings theme selection and Plan Access purchase/header layouts;
- iOS Stack/Settings/Plan Access and synthetic Demo Preview use Dynamic Type-aware vertical fallbacks;
- Android splash now uses the product palette, one finite progress transition and a static reduced-motion path; iOS launch branding no longer repeats forever;
- Android expandable Settings content is state-driven without decorative reveal animation;
- legacy Glass card variants, dead elevation/hero/chart/badge/insight presentation tokens and stale navigation animation imports are removed;
- Android/iOS synthetic Demo Preview uses fixed fixtures and the same primary-surface + continuous-row hierarchy as production UI;
- `docs/qa/UI_RELEASE_CANDIDATE_MATRIX.md`, `docs/design/UI_SCREENSHOT_PACK.md` and `scripts/stage_b_ui_rc_gate.py` lock the final presentation contract.

No further small UI redesign stages are planned after Stage B. Future presentation changes should be bug fixes, evidence-driven product work or explicitly requested redesigns.
