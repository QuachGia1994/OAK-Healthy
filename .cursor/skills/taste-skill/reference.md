# OAK Healthy — Taste Skill Reference

## Navigation architecture

| Concern | iOS | Android |
|---------|-----|---------|
| Root | `SupplementTrackerApp.swift` → `MainTabView` | `MainActivity.kt` → `AppNavigation.kt` |
| Tabs | Native `TabView`: Home, Stack, History | `OakBottomBar`: Home, My Stack, History |
| Settings | `.sheet` from toolbar gear | Push route + Close |
| Add/Edit | `.sheet` | `navController.navigate` |
| Onboarding | Blocking sheet over tabs | `startDestination` swap |
| Sync Center | `NavigationLink` from Stack | Push from MyStack quick actions |

## Design tokens

### Background

| | iOS (`OAKCard.swift`) | Android (`OakBackground.kt`) |
|--|----------------------|------------------------------|
| Light | `systemGroupedBackground` → `systemBackground` | `#EAF7FF` → `#F1F8E9` |
| Dark | `systemBackground` @ 0.95 → black | `#120025` → black |
| API | `.oakBackground()` | `OakBackground { }` |

### Card

| | iOS | Android |
|--|-----|---------|
| Default radius | 16pt (`oakCardStyle`) | 20.dp (`OakCard`) |
| Screen usage | Often 14–16pt; History local `.oakCard()` 14pt | Often 28.dp ad-hoc |
| Variants | `.glass` only | `Glass`, `Surface` |

### Colors

- **Android:** `OakColors.kt` — dose status, insights gradient, chart, badges
- **iOS:** No token file; `Color.accentColor`, system colors, logo RGB in `OAKLogoView.swift`

### Secondary text

- Android screens: `onSurface.copy(alpha = 0.78f)`
- Android row composables: `onSurfaceVariant` (inconsistent)
- iOS: `.secondary`, `.foregroundStyle(.secondary)`

## Known parity gaps (verify before each audit)

| Gap | iOS | Android |
|-----|-----|---------|
| Onboarding steps | 3 (client, notifications, done) | 5 (+ exact alarm, battery) |
| Client picker in toolbar | Home + Stack | Home only |
| Sync in Settings | Section exists but unwired | Not in Settings |
| Safe Mode / import gate | Yes | No |
| Integrity block | No | Yes |
| Branded splash | No | Yes (~3.3s) |
| Insights empty icon | `clock` | `Search` |
| Tab bottom inset | 128pt | 100.dp + bar padding |

## Audit file checklist

When auditing, read these pairs together:

```
HomeView.swift          ↔ HomeScreen.kt
HistoryView.swift       ↔ HistoryScreen.kt
StackView.swift         ↔ MyStackListScreen.kt
SettingsView.swift      ↔ SettingsScreen.kt + SettingsComponents.kt
AddSupplementView.swift ↔ AddSupplementScreen.kt
OnboardingView.swift    ↔ OnboardingScreen.kt
SyncCenterView.swift    ↔ SyncCenterScreen.kt
OAKCard.swift           ↔ OakCard.kt + OakColors.kt + OakBackground.kt
```

## Taste Skill upstream skills

| Install name | Use when |
|--------------|----------|
| `design-taste-frontend` | Greenfield web UI (not this repo) |
| `redesign-existing-projects` | Generic redesign audit checklist |
| `minimalist-ui` | User wants Notion/Linear direction |
| `high-end-visual-design` | Premium calm whitespace direction |

Source: https://github.com/Leonxlnx/taste-skill
