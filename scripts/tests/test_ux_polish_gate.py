import unittest

import scripts.ux_polish_gate as gate


class UxPolishGateTests(unittest.TestCase):
    def test_repository_contract_passes(self) -> None:
        gate.main()

    def test_required_paths_exist(self) -> None:
        for path in gate.REQUIRED:
            self.assertTrue(path.exists(), path)

    def test_feedback_keys_exist_in_android_locales_and_ios(self) -> None:
        android_en = gate.ANDROID_EN.read_text(encoding="utf-8")
        android_vi = gate.ANDROID_VI.read_text(encoding="utf-8")
        ios = gate.IOS_LOCALIZATION.read_text(encoding="utf-8")
        for key in gate.FEEDBACK_KEYS:
            self.assertIn(f'name="{key}"', android_en)
            self.assertIn(f'name="{key}"', android_vi)
            self.assertIn(f'"{key}"', ios)

    def test_ios_history_does_not_log_raw_client_id(self) -> None:
        text = gate.IOS_HISTORY.read_text(encoding="utf-8")
        self.assertNotIn('"clientId"', text)
        self.assertIn('"has_client"', text)


if __name__ == "__main__":
    unittest.main()
