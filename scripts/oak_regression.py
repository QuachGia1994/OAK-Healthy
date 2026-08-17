#!/usr/bin/env python3
"""Run the deterministic OAK Healthy repository regression matrix."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GATES = (
    "scripts/product_readiness.py",
    "scripts/data_recovery_gate.py",
    "scripts/reminder_reliability_gate.py",
    "scripts/sync_engine_gate.py",
    "scripts/coach_workspace_gate.py",
    "scripts/activation_retention_gate.py",
    "scripts/architecture_boundaries_gate.py",
    "scripts/performance_battery_gate.py",
    "scripts/security_hardening_gate.py",
    "scripts/ux_polish_gate.py",
    "scripts/editorial_design_gate.py",
    "scripts/health_ui_redesign_gate.py",
    "scripts/p11_completion_gate.py",
    "scripts/stage_b_ui_rc_gate.py",
    "scripts/store_activation_gate.py",
    "scripts/release_preflight.py",
)


def run_command(args: list[str]) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def run_regression() -> None:
    for relative in GATES:
        run_command([sys.executable, relative])
    run_command([sys.executable, "-m", "unittest", "discover", "scripts/tests", "-q"])


def main() -> int:
    try:
        run_regression()
    except subprocess.CalledProcessError as error:
        print(f"OAK regression matrix failed: {error.cmd}")
        return error.returncode or 1
    print("OAK regression matrix passed: P8-P12-CLOSE plus UI-R1/UI-R2 and Stage A/Stage B final UI release-candidate contracts are green.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
