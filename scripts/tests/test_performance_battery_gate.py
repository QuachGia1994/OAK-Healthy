import unittest

from scripts import performance_battery_gate as gate


class PerformanceBatteryGateTests(unittest.TestCase):
    def test_repository_contract_passes(self) -> None:
        gate.validate_repository()

    def test_android_hot_paths_are_bounded(self) -> None:
        history = gate.read("Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryViewModel.kt")
        coach = gate.read("Android/app/src/main/java/com/example/supplementtracker/service/CoachWorkspaceSource.kt")
        self.assertIn("getRecordsByDateRange", history)
        self.assertIn("getRecordsByDateRange", coach)
        self.assertNotIn("observeAllRecordsByClient(id)", history)
        self.assertNotIn("getAllRecordsByClient", coach)

    def test_ios_hot_paths_use_fetch_limits(self) -> None:
        store = gate.read("iOS/Services/ClientScopedStore.swift")
        self.assertIn("descriptor.fetchLimit = max(0, limit)", store)
        self.assertIn("descriptor.fetchLimit = 1", store)
        self.assertNotIn("flatMap(\\.intakeRecords)", store)


if __name__ == "__main__":
    unittest.main()
