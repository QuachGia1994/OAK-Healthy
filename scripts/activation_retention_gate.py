from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ANDROID = ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/ActivationRetention.kt"
IOS = ROOT / "iOS/Services/ActivationRetention.swift"
ANDROID_HOME = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeScreen.kt"
IOS_HOME = ROOT / "iOS/Views/HomeView.swift"
ANDROID_DIAGNOSTICS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/DiagnosticsReporter.kt"
IOS_DIAGNOSTICS = ROOT / "iOS/Services/DiagnosticsReporter.swift"
REQUIRED = [ANDROID, IOS, ANDROID_HOME, IOS_HOME, ANDROID_DIAGNOSTICS, IOS_DIAGNOSTICS]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    for path in REQUIRED:
        require(path.exists(), f"Missing P9.5 file: {path.relative_to(ROOT)}")

    android = ANDROID.read_text(encoding="utf-8")
    ios = IOS.read_text(encoding="utf-8")
    android_home = ANDROID_HOME.read_text(encoding="utf-8")
    ios_home = IOS_HOME.read_text(encoding="utf-8")
    diagnostics = ANDROID_DIAGNOSTICS.read_text(encoding="utf-8") + IOS_DIAGNOSTICS.read_text(encoding="utf-8")

    for marker in ["CLIENT_READY", "ROUTINE_READY", "FIRST_ACTION", "REMINDER_READY"]:
        require(marker in android, f"Android activation milestone missing: {marker}")
    for marker in ["clientReady", "routineReady", "firstAction", "reminderReady"]:
        require(marker in ios, f"iOS activation milestone missing: {marker}")

    require("firstValueReached" in android and "firstValueReached" in ios, "First-value completion policy missing")
    require("coreMilestones" in android and "coreMilestones" in ios, "Core milestone list missing")
    require("activation_milestone" in diagnostics, "Aggregate activation event is not allowlisted")
    require("\"milestone\"" in diagnostics and "\"state\"" in diagnostics, "Aggregate activation fields missing")

    for forbidden in ['"client_id"', '"supplement"', '"dose"', '"note"', '"health"']:
        require(forbidden not in android.lower(), f"Android activation store contains forbidden payload field: {forbidden}")
        require(forbidden not in ios.lower(), f"iOS activation store contains forbidden payload field: {forbidden}")

    for marker in ["activation_first_value_title", "activation_pressure_free_hint", "activation_no_routine_title"]:
        require(marker in android_home and marker in ios_home, f"Cross-platform activation UI missing: {marker}")
    require("recovery_pressure_free_hint" in android_home and "recovery_pressure_free_hint" in ios_home, "Pressure-free recovery copy missing")
    require("home_rhythm_days_format" in android_home and "home_rhythm_days_format" in ios_home, "Pressure-free rhythm display missing")

    print("Activation/retention gate passed: first-value milestones, actionable recovery, pressure-free progress, and aggregate-only telemetry are wired cross-platform.")


if __name__ == "__main__":
    main()
