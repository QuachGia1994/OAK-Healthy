#!/usr/bin/env python3
"""Repository-side product maturity gate for OAK Healthy P7/P8."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ROOT / "docs" / "qa" / "PRODUCT_MATURITY_SCENARIOS.json"
REQUIRED_CATEGORIES = {"notification", "coach", "sync", "backup", "accessibility", "qa", "migration"}
REQUIRED_PLATFORMS = {"android", "ios"}


def load_scenarios(path: Path = SCENARIOS) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_scenarios(payload: dict) -> list[str]:
    errors: list[str] = []
    items = payload.get("scenarios", [])
    if payload.get("schema_version") != 1 or payload.get("synthetic_only") is not True:
        errors.append("scenario catalog metadata is invalid")
    ids = [item.get("id") for item in items]
    if len(ids) != len(set(ids)) or any(not value for value in ids):
        errors.append("scenario IDs must be non-empty and unique")
    categories = {item.get("category") for item in items}
    if not REQUIRED_CATEGORIES.issubset(categories):
        errors.append("scenario categories are incomplete")
    errors.extend(validate_scenario_items(items))
    return errors


def validate_scenario_items(items: list[dict]) -> list[str]:
    errors: list[str] = []
    for item in items:
        platforms = set(item.get("platforms", []))
        if not platforms or not platforms.issubset(REQUIRED_PLATFORMS):
            errors.append(f"invalid platforms for {item.get('id')}")
        if not item.get("steps") or not item.get("expected"):
            errors.append(f"missing steps/expected for {item.get('id')}")
    return errors


def required_files() -> list[Path]:
    return [
        ROOT / "docs" / "DEFERRED_STORE_DEVELOPMENT.md",
        ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/NotificationReliability.kt",
        ROOT / "Android/app/src/main/java/com/example/supplementtracker/service/SyncHealth.kt",
        ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/export/OAKBackupIntegrity.kt",
        ROOT / "iOS/Services/NotificationReliability.swift",
        ROOT / "iOS/Services/SyncHealth.swift",
        ROOT / "iOS/Services/BackupIntegrity.swift",
    ]


def validate_files() -> list[str]:
    return [f"missing required file: {path.relative_to(ROOT)}" for path in required_files() if not path.exists()]


def validate_guards() -> list[str]:
    android = (ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation/home/SettingsScreen.kt").read_text(encoding="utf-8")
    ios = (ROOT / "iOS/Views/SettingsView.swift").read_text(encoding="utf-8")
    errors: list[str] = []
    if "BuildConfig.DEBUG" not in android:
        errors.append("Android synthetic demo is not debug-gated")
    if "#if DEBUG" not in ios:
        errors.append("iOS synthetic demo is not debug-gated")
    return errors


def validate_positioning() -> list[str]:
    paths = [ROOT / "Android/app/src/main/res/values/strings.xml", ROOT / "iOS/Services/LocalizationService.swift"]
    forbidden = ("doctors monitoring patients", "bác sĩ theo dõi bệnh nhân")
    text = "\n".join(path.read_text(encoding="utf-8").lower() for path in paths)
    return [f"forbidden medical positioning remains: {phrase}" for phrase in forbidden if phrase in text]


def validate_identifier_telemetry() -> list[str]:
    text = (ROOT / "iOS/Views/SyncCenterView.swift").read_text(encoding="utf-8")
    forbidden = ('"currentClientId": activeClient', '"clientId": activeClient', '"binId": activeBinId')
    return ["raw sync identifier telemetry remains"] if any(fragment in text for fragment in forbidden) else []


def run() -> list[str]:
    errors = validate_files() + validate_scenarios(load_scenarios())
    errors += validate_guards() + validate_positioning()
    errors += validate_identifier_telemetry()
    return errors


def main() -> int:
    errors = run()
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("Product readiness gate passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
