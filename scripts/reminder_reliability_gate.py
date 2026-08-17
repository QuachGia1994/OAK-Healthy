#!/usr/bin/env python3
"""Fail-closed repository checks for P9.2 reminder recovery wiring."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class GateError(RuntimeError):
    """Raised when reminder recovery loses a required safety invariant."""


def _read(relative: str) -> str:
    path = ROOT / relative
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        raise GateError(f"Cannot read {relative}: {error}") from error


def _require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise GateError(f"{label} is missing {token}")


def _function_slice(text: str, start: str, end: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        raise GateError(f"Cannot find recovery function: {start}")
    end_index = text.find(end, start_index + len(start))
    if end_index < 0:
        raise GateError(f"Cannot find recovery function boundary: {end}")
    return text[start_index:end_index]


def _assert_no_intake_mutation(text: str, label: str) -> None:
    forbidden = ("IntakeRecord", "RecordDose", "persistDose", "TAKEN", "SKIPPED", "taken", "skipped")
    hits = [token for token in forbidden if token in text]
    if hits:
        raise GateError(f"{label} may mutate intake history: {', '.join(hits)}")


def validate_repository() -> None:
    manifest = _read("Android/app/src/main/AndroidManifest.xml")
    receiver = _read("Android/app/src/main/java/com/example/supplementtracker/receiver/AlarmRescheduleReceiver.kt")
    android_policy = _read("Android/app/src/main/java/com/example/supplementtracker/service/NotificationRecovery.kt")
    android_engine = _read("Android/app/src/main/java/com/example/supplementtracker/service/NotificationScheduleEngine.kt")
    android_vm = _read("Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeViewModel.kt")
    ios_policy = _read("iOS/Services/NotificationRecovery.swift")
    ios_service = _read("iOS/Services/NotificationService.swift")
    ios_lifecycle = _read("iOS/Services/NotificationScheduleLifecycleCoordinator.swift")
    ios_app = _read("iOS/SupplementTrackerApp.swift")

    for action in (
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.TIME_SET",
    ):
        _require(manifest, action, "Android recovery receiver")
    _require(receiver, "oakLastNotificationRecoveryTrigger", "Android recovery diagnostics")
    _require(android_policy, "NotificationRecoveryPolicy", "Android recovery policy")
    _require(android_engine, "reconcileIfNeeded", "Android schedule reconciliation")

    _require(ios_policy, "NotificationRecoveryPolicy", "iOS recovery policy")
    _require(ios_service, "reconcileSchedulesIfNeeded", "iOS schedule reconciliation")
    _require(ios_service, "futureIdentifiers", "iOS stale-shadow protection")
    _require(ios_app, ".NSSystemTimeZoneDidChange", "iOS timezone recovery")
    _require(ios_app, ".NSSystemClockDidChange", "iOS clock recovery")
    _require(ios_app, "notificationLifecycle.reconcileIfEnabled", "iOS foreground recovery")
    _require(ios_lifecycle, "reconcileSchedulesIfNeeded", "iOS lifecycle recovery boundary")

    android_recovery = _function_slice(
        android_vm,
        "private suspend fun autoReconcileNotificationSchedules()",
        "private fun recordNotificationRebuild()",
    )
    ios_recovery = _function_slice(
        ios_lifecycle,
        "func reconcileIfEnabled(",
        "private func activeSupplements",
    )
    ios_service_entry = _function_slice(
        ios_service,
        "public func reconcileSchedulesIfNeeded(",
        "// MARK: - Private Helpers",
    )
    ios_service_helpers = _function_slice(
        ios_service,
        "private func notificationEnvironmentChanged(settings: UNNotificationSettings)",
        "private func requestIdentifier(",
    )
    _assert_no_intake_mutation(android_policy + android_engine + android_recovery + receiver, "Android recovery path")
    _assert_no_intake_mutation(
        ios_policy + ios_service_entry + ios_service_helpers + ios_recovery,
        "iOS recovery path",
    )


def main() -> int:
    try:
        validate_repository()
    except GateError as error:
        print(f"Reminder reliability gate failed: {error}")
        return 1
    print("Reminder reliability gate passed: Android lifecycle recovery + iOS foreground/time-change reconciliation; no intake mutation hooks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
