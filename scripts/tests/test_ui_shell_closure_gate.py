import unittest

from scripts import ui_shell_closure_gate


class UiShellClosureGateTests(unittest.TestCase):
    def test_ui_shell_closure_contract(self) -> None:
        ui_shell_closure_gate.main()


if __name__ == "__main__":
    unittest.main()
