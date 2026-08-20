import unittest

from scripts import health_ui_redesign_gate


class HealthUiRedesignGateTests(unittest.TestCase):
    def test_ui_r1_contract(self) -> None:
        health_ui_redesign_gate.main()


if __name__ == "__main__":
    unittest.main()
