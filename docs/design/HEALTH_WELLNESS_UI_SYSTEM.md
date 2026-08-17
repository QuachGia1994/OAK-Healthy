# OAK Healthy — Health & Wellness UI System

Status: UI-R1 implementation checkpoint

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

Deferred to UI-R2 and later:
- History/Trends scanability and chart unification;
- detail/Coach/Stack density pass;
- Settings information architecture;
- onboarding polish and final cross-screen motion review.
