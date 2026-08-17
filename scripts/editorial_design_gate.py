from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ANDROID_COLORS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakColors.kt"
ANDROID_BACKGROUND = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakBackground.kt"
ANDROID_CARD = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakCard.kt"
ANDROID_TYPE = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/designsystem/OakTypography.kt"
ANDROID_HOME = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HomeScreen.kt"
ANDROID_HISTORY = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryScreen.kt"
ANDROID_STACK = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/MyStackListScreen.kt"
ANDROID_SETTINGS = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/SettingsComponents.kt"
ANDROID_COACH = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/coach/CoachOverviewScreen.kt"
ANDROID_SYNC = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/sync/SyncCenterScreen.kt"
IOS_CARD = ROOT / "iOS/Views/OAKCard.swift"
IOS_HOME = ROOT / "iOS/Views/HomeView.swift"
IOS_HISTORY = ROOT / "iOS/Views/HistoryView.swift"
IOS_STACK = ROOT / "iOS/Views/StackView.swift"
IOS_SETTINGS = ROOT / "iOS/Views/SettingsView.swift"
IOS_COACH = ROOT / "iOS/Views/CoachOverviewView.swift"
IOS_SYNC = ROOT / "iOS/Views/SyncCenterView.swift"
RESEARCH = ROOT / "docs/research/huashu-native-redesign.md"
QA_MATRIX = ROOT / "docs/qa/EDITORIAL_NATIVE_REDESIGN_MATRIX.md"

REQUIRED = [
    ANDROID_COLORS,
    ANDROID_BACKGROUND,
    ANDROID_CARD,
    ANDROID_TYPE,
    ANDROID_HOME,
    ANDROID_HISTORY,
    ANDROID_STACK,
    ANDROID_SETTINGS,
    ANDROID_COACH,
    ANDROID_SYNC,
    IOS_CARD,
    IOS_HOME,
    IOS_HISTORY,
    IOS_STACK,
    IOS_SETTINGS,
    IOS_COACH,
    IOS_SYNC,
    RESEARCH,
    QA_MATRIX,
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> None:
    for path in REQUIRED:
        require(path.exists(), f"Missing editorial redesign file: {path.relative_to(ROOT)}")

    android_colors = read(ANDROID_COLORS)
    android_background = read(ANDROID_BACKGROUND)
    android_card = read(ANDROID_CARD)
    android_type = read(ANDROID_TYPE)
    android_screens = [read(path) for path in [ANDROID_HOME, ANDROID_HISTORY, ANDROID_STACK, ANDROID_SETTINGS, ANDROID_COACH, ANDROID_SYNC]]
    ios_card = read(IOS_CARD)
    ios_screens = [read(path) for path in [IOS_HOME, IOS_HISTORY, IOS_STACK, IOS_SETTINGS, IOS_COACH, IOS_SYNC]]

    for token in ["Paper", "PaperRaised", "PaperMuted", "Ink", "InkMuted", "Hairline", "Accent"]:
        require(f"val {token}" in android_colors, f"Android editorial token missing: {token}")
    require("SolidColor(MaterialTheme.colorScheme.background)" in android_background, "Android background must remain a restrained solid paper surface")
    require("OakCardVariant.Paper" in android_card, "Android Paper card variant missing")
    require("MaterialTheme.colorScheme.surface" in android_card and "outlineVariant" in android_card, "Android paper card surface/hairline contract missing")
    require("FontFamily.Serif" in android_type, "Android display serif token missing")

    for name, screen in zip(["Home", "History", "Stack", "Settings", "Coach", "Sync"], android_screens):
        require("Brush.linearGradient" not in screen, f"Android {name} reintroduced a decorative linear gradient")
    require("OakTypography.Display" in android_screens[0], "Android Home display hierarchy missing")
    require("OakTypography.Display" in android_screens[1], "Android History display hierarchy missing")
    require("OakTypography.Display" in android_screens[2], "Android Stack display hierarchy missing")
    require("OakTypography.Display" in android_screens[4], "Android Coach display hierarchy missing")
    require("MaterialTheme.colorScheme.surface" in android_screens[3] and "outlineVariant" in android_screens[3], "Android Settings flat paper grouping missing")
    require("RoundedCornerShape(28.dp)" not in android_screens[5], "Android Sync reverted to oversized rounded card groups")

    for token in ["paper", "paperRaised", "paperMuted", "ink", "inkMuted", "hairline", "accent"]:
        require(f"static let {token}" in ios_card, f"iOS editorial token missing: {token}")
    require("ultraThinMaterial" not in ios_card, "iOS shared card must not revert to glass material")
    require("Circle()" not in ios_card and ".blur(" not in ios_card, "iOS shared background must not restore decorative glow circles")
    require("oakDisplay" in ios_card, "iOS display serif helper missing")
    for name, screen in zip(["Home", "History", "Stack", "Settings", "Coach", "Sync"], ios_screens):
        require("LinearGradient" not in screen, f"iOS {name} reintroduced a decorative linear gradient")
    require(".oakDisplay" in ios_screens[0], "iOS Home display hierarchy missing")
    require(".oakDisplay" in ios_screens[1], "iOS History display hierarchy missing")
    require(".oakDisplay" in ios_screens[2], "iOS Stack display hierarchy missing")
    require(".oakDisplay" in ios_screens[4], "iOS Coach display hierarchy missing")

    print("Editorial design gate passed: warm paper tokens, restrained surfaces, serif data hierarchy, and gradient-free core screens are aligned cross-platform.")


if __name__ == "__main__":
    main()
