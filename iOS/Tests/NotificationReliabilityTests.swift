import XCTest
@testable import OAKHealthy

final class NotificationReliabilityTests: XCTestCase {
    func testHealthyWhenPendingAndShadowAgree() {
        let report = NotificationReliabilityEvaluator.evaluate(baseInput())

        XCTAssertEqual(report.level, .healthy)
        XCTAssertFalse(report.shouldOfferRepair)
    }

    func testMismatchRequiresRepair() {
        let report = NotificationReliabilityEvaluator.evaluate(
            input(pendingOnlyCount: 1, shadowOnlyCount: 1)
        )

        XCTAssertEqual(report.level, .needsRepair)
        XCTAssertEqual(report.mismatchCount, 2)
        XCTAssertTrue(report.shouldOfferRepair)
    }

    func testNoPendingSchedulesIsDegradedWithoutAssumingBreakage() {
        let report = NotificationReliabilityEvaluator.evaluate(input(pendingCount: 0))

        XCTAssertEqual(report.level, .degraded)
        XCTAssertFalse(report.shouldOfferRepair)
    }

    func testDisabledNotificationsAreInactive() {
        let report = NotificationReliabilityEvaluator.evaluate(input(enabledByUser: false, pendingCount: 0))

        XCTAssertEqual(report.level, .inactive)
        XCTAssertFalse(report.shouldOfferRepair)
    }

    private func baseInput() -> NotificationReliabilityInput {
        input()
    }

    private func input(
        enabledByUser: Bool = true,
        pendingCount: Int = 4,
        pendingOnlyCount: Int = 0,
        shadowOnlyCount: Int = 0
    ) -> NotificationReliabilityInput {
        NotificationReliabilityInput(
            permissionGranted: true,
            enabledByUser: enabledByUser,
            hasActiveClient: true,
            activeSupplementCount: 2,
            pendingCount: pendingCount,
            pendingOnlyCount: pendingOnlyCount,
            shadowOnlyCount: shadowOnlyCount,
            shadowErrorCount: 0
        )
    }
}
