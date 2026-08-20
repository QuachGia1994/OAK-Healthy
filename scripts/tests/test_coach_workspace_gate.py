import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class CoachWorkspaceGateTests(unittest.TestCase):
    def test_gate_passes_repository_contract(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts/coach_workspace_gate.py")],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Coach workspace gate passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
