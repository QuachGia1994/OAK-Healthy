import Foundation
import Observation
import SwiftData

/// Dữ liệu điểm trên biểu đồ.
public struct ChartData: Identifiable, Sendable {
    public let id = UUID()
    public let date: Date
    public let count: Int
}

public struct InsightsItem: Identifiable, Sendable {
    public let id = UUID()
    public let title: String
    public let count: Int
}

public struct InsightsSummary: Sendable {
    public let windowDays: Int
    public let takenCount: Int
    public let skippedCount: Int
    public let lateCount: Int
    public let completionRate: Double
    public let topSkipped: [InsightsItem]
    public let topLate: [InsightsItem]
    public let topLateHour: InsightsItem?
}

/// ViewModel xử lý logic lịch sử uống trên iOS.
@Observable
@MainActor
public final class HistoryViewModel {
    // MARK: - State
    public var weeklyData: [ChartData] = []
    public var insights7: InsightsSummary?
    public var insights30: InsightsSummary?
    
    public init() {}
    
    // MARK: - Logic
    
    /// Tổng hợp dữ liệu uống theo tuần.
    public func processHistory(records: [IntakeRecord]) {
        weeklyData = weeklyChartData(records: records)
        insights7 = buildInsights(records: records, windowDays: 7)
        insights30 = buildInsights(records: records, windowDays: 30)
    }

    private func weeklyChartData(records: [IntakeRecord]) -> [ChartData] {
        let calendar = Calendar.current
        let todayStart = calendar.startOfDay(for: .now)
        var counts: [Date: Int] = [:]
        counts.reserveCapacity(7)
        for record in records {
            let day = calendar.startOfDay(for: record.date)
            counts[day, default: 0] += 1
        }
        return (0..<7).reversed().compactMap { offset in
            guard let date = calendar.date(byAdding: .day, value: -offset, to: todayStart) else { return nil }
            return ChartData(date: date, count: counts[date] ?? 0)
        }
    }

    private func buildInsights(records: [IntakeRecord], windowDays: Int) -> InsightsSummary? {
        guard windowDays > 0 else { return nil }
        let calendar = Calendar.current
        let todayStart = calendar.startOfDay(for: .now)
        guard let start = calendar.date(byAdding: .day, value: -(windowDays - 1), to: todayStart) else { return nil }
        let windowRecords = records.filter {
            let day = calendar.startOfDay(for: $0.date)
            return day >= start && day <= todayStart
        }
        if windowRecords.isEmpty { return nil }
        let taken = windowRecords.filter { $0.status == IntakeStatus.taken.rawValue }.count
        let skipped = windowRecords.filter { $0.status == IntakeStatus.skipped.rawValue }.count
        let late = windowRecords.filter(isLateTaken).count
        let denom = max(1, taken + skipped)
        let completionRate = Double(taken) / Double(denom)
        return InsightsSummary(
            windowDays: windowDays,
            takenCount: taken,
            skippedCount: skipped,
            lateCount: late,
            completionRate: completionRate,
            topSkipped: topItems(records: windowRecords, status: IntakeStatus.skipped.rawValue),
            topLate: topLateItems(records: windowRecords),
            topLateHour: topLateHour(records: windowRecords)
        )
    }

    private func isLateTaken(_ record: IntakeRecord) -> Bool {
        guard record.status == IntakeStatus.taken.rawValue else { return false }
        guard record.updatedAtEpochMs > 0 else { return false }
        let scheduled = Int64(record.date.timeIntervalSince1970 * 1000)
        let threshold = scheduled + 20 * 60 * 1000
        return record.updatedAtEpochMs > threshold
    }

    private func topItems(records: [IntakeRecord], status: String) -> [InsightsItem] {
        let groups = Dictionary(grouping: records.filter { $0.status == status }) { $0.supplement?.name ?? "not_available".localized }
        return groups
            .map { InsightsItem(title: $0.key, count: $0.value.count) }
            .sorted { $0.count > $1.count }
            .prefix(3)
            .map { $0 }
    }

    private func topLateItems(records: [IntakeRecord]) -> [InsightsItem] {
        let groups = Dictionary(grouping: records.filter(isLateTaken)) { $0.supplement?.name ?? "not_available".localized }
        return groups
            .map { InsightsItem(title: $0.key, count: $0.value.count) }
            .sorted { $0.count > $1.count }
            .prefix(3)
            .map { $0 }
    }

    private func topLateHour(records: [IntakeRecord]) -> InsightsItem? {
        let calendar = Calendar.current
        let late = records.filter(isLateTaken)
        if late.isEmpty { return nil }
        let groups = Dictionary(grouping: late) {
            let date = Date(timeIntervalSince1970: TimeInterval($0.updatedAtEpochMs) / 1000)
            return calendar.component(.hour, from: date)
        }
        let best = groups.max { $0.value.count < $1.value.count }
        guard let best else { return nil }
        let label = String(format: "%02d:00", best.key)
        return InsightsItem(title: label, count: best.value.count)
    }
}
