import unittest

import scripts.editorial_design_gate as gate


class EditorialDesignGateTests(unittest.TestCase):
    def test_repository_contract_passes(self) -> None:
        gate.main()

    def test_required_paths_exist(self) -> None:
        for path in gate.REQUIRED:
            self.assertTrue(path.exists(), path)

    def test_shared_backgrounds_do_not_restore_glow_or_glass(self) -> None:
        android_background = gate.read(gate.ANDROID_BACKGROUND)
        ios_card = gate.read(gate.IOS_CARD)
        self.assertIn("SolidColor", android_background)
        self.assertNotIn("ultraThinMaterial", ios_card)
        self.assertNotIn(".blur(", ios_card)


if __name__ == "__main__":
    unittest.main()
