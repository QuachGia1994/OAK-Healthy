#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

FORBIDDEN_KEY_TOKENS = {
    "client", "profile", "supplement", "dose", "intake", "email", "receipt",
    "transaction", "sync", "encryption", "user_id", "device_id", "name",
}
FUNNEL_KEYS = {
    "plan_access_views",
    "purchase_starts",
    "purchase_successes",
    "purchase_failures",
    "restore_starts",
    "restore_successes",
}
STORE_KEYS = {
    "active_start",
    "active_end",
    "new_paid",
    "cancellations",
    "refunds",
    "renewals",
}
MIX_KEYS = {
    "pro_monthly",
    "pro_annual",
    "coach_monthly",
    "coach_annual",
}
SUPPORT_KEYS = {"purchase_tickets", "restore_tickets"}


def load_snapshot(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("metrics root must be a JSON object")
    return data


def find_forbidden_keys(value: object, path: str = "root") -> list[str]:
    findings: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = str(key).lower()
            if any(token in normalized for token in FORBIDDEN_KEY_TOKENS):
                findings.append(f"{path}.{key}")
            findings.extend(find_forbidden_keys(child, f"{path}.{key}"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            findings.extend(find_forbidden_keys(child, f"{path}[{index}]"))
    return findings


def validate_counts(section: object, expected: set[str], label: str) -> dict[str, int]:
    if not isinstance(section, dict) or set(section) != expected:
        raise ValueError(f"{label} must contain exactly: {', '.join(sorted(expected))}")
    result: dict[str, int] = {}
    for key, value in section.items():
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ValueError(f"{label}.{key} must be a non-negative integer")
        result[key] = value
    return result


def ratio(numerator: int, denominator: int) -> str:
    if denominator <= 0:
        return "n/a"
    return f"{(numerator / denominator) * 100:.1f}%"


def per_thousand(numerator: int, denominator: int) -> str:
    if denominator <= 0:
        return "n/a"
    return f"{(numerator / denominator) * 1000:.1f}"


def render_report(data: dict) -> str:
    funnel = validate_counts(data.get("funnel"), FUNNEL_KEYS, "funnel")
    store = validate_counts(data.get("store"), STORE_KEYS, "store")
    mix = validate_counts(data.get("active_product_mix"), MIX_KEYS, "active_product_mix")
    support = validate_counts(data.get("support"), SUPPORT_KEYS, "support")
    active_mix = sum(mix.values())
    annual = mix["pro_annual"] + mix["coach_annual"]
    coach = mix["coach_monthly"] + mix["coach_annual"]
    support_total = support["purchase_tickets"] + support["restore_tickets"]
    lines = [
        "# OAK Healthy Commercial KPI Snapshot",
        "",
        f"Period: {data.get('period_start', '')} -> {data.get('period_end', '')}",
        "",
        "## Funnel",
        f"- Paywall -> checkout start: {ratio(funnel['purchase_starts'], funnel['plan_access_views'])}",
        f"- Checkout success: {ratio(funnel['purchase_successes'], funnel['purchase_starts'])}",
        f"- Restore success: {ratio(funnel['restore_successes'], funnel['restore_starts'])}",
        "",
        "## Store health",
        f"- Net active subscriber change: {store['active_end'] - store['active_start']:+d}",
        f"- Cancellation rate vs opening active base: {ratio(store['cancellations'], store['active_start'])}",
        f"- Refund rate vs new paid: {ratio(store['refunds'], store['new_paid'])}",
        f"- Renewal / opening active base: {ratio(store['renewals'], store['active_start'])}",
        "",
        "## Plan mix",
        f"- Annual mix: {ratio(annual, active_mix)}",
        f"- Coach mix: {ratio(coach, active_mix)}",
        "",
        "## Support",
        f"- Purchase/restore tickets per 1,000 active subscribers: {per_thousand(support_total, store['active_end'])}",
        "",
        "Store-console aggregate subscription metrics are the source of truth for retention/churn. App diagnostics are used only for consented, privacy-safe funnel counts.",
    ]
    return "\n".join(lines) + "\n"


def validate_snapshot(data: dict) -> None:
    if data.get("schema_version") != 1:
        raise ValueError("schema_version must be 1")
    forbidden = find_forbidden_keys(data)
    if forbidden:
        raise ValueError("sensitive/user-level keys are forbidden: " + ", ".join(forbidden))
    for key in ("period_start", "period_end"):
        if not isinstance(data.get(key), str) or not data[key].strip():
            raise ValueError(f"{key} is required")


def main() -> int:
    parser = argparse.ArgumentParser(description="Render privacy-safe aggregate commercial KPIs.")
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        data = load_snapshot(args.snapshot)
        validate_snapshot(data)
        report = render_report(data)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Commercial metrics failed: {error}")
        return 1
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
