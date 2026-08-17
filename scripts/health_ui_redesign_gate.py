#!/usr/bin/env python3
"""Validate OAK Healthy wellness UI foundation through Stage A."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation"
I = ROOT / "iOS/Views"

FILES = {
    "android_tokens": A / "designsystem/OakTokens.kt",
    "android_colors": A / "designsystem/OakColors.kt",
    "android_nav": A / "navigation/AppNavigation.kt",
    "android_home": A / "home/HomeScreen.kt",
    "android_history": A / "home/HistoryScreen.kt",
    "android_stack": A / "home/MyStackListScreen.kt",
    "android_settings": A / "home/SettingsComponents.kt",
    "android_notification": A / "home/NotificationCheckScreen.kt",
    "android_add": A / "add_supplement/AddSupplementScreen.kt",
    "android_coach": A / "coach/CoachOverviewScreen.kt",
    "android_plan": A / "monetization/PlanAccessScreen.kt",
    "android_onboarding": A / "onboarding/OnboardingScreen.kt",
    "android_sync": A / "sync/SyncCenterScreen.kt",
    "android_en": ROOT / "Android/app/src/main/res/values/strings.xml",
    "android_vi": ROOT / "Android/app/src/main/res/values-vi/strings.xml",
    "ios_tokens": I / "OAKDesignTokens.swift",
    "ios_palette": I / "OAKCard.swift",
    "ios_home": I / "HomeView.swift",
    "ios_history": I / "HistoryView.swift",
    "ios_stack": I / "StackView.swift",
    "ios_settings": I / "SettingsView.swift",
    "ios_client": I / "ClientEditorSheet.swift",
    "ios_notification": I / "NotificationDebugView.swift",
    "ios_add": I / "AddSupplementView.swift",
    "ios_coach": I / "CoachOverviewView.swift",
    "ios_coach_detail": I / "CoachClientDetailView.swift",
    "ios_plan": I / "PlanAccessView.swift",
    "ios_onboarding": I / "OnboardingView.swift",
    "ios_sync": I / "SyncCenterView.swift",
    "ios_app": ROOT / "iOS/SupplementTrackerApp.swift",
    "ios_localization": ROOT / "iOS/Services/LocalizationService.swift",
    "design_doc": ROOT / "docs/design/HEALTH_WELLNESS_UI_SYSTEM.md",
    "qa_matrix": ROOT / "docs/qa/HEALTH_UI_REDESIGN_MATRIX.md",
}


def read(name: str) -> str:
    path = FILES[name]
    if not path.exists():
        raise SystemExit(f"Missing UI redesign file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def validate_foundation(data: dict[str, str]) -> None:
    for token in ("OakSpacing", "OakRadius", "OakElevation", "OakTypeScale"):
        require(token in data["android_tokens"], f"Android token family missing: {token}")
    for token in ("OAKSpacing", "OAKRadius", "OAKTypeScale"):
        require(token in data["ios_tokens"], f"iOS token family missing: {token}")
    require("0xFF1F6B4D" in data["android_colors"] and "0xFF0B0F0D" in data["android_colors"], "Android wellness palette missing")
    require("0.1216" in data["ios_palette"] and "0.0431" in data["ios_palette"], "iOS wellness palette missing")
    require("shadow(4.dp" not in data["android_nav"], "Android shell reverted to floating shadow treatment")
    require("toolbarBackground(.ultraThinMaterial, for: .tabBar)" not in data["ios_app"], "iOS tab bar reverted to material shell")


def validate_core_flows(data: dict[str, str]) -> None:
    require("HomeDashboardHeader" in data["android_home"] and "LinearProgressIndicator" in data["android_home"], "Android Home primary insight missing")
    require("HomeSummaryPanel" in data["ios_home"] and "ProgressView(value: progress)" in data["ios_home"], "iOS Home primary insight missing")
    require(".oakCardStyle(.glass" not in data["ios_home"], "iOS Home retained glass surfaces")
    require("ultraThinMaterial" not in data["ios_home"], "iOS Home retained material surfaces")

    android_record = data["android_history"].split("private fun HistoryRecordItem", 1)[-1].split("private fun historySectionTitle", 1)[0]
    ios_record = data["ios_history"].split("private struct HistoryRow", 1)[-1].split("private enum HistoryFilter", 1)[0]
    require("HistoryCompletionRing" in data["android_history"] and "HistoryFilterSegment" in data["android_history"], "Android History scanability contract missing")
    require("OakCard(" not in android_record, "Android History reverted to card-per-record")
    require("HistoryCompletionRing" in data["ios_history"] and ".oakCardStyle(.glass" not in ios_record, "iOS History scanability contract missing")

    require("SupplementFormSurface" in data["android_add"] and "SupplementSectionCard" not in data["android_add"], "Android tracking flow reverted to nested section cards")
    require(".oakCardStyle(.paper, cornerRadius: OAKRadius.lg)" in data["ios_add"], "iOS continuous tracking form missing")


def validate_stage_a(data: dict[str, str]) -> None:
    android_stack_row = data["android_stack"].split("private fun StackSupplementRow", 1)[-1].split("private fun StackEmptyState", 1)[0]
    require("StackOverviewSurface" in data["android_stack"] and "OakCard(" not in android_stack_row, "Android Stack still uses card-per-supplement")
    require(".oakCardStyle(.glass" not in data["ios_stack"] and ".listRowSeparator(.visible)" in data["ios_stack"], "iOS Stack still uses glass/card-per-row treatment")

    require("RoundedCornerShape(32.dp)" not in data["android_settings"], "Android Settings retained oversized legacy radius")
    require("ultraThinMaterial" not in data["ios_settings"] and "glassRowBackground" not in data["ios_settings"], "iOS Settings retained material/glass rows")
    require("thinMaterial" not in data["ios_client"] and "LinearGradient" not in data["ios_client"], "iOS client editor retained material/gradient decoration")

    require("CLIENT,\n    REMINDERS,\n    DONE" in data["android_onboarding"], "Android onboarding was not consolidated to three steps")
    require("ExactAlarmStep" in data["android_onboarding"] and "BatteryStep" in data["android_onboarding"], "Android onboarding lost reliability actions")
    require("sparkles" not in data["ios_onboarding"] and ".oakCardStyle(.glass" not in data["ios_onboarding"], "iOS onboarding retained decorative/glass treatment")

    require("CoachSummaryCard(summary)" in data["android_coach"] and "import androidx.compose.material3.Card" not in data["android_coach"], "Android Coach still fragments reports into Material cards")
    require(".oakCardStyle(.paper, cornerRadius: OAKRadius.lg)" in data["ios_coach"], "iOS Coach primary workspace surface missing")
    require("coach_detail_comparison_title" in data["ios_coach_detail"] and "listSectionSpacing" in data["ios_coach_detail"], "iOS Coach detail hierarchy missing")

    require("CurrentPlanHero" in data["android_plan"] and "PlanComparisonSurface" in data["android_plan"] and "StorePurchaseSurface" in data["android_plan"], "Android plan hierarchy missing")
    require("PlanCard(" not in data["android_plan"] and "ElevatedCard" not in data["android_plan"], "Android paywall reverted to card-per-plan/product")
    require("currentPlanHero" in data["ios_plan"] and "planComparisonSurface" in data["ios_plan"] and "storePurchaseSurface" in data["ios_plan"], "iOS plan hierarchy missing")

    require("isLogsVisible" in data["android_sync"] and "if (isLogsVisible)" in data["android_sync"], "Android Sync logs are not progressive disclosure")
    require("isLogsVisible" in data["ios_sync"] and "syncRowBackground" in data["ios_sync"] and "glassRowBackground" not in data["ios_sync"], "iOS Sync progressive disclosure/solid surfaces missing")

    require("isTechnicalDetailsVisible" in data["android_notification"] and "ElevatedCard" not in data["android_notification"], "Android notification diagnostics still expose/card raw details")
    require("DisclosureGroup" in data["ios_notification"] and "isTechnicalDetailsVisible" in data["ios_notification"], "iOS notification diagnostics are not progressive disclosure")

    safe_boot = data["ios_app"].split("private struct SafeBootView", 1)[-1].split("private struct SafeModeView", 1)[0]
    safe_mode = data["ios_app"].split("private struct SafeModeView", 1)[-1].split("private struct MainTabView", 1)[0]
    require("LinearGradient" not in safe_boot and "oakBackground()" in safe_boot, "iOS Safe Boot retained a separate gradient visual language")
    require("DisclosureGroup" in safe_mode and "oakCardStyle(.paper" in safe_mode, "iOS Safe Mode recovery/debug hierarchy missing")

    for name in ("ios_stack", "ios_settings", "ios_client", "ios_onboarding", "ios_coach", "ios_coach_detail", "ios_plan", "ios_sync", "ios_notification"):
        require(".oakCardStyle(.glass" not in data[name], f"Stage A view retained glass surface: {name}")


def validate_localization_and_docs(data: dict[str, str]) -> None:
    for name in ("android_en", "android_vi", "ios_localization"):
        resource = data[name]
        for key in ("home_summary_recorded_format", "history_completion_title", "history_results_count_format", "billing_store_products"):
            require(key in resource, f"Localization parity missing {key} in {name}")
    require("Stage A" in data["design_doc"], "Stage A design-system checkpoint missing")
    require("Complete product UI" in data["qa_matrix"], "Stage A QA matrix coverage missing")


def main() -> None:
    data = {name: read(name) for name in FILES}
    validate_foundation(data)
    validate_core_flows(data)
    validate_stage_a(data)
    validate_localization_and_docs(data)
    print("Health UI redesign gate passed: UI-R1/UI-R2 and Stage A complete-product UI contracts are aligned cross-platform.")


if __name__ == "__main__":
    main()
