import XCTest
@testable import OAKHealthy

final class SyncHealthTests: XCTestCase {
    func testUnlinkedIsNeutral() {
        let report = SyncHealthEvaluator.evaluate(input(hasLink: false))
        XCTAssertEqual(report.level, .unlinked)
        XCTAssertEqual(report.action, .none)
    }

    func testPendingChangesOfferSyncNow() {
        let report = SyncHealthEvaluator.evaluate(input(hasPendingChanges: true))
        XCTAssertEqual(report.level, .pending)
        XCTAssertEqual(report.action, .syncNow)
    }

    func testMissingKeyOffersImportKey() {
        let report = SyncHealthEvaluator.evaluate(
            input(lastError: "Missing cloud sync key", encryptionEnabled: true)
        )
        XCTAssertEqual(report.level, .needsKey)
        XCTAssertEqual(report.action, .importKey)
    }

    func testNetworkFailureIsRetryable() {
        let report = SyncHealthEvaluator.evaluate(input(lastError: "Network timeout"))
        XCTAssertEqual(report.level, .retryableError)
        XCTAssertEqual(report.action, .syncNow)
    }

    func testSuccessfulSyncIsHealthy() {
        let report = SyncHealthEvaluator.evaluate(input())
        XCTAssertEqual(report.level, .healthy)
        XCTAssertEqual(report.action, .none)
    }

    private func input(
        hasLink: Bool = true,
        hasPendingChanges: Bool = false,
        lastError: String? = nil,
        encryptionEnabled: Bool = true
    ) -> SyncHealthInput {
        SyncHealthInput(
            hasLink: hasLink,
            autoSyncEnabled: true,
            hasPendingChanges: hasPendingChanges,
            lastSyncEpochMs: 1_000,
            lastAttemptEpochMs: 1_000,
            lastError: lastError,
            encryptionEnabled: encryptionEnabled
        )
    }
}
