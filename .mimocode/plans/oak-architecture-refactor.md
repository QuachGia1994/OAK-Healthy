# OAK Healthy — Architecture Refactor Execution Plan

## Objective
Reduce `HomeViewModel` orchestration and business-logic coupling without changing product behavior, protocol/schema, or platform framework.

## Phrase 1 — Dashboard domain extraction [IMPLEMENTED]
- Keep dashboard calculation in `domain/usecase/CalculateHomeDashboardUseCase`.
- Inject one use-case instance into `HomeViewModel`.
- Remove the duplicated dashboard calculation helpers from `HomeViewModel`.
- Keep only UI mapping from domain result -> `HomeUiState`.
- Build/test authority: GitHub Actions.

## Phrase 2 — Backup import extraction [IMPLEMENTED]
- Create `ImportBackupUseCase` in `domain/usecase`.
- Move JSON compatibility decode, DTO -> domain mapping, record filtering, and atomic repository import out of `HomeViewModel`.
- Keep payload decompression, UI messages, refresh, notification reschedule, and import timestamp side effects in `HomeViewModel`.
- Build/test authority: GitHub Actions.

## Phrase 3 — Dose mutation boundary [IMPLEMENTED]
- Create `RecordDoseUseCase` for taken/skipped dose persistence and supplement last-taken update.
- Move dose identity/status persistence out of `HomeViewModel`.
- Keep notification reschedule + auto-sync trigger as application side effects at the ViewModel boundary.
- Build/test authority: GitHub Actions.

## Phrase 4 — Notification orchestration boundary [IMPLEMENTED]
- Create `NotificationScheduleEngine` in `service/` because the boundary owns `NotificationSchedulerImpl` infrastructure.
- Move client/supplement loading and scheduler invocation out of `HomeViewModel`.
- Keep `HomeViewModel` responsible only for triggering reschedule/clear operations.
- Build/test authority: GitHub Actions.

## Phrase 5 — Client/profile mutations [IMPLEMENTED]
- Extract client create/update/delete validation and persistence into `ClientProfileUseCase`.
- Use repository state as the domain source-of-truth for duplicate-name validation; keep `ActiveClientManager` out of the use case.
- Keep ViewModel responsible only for UI-facing coroutine launches.
- Build/test authority: GitHub Actions.

## Phrase 6 — Cleanup and dependency hygiene
- Remove dead imports/helpers created by the extractions.
- Prefer constructor-injected use cases over `new UseCase()` inside methods.
- Re-run architecture grep for direct infrastructure access from `HomeViewModel`.
- Final CI verification and diff review through GitHub Actions.

## Release gate
No commit until the active phrase and all completed phrases have green GitHub Actions build/test evidence. Runtime smoke remains a separate environment gate because `adb` is currently unavailable.
