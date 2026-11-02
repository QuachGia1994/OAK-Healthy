---
name: taste-skill
description: Audits OAK Healthy UI/UX on iOS (SwiftUI) and Android (Jetpack Compose) using Taste Skill anti-slop principles. Compares cross-platform parity, typography, spacing, color discipline, motion, and accessibility. Use when reviewing UI/UX, comparing iOS vs Android, redesign audits, or when the user mentions taste-skill, design parity, or anti-slop.
---

# OAK Healthy — Taste Skill (Cross-Platform UI/UX)

Adapted from [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) (`redesign-existing-projects` + v2 audit protocol), scoped to **native mobile** and this repo's dual codebase.

## Design read (declare first)

Before auditing, state one line:

> Reading this as: **health/supplement tracker** for daily-use consumers, with a **calm glass + gradient** language, leaning toward **platform-native patterns** (SwiftUI materials / Material 3) with **cross-platform parity**, not web landing-page aesthetics.

## Dials (OAK defaults)

| Dial | Value | Meaning for this app |
|------|-------|----------------------|
| DESIGN_VARIANCE | 4 | Symmetric lists, predictable tabs — not marketing asymmetry |
| MOTION_INTENSITY | 3 | Snappy toggles, no cinematic scroll hijacks |
| VISUAL_DENSITY | 6 | Data-heavy home/history; readable but compact |

Override only when the user asks for a visual overhaul.

## Workflow

```
1. Map screens     → iOS/Views ↔ Android/presentation (see reference.md)
2. Scan both sides → design tokens, navigation, empty/loading/error states
3. Diagnose        → anti-slop + parity checklist below
4. Report          → use output template; cite file:line for every finding
5. Fix (if asked)  → smallest diff on shared behavior; respect platform HIG/M3
```

**Rules**
- Audit first; do not rewrite UI unless asked.
- Work with existing stacks (SwiftUI, Compose, `OakCard` / `oakCardStyle`).
- Prefer centralizing tokens over one-off fixes on a single screen.
- Platform-specific chrome (TabView vs bottom bar) is OK; **feature parity** and **visual rhythm** must match.

## Screen pairs (quick map)

| Screen | iOS | Android |
|--------|-----|---------|
| Home | `iOS/Views/HomeView.swift` | `Android/.../home/HomeScreen.kt` |
| History | `iOS/Views/HistoryView.swift` | `Android/.../home/HistoryScreen.kt` |
| Stack | `iOS/Views/StackView.swift` | `Android/.../home/MyStackListScreen.kt` |
| Settings | `iOS/Views/SettingsView.swift` | `Android/.../home/SettingsScreen.kt` |
| Add | `iOS/Views/AddSupplementView.swift` | `Android/.../add_supplement/AddSupplementScreen.kt` |
| Onboarding | `iOS/Views/OnboardingView.swift` | `Android/.../onboarding/OnboardingScreen.kt` |
| Sync | `iOS/Views/SyncCenterView.swift` | `Android/.../sync/SyncCenterScreen.kt` |
| Design system | `iOS/Views/OAKCard.swift` | `Android/.../designsystem/Oak*.kt` |

Full map: [reference.md](reference.md)

## Anti-slop audit (both platforms)

Run per screen pair. Flag severity: **BUG** | **IMPROVEMENT** | **MINOR** | **PARITY**.

### Typography
- Fixed pixel sizes (`52pt`/`52.sp`) for hero numbers — breaks Dynamic Type / font scaling
- Same semantic level using same weight (e.g. row + section header both `.headline`)
- Hardcoded locale strings where `.localized` / `stringResource` exists elsewhere
- Caption-only blocks with no hierarchy (Sync Center status fields)

### Color discipline
- More than one competing accent in a single control group (Home dose filter bar)
- Duplicated dark gradient RGB across 5+ views instead of `oakBackground()` / `OakBackground`
- Dose status colors not routed through `OakColors` (Android) or a shared iOS token file
- Pure semantic overload: `.blue`, `.red`, `.green`, `.orange` on iOS without token names

### Spacing
- Off 4pt/8dp grid (14pt padding, 10dp gaps mixed with 8/12/16)
- Inconsistent card radius: iOS 16 vs Android 20 default vs 28 in screens
- Tab clearance: iOS `128pt` vs Android `100.dp` bottom padding
- First list section different insets from subsequent sections (Home)

### Layout & components
- Dead/orphan UI code (structs never used, settings sections not wired)
- iOS `OAKCard`: single `.glass` variant; Android `OakCard`: Glass + Surface — document intentional gap
- Empty states using different icons/copy (History: clock vs search)
- Hardcoded bottom safe-area padding instead of tab-bar-aware layout

### Motion
- Uncoordinated animation curves on the same interaction
- Timers/animations running when app backgrounded
- Missing `accessibilityReduceMotion` handling (iOS) / no reduced-motion alternative (Android)

### Accessibility
- Toolbar icons without labels (`plus`, `gear`)
- `.buttonStyle(.plain)` / small tap targets below 44pt
- Android: `contentDescription` present — check for hardcoded English (`"Done"`, `"Pending"`)
- Onboarding progress hidden without parent `accessibilityValue`

### Cross-platform parity (PARITY severity)
- Feature exists on one platform only (onboarding steps, client picker placement, Safe Mode, splash)
- Same feature, different entry path (Sync Center: Stack/MyStack only; iOS Settings section orphaned)
- Settings: iOS sheet vs Android push — acceptable if UX equivalent
- Shared numeric tokens diverge (insights gradient, card radius, secondary text alpha)

## Fix priority

1. **BUG** — accessibility blockers, broken navigation, contrast failures
2. **PARITY** — feature/entry mismatches users will notice across devices
3. **IMPROVEMENT** — token centralization, typography scale, spacing grid
4. **MINOR** — DRY, dead code, animation polish

## Output template

```markdown
# OAK Healthy — Taste Skill Audit

**Design read:** [one line]
**Dials:** VARIANCE / MOTION / DENSITY

## Executive summary
[2–3 sentences: biggest gaps and recommended focus]

## Cross-platform parity
| Area | iOS | Android | Severity | Recommendation |
|------|-----|---------|----------|----------------|

## iOS findings
| Severity | Screen | Issue | Fix |

## Android findings
| Severity | Screen | Issue | Fix |

## Shared design system
[Token unification targets: colors, radius, spacing, secondary text]

## Top 5 actions
1. ...
```

## Existing reports

- iOS-only audit: `iOS/UI_REVIEW_REPORT.md` — merge findings; do not duplicate blindly; re-verify in code.

## Install upstream taste-skill (optional)

For web/greenfield work outside this repo:

```bash
npx skills add https://github.com/Leonxlnx/taste-skill --skill "redesign-existing-projects"
```

This project skill stays the source of truth for **OAK Healthy iOS ↔ Android** audits.
