# OAK Healthy — SwiftUI UI/UX Audit

Reviewed: SupplementTrackerApp.swift, HomeView.swift, SettingsView.swift, AddSupplementView.swift, HistoryView.swift, OAKCard.swift, StackView.swift, OnboardingView.swift, SyncCenterView.swift, LetterStormLogoView.swift, OAKLogoView.swift, UserGuideView.swift, NotificationDebugView.swift

---

## 1. Anti-Slop: Dead / Boilerplate Code

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| IMPROVEMENT | HomeView.swift:609-631 | `CountChip` struct defined but never used anywhere. | Delete. |
| IMPROVEMENT | SettingsView.swift:561-578 | `GuideRow` defined but never called — `UserGuideView` has its own `GuideRowView`. | Delete. |
| IMPROVEMENT | SettingsView.swift:266-287 | `supplementListSection` and `userGuideSection` computed properties orphaned — not referenced from `settingsList`. | Remove or reconnect. |
| IMPROVEMENT | UserGuideView.swift:89-92 | All 7 guide icons hardcode `.blue` tint with no variation. | Per-row accent or `Color.accentColor`. |
| MINOR | HomeView.swift:156,382 | Edit swipe `.tint(.orange)` copy-pasted across two row builders. | Shared constant. |

## 2. Typography Hierarchy

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| IMPROVEMENT | HistoryView.swift:296 | `Font.system(size: 52, weight: .bold)` hardcoded pixel size — ignores Dynamic Type. | `@ScaledMetric` or semantic font. |
| IMPROVEMENT | SyncCenterView.swift:188-289 | ~12 consecutive lines all `.font(.caption)` — no visual hierarchy among status fields. | Vary: `.footnote` primary, `.caption` secondary, `.caption2` metadata. |
| IMPROVEMENT | HomeView.swift:674 vs 583 | Supplement row `.headline` collides with section header `.headline` — same weight for content and structure. | Row: `.subheadline`; keep `.headline` for headers. |
| MINOR | AddSupplementView.swift:294 | Weekday labels hardcoded Vietnamese `["T2","T3",...]` despite app-wide `.localized` pattern. | `Calendar.current.shortWeekdaySymbols`. |
| MINOR | UserGuideView.swift:94-98 | Detail `.body` is too large relative to `.headline` title — jump feels abrupt. | `.subheadline` for detail text. |

## 3. Spacing Consistency

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| IMPROVEMENT | HomeView.swift:69 | First section list row insets `(14, 16, 8, 16)` differ from subsequent rows `(6, 16, 6, 16)` — inconsistent vertical rhythm. | Standardize top padding across sections. |
| IMPROVEMENT | HistoryView.swift:119-121 | Outer VStack `spacing: 24` but inner card padding `16` creates inconsistent gap ratios (24 vs 16 vs 8 throughout). | Establish 4pt grid: 4, 8, 12, 16, 20, 24. |
| MINOR | OnboardingView.swift:441 | `onboardingCard()` uses `padding(14)` — 14 is off the 4pt grid. | Use `padding(12)` or `padding(16)`. |
| MINOR | AddSupplementView.swift:252-253 | Weekday grid `spacing: 8` inside a VStack with `spacing: 10` — two non-grid-aligned values coexist. | Pick 8 or 12 consistently. |

## 4. Color Discipline

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| BUG | HomeView.swift:482-509 | Filter bar uses 4 tint colors (`.blue`, `.red`, `.green`, `.orange`) plus accent for selection — exceeds 3 accent limit, competes for attention. | Reduce to accent + 1 semantic pair. |
| IMPROVEMENT | HomeView.swift:311-314 | `backgroundGradient` hardcodes `Color(red: 0.08, green: 0.0, blue: 0.15)` for dark mode — repeated identically in 5 views (Home, Settings, Add, History, Stack, Sync). DRY violation and brittle. | Extract to a shared `ViewModifier` or `EnvironmentKey`. |
| MINOR | OAKLogoView.swift:11-49 | Logo uses 5+ custom RGB colors — intentional for branding but won't adapt to tinted accessibility filters. | Not urgent, but consider `Color.accentColor` for the green. |
| MINOR | HistoryView.swift:271-276 | InsightsTrendCard gradient is hardcoded RGB `Color(red: 0.10, green: 0.55, blue: 1.0)` — won't respect accent overrides. | Use `.tint` or system blue. |

## 5. Motion & Animation

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| IMPROVEMENT | HomeView.swift:713 | `withAnimation(.snappy)` on action sheet presentation — snappy is fast but the icon scale animation at line 800 uses `.spring(response: 0.22)` with no coordination. Two independent animation curves. | Unify to one spring or use `.snappy` everywhere. |
| MINOR | HomeView.swift:26 | 30-second `refreshTimer` ticks even when app is backgrounded — wastes CPU. | Gate on `scenePhase == .active`. |
| MINOR | LetterStormLogoView.swift:81-107 | `TimelineView` runs at 24fps continuously even when frozen. The freeze check at line 95-98 happens inside the Canvas closure. | Break out of TimelineView once `freezeT` is set. |

## 6. Accessibility

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| BUG | HomeView.swift:207-208 | Add button toolbar `Image(systemName: "plus")` has no `.accessibilityLabel`. VoiceOver reads "plus" or nothing. | `.accessibilityLabel("add_supplement".localized)`. |
| BUG | HomeView.swift:214-215 | Gear icon toolbar has no `.accessibilityLabel`. | `.accessibilityLabel("settings_title".localized)`. |
| BUG | HistoryView.swift:131-137 | Same missing label on gear icon in History toolbar. | Same fix. |
| BUG | StackView.swift:98-112 | Both toolbar icons (plus, gear) missing accessibility labels. | Same fix. |
| IMPROVEMENT | HomeView.swift:537-561 | `TodayStripButton` uses `.buttonStyle(.plain)` — VoiceOver won't announce it as interactive. | Use default button style or add `.accessibilityAddTraits(.isButton)`. |
| IMPROVEMENT | StackView.swift:145-147 | `glassRowBackground` applies material but has no minimum height — tap targets may fall below 44pt. | Add `.frame(minHeight: 44)` to list rows. |
| IMPROVEMENT | HistoryView.swift:86-93 | Empty state icon `Image(systemName: "clock")` lacks accessibility label. | `.accessibilityLabel("no_logs_yet".localized)`. |
| MINOR | OnboardingView.swift:410-412 | Progress dots marked `.accessibilityHidden(true)` but parent has no replacement description. | Add `accessibilityValue` with step description to parent. |

## 7. Layout & Geometry

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| IMPROVEMENT | HomeView.swift:180 | `.safeAreaPadding(.bottom, 128)` hardcoded — on iPhone SE this pushes content far up; on Max/Pro it's excessive. | Use `GeometryReader` to calculate based on screen height or `@Environment(\.verticalSizeClass)`. |
| IMPROVEMENT | HistoryView.swift:121 | `.padding(.bottom, 136)` same hardcoded bottom padding issue. | Same fix. |
| IMPROVEMENT | StackView.swift:78 | `.safeAreaPadding(.bottom, 128)` same pattern. | Same fix. |
| IMPROVEMENT | HomeView.swift:472-474 | `HomeDoseFilterBar` uses `GridItem(.flexible(), spacing: 10)` in 2-column grid — on narrow screens (SE), text clips. | Use `adaptive` grid or reduce to single column on compact width. |
| MINOR | HistoryView.swift:425 | `HistoryRow` time column `frame(width: 64)` — on very long supplement names this can overlap. | Use `fixedSize` or flexible layout. |

## 8. Dark Mode

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| BUG | SafeBootView.swift:239 | `ProgressView().tint(colorScheme == .dark ? .white : .black)` — forces black tint in light mode, but `.primary` is more semantic and respects accessibility contrast settings. | Use `.tint(.primary)`. |
| IMPROVEMENT | HomeView.swift:311-314 | Dark gradient `Color(red: 0.08, green: 0.0, blue: 0.15)` is a very dark purple — fine, but the light gradient uses `systemGroupedBackground` which is semantic. Asymmetric: one side adapts, other doesn't. | Use semantic dark background or `Color(.systemBackground)`. |
| IMPROVEMENT | LetterStormLogoView.swift:217-218 | Base/shadow colors switch on `colorScheme` but use `UIColor` → `Color` bridging via hardcoded RGBA. Should work, but test with increased contrast accessibility setting. | Use `.primary` and `.secondary` semantic colors. |
| MINOR | OnboardingView.swift:431-434 | Progress dot inactive uses `.secondary.opacity(0.25)` — too faint in light mode, borderline invisible. | Use `.tertiary` or increase to 0.4. |

## 9. Error / Empty / Loading States

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| BUG | HomeView.swift:42-57 | Empty state when no clients exists uses `.oakCardStyle(.glass)` but is not inside a List — on its own in a ZStack with the background gradient. The card floats without context. | Wrap in a centered container with explicit spacing from the gradient. |
| IMPROVEMENT | HistoryView.swift:84-94 | Empty logs state shows clock icon + text but no illustration or action. Compared to HomeView's empty state which has an action button, this feels incomplete. | Add a "add your first supplement" action or illustration. |
| IMPROVEMENT | NotificationDebugView.swift:28-33 | Loading state is bare `ProgressView()` with no message — user sees spinner with no context about what's loading. | Add `Text("loading".localized)` below the spinner. |
| IMPROVEMENT | AddSupplementView.swift:70-79 | Saving overlay uses `Color.black.opacity(0.2)` — this is a blocking scrim but there's no text explaining the save is in progress. | Add label like "saving".localized below the ProgressView. |
| MINOR | SyncCenterView.swift:380-382 | Sync loading state shows bare `ProgressView()` inline in the list — jarring among text rows. | Wrap in an HStack with label. |

## 10. Navigation Patterns

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| IMPROVEMENT | HomeView.swift:187-217 | Two trailing toolbar items (plus, gear) placed independently — on narrow screens they can overlap or push title offscreen. | Combine into a single `Menu` or use `ToolbarItemGroup`. |
| IMPROVEMENT | StackView.swift:84-112 | Identical dual trailing toolbar pattern as HomeView — same overlap risk. | Same fix. |
| IMPROVEMENT | HistoryView.swift:130-138 | Gear icon opens Settings as sheet — consistent with other tabs, good. But HomeView also has gear as sheet. Users may expect gear to always be a sheet, not NavigationLink. | Document this as intentional; the pattern is actually consistent across all 3 tabs. |
| MINOR | SupplementTrackerApp.swift:102-107 | `MainTabView` shows 3 tabs (Home, Stack, History) but there's no settings tab — settings is accessed via toolbar button on each tab. Could confuse new users. | Consider adding a 4th tab or onboarding hint. |
| MINOR | SyncCenterView.swift:50-61 | SyncCenter is a NavigationLink pushed from StackView — deep hierarchy (Tab → Stack → SyncCenter). Back navigation requires 2 taps. | Consider presenting as sheet for shallower depth. |

---

## Summary

**Severity breakdown**: 7 BUG, 24 IMPROVEMENT, 15 MINOR

**Top priorities**:
1. Missing accessibility labels on all toolbar icons (BUG — VoiceOver broken)
2. Hardcoded Dynamic Type sizes in InsightsTrendCard (BUG — accessibility violation)
3. Hardcoded bottom padding (128/136pt) breaks on small/large devices (IMPROVEMENT — layout)
4. DRY violation: background gradient duplicated in 6 files (IMPROVEMENT — maintenance)
5. Weekday labels not localized (MINOR — i18n)
