---
version: ui-rc
name: OAK Wellness Paper
description: Calm, scan-first health/wellness UI shared by Android and iOS.
colors:
  accent: "#1F6B4D"
  accent-dark: "#7DD3A8"
  paper: "#F6F7F5"
  paper-raised: "#FFFFFF"
  paper-muted: "#EEF1EE"
  ink: "#111513"
  ink-muted: "#59615C"
  hairline: "#DCE2DD"
  paper-dark: "#0B0F0D"
  paper-raised-dark: "#111714"
  paper-muted-dark: "#18201C"
  ink-dark: "#F2F5F3"
  ink-muted-dark: "#AAB5AE"
  hairline-dark: "#405047"
  due: "#466A8D"
  taken: "#1F6B4D"
  skipped: "#9A661F"
  missed: "#B5473F"
  due-dark: "#91AEC8"
  taken-dark: "#7DD3A8"
  skipped-dark: "#D2A35F"
  missed-dark: "#E28C82"
typography:
  screen-title: 30
  hero-number: 42
  section-title: 20
  metric: 24
  body: 16
  caption: 13
spacing:
  xs: 4
  sm: 8
  md: 12
  lg: 16
  xl: 24
  xxl: 32
  section: 28
radius:
  sm: 10
  md: 14
  lg: 18
  xl: 22
  pill: 999
---

# OAK Healthy Design System

## Product character

OAK Healthy is a modern health/wellness product, not a hospital dashboard. Screens favor whitespace, strong typography, one primary insight or action, prominent progress/charts, and continuous rows instead of card-per-item fragmentation.

Android and iOS keep native interaction behavior while sharing the same hierarchy and semantic tokens. The source-of-truth token files are:

- Android: `presentation/designsystem/OakColors.kt`, `OakTokens.kt`.
- iOS: `Views/OAKDesignTokens.swift`.

`DESIGN.md` documents those tokens; it must not introduce a parallel palette or formula.

## Color semantics

The single neutral accent is moss green. Health states keep stable semantic colors across platforms:

- Due: muted blue.
- Taken: moss green.
- Skipped: amber/brown.
- Missed: restrained red.

Never rely on color alone. Pair state color with a label, icon, count, or progress cue. Dark theme uses near-black paper surfaces with brighter text and semantic colors tuned for contrast.

## Typography and hierarchy

Use native system sans-serif for body and controls. Serif is reserved for display/hero health metrics where already defined by the shared typography helpers. Do not create screen-specific font scales.

A normal screen should read in this order:

1. screen title/context;
2. one primary insight or primary action;
3. compact supporting metrics/chart;
4. continuous rows/details;
5. technical or diagnostic detail only after explicit disclosure.

## Layout and surfaces

Use the shared 4/8/12/16/24/32 spacing rhythm. Default screen gutter is 16, compact phones may use 12, and wide layouts may expand to 28 while keeping content hierarchy intact.

Use paper surfaces and hairline separation. Avoid nested cards, per-row cards, decorative gradients, blur/glass surfaces, and heavy elevation. Radius is restrained; 14–18 is the normal surface range and pills are reserved for true short-selection/status patterns.

## App shell and bottom navigation

The three primary destinations are Home, Stack, and History. Settings and secondary flows are pushed from those destinations rather than occupying a fourth primary tab.

Android:

- `Scaffold.bottomBar` owns the bottom navigation boundary.
- System navigation inset is consumed exactly once by the bottom bar.
- Full state shows icon + label; scrolling down on a primary tab minimizes to a 56dp icon-only bar, scrolling up restores the 80dp full bar.
- The overdue count is the only tab badge.
- Switching primary tabs restores the expanded state.

Apple platforms:

- Use native `TabView` and system tab-bar safe-area handling.
- On iPhone with iOS 26+, the native tab bar minimizes on downward scroll and expands on upward scroll.
- Older iOS versions keep the stable full tab bar; iPad keeps native non-minimized behavior.
- The overdue count is the only tab badge.

## Accessibility and motion

Android interactive targets remain at least 48dp; iOS targets remain at least 44pt. Preserve TalkBack/VoiceOver labels even when visual tab labels are minimized. Large text must wrap or switch to vertical layouts rather than compress health data.

Motion is functional only. Android shell minimization uses a short height transition and becomes immediate when system animator duration is disabled. iOS uses the native tab-bar minimization behavior. Avoid decorative infinite animation.

## Screen patterns

- Home: one daily summary, progress first, inline Due/Missed/Taken/Skipped controls.
- Stack: one overview plus continuous routine rows and direct add/sync actions.
- History: completion/trend hero, prominent 7-day activity chart, compact filters, continuous date-grouped timeline.
- Coach: one workspace insight plus continuous client rows and same-client comparison.
- Forms: one continuous form surface with section dividers; avoid one card per field group.
- Sync/recovery: human-readable health and repair state first; technical logs/IDs/keys disclosed on demand.
- Empty/loading/error/success/partial states must be explicit and never replaced by fake runtime sample health data.

## Do / do not

Do keep semantic health colors stable, use native controls, preserve reduced-motion/accessibility behavior, and reuse shared tokens/components only where repeated patterns are proven.

Do not add arbitrary gradients, glassmorphism, decorative color, giant dashboard grids, duplicated token values, or one-off screen styles. Do not encode health/business formulas in presentation code.
