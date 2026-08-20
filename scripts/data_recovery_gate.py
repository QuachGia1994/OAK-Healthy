#!/usr/bin/env python3
"""Fail-closed validation for P9.1 data-recovery fixtures and migration coverage."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MATRIX = ROOT / "fixtures" / "data_recovery" / "matrix.json"
REQUIRED_SCHEMAS = {"legacy-array", "export-v1", "oak-1.1", "oak-2.0"}
REQUIRED_DIRECTIONS = {"android-to-ios", "ios-to-android"}
REQUIRED_INVARIANTS = {
    "no-orphan-history",
    "no-client-replacement",
    "routine-fields-preserved",
    "history-count-preserved",
}


class GateError(RuntimeError):
    """Raised when a recovery fixture or migration declaration is unsafe."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise GateError(message)


def _load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise GateError(f"Cannot load {path}: {error}") from error


def _fixture_shape(source_schema: str, payload: Any) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    if source_schema == "legacy-array":
        _require(isinstance(payload, list), "legacy-array fixture must be a JSON array")
        return payload, []
    _require(isinstance(payload, dict), f"{source_schema} fixture must be a JSON object")
    if source_schema == "export-v1":
        _require(payload.get("schemaVersion") == 1, "export-v1 must declare schemaVersion 1")
        return payload.get("supplements", []), []
    if source_schema == "oak-1.1":
        _require(payload.get("version") == "1.1", "oak-1.1 must declare version 1.1")
        return payload.get("stack", []), payload.get("history", [])
    if source_schema == "oak-2.0":
        _require(payload.get("version") == "2.0", "oak-2.0 must declare version 2.0")
        _require(payload.get("meta", {}).get("schemaVersion") == 2, "oak-2.0 must declare meta.schemaVersion 2")
        return payload.get("supplements", []), payload.get("historyLogs", [])
    raise GateError(f"Unsupported fixture schema: {source_schema}")


def _validate_routines(source_schema: str, supplements: list[dict[str, Any]]) -> set[str]:
    ids: list[str] = []
    for index, item in enumerate(supplements):
        _require(isinstance(item, dict), f"{source_schema} supplement {index} must be an object")
        for field in ("name", "startDate", "cycle"):
            _require(field in item, f"{source_schema} supplement {index} is missing {field}")
        cycle = item["cycle"]
        _require(isinstance(cycle, dict), f"{source_schema} supplement {index} cycle must be an object")
        for field in ("isContinuous", "daysOn", "daysOff"):
            _require(field in cycle, f"{source_schema} supplement {index} cycle is missing {field}")
        if "id" in item:
            _require(isinstance(item["id"], str) and item["id"], f"{source_schema} supplement {index} has an invalid id")
            ids.append(item["id"])
    if source_schema.startswith("oak-"):
        _require(len(ids) == len(supplements), f"{source_schema} requires stable supplement IDs")
        _require(len(ids) == len(set(ids)), f"{source_schema} has duplicate supplement IDs")
    return set(ids)


def _validate_history(source_schema: str, history: list[dict[str, Any]], supplement_ids: set[str]) -> None:
    history_ids: list[str] = []
    for index, item in enumerate(history):
        _require(isinstance(item, dict), f"{source_schema} history {index} must be an object")
        supplement_id = item.get("supplementId")
        _require(supplement_id in supplement_ids, f"{source_schema} history {index} is orphaned")
        history_id = item.get("id")
        _require(isinstance(history_id, str) and history_id, f"{source_schema} history {index} has an invalid id")
        history_ids.append(history_id)
    _require(len(history_ids) == len(set(history_ids)), f"{source_schema} has duplicate history IDs")


def validate_matrix_data(matrix: dict[str, Any], fixture_dir: Path) -> int:
    _require(matrix.get("version") == 1, "matrix version must be 1")
    schemas = set(matrix.get("requiredBackupSchemas", []))
    _require(schemas == REQUIRED_SCHEMAS, f"required schemas must be {sorted(REQUIRED_SCHEMAS)}")
    _require(set(matrix.get("invariants", [])) >= REQUIRED_INVARIANTS, "recovery invariants are incomplete")

    fixtures = matrix.get("fixtures")
    _require(isinstance(fixtures, list) and fixtures, "matrix fixtures must be non-empty")
    declared_schemas: set[str] = set()
    directions: set[str] = set()
    fixture_root = fixture_dir.resolve()

    for entry in fixtures:
        _require(isinstance(entry, dict), "fixture matrix entries must be objects")
        source_schema = entry.get("sourceSchema")
        declared_schemas.add(source_schema)
        directions.add(entry.get("direction"))
        _require(entry.get("clientPolicy") == "target-client-preserved", f"{source_schema} must preserve the target client")
        relative_path = Path(str(entry.get("file", "")))
        _require(relative_path.name == str(relative_path) and relative_path.name, "fixture paths must be local filenames")
        fixture_path = (fixture_root / relative_path).resolve()
        _require(fixture_path.parent == fixture_root, f"fixture escapes recovery directory: {relative_path}")
        payload = _load_json(fixture_path)
        supplements, history = _fixture_shape(source_schema, payload)
        _require(len(supplements) == entry.get("expectedSupplements"), f"{relative_path} supplement count changed")
        _require(len(history) == entry.get("expectedHistory"), f"{relative_path} history count changed")
        supplement_ids = _validate_routines(source_schema, supplements)
        _validate_history(source_schema, history, supplement_ids)

    _require(declared_schemas == REQUIRED_SCHEMAS, "fixtures do not cover every supported backup schema")
    _require(directions >= REQUIRED_DIRECTIONS, "fixtures must cover Android-to-iOS and iOS-to-Android")

    migrations = matrix.get("databaseMigrations", {})
    android = migrations.get("androidRoom", {})
    ios = migrations.get("iosSwiftData", {})
    _require(android.get("supportedSources") == [2, 3, 4, 5], "Android migration matrix must cover 2/3/4/5")
    _require(android.get("target") == 6, "Android migration target must be 6")
    _require(ios.get("supportedSources") == ["legacy-default", "1.0.0"], "iOS migration matrix is incomplete")
    _require(ios.get("target") == "1.0.0", "iOS migration target must be 1.0.0")
    return len(fixtures)


def validate_matrix(path: Path = DEFAULT_MATRIX) -> int:
    matrix = _load_json(path)
    _require(isinstance(matrix, dict), "matrix must be a JSON object")
    return validate_matrix_data(matrix, path.parent)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=Path, default=DEFAULT_MATRIX)
    args = parser.parse_args()
    try:
        fixture_count = validate_matrix(args.matrix)
    except GateError as error:
        print(f"Data recovery gate failed: {error}")
        return 1
    print(
        "Data recovery gate passed: "
        f"{fixture_count} fixtures; backup schemas legacy/export/OAK 1.1/OAK 2.0; "
        "Android DB 2/3/4/5->6; iOS legacy-default/1.0.0->1.0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
