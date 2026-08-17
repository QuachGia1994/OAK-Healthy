from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FILES = {
    "android_status": ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/CloudSyncStatusReader.kt",
    "android_notification": ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/NotificationDiagnosticsSource.kt",
    "android_coach": ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/CoachWorkspaceSource.kt",
    "android_home_vm": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeViewModel.kt",
    "android_notification_ui": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/NotificationCheckScreen.kt",
    "android_history_vm": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryViewModel.kt",
    "ios_status": ROOT / "iOS/Services/SyncCenterStatusReader.swift",
    "ios_log": ROOT / "iOS/Services/CloudSyncLogStore.swift",
    "ios_bootstrap": ROOT / "iOS/Services/AppBootstrapper.swift",
    "ios_import": ROOT / "iOS/Services/PendingImportRecoveryCoordinator.swift",
    "ios_notification": ROOT / "iOS/Services/NotificationScheduleLifecycleCoordinator.swift",
    "ios_sync_view": ROOT / "iOS/Views/SyncCenterView.swift",
    "ios_app": ROOT / "iOS/SupplementTrackerApp.swift",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def text(key: str) -> str:
    return FILES[key].read_text(encoding="utf-8")


def check_required_files() -> None:
    for name, path in FILES.items():
        require(path.exists(), f"Missing P10.1 boundary file: {name} -> {path.relative_to(ROOT)}")


def check_android_boundaries() -> None:
    home = text("android_home_vm")
    notification_ui = text("android_notification_ui")
    history = text("android_history_vm")
    require("CloudSyncStatusSource" in home, "Android HomeViewModel must depend on sync status interface")
    require("interface CloudSyncStatusSource" in text("android_status"), "Android sync status interface missing")
    for key in ["cloudSyncBytesDownloaded_", "cloudSyncConflictRemoteWins_", "cloudSyncJournal_"]:
        require(key not in home, f"Android HomeViewModel still owns sync persistence key: {key}")
    require("NotificationDiagnosticsSource" in notification_ui, "Notification UI must accept diagnostics interface")
    require("diagnosticsSourceFactory" in notification_ui, "Notification diagnostics factory injection missing")
    for marker in ["NotificationDebugStore", "NotificationSchedulerImpl(", "AlarmManager", "PowerManager"]:
        require(marker not in notification_ui, f"Notification UI still owns platform diagnostics: {marker}")
    require("CoachWorkspaceSourceProvider" in history, "HistoryViewModel must depend on Coach source provider")
    require("getAllRecordsByClient" not in history, "HistoryViewModel still loads Coach repository records directly")


def check_ios_boundaries() -> None:
    sync_view = text("ios_sync_view")
    app = text("ios_app")
    require("SyncCenterStatusReader.read" in sync_view, "iOS Sync Center must use typed status reader")
    require("CloudSyncLogStore" in sync_view, "iOS Sync Center must use CloudSyncLogStore")
    for marker in ["cloudSyncBytesDownloaded_", "JSONDecoder().decode([CloudSyncLogEntry]", "cloudSyncLog_\\("]:
        require(marker not in sync_view, f"iOS Sync Center still owns sync persistence: {marker}")
    for marker in ["AppBootstrapper()", "PendingImportRecoveryCoordinator", "NotificationScheduleLifecycleCoordinator"]:
        require(marker in app, f"iOS app orchestration missing boundary: {marker}")
    for marker in ["private func makeModelContainer", "private func createImportClient", "rescheduleImportedNotifications", "private func reconcileNotificationSchedules"]:
        require(marker not in app, f"iOS SwiftUI still owns extracted behavior: {marker}")


def main() -> None:
    check_required_files()
    check_android_boundaries()
    check_ios_boundaries()
    print("Architecture boundaries gate passed: sync, notification, backup/bootstrap and Coach ownership are separated from UI orchestration.")


if __name__ == "__main__":
    main()
