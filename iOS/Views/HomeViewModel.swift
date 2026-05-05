import Foundation
import Observation
import SwiftData

/// ViewModel xử lý logic cho màn hình chính Dashboard trên iOS.
@Observable
@MainActor
public final class HomeViewModel {
    // MARK: - State
    public var activeSupplements: [String: [UserSupplement]] = [:]
    public var restingSupplements: [RestingSupplementInfo] = []
    
    // MARK: - Dependencies
    private let cycleEngine: any CycleCalculating
    
    public init(cycleEngine: any CycleCalculating = CycleCalculator()) {
        self.cycleEngine = cycleEngine
        
        // Lắng nghe thay đổi múi giờ hệ thống
        NotificationCenter.default.addObserver(
            forName: Notification.Name.NSSystemTimeZoneDidChange,
            object: nil,
            queue: .main
        ) { _ in
        }
    }
    
    // MARK: - Logic
    
    /// Toggle trạng thái uống trong ngày hôm nay.
    public func toggleIntake(for supplement: UserSupplement, context: ModelContext) {
        let calendar = Calendar.current
        let today = Date.now
        
        // Kiểm tra xem đã có bản ghi trong ngày hôm nay chưa
        let todayRecords = supplement.intakeRecords.filter { calendar.isDate($0.date, inSameDayAs: today) }
        
        if let existingRecord = todayRecords.first {
            // Nếu đã có -> Xóa (Untick)
            context.delete(existingRecord)
        } else {
            // Nếu chưa có -> Thêm mới (Tick)
            let newRecord = IntakeRecord(date: today, status: "Taken", supplement: supplement)
            context.insert(newRecord)
        }
        
        try? context.save()
    }
    
    /// Kiểm tra xem một chất đã được uống hôm nay chưa.
    public func isTakenToday(_ supplement: UserSupplement) -> Bool {
        let calendar = Calendar.current
        let today = Date.now
        return supplement.intakeRecords.contains { calendar.isDate($0.date, inSameDayAs: today) }
    }
    
    /// Xóa thực phẩm bổ sung.
    public func deleteSupplement(_ supplement: UserSupplement, context: ModelContext) {
        if !supplement.intakeRecords.isEmpty {
            for record in supplement.intakeRecords {
                context.delete(record)
            }
        }
        context.delete(supplement)
        try? context.save()
    }
    
    /// Phân loại danh sách thực phẩm bổ sung.
    /// - Parameter supplements: Danh sách từ SwiftData.
    public func processSupplements(_ supplements: [UserSupplement]) {
        let today = Date.now
        var active: [String: [UserSupplement]] = [:]
        var resting: [RestingSupplementInfo] = []
        
        for supplement in supplements {
            let status = try? cycleEngine.determineStatus(
                for: supplement.startDate,
                config: supplement.cycleConfig,
                at: today
            )
            
            if status == .on {
                active[supplement.intakeTime, default: []].append(supplement)
            } else if status == .off {
                let daysRemaining = calculateDaysRemaining(for: supplement, at: today)
                resting.append(RestingSupplementInfo(supplement: supplement, daysRemaining: daysRemaining))
            }
        }
        
        self.activeSupplements = active
        self.restingSupplements = resting
    }
    
    private func calculateDaysRemaining(for supplement: UserSupplement, at today: Date) -> Int {
        let config = supplement.cycleConfig
        let totalCycleDays = config.daysOn + config.daysOff
        guard totalCycleDays > 0 else { return 0 }
        
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: supplement.startDate, to: today)
        let daysElapsed = components.day ?? 0
        
        let dayInCycle = daysElapsed % totalCycleDays
        return totalCycleDays - dayInCycle
    }
}
