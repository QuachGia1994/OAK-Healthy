# App Shell Closure + Scroll-Minimized Bottom Bar

This is one presentation-only closure stage after the Stage B UI release candidate. It does not change health persistence, dose status, recurrence, sync, backup/import, reminders, billing, entitlements, analytics consent, or store execution.

## Android shell

- `Scaffold.bottomBar` owns the primary navigation bar.
- The `NavHost` consumes `Scaffold` content padding and window insets once.
- `OakBottomBar` disables `NavigationBar`'s internal insets and applies `navigationBarsPadding()` exactly once at the shell boundary.
- Primary-tab scroll is observed through nested scroll without consuming content scroll.
- Downward user scroll minimizes the bar after a small threshold; upward user scroll restores it.
- Full state is 80dp with icon + label. Compact state is 56dp icon-only with the existing overdue badge.
- Tab selection restores the expanded state before navigating.
- System animator scale disabled => shell height change is immediate rather than animated.
- Touch targets and icon accessibility descriptions stay provided by native Material navigation items.

Manual Android matrix:
- gesture navigation and 3-button navigation;
- 320dp, 360dp, 412dp, 600dp widths;
- fontScale 1.0, 1.3 and 2.0;
- Home, Stack and History long-scroll/down-scroll/up-scroll;
- switch tabs while compact, rotate/recreate activity, enter/return from Settings;
- Light/Dark and EN/VI.

## Apple shell

- Native `TabView` remains the primary navigation owner.
- On iPhone with iOS 26+, `tabBarMinimizeBehavior(.onScrollDown)` provides native scroll minimization and expansion.
- Deployment target remains iOS 17; the iOS 26 behavior is availability-gated and older systems keep the stable full tab bar.
- iPad keeps native non-minimized tab behavior because Apple's minimize behavior is iPhone-only.
- Existing active-client `.id(...)` tab boundaries remain intentional to prevent stale cross-client presentation state after profile changes.
- Native safe-area handling and the existing overdue badge remain unchanged.

Manual Apple matrix:
- iOS 17 fallback full tab bar;
- iOS 26 iPhone scroll-down minimize / scroll-up restore;
- iPad full tab bar;
- Dynamic Type default and accessibility sizes;
- VoiceOver, Light/Dark, EN/VI;
- active-client switch must not show stale prior-client tab content.

## Design-token ownership

- Android palette owner: `OakColors.kt`; spacing/radius/type owner: `OakTokens.kt`.
- iOS palette, spacing, radius and type owner: `OAKDesignTokens.swift`.
- `OAKCard.swift` consumes tokens and does not declare a parallel palette.
- `DESIGN.md` mirrors implementation tokens and contains no legacy teal/hero-gradient/glass design contract.

## Automated closure gate

`python3 scripts/ui_shell_closure_gate.py` verifies shell ownership, inset policy, scroll-minimize contracts, iOS availability fallback and token ownership. The gate is part of `Quality Gates` and `scripts/oak_regression.py`.

Closure requires Android Build, iOS Build/unsigned IPA and Quality Gates green on the same pushed source SHA before the development branch can fast-forward `main`.
