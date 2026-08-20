import Foundation

public struct CoachWindowStats: Equatable, Sendable {
    public let takenCount: Int
    public let skippedCount: Int
    public let completionPercent: Int?
    public let activeDays: Int
}

public struct CoachClientDetail: Equatable, Sendable {
    public let clientId: UUID
    public let name: String
    public let windowDays: Int
    public let current: CoachWindowStats
    public let previous: CoachWindowStats
    public let completionDeltaPoints: Int?
    public let lastActivity: Date?
    public let trend: [CoachTrendPoint]
}

public enum CoachRoutineFeeling: String, Codable, CaseIterable, Hashable, Sendable {
    case comfortable
    case okay
    case difficult
}

public struct CoachCheckInEntry: Codable, Equatable, Identifiable, Sendable {
    public var id: Int64 { epochMs }
    public let epochMs: Int64
    public let feeling: CoachRoutineFeeling
    public let note: String
}

public struct CoachReportDocument: Equatable, Sendable {
    public let clientName: String
    public let windowDays: Int
    public let generatedAtEpochMs: Int64
    public let current: CoachWindowStats
    public let previous: CoachWindowStats
    public let completionDeltaPoints: Int?
    public let trend: [CoachTrendPoint]
    public let checkIns: [CoachCheckInEntry]
}

public protocol CoachReportRenderer {
    associatedtype Output
    func render(_ document: CoachReportDocument) throws -> Output
}

public enum CoachWorkspaceBuilder {
    public static func buildDetail(
        client: CoachClientSnapshot,
        records: [CoachRecordSnapshot],
        now: Date,
        calendar: Calendar = .current,
        windowDays: Int = 7
    ) -> CoachClientDetail {
        let days = normalizeWindowDays(windowDays)
        let today = calendar.startOfDay(for: now)
        let currentStart = calendar.date(byAdding: .day, value: -(days - 1), to: today) ?? today
        let previousStart = calendar.date(byAdding: .day, value: -days, to: currentStart) ?? currentStart
        let previousEnd = currentStart
        let currentEnd = calendar.date(byAdding: .day, value: 1, to: today) ?? now
        let current = records.filter { $0.date >= currentStart && $0.date < currentEnd }
        let previous = records.filter { $0.date >= previousStart && $0.date < previousEnd }
        return detail(client, days: days, current: current, previous: previous, start: currentStart, calendar: calendar)
    }

    public static func reportDocument(
        detail: CoachClientDetail,
        checkIns: [CoachCheckInEntry],
        generatedAtEpochMs: Int64
    ) -> CoachReportDocument {
        CoachReportDocument(
            clientName: detail.name,
            windowDays: detail.windowDays,
            generatedAtEpochMs: generatedAtEpochMs,
            current: detail.current,
            previous: detail.previous,
            completionDeltaPoints: detail.completionDeltaPoints,
            trend: detail.trend,
            checkIns: Array(checkIns.prefix(5))
        )
    }

    private static func detail(
        _ client: CoachClientSnapshot,
        days: Int,
        current: [CoachRecordSnapshot],
        previous: [CoachRecordSnapshot],
        start: Date,
        calendar: Calendar
    ) -> CoachClientDetail {
        let currentStats = stats(current, calendar: calendar)
        let previousStats = stats(previous, calendar: calendar)
        return CoachClientDetail(
            clientId: client.id,
            name: client.name,
            windowDays: days,
            current: currentStats,
            previous: previousStats,
            completionDeltaPoints: delta(currentStats.completionPercent, previousStats.completionPercent),
            lastActivity: current.map(\.date).max(),
            trend: trend(current, start: start, windowDays: days, calendar: calendar)
        )
    }

    private static func stats(_ records: [CoachRecordSnapshot], calendar: Calendar) -> CoachWindowStats {
        let taken = records.filter { $0.status == IntakeStatus.taken.rawValue }.count
        let skipped = records.filter { $0.status == IntakeStatus.skipped.rawValue }.count
        let activeDays = Set(records.map { calendar.startOfDay(for: $0.date) }).count
        return CoachWindowStats(
            takenCount: taken,
            skippedCount: skipped,
            completionPercent: completion(taken: taken, skipped: skipped),
            activeDays: activeDays
        )
    }

    private static func trend(
        _ records: [CoachRecordSnapshot],
        start: Date,
        windowDays: Int,
        calendar: Calendar
    ) -> [CoachTrendPoint] {
        let bucketDays = windowDays <= 7 ? 1 : 7
        let count = max(1, (windowDays + bucketDays - 1) / bucketDays)
        return (0..<count).map { index in
            let bucketStart = calendar.date(byAdding: .day, value: index * bucketDays, to: start) ?? start
            let bucketEnd = calendar.date(byAdding: .day, value: bucketDays, to: bucketStart) ?? bucketStart
            return trendPoint(records.filter { $0.date >= bucketStart && $0.date < bucketEnd }, start: bucketStart)
        }
    }

    private static func trendPoint(_ records: [CoachRecordSnapshot], start: Date) -> CoachTrendPoint {
        let taken = records.filter { $0.status == IntakeStatus.taken.rawValue }.count
        let skipped = records.filter { $0.status == IntakeStatus.skipped.rawValue }.count
        return CoachTrendPoint(
            bucketStart: start,
            takenCount: taken,
            skippedCount: skipped,
            completionPercent: completion(taken: taken, skipped: skipped)
        )
    }

    private static func completion(taken: Int, skipped: Int) -> Int? {
        DoseTimingPolicy.completionPercent(taken: taken, skipped: skipped)
    }

    private static func delta(_ current: Int?, _ previous: Int?) -> Int? {
        guard let current, let previous else { return nil }
        return current - previous
    }

    private static func normalizeWindowDays(_ days: Int) -> Int {
        [7, 30, 90].contains(days) ? days : 7
    }
}

public enum CoachCheckInPolicy {
    public static let maxNoteLength = 500
    public static let maxEntries = 20

    public static func normalizedNote(_ raw: String) -> String {
        String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxNoteLength))
    }

    public static func adding(_ entry: CoachCheckInEntry, to existing: [CoachCheckInEntry]) -> [CoachCheckInEntry] {
        let normalized = CoachCheckInEntry(
            epochMs: entry.epochMs,
            feeling: entry.feeling,
            note: normalizedNote(entry.note)
        )
        var seen = Set<Int64>()
        return ([normalized] + existing).filter { seen.insert($0.epochMs).inserted }.prefix(maxEntries).map { $0 }
    }
}

public enum CoachCheckInStore {
    public static func entries(clientId: UUID, defaults: UserDefaults = .standard) throws -> [CoachCheckInEntry] {
        guard let data = defaults.data(forKey: key(clientId)) else { return [] }
        return try JSONDecoder().decode([CoachCheckInEntry].self, from: data)
    }

    public static func add(
        clientId: UUID,
        feeling: CoachRoutineFeeling,
        note: String,
        epochMs: Int64,
        defaults: UserDefaults = .standard
    ) throws {
        let entry = CoachCheckInEntry(epochMs: epochMs, feeling: feeling, note: note)
        let existing = try entries(clientId: clientId, defaults: defaults)
        let updated = CoachCheckInPolicy.adding(entry, to: existing)
        defaults.set(try JSONEncoder().encode(updated), forKey: key(clientId))
    }

    private static func key(_ clientId: UUID) -> String {
        "coachCheckIns_\(clientId.uuidString.lowercased())"
    }
}
