import XCTest
@testable import OAKHealthy

final class DiagnosticsPrivacyPolicyTests: XCTestCase {
    func testUnknownEventsAreRejected() {
        XCTAssertNil(DiagnosticsPrivacyPolicy.sanitize(event: "supplement_taken", fields: [:]))
    }

    func testSensitiveFieldsAreDropped() throws {
        let sanitized = try XCTUnwrap(DiagnosticsPrivacyPolicy.sanitize(
            event: "plan_access_view",
            fields: [
                "plan": "PRO",
                "client_id": "00000000-0000-0000-0000-000000000001",
                "supplement": "Vitamin D3",
                "error": "raw server body"
            ]
        ))

        XCTAssertEqual(sanitized.0, "plan_access_view")
        XCTAssertEqual(sanitized.1, ["plan": "pro"])
    }

    func testValuesAreNormalizedAndBounded() throws {
        let sanitized = try XCTUnwrap(DiagnosticsPrivacyPolicy.sanitize(
            event: "billing_purchase_started",
            fields: ["billing_period": "PRO Annual / Storefront with spaces and extra text"]
        ))
        let value = try XCTUnwrap(sanitized.1["billing_period"])

        XCTAssertLessThanOrEqual(value.count, 40)
        XCTAssertNotNil(value.range(of: #"^[a-z0-9_-]*$"#, options: .regularExpression))
    }
}
