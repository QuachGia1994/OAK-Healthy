import Foundation

/// Một class helper để kiểm tra logic tính toán chu kỳ.
/// Người dùng có thể copy logic này vào XCTest.
public struct CycleVerification {
    
    public static func verify() {
        let calculator = CycleCalculator()
        let calendar = Calendar.current
        let today = Date.now
        
        // 1. Kiểm tra Ashwagandha (56 On / 14 Off)
        let ashConfig = CycleConfig(daysOn: 56, daysOff: 14)
        
        // Bắt đầu từ 60 ngày trước -> Đã qua 1 chu kỳ 70 ngày? 
        // 60 ngày -> Vẫn đang trong 56 ngày đầu + 4 ngày nghỉ? -> Off
        if let sixtyDaysAgo = calendar.date(byAdding: .day, value: -60, to: today) {
            do {
                let status = try calculator.determineStatus(for: sixtyDaysAgo, config: ashConfig, at: today)
                print("Ashwagandha (60 days ago): \(status.rawValue)") // Expected: Nghỉ
            } catch {
                print("Error calculating Ashwagandha")
            }
        }
        
        // 2. Kiểm tra Vitamin D3 (Continuous)
        let d3Config = CycleConfig.continuous
        if let yearAgo = calendar.date(byAdding: .year, value: -1, to: today) {
            do {
                let status = try calculator.determineStatus(for: yearAgo, config: d3Config, at: today)
                print("Vitamin D3 (1 year ago): \(status.rawValue)") // Expected: Được uống
            } catch {
                print("Error calculating Vitamin D3")
            }
        }
    }
}
