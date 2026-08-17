#!/usr/bin/env python3
"""Fail-closed P10.3 security/privacy repository contracts."""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FILES = {
    "rules": ROOT / "firebase/database.rules.json",
    "android_extract": ROOT / "Android/app/src/main/res/xml/data_extraction_rules.xml",
    "android_crypto": ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/CloudSyncCrypto.kt",
    "ios_crypto": ROOT / "iOS/Services/CloudSyncCrypto.swift",
    "android_diag": ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/DiagnosticsReporter.kt",
    "ios_diag": ROOT / "iOS/Services/DiagnosticsReporter.swift",
    "ios_notification_diag": ROOT / "iOS/Views/NotificationDebugView.swift",
    "android_crypto_test": ROOT / "Android/app/src/test/java/com/example/supplementtracker/service/CloudSyncCryptoInteropTest.kt",
    "ios_crypto_test": ROOT / "iOS/Tests/CloudSyncTests.swift",
}

SENSITIVE_ANALYTICS_KEYS = ('"client_id"', '"supplement"', '"dose"', '"note"', '"health"', '"sync_key"')
SECRET_PATTERNS = (
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"\bghp_[A-Za-z0-9]{30,}\b"),
    re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{20,}\b"),
    re.compile(r"\bsk-proj-[A-Za-z0-9_-]{20,}\b"),
)


def read(key: str) -> str:
    return FILES[key].read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def validate_firebase_rules() -> None:
    rules = read("rules")
    require(rules.count('"auth != null"') >= 2, "Firebase read/write must require auth")
    require("newData.val() > data.val()" in rules, "Firebase revision must be strictly monotonic")
    require("newData.val().length <= 1048576" in rules, "Firebase payload must remain capped at 1 MiB")


def validate_local_secrets() -> None:
    extraction = read("android_extract")
    for path in ("supplement_db", "oak_settings", "oak_settings_recovery", "oak_db_encryption"):
        require(extraction.count(f'path="{path}"') >= 2, f"Sensitive Android data must be excluded from backup and device transfer: {path}")

    android = read("android_crypto")
    ios = read("ios_crypto")
    require("AndroidKeyStore" in android and "A256GCM" in android, "Android cloud keys must remain wrapped with platform keystore + AES-GCM")
    require("Rejected plaintext downgrade" in android, "Android must reject plaintext downgrade")
    require("kSecAttrAccessibleWhenUnlockedThisDeviceOnly" in ios, "iOS cloud keys must remain device-only Keychain items")
    require("A256GCM" in ios and "unencryptedPayloadRejected" in ios, "iOS must retain AES-GCM and plaintext downgrade rejection")


def validate_diagnostics() -> None:
    combined = (read("android_diag") + read("ios_diag")).lower()
    for key in SENSITIVE_ANALYTICS_KEYS:
        require(key not in combined, f"Sensitive diagnostics field is allowlisted: {key}")
    require("activation_milestone" in combined, "Activation telemetry allowlist unexpectedly missing")
    require("milestone" in combined and "state" in combined, "Activation telemetry must remain aggregate-only")
    notification_diag = read("ios_notification_diag")
    require("activeClientManager.currentClientId?.uuidString" not in notification_diag,
            "iOS notification diagnostics must not expose raw client identity")


def validate_tamper_tests() -> None:
    require("rejectsTamperedCiphertext" in read("android_crypto_test"), "Android AES-GCM tamper regression test missing")
    require("testRejectsTamperedCiphertext" in read("ios_crypto_test"), "iOS AES-GCM tamper regression test missing")


def validate_secret_scan() -> None:
    excluded = {"google-services.json", "GoogleService-Info.plist"}
    suffixes = {".kt", ".swift", ".py", ".yml", ".yaml", ".json", ".md", ".xml"}
    result = subprocess.run(
        ["git", "ls-files"], cwd=ROOT, check=True, capture_output=True, text=True
    )
    for relative in result.stdout.splitlines():
        path = ROOT / relative
        if path.name in excluded or path.suffix.lower() not in suffixes:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for pattern in SECRET_PATTERNS:
            require(pattern.search(text) is None, f"Potential committed secret pattern in {relative}")


def validate_repository() -> None:
    validate_firebase_rules()
    validate_local_secrets()
    validate_diagnostics()
    validate_tamper_tests()
    validate_secret_scan()


def main() -> int:
    try:
        validate_repository()
    except (OSError, RuntimeError) as error:
        print(f"Security hardening gate failed: {error}")
        return 1
    print("Security hardening gate passed: auth/rules, key lifecycle, tamper tests, backup exclusions, privacy allowlists and secret scan are intact.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
