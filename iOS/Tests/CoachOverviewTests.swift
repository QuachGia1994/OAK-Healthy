import XCTest
@testable import OAKHealthy

final class CoachOverviewTests: XCTestCase {
    func testCheckInThresholdAndSevenDayWindow() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let today = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 8, day: 16, hour: 12)))
        let client = CoachClientSnapshot(id: UUID(), name: "Alex")
        func day(_ offset: Int) -> Date {
            calendar.date(byAdding: .day, value: offset, to: today)!
        }
        let records = [
            CoachRecordSnapshot(date: day(0), status: "Taken"),
            CoachRecordSnapshot(date: day(-1), status: "Taken"),
            CoachRecordSnapshot(date: day(-2), status: "Skipped"),
            CoachRecordSnapshot(date: day(-8), status: "Skipped")
        ]

        let result = CoachOverviewBuilder.build(
            clients: [client],
            recordsByClient: [client.id: records],
            now: today,
            calendar: calendar
        )

        let summary = try XCTUnwrap(result.clients.first)
        XCTAssertEqual(summary.takenCount, 2)
        XCTAssertEqual(summary.skippedCount, 1)
        XCTAssertEqual(summary.completionPercent, 66)
        XCTAssertTrue(summary.needsCheckIn)
    }

    func testLowVolumeDoesNotTriggerCheckIn() throws {
        let client = CoachClientSnapshot(id: UUID(), name: "Casey")
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let records = [
            CoachRecordSnapshot(date: now, status: "Skipped"),
            CoachRecordSnapshot(date: now.addingTimeInterval(-86_400), status: "Taken")
        ]

        let result = CoachOverviewBuilder.build(
            clients: [client],
            recordsByClient: [client.id: records],
            now: now
        )

        XCTAssertFalse(try XCTUnwrap(result.clients.first).needsCheckIn)
        XCTAssertEqual(result.activeClients, 1)
        XCTAssertEqual(result.needsCheckInCount, 0)
    }
}
