# Health UI Redesign — UI-R1/UI-R2 QA Matrix

| Area | Android | iOS | Contract |
|---|---|---|---|
| Shared tokens | `OakTokens.kt`, `OakColors.kt` | `OAKDesignTokens.swift`, `OAKCard.swift` | Same spacing/radius/type scale and neutral wellness palette |
| App shell | Flat bottom navigation + hairline | Solid tab-bar surface | No floating/glass navigation shell |
| Home primary insight | Single daily summary surface | Single daily summary surface | Progress is visually primary; status metrics are inline controls, not four cards |
| Recovery | Inside daily summary | Inside daily summary | Neutral, actionable, not punitive |
| Tracking form | One form surface, divided sections | One form surface, divided sections | Details/Timing/Rhythm are sections, not nested page cards |
| History hero | Completion ring + 7/30 trend | Completion ring + 7/30 trend | Completion/trend is the dominant History insight; total/late are supporting metrics |
| History activity | Accent 7-day bar chart, no wrapper card | Accent 7-day bar chart, no wrapper card | Secondary chart uses the same wellness visual language |
| History filters | Muted search + segmented state filter | Muted search + segmented state filter | Search/filter controls are compact and scan-friendly |
| History timeline | Sticky date + continuous divider rows | Date group + continuous divider rows | No card-per-record treatment; day and status remain quickly scannable |
| Dark theme | Neutral near-black surfaces, light green accent | Matching neutral dark surfaces | Primary/secondary text remains readable |
| Accessibility | Existing 48dp targets and semantics preserved | Existing 44pt targets and labels preserved | Dynamic/large text falls back to two-row metrics |
| Business logic | Unchanged | Unchanged | Dose persistence, recurrence, reminders and entitlement contracts remain outside presentation redesign |

## Runtime checks

- Home: Due/Missed/Taken/Skipped filters remain toggleable.
- Home: missed recovery still opens the overdue filter.
- Home: progress handles zero scheduled items without division errors.
- Add/Edit: save validation and recurrence controls behave as before.
- Add/Edit: large text and narrow phones do not clip the four Home metrics.
- Light/dark: semantic Due/Missed/Taken/Skipped colors remain distinguishable.
- Tablet/wide: content expands without creating extra dashboard columns or card grids.
- History: 7/30-day completion remains the dominant signal and details affordance still opens analytics details.
- History: search and Taken/Skipped filters preserve existing query behavior and visible record counts update with filters.
- History: loading/error/empty/no-match states remain distinct and actionable where previously supported.
- History: date grouping and status semantics remain intact without a card around every record.
