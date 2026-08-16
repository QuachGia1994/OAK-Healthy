from __future__ import annotations

import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.data_recovery_gate import DEFAULT_MATRIX, GateError, validate_matrix_data


class DataRecoveryGateTests(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture_dir = DEFAULT_MATRIX.parent
        self.matrix = json.loads(DEFAULT_MATRIX.read_text(encoding="utf-8"))

    def test_repository_matrix_passes(self) -> None:
        self.assertEqual(5, validate_matrix_data(self.matrix, self.fixture_dir))

    def test_orphan_history_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            copied = Path(temp) / "data_recovery"
            shutil.copytree(self.fixture_dir, copied)
            path = copied / "android_to_ios_v2.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["historyLogs"][0]["supplementId"] = "missing"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(GateError, "orphaned"):
                validate_matrix_data(self.matrix, copied)

    def test_history_count_regression_is_rejected(self) -> None:
        changed = copy.deepcopy(self.matrix)
        changed["fixtures"][0]["expectedHistory"] = 99
        with self.assertRaisesRegex(GateError, "history count changed"):
            validate_matrix_data(changed, self.fixture_dir)

    def test_incomplete_android_migration_matrix_is_rejected(self) -> None:
        changed = copy.deepcopy(self.matrix)
        changed["databaseMigrations"]["androidRoom"]["supportedSources"] = [2, 3, 4]
        with self.assertRaisesRegex(GateError, "Android migration matrix"):
            validate_matrix_data(changed, self.fixture_dir)


if __name__ == "__main__":
    unittest.main()
