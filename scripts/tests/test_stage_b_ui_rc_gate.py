import unittest

from scripts import stage_b_ui_rc_gate


class StageBUiReleaseCandidateGateTests(unittest.TestCase):
    def test_stage_b_contract(self) -> None:
        stage_b_ui_rc_gate.main()


if __name__ == "__main__":
    unittest.main()
