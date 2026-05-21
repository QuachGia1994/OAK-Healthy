import XCTest
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
}

