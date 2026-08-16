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

public struct CoachOverviewSummary: Equatable {
    public let totalClients: Int
    public let activeClients: Int
    public let needsCheckInCount: Int
    public let clients: [CoachClientSummary]
}

public enum CoachOverviewBuilder {
    public static func build(
        clients: [CoachClientSnapshot],
        recordsByClient: [UUID: [CoachRecordSnapshot]],
        now: Date,
        calendar: Calendar = .current
    ) -> CoachOverviewSummary {
        let today = calendar.startOfDay(for: now)
        let start = calendar.date(byAdding: .day, value: -6, to: today) ?? today
        let end = calendar.date(byAdding: .day, value: 1, to: today) ?? now
        let summaries = clients.map { client in
            buildClientSummary(client, records: recordsByClient[client.id] ?? [], start: start, end: end)
        }.sorted(by: summaryOrder)
        return CoachOverviewSummary(
            totalClients: summaries.count,
            activeClients: summaries.filter { $0.takenCount + $0.skippedCount > 0 }.count,
            needsCheckInCount: summaries.filter { $0.needsCheckIn }.count,
            clients: summaries
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
        let total = taken + skipped
        let completion = total == 0 ? nil : Int((Double(taken) / Double(total)) * 100)
        return CoachClientSummary(
            clientId: client.id,
            name: client.name,
            takenCount: taken,
            skippedCount: skipped,
            completionPercent: completion,
            lastActivity: window.map(\.date).max(),
            needsCheckIn: total >= 3 && (completion ?? 100) < 70
        )
    }

    private static func summaryOrder(_ lhs: CoachClientSummary, _ rhs: CoachClientSummary) -> Bool {
        if lhs.needsCheckIn != rhs.needsCheckIn { return lhs.needsCheckIn }
        let lhsCompletion = lhs.completionPercent ?? Int.max
        let rhsCompletion = rhs.completionPercent ?? Int.max
        if lhsCompletion != rhsCompletion { return lhsCompletion < rhsCompletion }
        return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
    }
}
