import unittest

from scripts import p11_completion_gate


class P11CompletionGateTests(unittest.TestCase):
    def test_dark_text_contrast_budget(self) -> None:
        self.assertGreaterEqual(p11_completion_gate.contrast_ratio("AAB5AE", "111714"), 4.5)
        self.assertGreaterEqual(p11_completion_gate.contrast_ratio("F2F5F3", "0B0F0D"), 7.0)

    def test_hairline_contrast_budget(self) -> None:
        self.assertGreaterEqual(p11_completion_gate.contrast_ratio("405047", "111714"), 2.0)

    def test_required_stage_files_exist(self) -> None:
        for path in p11_completion_gate.REQUIRED:
            self.assertTrue(path.exists(), str(path))


if __name__ == "__main__":
    unittest.main()
