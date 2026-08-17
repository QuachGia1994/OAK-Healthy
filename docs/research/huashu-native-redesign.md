# Huashu-inspired native redesign research

Start time: 2026-08-17T13:27+07:00

## Initial purpose
Research `https://github.com/alchaincyf/huashu-design` and translate its current design philosophy into a coherent native redesign for OAK Healthy on Android Compose and iOS SwiftUI. Constraints: preserve existing health-tracker behavior, data, sync, reminders, commerce, accessibility states, EN/VI support, light/dark mode, and current uncommitted P11.1 work; do not embed Huashu HTML or copy its demo literally.

## Strategy
Read the Huashu repository's app-prototype, content, typography and design-style references, then compare those principles against OAK Healthy's existing native design system and high-traffic screens. Apply the principles at the token/component layer first, then make only the screen-level changes needed to create a visible cross-platform identity.

## Checklist
- [x] Verify the referenced repository and current public traction.
- [x] Read Huashu guidance for app prototypes, tracker/data products, typography and anti-AI-slop visual constraints.
- [x] Inspect OAK Healthy Android/iOS color, background, card and feedback primitives.
- [x] Inventory card-heavy Home, History, Stack, Settings, Coach and Sync surfaces.
- [x] Choose a native OAK adaptation rather than copying the Huashu demo palette or HTML implementation.
- [ ] Implement the cross-platform visual foundation and targeted screen changes.
- [ ] Run repository gates and document runtime-only visual verification still required.

## Result
Huashu is a design-judgment skill rather than a mobile component library. Its useful constraints for OAK Healthy are: one warm background and one dominant accent; typography-led hierarchy; restraint with containers, borders, decorative icons and gradients; higher information density is appropriate for tracker/data products; and one signature visual detail should carry more emphasis than decorating every surface equally.

The existing OAK Healthy UI conflicts with that direction in a few systemic places: Android and iOS both use glass/material cards with borders and shadows as the default container; both use teal/blue gradients for hero/insight surfaces; iOS adds blurred background glow circles; Android Settings and Sync use large 20-28dp rounded containers repeatedly; and Stack/History use gradient hero cards with white text. These are high-leverage because changing the shared card/background tokens affects most of the app without touching domain behavior.

Chosen direction: **Warm Editorial Health**. Light mode uses warm paper, raised paper, deep ink and one moss OAK green accent. Dark mode uses near-black green-tinted paper, warm off-white ink and a lighter moss accent. Serif is limited to hero numbers/display headlines; native system sans remains the interaction/body font. Status colors remain semantic exceptions because Taken/Skipped/Missed/Due communicate health-tracker state, while non-semantic charts and decoration collapse back to the OAK accent. Hairlines and whitespace replace most glass borders/shadows. Tracker screens stay information-dense rather than becoming sparse lifestyle pages.

### Verification
Research sources reviewed on 2026-08-17:
- `https://github.com/alchaincyf/huashu-design`
- `https://github.com/alchaincyf/huashu-design/blob/main/references/app-prototype.md`
- `https://github.com/alchaincyf/huashu-design/blob/main/references/content-guidelines.md`
- `https://github.com/alchaincyf/huashu-design/blob/main/references/design-styles.md`
- `https://github.com/alchaincyf/huashu-design/blob/main/references/typography.md`
- OSSInsight repository snapshot observed approximately 22.3k stars and 2.6k forks, used only as evidence that the reference is currently receiving substantial attention, not as evidence that every design choice is appropriate for OAK Healthy.

Local verification before implementation found shared primitives at Android `presentation/designsystem/OakColors.kt`, `OakBackground.kt`, `OakCard.kt` and iOS `Views/OAKCard.swift`; explicit shared card usage appears across Home, History, Stack, Settings, onboarding, Add Supplement and Sync. Current dirty P11.1 files are preserved as part of the redesign scope rather than reset or overwritten wholesale.

### Corroborating links
- Huashu app prototype guidance: `https://github.com/alchaincyf/huashu-design/blob/main/references/app-prototype.md`
- Huashu anti-slop/content constraints: `https://github.com/alchaincyf/huashu-design/blob/main/references/content-guidelines.md`
- Huashu style vocabulary: `https://github.com/alchaincyf/huashu-design/blob/main/references/design-styles.md`
- Huashu typography guidance: `https://github.com/alchaincyf/huashu-design/blob/main/references/typography.md`

## Decision
Action: implement the Warm Editorial Health system through `OakColors`/`OAKPalette`, shared background/card/feedback primitives, then targeted Home/History/Stack/Settings/Coach/Sync changes. The active execution plan is `C:\Users\PHONGQK\.aki\mcpsv\task\oak-healthy-huashu-redesign\plan.md`. Cross-references: `ROADMAP.md`, `CHANGELOG.md`, `docs/qa/UX_POLISH_MATRIX.md`.
