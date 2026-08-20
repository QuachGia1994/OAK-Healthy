import unittest

from scripts.verify_xcode_test_run import verify


class VerifyXcodeTestRunTests(unittest.TestCase):
    def test_accepts_passing_suite_even_when_runner_exit_is_handled_elsewhere(self) -> None:
        log = """
Test Suite 'Selected tests' passed at 2026-08-17 05:45:04.202.
    Executed 105 tests, with 0 failures (0 unexpected) in 121.311 seconds
"""
        ok, reason = verify(log, xcresult_exists=True)
        self.assertTrue(ok)
        self.assertIn("105 tests passed", reason)

    def test_rejects_failed_suite(self) -> None:
        log = """
Test Suite 'Selected tests' failed at 2026-08-17 05:45:04.202.
    Executed 105 tests, with 1 failure (0 unexpected) in 121.311 seconds
"""
        ok, reason = verify(log, xcresult_exists=True)
        self.assertFalse(ok)
        self.assertIn("failed", reason)

    def test_rejects_missing_xcresult(self) -> None:
        log = """
Test Suite 'Selected tests' passed at 2026-08-17 05:45:04.202.
    Executed 105 tests, with 0 failures (0 unexpected) in 121.311 seconds
"""
        ok, reason = verify(log, xcresult_exists=False)
        self.assertFalse(ok)
        self.assertIn("xcresult", reason)

    def test_rejects_zero_tests(self) -> None:
        log = """
Test Suite 'Selected tests' passed at 2026-08-17 05:45:04.202.
    Executed 0 tests, with 0 failures (0 unexpected) in 0.100 seconds
"""
        ok, reason = verify(log, xcresult_exists=True)
        self.assertFalse(ok)
        self.assertIn("no tests", reason)

    def test_rejects_missing_selected_suite_marker(self) -> None:
        log = "Executed 105 tests, with 0 failures (0 unexpected) in 121.311 seconds"
        ok, reason = verify(log, xcresult_exists=True)
        self.assertFalse(ok)
        self.assertIn("pass marker", reason)


if __name__ == "__main__":
    unittest.main()
