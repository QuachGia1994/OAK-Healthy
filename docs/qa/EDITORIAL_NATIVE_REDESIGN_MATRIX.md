# Editorial native redesign QA matrix

Purpose: verify the Huashu-inspired Warm Editorial Health redesign without changing OAK Healthy product behavior.

| Area | Android | iOS | Expected |
|---|---|---|---|
| Global light background | Material color scheme + `OakBackground` | `oakBackground()` | Warm paper, no glow/mesh background |
| Global dark background | Material dark scheme | `oakBackground()` | Near-black green-tinted paper, warm text |
| Primary accent | `OakColors.Accent` | `OAKPalette.accent` | One moss OAK accent for non-semantic emphasis |
| Status colors | Taken/Skipped/Missed/Due tokens | matching OAK palette functions | Semantic colors remain distinguishable |
| Shared cards | `OakCard` Paper | `oakCardStyle(.paper)` | Opaque paper surface, hairline border, minimal/no shadow |
| Home | serif section/metric hierarchy, compact status cells | serif title/status metrics, compact streak | Daily state readable before decoration |
| History | paper insight surface, serif total, accent trend | same hierarchy | Data-first; no hero gradient |
| Stack | paper overview, serif total, inline metrics | same hierarchy | No gradient hero/pill metric cluster |
| Settings | 14dp paper groups | native List + resilient value rows | Long EN/VI labels do not collapse values |
| Coach | serif summary metrics | serif summary metrics | Dense report hierarchy preserved |
| Sync | paper 14dp operational groups | native List over paper background | Recovery/action hierarchy stays explicit |
| Empty/error | `OakFeedbackCard` | `OAKFeedbackView` | Paper feedback surface and existing actions retained |

## Manual runtime cases before release

1. Android small phone and large phone, light/dark, font scale 1.0 and >=1.3.
2. iPhone compact and large simulator/device, light/dark, Dynamic Type default and accessibility size.
3. Home with due, missed, taken and skipped items present simultaneously.
4. History with analytics data, no data, no-match search and repository error/retry state.
5. Stack with 0, 1 and many supplements; long EN/VI supplement names.
6. Settings long notification rebuild timestamp and long Vietnamese labels.
7. Coach locked, empty, populated and check-in-detail states.
8. Sync unlinked, healthy, pending, missing-key and retryable-error states.

Static repository gate: `python3 scripts/editorial_design_gate.py`.
