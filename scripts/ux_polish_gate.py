from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ANDROID_FEEDBACK = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakFeedbackCard.kt"
ANDROID_HISTORY = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt"
ANDROID_HISTORY_VM = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryViewModel.kt"
ANDROID_COACH = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/coach/CoachOverviewScreen.kt"
ANDROID_SETTINGS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/SettingsComponents.kt"
ANDROID_SYNC = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/sync/SyncCenterScreen.kt"
ANDROID_EN = ROOT / "Android/app/src/main/res/values/strings.xml"
ANDROID_VI = ROOT / "Android/app/src/main/res/values-vi/strings.xml"
IOS_FEEDBACK = ROOT / "iOS/Views/OAKFeedbackView.swift"
IOS_HISTORY = ROOT / "iOS/Views/HistoryView.swift"
IOS_COACH = ROOT / "iOS/Views/CoachOverviewView.swift"
IOS_SETTINGS = ROOT / "iOS/Views/SettingsView.swift"
IOS_SYNC = ROOT / "iOS/Views/SyncCenterView.swift"
IOS_LOCALIZATION = ROOT / "iOS/Services/LocalizationService.swift"
QA_MATRIX = ROOT / "docs/qa/UX_POLISH_MATRIX.md"

REQUIRED = [
    ANDROID_FEEDBACK,
    ANDROID_HISTORY,
    ANDROID_HISTORY_VM,
    ANDROID_COACH,
    ANDROID_SETTINGS,
    ANDROID_SYNC,
    ANDROID_EN,
    ANDROID_VI,
    IOS_FEEDBACK,
    IOS_HISTORY,
    IOS_COACH,
    IOS_SETTINGS,
    IOS_SYNC,
    IOS_LOCALIZATION,
    QA_MATRIX,
]

FEEDBACK_KEYS = [
    "history_empty_title",
    "history_empty_body",
    "history_no_matches_title",
    "history_no_matches_body",
    "history_load_failed_title",
    "history_load_failed_body",
    "coach_load_failed_title",
    "coach_empty_title",
    "retry",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    for path in REQUIRED:
        require(path.exists(), f"Missing P11.1 file: {path.relative_to(ROOT)}")

    android_history = ANDROID_HISTORY.read_text(encoding="utf-8")
    android_vm = ANDROID_HISTORY_VM.read_text(encoding="utf-8")
    android_coach = ANDROID_COACH.read_text(encoding="utf-8")
    android_settings = ANDROID_SETTINGS.read_text(encoding="utf-8")
    android_sync = ANDROID_SYNC.read_text(encoding="utf-8")
    android_en = ANDROID_EN.read_text(encoding="utf-8")
    android_vi = ANDROID_VI.read_text(encoding="utf-8")
    ios_history = IOS_HISTORY.read_text(encoding="utf-8")
    ios_coach = IOS_COACH.read_text(encoding="utf-8")
    ios_settings = IOS_SETTINGS.read_text(encoding="utf-8")
    ios_sync = IOS_SYNC.read_text(encoding="utf-8")
    ios_localization = IOS_LOCALIZATION.read_text(encoding="utf-8")

    require("HistoryUiState.Error" in android_history, "Android History error state is not rendered")
    require("retryHistory" in android_history and "retryHistory" in android_vm, "Android History retry action missing")
    require("history_no_matches_title" in android_history, "Android History no-match feedback missing")
    require("onNavigateToSettings" in android_history and "HistoryUiState.NoClient" in android_history, "Android History no-client recovery missing")
    require("coach_load_failed_title" in android_coach and "onRetry" in android_coach, "Android Coach retry feedback missing")
    require("coach_empty_title" in android_coach, "Android Coach empty feedback missing")

    require("historyLoadFailed" in ios_history and "reloadVersion" in ios_history, "iOS History retry state missing")
    require("history_no_matches_title" in ios_history, "iOS History no-match feedback missing")
    require('"has_client"' in ios_history, "iOS History diagnostics should use aggregate client presence")
    require('"clientId"' not in ios_history, "iOS History diagnostics must not expose raw client identity")
    require("coach_empty_title" in ios_coach and "OAKFeedbackView" in ios_coach, "iOS Coach feedback state missing")

    for key in FEEDBACK_KEYS:
        require(f'name="{key}"' in android_en, f"Android EN feedback string missing: {key}")
        require(f'name="{key}"' in android_vi, f"Android VI feedback string missing: {key}")
        require(f'"{key}"' in ios_localization, f"iOS EN/VI feedback string missing: {key}")

    require(".weight(1f)" in android_settings and "maxLines = 1" in android_settings, "Android Settings long-row protection missing")
    require("layoutPriority" in ios_settings or "lineLimit" in ios_settings, "iOS Settings long-row protection missing")
    require("SyncRecoveryAction.SYNC_NOW" in android_sync, "Android Sync recovery action missing")
    require("syncHealthSummary" in ios_sync and "retryAction" in ios_sync, "iOS Sync recovery feedback missing")

    print("UX polish gate passed: shared feedback states, actionable recovery, long-row protection, and EN/VI parity are wired cross-platform.")


if __name__ == "__main__":
    main()
