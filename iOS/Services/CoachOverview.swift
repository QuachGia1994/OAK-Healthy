import Foundation

public struct CoachClientSnapshot: Equatable {
    public let id: UUID
    public let name: String

    public init(id: UUID, name: String) {
        self.id = id
        self.name = name
    }
}

public struct CoachRecordSnapshot: Equatable {
    public let date: Date
    public let status: String

    public init(date: Date, status: String) {
        self.date = date
        self.status = status
    }
}

public struct CoachClientSummary: Equatable, Identifiable {
    public var id: UUID { clientId }
    public let clientId: UUID
    public let name: String
    public let takenCount: Int
    public let skippedCount: Int
    public let completionPercent: Int?
    public let lastActivity: Date?
    public let needsCheckIn: Bool
}

public struct CoachTrendPoint: Equatable, Identifiable, Sendable {
    public var id: Date { bucketStart }
    public let bucketStart: Date
    public let takenCount: Int
    public let skippedCount: Int
    public let completionPercent: Int?
}

public struct CoachOverviewSummary: Equatable {
    public let totalClients: Int
    public let activeClients: Int
    public let needsCheckInCount: Int
    public let clients: [CoachClientSummary]
    public let windowDays: Int
    public let takenCount: Int
    public let skippedCount: Int
    public let overallCompletionPercent: Int?
    public let trend: [CoachTrendPoint]
}

public enum CoachOverviewBuilder {
    public static func build(
        clients: [CoachClientSnapshot],
        recordsByClient: [UUID: [CoachRecordSnapshot]],
        now: Date,
        calendar: Calendar = .current,
        windowDays: Int = 7
    ) -> CoachOverviewSummary {
        let safeWindowDays = normalizeWindowDays(windowDays)
        let today = calendar.startOfDay(for: now)
        let start = calendar.date(byAdding: .day, value: -(safeWindowDays - 1), to: today) ?? today
        let end = calendar.date(byAdding: .day, value: 1, to: today) ?? now
        let summaries = clients.map { client in
            buildClientSummary(client, records: recordsByClient[client.id] ?? [], start: start, end: end)
        }.sorted(by: summaryOrder)
        let windowRecords = recordsByClient.values.flatMap { $0 }.filter { $0.date >= start && $0.date < end }
        return makeOverview(summaries, records: windowRecords, start: start, windowDays: safeWindowDays, calendar: calendar)
    }

    private static func makeOverview(
        _ summaries: [CoachClientSummary],
        records: [CoachRecordSnapshot],
        start: Date,
        windowDays: Int,
        calendar: Calendar
    ) -> CoachOverviewSummary {
        let taken = records.filter { $0.status == IntakeStatus.taken.rawValue }.count
        let skipped = records.filter { $0.status == IntakeStatus.skipped.rawValue }.count
        return CoachOverviewSummary(
            totalClients: summaries.count,
            activeClients: summaries.filter { $0.takenCount + $0.skippedCount > 0 }.count,
            needsCheckInCount: summaries.filter { $0.needsCheckIn }.count,
            clients: summaries,
            windowDays: windowDays,
            takenCount: taken,
            skippedCount: skipped,
            overallCompletionPercent: completionPercent(taken: taken, skipped: skipped),
            trend: buildTrend(records: records, start: start, windowDays: windowDays, calendar: calendar)
        )
    }

    private static func buildClientSummary(
        _ client: CoachClientSnapshot,
        records: [CoachRecordSnapshot],
        start: Date,
        end: Date
    ) -> CoachClientSummary {
        let window = records.filter { $0.date >= start && $0.date < end }
        let taken = window.filter { $0.status == IntakeStatus.taken.rawValue }.count
        let skipped = window.filter { $0.status == IntakeStatus.skipped.rawValue }.count
        let completion = completionPercent(taken: taken, skipped: skipped)
        return CoachClientSummary(
            clientId: client.id,
            name: client.name,
            takenCount: taken,
            skippedCount: skipped,
            completionPercent: completion,
            lastActivity: window.map(\.date).max(),
            needsCheckIn: taken + skipped >= 3 && (completion ?? 100) < 70
        )
    }

    private static func buildTrend(
        records: [CoachRecordSnapshot],
        start: Date,
        windowDays: Int,
        calendar: Calendar
    ) -> [CoachTrendPoint] {
        let bucketDays = windowDays <= 7 ? 1 : 7
        let bucketCount = max(1, (windowDays + bucketDays - 1) / bucketDays)
        return (0..<bucketCount).map { index in
            let bucketStart = calendar.date(byAdding: .day, value: index * bucketDays, to: start) ?? start
            let bucketEnd = calendar.date(byAdding: .day, value: bucketDays, to: bucketStart) ?? bucketStart
            return trendPoint(records: records.filter { $0.date >= bucketStart && $0.date < bucketEnd }, start: bucketStart)
        }
    }

    private static func trendPoint(records: [CoachRecordSnapshot], start: Date) -> CoachTrendPoint {
        let taken = records.filter { $0.status == IntakeStatus.taken.rawValue }.count
        let skipped = records.filter { $0.status == IntakeStatus.skipped.rawValue }.count
        return CoachTrendPoint(
            bucketStart: start,
            takenCount: taken,
            skippedCount: skipped,
            completionPercent: completionPercent(taken: taken, skipped: skipped)
        )
    }

    private static func completionPercent(taken: Int, skipped: Int) -> Int? {
        let total = taken + skipped
        return total == 0 ? nil : Int((Double(taken) / Double(total)) * 100)
    }

    private static func normalizeWindowDays(_ windowDays: Int) -> Int {
        [7, 30, 90].contains(windowDays) ? windowDays : 7
    }

    private static func summaryOrder(_ lhs: CoachClientSummary, _ rhs: CoachClientSummary) -> Bool {
        if lhs.needsCheckIn != rhs.needsCheckIn { return lhs.needsCheckIn }
        let lhsCompletion = lhs.completionPercent ?? Int.max
        let rhsCompletion = rhs.completionPercent ?? Int.max
        if lhsCompletion != rhsCompletion { return lhsCompletion < rhsCompletion }
        return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
    }
}
