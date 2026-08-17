import unittest

from scripts import oak_regression


class OakRegressionTests(unittest.TestCase):
    def test_all_gate_paths_exist(self) -> None:
        for relative in oak_regression.GATES:
            self.assertTrue((oak_regression.ROOT / relative).exists(), relative)

    def test_regression_matrix_includes_p10_hardening(self) -> None:
        self.assertIn("scripts/architecture_boundaries_gate.py", oak_regression.GATES)
        self.assertIn("scripts/performance_battery_gate.py", oak_regression.GATES)
        self.assertIn("scripts/security_hardening_gate.py", oak_regression.GATES)
        self.assertIn("scripts/release_preflight.py", oak_regression.GATES)


if __name__ == "__main__":
    unittest.main()
