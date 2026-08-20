import XCTest
@testable import OAKHealthy

final class CoachWorkspaceTests: XCTestCase {
    func testDetailComparesOnlySameClientPreviousWindow() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 8, day: 17, hour: 12)))
        let client = CoachClientSnapshot(id: UUID(), name: "Alex")
        func day(_ offset: Int) -> Date { calendar.date(byAdding: .day, value: offset, to: now)! }
        let records = [
            CoachRecordSnapshot(date: day(0), status: "Taken"),
            CoachRecordSnapshot(date: day(-1), status: "Taken"),
            CoachRecordSnapshot(date: day(-2), status: "Skipped"),
            CoachRecordSnapshot(date: day(-7), status: "Taken"),
            CoachRecordSnapshot(date: day(-8), status: "Skipped"),
            CoachRecordSnapshot(date: day(-9), status: "Skipped")
        ]
        let detail = CoachWorkspaceBuilder.buildDetail(
            client: client,
            records: records,
            now: now,
            calendar: calendar,
            windowDays: 7
        )
        XCTAssertEqual(detail.current.completionPercent, 66)
        XCTAssertEqual(detail.previous.completionPercent, 33)
        XCTAssertEqual(detail.completionDeltaPoints, 33)
        XCTAssertEqual(detail.current.activeDays, 3)
    }

    func testMissingPreviousWindowHasNoDelta() {
        let client = CoachClientSnapshot(id: UUID(), name: "Casey")
        let detail = CoachWorkspaceBuilder.buildDetail(
            client: client,
            records: [CoachRecordSnapshot(date: .now, status: "Taken")],
            now: .now,
            windowDays: 30
        )
        XCTAssertNil(detail.previous.completionPercent)
        XCTAssertNil(detail.completionDeltaPoints)
    }

    func testCheckInPolicyBoundsNoteAndHistory() {
        var entries: [CoachCheckInEntry] = []
        for index in 0..<25 {
            let entry = CoachCheckInEntry(
                epochMs: Int64(index),
                feeling: .okay,
                note: String(repeating: "x", count: 700)
            )
            entries = CoachCheckInPolicy.adding(entry, to: entries)
        }
        XCTAssertEqual(entries.count, 20)
        XCTAssertEqual(entries.first?.note.count, 500)
    }
}
