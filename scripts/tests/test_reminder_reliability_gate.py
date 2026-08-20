from __future__ import annotations

import unittest

from scripts.reminder_reliability_gate import GateError, _assert_no_intake_mutation, _function_slice, validate_repository


class ReminderReliabilityGateTests(unittest.TestCase):
    def test_repository_wiring_passes(self) -> None:
        validate_repository()

    def test_intake_mutation_is_rejected(self) -> None:
        with self.assertRaisesRegex(GateError, "mutate intake history"):
            _assert_no_intake_mutation("persistDose()", "test recovery")

    def test_missing_function_boundary_is_rejected(self) -> None:
        with self.assertRaisesRegex(GateError, "boundary"):
            _function_slice("private func reconcile() {}", "private func reconcile()", "private func next()")


if __name__ == "__main__":
    unittest.main()
