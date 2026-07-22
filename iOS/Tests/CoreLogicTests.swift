import XCTest
import SwiftData
@testable import OAKHealthy

final class DoseEventKeyTests: XCTestCase {
    func testMakeDeterministic() {
        let supplementId = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let scheduledAtEpochMs: Int64 = 1_700_000_000_000

        let key1 = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)
        let key2 = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)

        XCTAssertEqual(key1, key2)
    }

    func testStableUUIDDeterministic() {
        let key = "supplement-1|1700000000000"

        let uuid1 = DoseEventKey.stableUUID(from: key)
        let uuid2 = DoseEventKey.stableUUID(from: key)

        XCTAssertEqual(uuid1, uuid2)
    }
}

final class TimeStringsTests: XCTestCase {
    func testNormalizeListTrimsSortsAndDedups() {
        let result = TimeStrings.normalizeList(" 7:05, 07:05 ; 21:30 | 21:30 ")
        XCTAssertEqual(["07:05", "21:30"], result)
    }

    func testParseLenientTimeRejectsOutOfRange() {
        XCTAssertNil(TimeStrings.parseLenientTime("24:00"))
        XCTAssertNil(TimeStrings.parseLenientTime("23:60"))
    }

    func testRemovingTimeKeepsOtherDoseTimes() {
        let result = TimeStrings.removingTime("7:00", from: "07:00, 14:30")
        XCTAssertEqual(["14:30"], result)
    }

    func testRemovingLastTimeLeavesEmptySchedule() {
        let result = TimeStrings.removingTime("07:00", from: "7:00")
        XCTAssertEqual([], result)
    }
}

@MainActor
final class HomeDoseTimeDeletionTests: XCTestCase {
    func testDeletingLastDoseTimeKeepsSupplement() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let supplement = UserSupplement(
            name: "Caffeine",
            startDate: .now,
            cycleConfig: .continuous,
            dailyDose: "200 mg",
            intakeTime: "07:00"
        )
        context.insert(supplement)
        try context.save()

        let viewModel = HomeViewModel()
        viewModel.processSupplements([supplement])
        viewModel.deleteDoseTime(
            supplement,
            timeString: "07:00",
            context: context,
            notificationService: NotificationService()
        )

        let stored = try context.fetch(FetchDescriptor<UserSupplement>())
        XCTAssertEqual("", supplement.intakeTime)
        XCTAssertNil(supplement.deletedAtEpochMs)
        XCTAssertEqual(1, stored.count)
    }

    private func makeContainer() throws -> ModelContainer {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(
            for: ClientProfile.self,
            UserSupplement.self,
            IntakeRecord.self,
            configurations: configuration
        )
    }
}
