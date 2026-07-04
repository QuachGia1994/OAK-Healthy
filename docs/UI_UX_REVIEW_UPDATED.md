# OAK Healthy UI/UX Review Updated

Scope: Android `HomeScreen.kt`, `HistoryScreen.kt`, iOS `HomeView.swift`, `HistoryView.swift`

## Android

| Type | Severity | Location | Note |
|---|---|---|---|
| Bug | Low | `Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt` | None obvious in the current pass; the main flows are structurally sound and the recent hierarchy tweaks reduced the most visible UI friction. |
| Improvement | Medium | `Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeScreen.kt` | The Today filter cards are now more neutral, but the active state could be even clearer on very small screens. A stronger selected background or pill treatment would make filtering faster to scan. |
| Improvement | Medium | `Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt` | The history insights card still uses a strong hero style. It works, but the large number and chart can compete for attention with the detail list below. |
| Improvement | Medium | `Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt` | Empty states now explain the next step better, but there is still no direct action from the history empty state. A shortcut to add a supplement would reduce dead ends. |
| Nice-to-have | Low | `Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeScreen.kt` | The client selector in the top bar is functional, but long client names can still make the header feel busy. A more compact menu trigger would help on narrow devices. |
| Nice-to-have | Low | `Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt` | The record rows are readable, but the time column could become slightly more flexible for long localized strings or user-entered time formats. |

## iOS

| Type | Severity | Location | Note |
|---|---|---|---|
| Bug | Low | `iOS/Views/HistoryView.swift` | No major blockers surfaced in the current code path. The obvious hard layout issues are mostly reduced after the padding cleanup. |
| Improvement | Medium | `iOS/Views/HomeView.swift` | The empty client state is cleaner now, but it still behaves like a static card. A stronger illustration or a second line of guidance would make onboarding feel more intentional. |
| Improvement | Medium | `iOS/Views/HomeView.swift` | The dashboard list still depends on a fixed bottom safe-area offset. The smaller value is better, but a device-aware offset would scale more gracefully across SE and Pro Max sizes. |
| Improvement | Medium | `iOS/Views/HistoryView.swift` | The empty history state now gives a next-step hint, but the overall history page still reads as a stack of good cards rather than a clear story. Tightening typographic hierarchy would improve scan speed. |
| Nice-to-have | Low | `iOS/Views/HomeView.swift` | The Today filter chips are usable, but the selected state could be made more explicit with stronger contrast or a denser active fill. |
| Nice-to-have | Low | `iOS/Views/HistoryView.swift` | The insights card is polished, but the large total number and segmented control could be visually balanced a bit better for smaller phones. |

## Summary

The current app already has a much stronger baseline than the original review suggested. The remaining work is mostly polish:

1. Make selected states a little more obvious.
2. Replace any remaining fixed spacing with device-aware spacing.
3. Give empty states one obvious next action whenever possible.
4. Keep the hero charts/cards, but reduce visual competition with the detail list.
