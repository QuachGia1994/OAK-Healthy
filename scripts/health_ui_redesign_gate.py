#!/usr/bin/env python3
"""Validate the UI-R1/UI-R2 health-wellness presentation contract."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ANDROID_TOKENS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakTokens.kt"
ANDROID_COLORS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakColors.kt"
ANDROID_NAV = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/navigation/AppNavigation.kt"
ANDROID_HOME = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeScreen.kt"
ANDROID_HISTORY = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt"
ANDROID_ADD = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/add_supplement/AddSupplementScreen.kt"
ANDROID_EN = ROOT / "Android/app/src/main/res/values/strings.xml"
ANDROID_VI = ROOT / "Android/app/src/main/res/values-vi/strings.xml"
IOS_TOKENS = ROOT / "iOS/Views/OAKDesignTokens.swift"
IOS_PALETTE = ROOT / "iOS/Views/OAKCard.swift"
IOS_APP = ROOT / "iOS/SupplementTrackerApp.swift"
IOS_HOME = ROOT / "iOS/Views/HomeView.swift"
IOS_HISTORY = ROOT / "iOS/Views/HistoryView.swift"
IOS_ADD = ROOT / "iOS/Views/AddSupplementView.swift"
IOS_LOCALIZATION = ROOT / "iOS/Services/LocalizationService.swift"
DESIGN_DOC = ROOT / "docs/design/HEALTH_WELLNESS_UI_SYSTEM.md"
QA_MATRIX = ROOT / "docs/qa/HEALTH_UI_REDESIGN_MATRIX.md"

REQUIRED = (
    ANDROID_TOKENS, ANDROID_COLORS, ANDROID_NAV, ANDROID_HOME, ANDROID_HISTORY,
    ANDROID_ADD, ANDROID_EN, ANDROID_VI, IOS_TOKENS, IOS_PALETTE, IOS_APP,
    IOS_HOME, IOS_HISTORY, IOS_ADD, IOS_LOCALIZATION, DESIGN_DOC, QA_MATRIX,
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    for path in REQUIRED:
        require(path.exists(), f"Missing UI redesign file: {path.relative_to(ROOT)}")

    android_tokens = read(ANDROID_TOKENS)
    android_colors = read(ANDROID_COLORS)
    android_nav = read(ANDROID_NAV)
    android_home = read(ANDROID_HOME)
    android_history = read(ANDROID_HISTORY)
    android_add = read(ANDROID_ADD)
    ios_tokens = read(IOS_TOKENS)
    ios_palette = read(IOS_PALETTE)
    ios_app = read(IOS_APP)
    ios_home = read(IOS_HOME)
    ios_history = read(IOS_HISTORY)
    ios_add = read(IOS_ADD)

    for token in ("OakSpacing", "OakRadius", "OakElevation", "OakTypeScale"):
        require(token in android_tokens, f"Android token family missing: {token}")
    for token in ("OAKSpacing", "OAKRadius", "OAKTypeScale"):
        require(token in ios_tokens, f"iOS token family missing: {token}")

    require("0xFF1F6B4D" in android_colors and "0xFF0B0F0D" in android_colors, "Android wellness palette missing")
    require("0.1216" in ios_palette and "0.0431" in ios_palette, "iOS wellness palette missing")

    require("HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)" in android_nav, "Android flat shell hairline missing")
    require("shadow(4.dp" not in android_nav, "Android navigation reverted to floating shadow shell")
    require("toolbarBackground(.ultraThinMaterial, for: .tabBar)" not in ios_app, "iOS tab bar reverted to material shell")
    require("OAKPalette.surface(for: colorScheme)" in ios_app, "iOS solid tab-bar surface missing")

    require("HomeDashboardHeader" in android_home and "LinearProgressIndicator" in android_home, "Android Home primary progress insight missing")
    require("TodayMetricButton" in android_home and "TodayStripButton" not in android_home, "Android Home still uses metric micro-cards")
    require("HomeSummaryPanel" in ios_home and "ProgressView(value: progress)" in ios_home, "iOS Home primary progress insight missing")
    require("HomeMetricButton" in ios_home and "HomeFilterButton" not in ios_home, "iOS Home still uses metric micro-cards")

    android_record = android_history.split("private fun HistoryRecordItem", 1)[-1].split("private fun historySectionTitle", 1)[0]
    ios_record = ios_history.split("private struct HistoryRow", 1)[-1].split("private enum HistoryFilter", 1)[0]
    require("HistoryCompletionRing" in android_history and "HistoryFilterSegment" in android_history, "Android History scanability contract missing")
    require("OakCard(" not in android_record, "Android History reverted to card-per-record timeline")
    require("HistoryCompletionRing" in ios_history, "iOS History completion/trend hero missing")
    require(".oakCardStyle(.glass" not in ios_record, "iOS History reverted to card-per-record timeline")
    require("Color.blue.gradient" not in ios_history, "iOS History reintroduced unrelated blue chart styling")
    require("toolbarBackground(.ultraThinMaterial" not in ios_history, "iOS History reintroduced material navigation")

    require("SupplementFormSurface" in android_add and "SupplementSectionCard" not in android_add, "Android tracking flow still uses nested section cards")
    require("item(\"form\")" in android_add, "Android tracking form is not a single surface")
    require(".oakCardStyle(.paper, cornerRadius: OAKRadius.lg)" in ios_add, "iOS tracking form surface missing")
    require(".oakCardStyle(.glass, cornerRadius: 22)" not in ios_add, "iOS tracking flow still uses three section cards")
    require("OAKPalette.accent.gradient" not in ios_add, "iOS tracking intro retained decorative gradient")

    for resource in (read(ANDROID_EN), read(ANDROID_VI), read(IOS_LOCALIZATION)):
        require("home_summary_recorded_format" in resource, "Home summary localization parity missing")
        require("history_completion_title" in resource, "History completion localization parity missing")
        require("history_results_count_format" in resource, "History result-count localization parity missing")

    require("Visual debt audit" in read(DESIGN_DOC), "Design debt audit missing")
    require("Tracking form" in read(QA_MATRIX), "UI-R1 QA matrix missing tracking coverage")
    require("History timeline" in read(QA_MATRIX), "UI-R2 QA matrix missing History coverage")

    print("Health UI redesign gate passed: UI-R1 foundation and UI-R2 History/Trends scanability are aligned cross-platform.")


if __name__ == "__main__":
    main()
