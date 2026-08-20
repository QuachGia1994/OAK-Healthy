import unittest

from scripts import architecture_boundaries_gate as gate


class ArchitectureBoundariesGateTests(unittest.TestCase):
    def test_required_boundary_files_exist(self) -> None:
        for path in gate.FILES.values():
            self.assertTrue(path.exists(), path)

    def test_android_ui_does_not_own_notification_platform_reads(self) -> None:
        content = gate.text("android_notification_ui")
        self.assertIn("AndroidNotificationDiagnosticsSource", content)
        self.assertNotIn("NotificationSchedulerImpl(", content)
        self.assertNotIn("AlarmManager", content)

    def test_ios_views_delegate_sync_and_recovery_persistence(self) -> None:
        sync = gate.text("ios_sync_view")
        app = gate.text("ios_app")
        self.assertIn("SyncCenterStatusReader.read", sync)
        self.assertIn("CloudSyncLogStore", sync)
        self.assertIn("PendingImportRecoveryCoordinator", app)
        self.assertNotIn("private func createImportClient", app)


if __name__ == "__main__":
    unittest.main()
