import unittest

from scripts import store_activation_gate


class StoreActivationGateTests(unittest.TestCase):
    def test_gate_main_passes(self) -> None:
        store_activation_gate.main()

    def test_product_catalog_is_stable(self) -> None:
        self.assertEqual(
            set(store_activation_gate.PRODUCT_IDS),
            {"oak_pro_monthly", "oak_pro_annual", "oak_coach_monthly", "oak_coach_annual"},
        )


if __name__ == "__main__":
    unittest.main()
