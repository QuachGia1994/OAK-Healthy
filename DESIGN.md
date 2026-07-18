---
version: alpha
name: OAK Calm Precision
description: A calm, trustworthy health dashboard shared by the iOS and Android apps.
colors:
  primary: "#0F6F75"
  primary-dark: "#78D2CF"
  primary-container: "#C5EAE7"
  on-primary-container: "#073F43"
  surface: "#FAFCF9"
  surface-dark: "#0C1C1A"
  on-surface: "#14201D"
  on-surface-dark: "#E4F0EC"
  due: "#1565C0"
  taken: "#237A4B"
  skipped: "#B45309"
  missed: "#C73538"
  due-dark: "#64B5F6"
  taken-dark: "#81C784"
  skipped-dark: "#FFB74D"
  missed-dark: "#EF9A9A"
  hero-start: "#087887"
  hero-end: "#0C4D78"
  on-hero: "#FFFFFF"
typography:
  display:
    fontFamily: System UI
    fontSize: 44px
    fontWeight: 700
    lineHeight: 1.05
  headline:
    fontFamily: System UI
    fontSize: 20px
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: System UI
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: System UI
    fontSize: 13px
    fontWeight: 600
    lineHeight: 1.2
rounded:
  sm: 12px
  md: 18px
  lg: 20px
  xl: 24px
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
components:
  hero-card:
    backgroundColor: "{colors.hero-end}"
    textColor: "{colors.on-hero}"
    typography: "{typography.display}"
    rounded: "{rounded.xl}"
    padding: "{spacing.lg}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  navigation-selected:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
  status-due:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.due}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  status-taken:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.taken}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  status-skipped:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.skipped}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  status-missed:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.missed}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  dark-card:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.on-surface-dark}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
  dark-accent:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  hero-gradient-start:
    backgroundColor: "{colors.hero-start}"
    textColor: "{colors.on-hero}"
    rounded: "{rounded.xl}"
  status-due-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.due-dark}"
    rounded: "{rounded.md}"
  status-taken-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.taken-dark}"
    rounded: "{rounded.md}"
  status-skipped-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.skipped-dark}"
    rounded: "{rounded.md}"
  status-missed-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.missed-dark}"
    rounded: "{rounded.md}"
---

# OAK Healthy Design System

## Overview

OAK Healthy uses calm precision: clinical enough to feel trustworthy, warm enough to support a daily habit. The visual density is moderate, with status and next actions visible before secondary detail. iOS and Android share hierarchy and tokens while retaining native navigation, controls, accessibility, and motion behavior.

## Colors

- Primary teal is reserved for navigation, links, and the most important neutral action.
- Due, taken, skipped, and missed always remain blue, green, orange, and red respectively. Never communicate these states by color alone; pair them with a label, count, or icon.
- Hero cards use the teal-to-blue range. Surfaces stay softly neutral so health data remains the focus.
- All normal-size text and interactive labels must meet WCAG AA contrast. Status colors in this file are the accessible text variants.

## Typography

Use each platform's native system family for Dynamic Type and localization. Rounded display numerals are allowed for dashboard totals. Use bold only for section hierarchy, totals, and selected states; body copy remains regular.

## Layout

Use an 8px rhythm with 4px micro-adjustments. Screen gutters are 16px, related controls use 8px or 12px gaps, and primary cards use 16px to 24px internal padding. Long data collections use native lazy lists with stable identity. Keep the primary summary above filters and the detailed feed below them.

## Elevation & Depth

Depth comes from tonal glass surfaces, a thin semantic border, and one soft shadow. Do not stack multiple shadows or place a glass card inside another glass card. Hero cards may use a stronger shadow because they anchor the screen.

## Shapes

Cards use 18px to 24px continuous corners. Inputs and compact status tiles use 12px to 18px corners. Pills are reserved for short metrics, selected navigation indicators, and streaks.

## Components

- Home status filters are four independent native buttons with a minimum 44px touch target. Tapping the selected filter returns to the all state.
- Supplement rows use a leading status rail plus text or icon so status survives grayscale and color-vision differences.
- Stack starts with one overview hero, then two quick actions, search, and the supplement list.
- History starts with the 7/30-day insight hero, followed by frequency and the searchable timeline.
- Bottom navigation uses native tab components. Selection uses the primary container; overdue count is the only badge.

## Do's and Don'ts

- Do keep the four dose colors stable across platforms.
- Do preserve native focus, Dynamic Type, TalkBack, VoiceOver, and reduced-motion behavior.
- Do use one clear hero surface per tab and avoid repetitive card nesting.
- Don't use purple as a generated default or introduce decorative color without a semantic role.
- Don't add custom gestures when a native button, picker, list, or navigation item covers the interaction.
- Don't animate every state change; reserve motion for selection, status confirmation, and screen transitions.
