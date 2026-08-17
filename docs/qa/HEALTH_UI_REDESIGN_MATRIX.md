# Health UI Redesign — UI-R1/UI-R2/Stage A QA Matrix

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
| Complete product UI — Stack | One overview + continuous supplement rows | One overview + continuous supplement rows | No card-per-supplement fragmentation; Add empty-state action remains available |
| Complete product UI — Coach | One workspace insight + continuous clients | One workspace insight + native continuous clients | Completion/trend is primary; same-client comparison and check-ins remain unchanged |
| Complete product UI — Settings/Profile | Flat sections, restrained radius | Solid list sections + flat editor | Branding does not obscure client actions; no material/gradient decoration |
| Complete product UI — Onboarding | Client → Reminders → Done | Client → Reminders → Done | Android exact-alarm/battery actions remain inside Reminders; no reliability capability removed |
| Complete product UI — Plan access | Current plan + comparison + store surface | Same hierarchy | Buy/restore/verification remain fail-closed; no plan/product card grid |
| Complete product UI — Sync/Recovery | Status first; logs/IDs/keys disclosed on demand | Status first; logs/IDs/keys disclosed on demand | Local data safety/retry semantics unchanged |
| Complete product UI — Notifications | One diagnostic summary + continuous alarm rows | One diagnostic summary + disclosure diagnostics | Raw diagnostics hidden by default; repair actions remain explicit |
| Complete product UI — Safe Mode | N/A dedicated surface | Paper recovery surface + collapsed debug tools | Import preview/apply/discard and destructive recovery stay explicit |
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
- Stage A: Stack, Coach, Settings/Profile, Onboarding, Plan Access, Sync/Recovery, Notifications and Safe Mode all use the shared wellness hierarchy without glass/material fragmentation.
- Stage A: buy/restore, sync conflict handling, import/recovery, notification repair and dose persistence behavior must match pre-redesign tests.
- Stage A: technical identifiers, keys, operation logs and raw diagnostic payloads are hidden until explicitly expanded.
