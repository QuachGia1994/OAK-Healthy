import unittest

from scripts import security_hardening_gate as gate


class SecurityHardeningGateTests(unittest.TestCase):
    def test_repository_security_contract_passes(self) -> None:
        gate.validate_repository()

    def test_firebase_rules_require_auth_and_monotonic_revision(self) -> None:
        rules = gate.read("rules")
        self.assertGreaterEqual(rules.count('"auth != null"'), 2)
        self.assertIn("newData.val() > data.val()", rules)
        self.assertIn("newData.val().length <= 1048576", rules)

    def test_diagnostics_do_not_allow_sensitive_health_identity_fields(self) -> None:
        combined = (gate.read("android_diag") + gate.read("ios_diag")).lower()
        for key in gate.SENSITIVE_ANALYTICS_KEYS:
            self.assertNotIn(key, combined)

    def test_notification_diagnostics_do_not_expose_client_uuid(self) -> None:
        notification_diag = gate.read("ios_notification_diag")
        self.assertNotIn("activeClientManager.currentClientId?.uuidString", notification_diag)

    def test_crypto_tamper_regressions_exist_on_both_platforms(self) -> None:
        self.assertIn("rejectsTamperedCiphertext", gate.read("android_crypto_test"))
        self.assertIn("testRejectsTamperedCiphertext", gate.read("ios_crypto_test"))


if __name__ == "__main__":
    unittest.main()
