#!/usr/bin/env python3
"""Validate the Stage B final UI/UX release-candidate contract."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FILES = {
    "android_card": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakCard.kt",
    "android_tokens": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakTokens.kt",
    "android_colors": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakColors.kt",
    "android_splash": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/splash/SplashScreen.kt",
    "android_stack": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/MyStackListScreen.kt",
    "android_settings": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/SettingsComponents.kt",
    "android_coach": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/coach/CoachOverviewScreen.kt",
    "android_plan": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/monetization/PlanAccessScreen.kt",
    "android_demo": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/demo/DemoPreviewScreen.kt",
    "ios_card": ROOT / "iOS/Views/OAKCard.swift",
    "ios_loading": ROOT / "iOS/Views/LetterStormLogoView.swift",
    "ios_stack": ROOT / "iOS/Views/StackView.swift",
    "ios_settings": ROOT / "iOS/Views/SettingsView.swift",
    "ios_plan": ROOT / "iOS/Views/PlanAccessView.swift",
    "ios_demo": ROOT / "iOS/Views/DemoPreviewView.swift",
    "matrix": ROOT / "docs/qa/UI_RELEASE_CANDIDATE_MATRIX.md",
    "screenshots": ROOT / "docs/design/UI_SCREENSHOT_PACK.md",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def validate_dead_styles(data: dict[str, str]) -> None:
    require("Glass" not in data["android_card"], "Android legacy Glass card variant still exists")
    require("OakElevation" not in data["android_tokens"], "Android unused elevation token family still exists")
    for token in ("ChartBar", "BadgeStart", "BadgeEnd", "StreakBorder", "InsightCardStart", "InsightCardEnd", "SkippedRecord", "SkippedBg"):
        require(token not in data["android_colors"], f"Android dead presentation token still exists: {token}")
    require("case glass" not in data["ios_card"], "iOS legacy glass card variant still exists")
    require("heroStart" not in data["ios_card"] and "heroEnd" not in data["ios_card"], "iOS dead hero gradient tokens still exist")


def validate_motion(data: dict[str, str]) -> None:
    splash = data["android_splash"]
    require("rememberOakReduceMotion" in splash, "Android splash does not respect reduced motion")
    require("Animatable" in splash, "Android splash finite progress transition missing")
    for forbidden in ("rememberInfiniteTransition", "infiniteRepeatable", "Brush.radialGradient"):
        require(forbidden not in splash, f"Android splash retained decorative/infinite motion: {forbidden}")
    require("AnimatedVisibility" not in data["android_settings"], "Android Settings retained decorative disclosure animation")
    loading = data["ios_loading"]
    require("accessibilityReduceMotion" in loading, "iOS launch branding Reduce Motion support missing")
    require("repeatForever" not in loading, "iOS launch branding retained infinite animation")


def validate_adaptive_layout(data: dict[str, str]) -> None:
    for key in ("android_stack", "android_settings", "android_coach", "android_plan"):
        require("rememberOakAdaptiveLayout" in data[key], f"Android adaptive layout missing from {key}")
    require("adaptive.stackMetrics" in data["android_stack"], "Android Stack large-text fallback missing")
    require("adaptive.stackMetrics" in data["android_plan"], "Android Plan Access large-text fallback missing")
    require("adaptive.stackMetrics" in data["android_coach"], "Android Coach large-text fallback missing")

    require("dynamicTypeSize" in data["ios_stack"] and "OAKResponsiveMetricLayout" in data["ios_stack"], "iOS Stack Dynamic Type fallback missing")
    require("dynamicTypeSize" in data["ios_settings"] and ".pickerStyle(.menu)" in data["ios_settings"], "iOS Settings accessibility theme-picker fallback missing")
    require("dynamicTypeSize" in data["ios_plan"], "iOS Plan Access Dynamic Type fallback missing")
    require("dynamicTypeSize" in data["ios_demo"] and "OAKResponsiveMetricLayout" in data["ios_demo"], "iOS Demo Preview accessibility fallback missing")


def validate_demo_pack(data: dict[str, str]) -> None:
    android_demo = data["android_demo"]
    require("demoRoutines" in android_demo and "DemoRoutineSurface" in android_demo, "Android deterministic continuous demo routine surface missing")
    require("import androidx.compose.material3.Card" not in android_demo, "Android demo reverted to card-per-row Material Card")
    ios_demo = data["ios_demo"]
    require("routineSurface" in ios_demo and "DemoRoutine" in ios_demo, "iOS deterministic demo routine surface missing")
    for key in ("demo_preview_privacy_badge", "demo_preview_presentation_note"):
        require(key in android_demo, f"Android synthetic demo privacy contract missing: {key}")
        require(key in ios_demo, f"iOS synthetic demo privacy contract missing: {key}")


def validate_docs(data: dict[str, str]) -> None:
    matrix = data["matrix"]
    for phrase in (
        "Cross-screen consistency",
        "Accessibility and device stress",
        "Motion / Reduce Motion",
        "Render/performance and dead presentation cleanup",
        "Screenshot/demo readiness",
        "Final gate",
    ):
        require(phrase in matrix, f"Stage B RC matrix missing section: {phrase}")
    screenshots = data["screenshots"]
    for screen in ("Home", "Stack", "History", "Coach", "Sync health", "Settings"):
        require(screen in screenshots, f"Screenshot pack missing required story: {screen}")
    require("no database writes" in screenshots and "no entitlement override" in screenshots, "Screenshot pack synthetic-data safety contract incomplete")


def main() -> None:
    for path in FILES.values():
        require(path.exists(), f"Missing Stage B UI RC file: {path.relative_to(ROOT)}")
    data = {name: read(path) for name, path in FILES.items()}
    validate_dead_styles(data)
    validate_motion(data)
    validate_adaptive_layout(data)
    validate_demo_pack(data)
    validate_docs(data)
    print("Stage B UI RC gate passed: final consistency, adaptive, motion, dead-style and synthetic screenshot contracts are aligned cross-platform.")


if __name__ == "__main__":
    main()
