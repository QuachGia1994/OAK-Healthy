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
        let today = Date.now
        
        // Lấy 7 ngày gần nhất
        var data: [ChartData] = []
        for i in (0..<7).reversed() {
            guard let date = calendar.date(byAdding: .day, value: -i, to: today) else { continue }
            let count = records.filter { calendar.isDate($0.date, inSameDayAs: date) }.count
            data.append(ChartData(date: date, count: count))
        }
        
        self.weeklyData = data
    }
}
