#!/usr/bin/env python3
"""Validate the app-shell ownership and scroll-minimized bottom-bar contract."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    "android_nav": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/navigation/AppNavigation.kt",
    "android_bar": ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/navigation/OakBottomBar.kt",
    "ios_app": ROOT / "iOS/SupplementTrackerApp.swift",
    "ios_tokens": ROOT / "iOS/Views/OAKDesignTokens.swift",
    "ios_card": ROOT / "iOS/Views/OAKCard.swift",
    "design": ROOT / "DESIGN.md",
    "matrix": ROOT / "docs/qa/APP_SHELL_CLOSURE_MATRIX.md",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def validate_android(data: dict[str, str]) -> None:
    nav = data["android_nav"]
    bar = data["android_bar"]
    require("bottomBar = {" in nav, "Android bottom bar is not owned by Scaffold.bottomBar")
    require(".consumeWindowInsets(innerPadding)" in nav, "Android NavHost does not consume Scaffold insets")
    require("Modifier.nestedScroll(bottomBarScrollState.nestedScrollConnection)" in nav, "Android primary content is not connected to bottom-bar scroll state")
    require("navigationBarsPadding" not in nav, "Android AppNavigation still applies a second navigation-bar inset")
    require(bar.count("navigationBarsPadding()") == 1, "Android bottom bar must consume navigation inset exactly once")
    require("WindowInsets(0, 0, 0, 0)" in bar, "Android NavigationBar internal insets are not disabled at the owned boundary")
    require("NestedScrollConnection" in bar and "NestedScrollSource.Drag" in bar, "Android scroll-minimize state does not observe direct user drag input")
    require("56.dp" in bar and "80.dp" in bar, "Android compact/full bottom-bar heights are missing")
    require("label = if (scrollState.compact)" in bar and "alwaysShowLabel = !scrollState.compact" in bar, "Android compact bar does not switch to icon-only presentation")
    require("rememberOakReduceMotion" in bar and "snap()" in bar, "Android bottom-bar transition does not respect reduced motion")
    require("scrollState.expand()" in bar, "Android tab switching does not restore expanded navigation")


def validate_ios(data: dict[str, str]) -> None:
    app = data["ios_app"]
    require("TabView(selection: $selectedTab)" in app, "iOS primary navigation is no longer native TabView")
    require(".oakScrollMinimizingTabBar()" in app, "iOS TabView is not wired to scroll minimization")
    require("if #available(iOS 26.0, *)" in app, "iOS scroll minimization lacks deployment-target fallback")
    require("tabBarMinimizeBehavior(.onScrollDown)" in app, "iOS 26 native tab-bar minimization is missing")
    for prefix in ("home-", "stack-", "history-"):
        require(f'.id("{prefix}' in app, f"iOS active-client tab isolation missing for {prefix.rstrip('-')}")


def validate_tokens_and_docs(data: dict[str, str]) -> None:
    tokens = data["ios_tokens"]
    card = data["ios_card"]
    design = data["design"]
    matrix = data["matrix"]
    require("enum OAKPalette" in tokens, "iOS palette is not owned by OAKDesignTokens.swift")
    require("enum OAKPalette" not in card, "iOS card component still declares a parallel palette")
    for token in ("#1F6B4D", "#7DD3A8", "#0B0F0D", "#405047"):
        require(token in design, f"DESIGN.md missing current token {token}")
    for stale in ("#0F6F75", "hero-start:", "hero-end:", "tonal glass"):
        require(stale not in design, f"DESIGN.md still contains legacy design contract: {stale}")
    for phrase in ("Android shell", "Apple shell", "Design-token ownership", "Automated closure gate"):
        require(phrase in matrix, f"App shell QA matrix missing section: {phrase}")


def main() -> None:
    for path in FILES.values():
        require(path.exists(), f"Missing app-shell closure file: {path.relative_to(ROOT)}")
    data = {name: read(path) for name, path in FILES.items()}
    validate_android(data)
    validate_ios(data)
    validate_tokens_and_docs(data)
    print("App shell closure gate passed: inset ownership, scroll-minimized navigation and token SSoT are aligned cross-platform.")


if __name__ == "__main__":
    main()
