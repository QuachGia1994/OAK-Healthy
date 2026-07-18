import XCTest
@testable import OAKHealthy

final class NotificationDoseActionTests: XCTestCase {
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: "NotificationDoseActionTests")
        defaults.removePersistentDomain(forName: "NotificationDoseActionTests")
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: "NotificationDoseActionTests")
        defaults = nil
        super.tearDown()
    }

    func testPendingActionRoundTrip() {
        let action = makeAction(identifier: NotificationService.Action.taken.rawValue)

        XCTAssertTrue(action.savePending(defaults: defaults))
        XCTAssertEqual(NotificationDoseAction.pending(defaults: defaults), action)
    }

    func testClearOnlyRemovesMatchingAction() {
        let stored = makeAction(identifier: NotificationService.Action.taken.rawValue)
        let different = makeAction(identifier: NotificationService.Action.skipped.rawValue)
        XCTAssertTrue(stored.savePending(defaults: defaults))

        different.clearPending(defaults: defaults)
        XCTAssertEqual(NotificationDoseAction.pending(defaults: defaults), stored)

        stored.clearPending(defaults: defaults)
        XCTAssertNil(NotificationDoseAction.pending(defaults: defaults))
    }

    private func makeAction(identifier: String) -> NotificationDoseAction {
        NotificationDoseAction(
            supplementId: UUID(uuidString: "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE") ?? UUID(),
            intakeTime: "08:00",
            actionIdentifier: identifier,
            requestIdentifier: "request-1",
            scheduledAtEpochMs: 1_750_000_000_000
        )
    }
}
