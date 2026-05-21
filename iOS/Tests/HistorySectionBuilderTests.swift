import XCTest
@testable import OAKHealthy

final class HistorySectionBuilderTests: XCTestCase {
    func testMakeSections_groupsByDayAndPreservesOrder() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!

        let dateA1 = calendar.date(from: DateComponents(year: 2026, month: 5, day: 1, hour: 10, minute: 0))!
        let dateA2 = calendar.date(from: DateComponents(year: 2026, month: 5, day: 1, hour: 12, minute: 0))!
        let dateB1 = calendar.date(from: DateComponents(year: 2026, month: 5, day: 2, hour: 9, minute: 0))!

        let recordA1 = IntakeRecord(id: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!, date: dateA1, status: IntakeStatus.taken.rawValue)
        let recordA2 = IntakeRecord(id: UUID(uuidString: "00000000-0000-0000-0000-0000000000A2")!, date: dateA2, status: IntakeStatus.skipped.rawValue)
        let recordB1 = IntakeRecord(id: UUID(uuidString: "00000000-0000-0000-0000-0000000000B1")!, date: dateB1, status: IntakeStatus.taken.rawValue)

        let sections = HistorySectionBuilder.makeSections(records: [recordB1, recordA2, recordA1], calendar: calendar)

        XCTAssertEqual(sections.count, 2)
        XCTAssertEqual(sections[0].date, calendar.startOfDay(for: dateB1))
        XCTAssertEqual(sections[1].date, calendar.startOfDay(for: dateA1))
        XCTAssertEqual(sections[0].rows.map(\.id), [recordB1.id])
        XCTAssertEqual(sections[1].rows.map(\.id), [recordA2.id, recordA1.id])
    }
}

