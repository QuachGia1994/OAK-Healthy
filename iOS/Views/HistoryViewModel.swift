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
        let start = calendar.date(byAdding: .day, value: -6, to: todayStart) ?? todayStart
        let isDescending = (records.first?.date ?? .distantPast) >= (records.last?.date ?? .distantPast)
        var counts: [Date: Int] = [:]
        counts.reserveCapacity(7)
        for record in records {
            let day = calendar.startOfDay(for: record.date)
            if day < start {
                if isDescending { break }
                continue
            }
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
        let isDescending = (records.first?.date ?? .distantPast) >= (records.last?.date ?? .distantPast)
        var taken = 0
        var skipped = 0
        var late = 0
        var skippedCounts: [String: Int] = [:]
        var lateCounts: [String: Int] = [:]
        var lateHourCounts: [Int: Int] = [:]
        for record in records {
            let day = calendar.startOfDay(for: record.date)
            if day > todayStart { continue }
            if day < start {
                if isDescending { break }
                continue
            }
            let name = record.supplement?.name ?? "not_available".localized
            if record.status == IntakeStatus.taken.rawValue {
                taken += 1
                if isLateTaken(record) {
                    late += 1
                    lateCounts[name, default: 0] += 1
                    let updatedAt = Date(timeIntervalSince1970: TimeInterval(record.updatedAtEpochMs) / 1000)
                    let hour = calendar.component(.hour, from: updatedAt)
                    lateHourCounts[hour, default: 0] += 1
                }
                continue
            }
            if record.status == IntakeStatus.skipped.rawValue {
                skipped += 1
                skippedCounts[name, default: 0] += 1
            }
        }
        if taken == 0, skipped == 0 { return nil }
        let denom = max(1, taken + skipped)
        let completionRate = Double(taken) / Double(denom)
        return InsightsSummary(
            windowDays: windowDays,
            takenCount: taken,
            skippedCount: skipped,
            lateCount: late,
            completionRate: completionRate,
            topSkipped: topItemsFromCounts(skippedCounts),
            topLate: topItemsFromCounts(lateCounts),
            topLateHour: topLateHourFromCounts(lateHourCounts)
        )
    }

    private func isLateTaken(_ record: IntakeRecord) -> Bool {
        guard record.status == IntakeStatus.taken.rawValue else { return false }
        guard record.updatedAtEpochMs > 0 else { return false }
        let scheduled = Int64(record.date.timeIntervalSince1970 * 1000)
        let threshold = scheduled + 20 * 60 * 1000
        return record.updatedAtEpochMs > threshold
    }

    private func topItemsFromCounts(_ counts: [String: Int]) -> [InsightsItem] {
        counts
            .map { InsightsItem(title: $0.key, count: $0.value) }
            .sorted { $0.count > $1.count }
            .prefix(3)
            .map { $0 }
    }

    private func topLateHourFromCounts(_ counts: [Int: Int]) -> InsightsItem? {
        guard let best = counts.max(by: { $0.value < $1.value }) else { return nil }
        let label = String(format: "%02d:00", best.key)
        return InsightsItem(title: label, count: best.value)
    }
}
