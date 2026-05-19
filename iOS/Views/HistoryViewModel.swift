import Foundation
import Observation
import SwiftData

/// Dữ liệu điểm trên biểu đồ.
public struct ChartData: Identifiable, Sendable {
    public let id = UUID()
    public let date: Date
    public let count: Int
}

/// ViewModel xử lý logic lịch sử uống trên iOS.
@Observable
@MainActor
public final class HistoryViewModel {
    // MARK: - State
    public var weeklyData: [ChartData] = []
    
    public init() {}
    
    // MARK: - Logic
    
    /// Tổng hợp dữ liệu uống theo tuần.
    public func processHistory(records: [IntakeRecord]) {
        let calendar = Calendar.current
        let todayStart = calendar.startOfDay(for: .now)
        var counts: [Date: Int] = [:]
        counts.reserveCapacity(7)
        
        for record in records {
            let day = calendar.startOfDay(for: record.date)
            counts[day, default: 0] += 1
        }
        
        var data: [ChartData] = []
        data.reserveCapacity(7)
        for i in (0..<7).reversed() {
            guard let date = calendar.date(byAdding: .day, value: -i, to: todayStart) else { continue }
            data.append(ChartData(date: date, count: counts[date] ?? 0))
        }
        weeklyData = data
    }
}
