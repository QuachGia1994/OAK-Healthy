import XCTest
@testable import OAKHealthy

final class NotificationRecoveryTests: XCTestCase {
    func testHealthyScheduleDoesNothing() {
        let decision = NotificationRecoveryPolicy.decide(input())

        XCTAssertEqual(decision, NotificationRecoveryDecision(action: .none, reason: .healthy))
    }

    func testPendingOnlyRepairsShadowWithoutRebuildingOSRequests() {
        let decision = NotificationRecoveryPolicy.decide(input(pendingOnlyCount: 2))

        XCTAssertEqual(decision, NotificationRecoveryDecision(action: .repairShadow, reason: .shadowDrift))
    }

    func testShadowOnlyRebuildsMissingOSRequests() {
        let decision = NotificationRecoveryPolicy.decide(input(shadowOnlyCount: 1))

        XCTAssertEqual(decision, NotificationRecoveryDecision(action: .rebuild, reason: .missingSchedules))
    }

    func testEnvironmentChangeRebuildsWhenRemindersAreActive() {
        let decision = NotificationRecoveryPolicy.decide(input(environmentChanged: true))

        XCTAssertEqual(decision, NotificationRecoveryDecision(action: .rebuild, reason: .environmentChanged))
    }

    func testZeroFutureScheduleDoesNotAssumeCorruption() {
        let decision = NotificationRecoveryPolicy.decide(input(pendingCount: 0))

        XCTAssertEqual(decision, NotificationRecoveryDecision(action: .none, reason: .noFutureSchedule))
    }

    func testDeniedPermissionNeverRebuilds() {
        let decision = NotificationRecoveryPolicy.decide(input(permissionGranted: false, shadowOnlyCount: 2))

        XCTAssertEqual(decision, NotificationRecoveryDecision(action: .none, reason: .inactive))
    }

    private func input(
        enabledByUser: Bool = true,
        permissionGranted: Bool = true,
        activeSupplementCount: Int = 2,
        pendingCount: Int = 4,
        pendingOnlyCount: Int = 0,
        shadowOnlyCount: Int = 0,
        shadowErrorCount: Int = 0,
        environmentChanged: Bool = false
    ) -> NotificationRecoveryInput {
        NotificationRecoveryInput(
            enabledByUser: enabledByUser,
            permissionGranted: permissionGranted,
            activeSupplementCount: activeSupplementCount,
            pendingCount: pendingCount,
            pendingOnlyCount: pendingOnlyCount,
            shadowOnlyCount: shadowOnlyCount,
            shadowErrorCount: shadowErrorCount,
            environmentChanged: environmentChanged
        )
    }
}
