import unittest

from scripts import product_readiness


class ProductReadinessTest(unittest.TestCase):
    def test_repository_readiness_passes(self):
        self.assertEqual([], product_readiness.run())

    def test_duplicate_scenario_ids_fail(self):
        payload = product_readiness.load_scenarios()
        payload["scenarios"].append(dict(payload["scenarios"][0]))

        errors = product_readiness.validate_scenarios(payload)

        self.assertTrue(any("unique" in error for error in errors))

    def test_invalid_catalog_metadata_fails(self):
        payload = product_readiness.load_scenarios()
        payload["synthetic_only"] = False

        errors = product_readiness.validate_scenarios(payload)

        self.assertTrue(any("metadata" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
