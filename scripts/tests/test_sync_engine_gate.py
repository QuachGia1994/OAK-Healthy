from __future__ import annotations

import unittest

from scripts.sync_engine_gate import GateError, _reject, _require, validate


class SyncEngineGateTests(unittest.TestCase):
    def test_repository_sync_engine_contract_passes(self) -> None:
        validate()

    def test_missing_required_hook_fails_closed(self) -> None:
        with self.assertRaises(GateError):
            _require("class Engine {}", "SyncBackoffPolicy.canAttempt", "backoff")

    def test_payload_field_in_queue_is_rejected(self) -> None:
        with self.assertRaises(GateError):
            _reject("val dailyDose = value", ("dailyDose",), "queue")


if __name__ == "__main__":
    unittest.main()
