#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCT_IDS = ("oak_pro_monthly", "oak_pro_annual", "oak_coach_monthly", "oak_coach_annual")


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"[FAIL] {message}")
    print(f"[OK] {message}")


def main() -> None:
    app_gradle = read("Android/app/build.gradle")
    baseline = read("Android/baselineprofile/build.gradle")
    root_gradle = read("Android/build.gradle")
    wrapper = read("Android/gradle/wrapper/gradle-wrapper.properties")
    play = read("Android/app/src/main/java/com/example/supplementtracker/service/GooglePlayBillingService.kt")
    integrity = read("Android/app/src/main/java/com/example/supplementtracker/security/AppIntegrity.kt")
    storekit = read("iOS/Services/StoreKitBillingService.swift")
    release = read(".github/workflows/release.yml")
    ios_build = read(".github/workflows/ios-build.yml")
    quality = read(".github/workflows/quality-gates.yml")
    android_catalog = read("Android/app/src/main/java/com/example/supplementtracker/service/CommercialEntitlements.kt")
    ios_catalog = read("iOS/Services/CommercialEntitlements.swift")
    docs = read("docs/P12_STORE_ACTIVATION.md")

    require("compileSdk 36" in app_gradle and "targetSdk 36" in app_gradle, "Android app targets API 36")
    require("compileSdk 36" in baseline and "targetSdk 36" in baseline, "Baseline profile targets API 36")
    require("com.android.tools.build:gradle:8.10.1" in root_gradle, "AGP supports API 36")
    require("gradle-8.11.1-bin.zip" in wrapper, "Gradle wrapper matches AGP toolchain")
    require(all('xcode-version: "latest-stable"' in text and "Require Xcode 26+" in text for text in (release, ios_build, quality)), "iOS CI/store enforces current stable Xcode 26+")
    require("16.4" not in ios_build and "16.4" not in release and "16.4" not in quality, "No store-capable iOS job remains on Xcode 16")
    require("execute_store_uploads" in release and "default: false" in release, "Store upload execution is explicit")
    require("Store Activation Readiness Only" in release, "No-upload readiness path exists")
    require("Blocked - Missing Store Credentials" in release and release.count("exit 1") >= 5, "Missing store credentials fail closed")
    require("purchaseOptions.clear()" in play, "Play offer map cannot retain stale store offers")
    require("Purchase.PurchaseState.PENDING" in play and "VERIFICATION_FAILED" in play, "Play pending/unverified purchases fail closed")
    require(".signatures ?: return null" in integrity and "certs ?: return null" in integrity, "API 36 signing certificate reads fail closed on missing signatures")
    require("Transaction.currentEntitlements" in storekit and "case .verified" in storekit, "StoreKit entitlements require verified transactions")
    for product_id in PRODUCT_IDS:
        require(product_id in android_catalog and product_id in ios_catalog, f"Catalog parity: {product_id}")
    require("NOT EXECUTED" in docs and "P12-CLOSE" in docs, "External store execution is not fabricated")
    print("Store activation gate passed: P12 repository activation contracts are ready and external store execution remains explicit.")


if __name__ == "__main__":
    main()
