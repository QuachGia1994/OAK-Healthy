#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCT_IDS = {
    "oak_pro_monthly",
    "oak_pro_annual",
    "oak_coach_monthly",
    "oak_coach_annual",
}
EXPECTED_APP_ID = "com.phongqk.oakhealthy"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if condition:
        print(f"[OK] {message}")
    else:
        print(f"[FAIL] {message}")
        failures.append(message)


def capture(pattern: str, text: str, label: str, failures: list[str]) -> str:
    match = re.search(pattern, text, re.MULTILINE)
    require(match is not None, f"{label} is declared", failures)
    return match.group(1) if match else ""


def main() -> int:
    failures: list[str] = []
    android_gradle = read("Android/app/build.gradle")
    ios_project = read("iOS/project.yml")
    android_catalog = read(
        "Android/app/src/main/java/com/example/supplementtracker/service/CommercialEntitlements.kt"
    )
    ios_catalog = read("iOS/Services/CommercialEntitlements.swift")
    android_manifest = read("Android/app/src/main/AndroidManifest.xml")
    release_workflow = read(".github/workflows/release.yml")

    android_id = capture(r"applicationId\s+[\"']([^\"']+)", android_gradle, "Android applicationId", failures)
    ios_id = capture(r"PRODUCT_BUNDLE_IDENTIFIER:\s*([^\s]+)", ios_project, "iOS bundle identifier", failures)
    require(android_id == EXPECTED_APP_ID, "Android applicationId matches store identity", failures)
    require(ios_id == EXPECTED_APP_ID, "iOS bundle identifier matches store identity", failures)

    android_version = capture(r"versionName\s+[\"']([^\"']+)", android_gradle, "Android versionName", failures)
    ios_version = capture(r"MARKETING_VERSION:\s*[\"']?([^\"'\s]+)", ios_project, "iOS marketing version", failures)
    require(android_version == ios_version, "Android and iOS marketing versions match", failures)

    android_build = capture(r"baseVersionCode\s*=\s*(\d+)", android_gradle, "Android base versionCode", failures)
    ios_build = capture(r"CURRENT_PROJECT_VERSION:\s*[\"']?(\d+)", ios_project, "iOS build number", failures)
    require(android_build == ios_build, "Android and iOS build numbers match", failures)

    for product_id in sorted(PRODUCT_IDS):
        require(product_id in android_catalog, f"Android catalog contains {product_id}", failures)
        require(product_id in ios_catalog, f"iOS catalog contains {product_id}", failures)

    require(
        'android:name="firebase_analytics_collection_enabled"' in android_manifest
        and 'android:value="false"' in android_manifest,
        "Android Analytics defaults to disabled",
        failures,
    )
    require(
        'android:name="firebase_crashlytics_collection_enabled"' in android_manifest,
        "Android Crashlytics default flag is declared",
        failures,
    )
    require(
        "FIREBASE_ANALYTICS_COLLECTION_ENABLED: false" in ios_project,
        "iOS Analytics defaults to disabled",
        failures,
    )
    require(
        "FirebaseCrashlyticsCollectionEnabled: false" in ios_project,
        "iOS Crashlytics defaults to disabled",
        failures,
    )
    require(
        "Upload Crashlytics dSYMs" in ios_project and "Crashlytics/run" in ios_project,
        "Signed iOS releases upload Crashlytics dSYMs",
        failures,
    )

    require((ROOT / "docs/PRIVACY_POLICY.md").is_file(), "Privacy policy source exists", failures)
    require(
        (ROOT / "docs/STORE_PRIVACY_DECLARATIONS.md").is_file(),
        "Store privacy declaration checklist exists",
        failures,
    )
    require(
        "PLAY_BILLING_PUBLIC_KEY" in release_workflow,
        "Release workflow supplies Play billing verification key",
        failures,
    )
    require(
        "STORE_BUILD_NUMBER" in release_workflow
        and "STORE_BUILD_NUMBER" in android_gradle
        and "STORE_BUILD_NUMBER" in read("iOS/fastlane/Fastfile"),
        "Store release build numbers are dynamic on both platforms",
        failures,
    )
    require(
        "|| 'beta'" in release_workflow and "|| 'internal'" in release_workflow,
        "Release automation defaults to beta/internal rather than production",
        failures,
    )
    require(
        "confirm_production" in release_workflow
        and "Production upload requires workflow_dispatch" in release_workflow,
        "Production upload requires explicit confirmation",
        failures,
    )
    require(
        "play-services-ads" not in android_gradle,
        "Android release has no Google Mobile Ads dependency",
        failures,
    )

    if failures:
        print(f"Release preflight failed with {len(failures)} issue(s).")
        return 1
    print("Release preflight passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
