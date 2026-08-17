from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ANDROID_COLORS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakColors.kt"
ANDROID_INTERACTION = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakInteraction.kt"
ANDROID_HOME = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeScreen.kt"
ANDROID_HISTORY = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt"
ANDROID_COACH = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/coach/CoachOverviewScreen.kt"
ANDROID_SYNC = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/sync/SyncCenterScreen.kt"
ANDROID_DEMO = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/demo/DemoPreviewScreen.kt"
ANDROID_EN = ROOT / "Android/app/src/main/res/values/strings.xml"
ANDROID_VI = ROOT / "Android/app/src/main/res/values-vi/strings.xml"
IOS_CARD = ROOT / "iOS/Views/OAKCard.swift"
IOS_INTERACTION = ROOT / "iOS/Views/OAKInteraction.swift"
IOS_HOME = ROOT / "iOS/Views/HomeView.swift"
IOS_HISTORY = ROOT / "iOS/Views/HistoryView.swift"
IOS_COACH = ROOT / "iOS/Views/CoachOverviewView.swift"
IOS_SYNC = ROOT / "iOS/Views/SyncCenterView.swift"
IOS_DEMO = ROOT / "iOS/Views/DemoPreviewView.swift"
IOS_LOCALIZATION = ROOT / "iOS/Services/LocalizationService.swift"
QA_MATRIX = ROOT / "docs/qa/P11_COMPLETION_MATRIX.md"
RC_DOC = ROOT / "docs/PRESTORE_RELEASE_CANDIDATE.md"

REQUIRED = (
    ANDROID_COLORS,
    ANDROID_INTERACTION,
    ANDROID_HOME,
    ANDROID_HISTORY,
    ANDROID_COACH,
    ANDROID_SYNC,
    ANDROID_DEMO,
    ANDROID_EN,
    ANDROID_VI,
    IOS_CARD,
    IOS_INTERACTION,
    IOS_HOME,
    IOS_HISTORY,
    IOS_COACH,
    IOS_SYNC,
    IOS_DEMO,
    IOS_LOCALIZATION,
    QA_MATRIX,
    RC_DOC,
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def relative_luminance(hex_color: str) -> float:
    rgb = [int(hex_color[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in rgb]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast_ratio(foreground: str, background: str) -> float:
    lighter = max(relative_luminance(foreground), relative_luminance(background))
    darker = min(relative_luminance(foreground), relative_luminance(background))
    return (lighter + 0.05) / (darker + 0.05)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def validate_dark_contrast(android_colors: str, ios_card: str) -> None:
    for token in ("0xFF1A211B", "0xFFD0D5CB", "0xFF56645A", "0xFF9AA59C"):
        require(token in android_colors, f"Android dark contrast token missing: {token}")
    for token in ("0.102", "0.816", "0.337"):
        require(token in ios_card, f"iOS dark contrast token missing: {token}")
    require(contrast_ratio("D0D5CB", "1A211B") >= 4.5, "Dark secondary text contrast below 4.5:1")
    require(contrast_ratio("F8F3E9", "111713") >= 7.0, "Dark primary text contrast below 7:1")
    require(contrast_ratio("56645A", "1A211B") >= 2.0, "Dark hairline contrast below 2:1")


def validate_interaction(android_interaction: str, android_home: str, ios_card: str, ios_home: str) -> None:
    require("minWidth = 48.dp" in android_interaction, "Android 48dp touch target missing")
    require("rememberOakAdaptiveLayout" in android_home, "Android responsive Home policy missing")
    require("rememberOakReduceMotion" in android_home, "Android reduced-motion policy missing")
    require("minWidth: 44" in ios_card and "minHeight: 44" in ios_card, "iOS 44pt touch target missing")
    require("ViewThatFits" in ios_home, "iOS responsive Home layout missing")
    require("accessibilityReduceMotion" in ios_home, "iOS Reduce Motion support missing")


def validate_product_surfaces(files: dict[str, str]) -> None:
    require("history_signal_window_format" in files["android_history"], "Android History signal missing")
    require("history_signal_window_format" in files["ios_history"], "iOS History signal missing")
    require("coach_attention_count_format" in files["android_coach"], "Android Coach attention signal missing")
    require("coach_attention_count_format" in files["ios_coach"], "iOS Coach attention signal missing")
    require("OAKResponsiveMetricLayout" in files["ios_coach"], "iOS Coach responsive metric layout missing")
    for key in ("sync_center_failure_safe_body", "isStatusDiagnosticsVisible"):
        require(key in files["android_sync"], f"Android Sync UX contract missing: {key}")
        require(key in files["ios_sync"], f"iOS Sync UX contract missing: {key}")
    require("ultraThinMaterial" not in files["ios_sync"], "iOS Sync rows must not use blur material after P11.9")
    for key in ("demo_preview_privacy_badge", "demo_preview_presentation_note"):
        require(key in files["android_demo"], f"Android synthetic demo contract missing: {key}")
        require(key in files["ios_demo"], f"iOS synthetic demo contract missing: {key}")


def validate_localization() -> None:
    android_en = read(ANDROID_EN)
    android_vi = read(ANDROID_VI)
    ios_localization = read(IOS_LOCALIZATION)
    keys = (
        "sync_center_diagnostics_show",
        "sync_center_diagnostics_hide",
        "sync_center_failure_safe_body",
        "coach_attention_count_format",
        "history_signal_window_format",
        "demo_preview_privacy_badge",
        "demo_preview_presentation_note",
    )
    for key in keys:
        require(f'name="{key}"' in android_en, f"Android EN P11 string missing: {key}")
        require(f'name="{key}"' in android_vi, f"Android VI P11 string missing: {key}")
        require(f'"{key}"' in ios_localization, f"iOS P11 localization missing: {key}")


def validate_docs() -> None:
    matrix = read(QA_MATRIX)
    for stage in ["P11.2", "P11.3", "P11.4", "P11.5", "P11.6", "P11.7", "P11.8", "P11.9", "P11.10", "P11-CLOSE"]:
        require(stage in matrix, f"P11 completion matrix missing {stage}")
    rc = read(RC_DOC)
    require("Explicitly deferred to P12" in rc, "Pre-store candidate must keep store activation deferred")
    require("TestFlight" in rc and "Play Internal" in rc, "Pre-store deferral list incomplete")


def main() -> None:
    for path in REQUIRED:
        require(path.exists(), f"Missing P11 completion file: {path.relative_to(ROOT)}")
    android_colors = read(ANDROID_COLORS)
    ios_card = read(IOS_CARD)
    validate_dark_contrast(android_colors, ios_card)
    validate_interaction(read(ANDROID_INTERACTION), read(ANDROID_HOME), ios_card, read(IOS_HOME))
    validate_product_surfaces({
        "android_history": read(ANDROID_HISTORY),
        "ios_history": read(IOS_HISTORY),
        "android_coach": read(ANDROID_COACH),
        "ios_coach": read(IOS_COACH),
        "android_sync": read(ANDROID_SYNC),
        "ios_sync": read(IOS_SYNC),
        "android_demo": read(ANDROID_DEMO),
        "ios_demo": read(IOS_DEMO),
    })
    validate_localization()
    validate_docs()
    print("P11 completion gate passed: dark contrast and P11.2-P11.10/P11-CLOSE contracts are present cross-platform.")


if __name__ == "__main__":
    main()
