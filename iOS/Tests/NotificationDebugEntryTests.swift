import XCTest
@testable import OAKHealthy

final class NotificationDebugEntryTests: XCTestCase {
    func testParseMany_raw_withFiveParts() {
        let raw = ["id||Name||Dose||Cycle||2026-05-22 10:00"]
        let items = NotificationDebugEntry.parseMany(raw)
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items[0].id, "id")
        XCTAssertEqual(items[0].name, "Name")
        XCTAssertEqual(items[0].dose, "Dose")
        XCTAssertEqual(items[0].cycleText, "Cycle")
    }

    func testParseMany_raw_withLegacyFourParts() {
        let raw = ["Name||Dose||Cycle||2026-05-22 10:00"]
        let items = NotificationDebugEntry.parseMany(raw)
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items[0].name, "Name")
        XCTAssertEqual(items[0].dose, "Dose")
        XCTAssertEqual(items[0].cycleText, "Cycle")
    }

    func testParseMany_raw_withLegacyPipeFormat() {
        let raw = ["Name | 2026-05-22 10:00"]
        let items = NotificationDebugEntry.parseMany(raw)
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items[0].name, "Name")
        XCTAssertEqual(items[0].dose, "")
        XCTAssertEqual(items[0].cycleText, "")
    }

    func testParseMany_ignoresInvalid() {
        let raw = ["", "bad||date"]
        let items = NotificationDebugEntry.parseMany(raw)
        XCTAssertEqual(items.count, 0)
    }
}
