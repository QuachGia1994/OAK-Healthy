#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlparse

PRODUCT_IDS = {
    "oak_pro_monthly",
    "oak_pro_annual",
    "oak_coach_monthly",
    "oak_coach_annual",
}
COMMON_BETA_CASES = {
    "no_purchase_free",
    "pro_monthly",
    "pro_annual",
    "coach_monthly",
    "coach_annual",
    "highest_plan_wins",
    "checkout_cancelled_no_grant",
    "invalid_unverified_no_grant",
    "restore_active_purchase",
    "expired_refunded_revoked_downgrade",
    "offline_no_fabricated_entitlement",
}
BETA_CASES = {
    "ios": COMMON_BETA_CASES | {"sandbox_billing_retry_store_state"},
    "android": COMMON_BETA_CASES | {
        "grace_period_retains_access",
        "account_hold_revokes_access",
        "account_hold_recovery_restores_access",
    },
}
SCREENSHOTS = {
    "today_routine",
    "build_stack",
    "advanced_cycles",
    "adherence_history",
    "coach_profiles",
    "encrypted_sync",
}
PLATFORMS = ("ios", "android")
OFFER_MODES = {"none", "introductory", "trial", "discount"}


def load_json(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("evidence root must be a JSON object")
    return data


def require(condition: bool, message: str, failures: list[str]) -> None:
    if condition:
        print(f"[OK] {message}")
        return
    print(f"[FAIL] {message}")
    failures.append(message)


def valid_reference(value: object) -> bool:
    text = str(value or "").strip().lower()
    return bool(text) and "replace_with" not in text and "placeholder" not in text and text != "todo"


def passed(entry: object) -> bool:
    return isinstance(entry, dict) and entry.get("status") == "pass" and valid_reference(entry.get("reference"))


def valid_https_url(value: object) -> bool:
    if not isinstance(value, str) or "<" in value or ">" in value:
        return False
    lowered = value.lower()
    if "replace_me" in lowered or "example.com" in lowered or "placeholder" in lowered:
        return False
    parsed = urlparse(value)
    return parsed.scheme == "https" and bool(parsed.netloc)


def validate_candidate(
    data: dict,
    expected_version: str,
    expected_build: str,
    expected_commit: str,
    failures: list[str],
) -> None:
    candidate = data.get("release_candidate", {})
    version = str(candidate.get("version", "")).strip()
    require(bool(version), "release candidate version is recorded", failures)
    if expected_version:
        require(version == expected_version, "evidence version matches tested candidate", failures)
    build = str(candidate.get("build", "")).strip()
    require(build.isdigit() and int(build) > 0, "release candidate build is a positive integer", failures)
    commit = str(candidate.get("commit", "")).strip().lower()
    require(bool(re.fullmatch(r"[0-9a-f]{7,40}", commit)), "release candidate commit is a git SHA", failures)
    if expected_build:
        require(build == expected_build, "evidence build matches requested store build", failures)
    if expected_commit:
        require(expected_commit.lower().startswith(commit) or commit.startswith(expected_commit.lower()), "evidence commit matches release commit", failures)


def validate_products(data: dict, failures: list[str]) -> None:
    products = data.get("store_products", {})
    for platform in PLATFORMS:
        entries = products.get(platform, {}) if isinstance(products, dict) else {}
        require(set(entries) == PRODUCT_IDS, f"{platform} evidence covers all stable product IDs", failures)
        for product_id in sorted(PRODUCT_IDS):
            require(passed(entries.get(product_id)), f"{platform} {product_id} is active and evidenced", failures)


def validate_beta_matrix(data: dict, failures: list[str]) -> None:
    matrix = data.get("beta_validation", {})
    for platform in PLATFORMS:
        entries = matrix.get(platform, {}) if isinstance(matrix, dict) else {}
        required_cases = BETA_CASES[platform]
        require(set(entries) == required_cases, f"{platform} beta matrix contains every required case", failures)
        for case in sorted(required_cases):
            require(passed(entries.get(case)), f"{platform} beta case passed: {case}", failures)


def validate_offer_strategy(data: dict, failures: list[str]) -> None:
    strategy = data.get("offer_strategy", {})
    for platform in PLATFORMS:
        entry = strategy.get(platform, {}) if isinstance(strategy, dict) else {}
        mode = str(entry.get("mode", "")).strip()
        basis = entry.get("basis_reference")
        require(mode in OFFER_MODES, f"{platform} offer strategy has an explicit mode", failures)
        require(valid_reference(basis), f"{platform} offer strategy cites beta conversion evidence", failures)


def validate_production(data: dict, failures: list[str]) -> None:
    urls = data.get("public_urls", {})
    require(valid_https_url(urls.get("privacy_policy")), "public privacy policy URL is HTTPS", failures)
    require(valid_https_url(urls.get("support")), "public support URL is HTTPS", failures)
    declarations = data.get("store_declarations", {})
    for key in ("app_store_privacy", "play_data_safety", "play_health_apps"):
        require(passed(declarations.get(key)), f"store declaration completed: {key}", failures)
    screenshots = data.get("screenshots", {})
    for platform in PLATFORMS:
        entries = screenshots.get(platform, {}) if isinstance(screenshots, dict) else {}
        require(set(entries) == SCREENSHOTS, f"{platform} screenshots cover the six required states", failures)
        for shot in sorted(SCREENSHOTS):
            require(passed(entries.get(shot)), f"{platform} release screenshot evidenced: {shot}", failures)
    rollback = data.get("rollback", {})
    require(passed(rollback), "rollback rehearsal/runbook is evidenced", failures)
    rollout = data.get("rollout", {})
    for platform in PLATFORMS:
        require(passed(rollout.get(platform)), f"{platform} staged rollout plan is evidenced", failures)
    support = data.get("support", {})
    require(passed(support), "purchase support flow is evidenced", failures)


def validate(
    data: dict,
    level: str,
    expected_build: str,
    expected_commit: str,
    expected_version: str = "",
) -> list[str]:
    failures: list[str] = []
    require(data.get("schema_version") == 1, "commercial evidence schema version is 1", failures)
    validate_candidate(data, expected_version, expected_build, expected_commit, failures)
    validate_products(data, failures)
    validate_beta_matrix(data, failures)
    validate_offer_strategy(data, failures)
    if level == "production":
        validate_production(data, failures)
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate real store/beta evidence before commercial release.")
    parser.add_argument("--evidence", required=True, type=Path)
    parser.add_argument("--require", choices=("beta", "production"), default="beta")
    parser.add_argument("--expected-version", default="")
    parser.add_argument("--expected-build", default="")
    parser.add_argument("--expected-commit", default="")
    args = parser.parse_args()

    try:
        data = load_json(args.evidence)
        failures = validate(
            data,
            args.require,
            args.expected_build,
            args.expected_commit,
            args.expected_version,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"[FAIL] unable to read commercial evidence: {error}")
        return 1
    if failures:
        print(f"Commercial {args.require} gate failed with {len(failures)} issue(s).")
        return 1
    print(f"Commercial {args.require} gate passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
