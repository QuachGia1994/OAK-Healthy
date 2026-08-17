import tempfile
import unittest
from pathlib import Path

import scripts.activation_retention_gate as gate


class ActivationRetentionGateTests(unittest.TestCase):
    def test_repository_contract_passes(self) -> None:
        gate.main()

    def test_required_paths_exist(self) -> None:
        for path in gate.REQUIRED:
            self.assertTrue(path.exists(), path)

    def test_activation_store_does_not_define_sensitive_payload_fields(self) -> None:
        combined = gate.ANDROID.read_text(encoding="utf-8").lower() + gate.IOS.read_text(encoding="utf-8").lower()
        for forbidden in ['"client_id"', '"supplement"', '"dose"', '"note"', '"health"']:
            self.assertNotIn(forbidden, combined)


if __name__ == "__main__":
    unittest.main()
