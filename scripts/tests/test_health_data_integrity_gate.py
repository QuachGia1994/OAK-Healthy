import unittest

from scripts import health_data_integrity_gate as gate


class HealthDataIntegrityGateTests(unittest.TestCase):
    def test_required_files_exist(self) -> None:
        self.assertEqual(gate.check_required_files(), [])

    def test_android_health_data_contracts(self) -> None:
        self.assertEqual(gate.check_android_contracts(), [])

    def test_ios_health_data_contracts(self) -> None:
        self.assertEqual(gate.check_ios_contracts(), [])

    def test_android_presentation_does_not_mutate_repository_directly(self) -> None:
        self.assertEqual(gate.check_android_presentation_persistence_boundary(), [])

    def test_ios_views_do_not_mutate_swiftdata_directly(self) -> None:
        self.assertEqual(gate.check_ios_view_persistence_boundary(), [])

    def test_health_data_flow_document_covers_owners(self) -> None:
        self.assertEqual(gate.check_docs(), [])


if __name__ == "__main__":
    unittest.main()
