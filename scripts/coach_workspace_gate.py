from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/CoachWorkspace.kt",
    ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/coach/CoachOverviewScreen.kt",
    ROOT / "Android/app/src/test/java/com/example/supplementtracker/service/CoachWorkspaceTest.kt",
    ROOT / "iOS/Services/CoachWorkspace.swift",
    ROOT / "iOS/Views/CoachClientDetailView.swift",
    ROOT / "iOS/Tests/CoachWorkspaceTests.swift",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    for path in REQUIRED:
        require(path.exists(), f"Missing P9.4 file: {path.relative_to(ROOT)}")

    android = REQUIRED[0].read_text(encoding="utf-8")
    ios = REQUIRED[3].read_text(encoding="utf-8")
    combined = "\n".join(path.read_text(encoding="utf-8") for path in REQUIRED[:5])

    require("CoachReportDocument" in android and "CoachReportRenderer" in android, "Android report renderer contract missing")
    require("CoachReportDocument" in ios and "CoachReportRenderer" in ios, "iOS report renderer contract missing")
    require("previousStart" in android and "previousStart" in ios, "Previous-period comparison missing")
    require("MAX_NOTE_LENGTH = 500" in android, "Android note bound missing")
    require("maxNoteLength = 500" in ios, "iOS note bound missing")
    require("CoachCheckInStore" in android and "CoachCheckInStore" in ios, "Local check-in store missing")

    forbidden_calls = ["DebugReporter", "Analytics", "Crashlytics", "Firebase"]
    for marker in forbidden_calls:
        require(marker not in android and marker not in ios, f"Coach workspace must stay local-only: {marker}")

    forbidden_medical = ["diagnosis", "diagnose", "medical score", "disease risk", "clinical score"]
    lowered = combined.lower()
    for phrase in forbidden_medical:
        require(phrase not in lowered, f"Non-medical positioning violated: {phrase}")

    print("Coach workspace gate passed: own-period comparisons, local-only check-ins, report renderer contracts, and non-medical positioning.")


if __name__ == "__main__":
    main()
