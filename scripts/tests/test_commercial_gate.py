from __future__ import annotations

import io
import json
import sys
import unittest
from contextlib import redirect_stdout
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

import commercial_gate  # noqa: E402


def validate_quiet(
    data: dict,
    level: str,
    expected_build: str,
    expected_commit: str,
    expected_version: str = "",
) -> list[str]:
    with redirect_stdout(io.StringIO()):
        return commercial_gate.validate(data, level, expected_build, expected_commit, expected_version)


class CommercialGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        template = ROOT / "docs/commercial/STORE_VALIDATION_EVIDENCE.template.json"
        cls.template = json.loads(template.read_text(encoding="utf-8"))

    def passing_evidence(self) -> dict:
        data = deepcopy(self.template)
        data["release_candidate"] = {
            "version": "1.0.1",
            "build": "42",
            "commit": "2e4dd03",
        }
        for platform in commercial_gate.PLATFORMS:
            for entry in data["store_products"][platform].values():
                entry.update(status="pass", reference=f"{platform}-product-evidence")
            for entry in data["beta_validation"][platform].values():
                entry.update(status="pass", reference=f"{platform}-beta-evidence")
            data["offer_strategy"][platform] = {
                "mode": "none",
                "basis_reference": f"{platform}-beta-metrics",
            }
            data["rollout"][platform].update(status="pass", reference=f"{platform}-rollout")
        data["public_urls"] = {
            "privacy_policy": "https://oakhealthy.app/privacy",
            "support": "https://oakhealthy.app/support",
        }
        for entry in data["store_declarations"].values():
            entry.update(status="pass", reference="store-declaration")
        for platform in commercial_gate.PLATFORMS:
            for entry in data["screenshots"][platform].values():
                entry.update(status="pass", reference=f"{platform}-release-screenshot")
        data["rollback"].update(status="pass", reference="rollback-runbook")
        data["support"].update(status="pass", reference="support-runbook")
        return data

    def test_template_does_not_fake_beta_pass(self) -> None:
        failures = validate_quiet(self.template, "beta", "", "")
        self.assertTrue(failures)

    def test_completed_production_evidence_passes(self) -> None:
        failures = validate_quiet(
            self.passing_evidence(),
            "production",
            "42",
            "2e4dd03acf53464e7949f6eb6c0161876b7c9aad",
            "1.0.1",
        )
        self.assertEqual([], failures)

    def test_production_rejects_placeholder_urls(self) -> None:
        data = self.passing_evidence()
        data["public_urls"]["support"] = "https://REPLACE_ME/support"
        failures = validate_quiet(data, "production", "42", "2e4dd03")
        self.assertIn("public support URL is HTTPS", failures)

    def test_build_must_match_tested_candidate(self) -> None:
        failures = validate_quiet(self.passing_evidence(), "beta", "43", "2e4dd03")
        self.assertIn("evidence build matches requested store build", failures)

    def test_offer_basis_cannot_be_placeholder(self) -> None:
        data = self.passing_evidence()
        data["offer_strategy"]["ios"]["basis_reference"] = "REPLACE_WITH_BETA_METRICS"
        failures = validate_quiet(data, "beta", "42", "2e4dd03")
        self.assertIn("ios offer strategy cites beta conversion evidence", failures)

    def test_version_must_match_tested_candidate(self) -> None:
        failures = validate_quiet(self.passing_evidence(), "beta", "42", "2e4dd03", "1.0.2")
        self.assertIn("evidence version matches tested candidate", failures)


if __name__ == "__main__":
    unittest.main()
