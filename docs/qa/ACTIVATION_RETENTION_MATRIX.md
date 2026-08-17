# P9.5 Activation & Retention 2.0 QA Matrix

This matrix covers first-value guidance and retention surfaces without turning routine adherence into a medical or competitive score.

| Scenario | Android | iOS | Expected |
|---|---|---|---|
| Existing profile is present | Home reconcile | Home reconcile | `client_ready` is backfilled locally once |
| First routine is created | AddSupplementViewModel | AddSupplementViewModel | `routine_ready` is persisted after successful create only |
| Existing routine predates P9.5 | Home reconcile | Home reconcile | routine milestone is backfilled without editing user data |
| First Taken/Skipped action | HomeViewModel | HomeViewModel | `first_action` is persisted after the intake mutation succeeds |
| Existing history predates P9.5 | 120-day record window / last-taken fallback | local intake relationship / last-taken fallback | first-action milestone is backfilled when evidence exists |
| Notifications are enabled and authorized | onboarding permission reconciliation | onboarding authorization reconciliation | optional `reminder_ready` is persisted; it does not block first value |
| No routine exists | actionable empty state | actionable empty state | user gets Add routine CTA |
| Overdue filter is empty | actionable empty state | actionable empty state | user gets Show all today CTA |
| Missed items exist | recovery card | recovery card | Review CTA is available with no score-loss language |
| Routine rhythm is zero | fresh-start copy | fresh-start copy | no broken-streak or failure language |
| Routine rhythm is positive | recent-rhythm copy | recent-rhythm copy | recent days are informational, not a score to protect |
| Anonymous diagnostics disabled | milestone store | milestone store | local milestones persist; Firebase event is not emitted |
| Anonymous diagnostics enabled | sanitizer + Firebase Analytics | sanitizer + Firebase Analytics | only `activation_milestone` with `milestone/state` reaches analytics |
| Caller supplies client/supplement/dose/note fields | privacy sanitizer | privacy sanitizer | sensitive/unknown fields are dropped |

## Invariants

- First value is profile + routine + first routine action; reminder permission is optional.
- No activation or retention path auto-writes Taken or Skipped.
- No analytics payload contains client identity, supplement name, dose, routine note, sync identifier, or health payload.
- Missing a day does not reduce a score, erase prior history, or trigger punitive copy.
- Existing users are reconciled from local evidence instead of being forced through a new-user checklist.
