import XCTest
@testable import OAKHealthy

final class ActiveProfileNotificationPolicyTests: XCTestCase {
    private let activeClientId = UUID(uuid: (1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
    private let otherClientId = UUID(uuid: (2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2))

    func testMatchingClientIsAllowed() {
        XCTAssertTrue(ActiveProfileNotificationPolicy.allows(
            activeClientId: activeClientId,
            supplementClientId: activeClientId
        ))
    }

    func testMissingOrDifferentClientIsRejected() {
        XCTAssertFalse(ActiveProfileNotificationPolicy.allows(activeClientId: nil, supplementClientId: activeClientId))
        XCTAssertFalse(ActiveProfileNotificationPolicy.allows(activeClientId: activeClientId, supplementClientId: nil))
        XCTAssertFalse(ActiveProfileNotificationPolicy.allows(activeClientId: activeClientId, supplementClientId: otherClientId))
    }
}
