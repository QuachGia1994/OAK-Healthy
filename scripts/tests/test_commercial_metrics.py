from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

import commercial_metrics  # noqa: E402


class CommercialMetricsTests(unittest.TestCase):
    def snapshot(self) -> dict:
        return {
            "schema_version": 1,
            "period_start": "2026-08-01",
            "period_end": "2026-08-31",
            "funnel": {
                "plan_access_views": 200,
                "purchase_starts": 80,
                "purchase_successes": 40,
                "purchase_failures": 40,
                "restore_starts": 10,
                "restore_successes": 8,
            },
            "store": {
                "active_start": 100,
                "active_end": 120,
                "new_paid": 40,
                "cancellations": 10,
                "refunds": 2,
                "renewals": 90,
            },
            "active_product_mix": {
                "pro_monthly": 50,
                "pro_annual": 30,
                "coach_monthly": 20,
                "coach_annual": 20,
            },
            "support": {
                "purchase_tickets": 4,
                "restore_tickets": 2,
            },
        }

    def test_report_computes_aggregate_kpis(self) -> None:
        data = self.snapshot()
        commercial_metrics.validate_snapshot(data)
        report = commercial_metrics.render_report(data)

        self.assertIn("Paywall -> checkout start: 40.0%", report)
        self.assertIn("Checkout success: 50.0%", report)
        self.assertIn("Annual mix: 41.7%", report)
        self.assertIn("Net active subscriber change: +20", report)

    def test_sensitive_user_level_keys_are_rejected(self) -> None:
        data = self.snapshot()
        data["user_id"] = "should-never-be-here"

        with self.assertRaisesRegex(ValueError, "sensitive/user-level keys"):
            commercial_metrics.validate_snapshot(data)

    def test_counts_must_be_non_negative_integers(self) -> None:
        data = self.snapshot()
        data["funnel"]["purchase_successes"] = -1
        commercial_metrics.validate_snapshot(data)

        with self.assertRaisesRegex(ValueError, "non-negative integer"):
            commercial_metrics.render_report(data)


if __name__ == "__main__":
    unittest.main()
