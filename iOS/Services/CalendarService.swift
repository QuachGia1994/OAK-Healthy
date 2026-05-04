import Foundation
import EventKit

/// Các lỗi liên quan đến lịch hệ thống.
public enum CalendarError: Error, Sendable {
    case accessDenied
    case syncFailed
    case unknown(Error)
}

/// Giao thức quản lý đồng bộ lịch.
public protocol CalendarManaging: Sendable {
    func requestAccess() async throws(CalendarError)
    func syncCycleToCalendar(for supplement: UserSupplement) async throws(CalendarError)
}

/// Dịch vụ xử lý Apple Calendar.
public struct CalendarService: CalendarManaging {
    
    private let eventStore = EKEventStore()
    private let cycleCalculator: any CycleCalculating
    
    public init(cycleCalculator: any CycleCalculating = CycleCalculator()) {
        self.cycleCalculator = cycleCalculator
    }
    
    /// Yêu cầu quyền truy cập lịch.
    public func requestAccess() async throws(CalendarError) {
        do {
            // Sử dụng API hiện đại cho iOS 17+
            let granted = try await eventStore.requestFullAccessToEvents()
            guard granted else { throw .accessDenied }
        } catch let error as CalendarError {
            throw error
        } catch {
            throw .unknown(error)
        }
    }
    
    /// Đồng bộ chu kỳ uống vào Apple Calendar.
    public func syncCycleToCalendar(for supplement: UserSupplement) async throws(CalendarError) {
        let calendar = Calendar.current
        
        // Tạo các sự kiện cho 90 ngày tới để người dùng dễ theo dõi
        for dayOffset in 0..<90 {
            guard let date = calendar.date(byAdding: .day, value: dayOffset, to: supplement.startDate) else { continue }
            
            let status = try? cycleCalculator.determineStatus(for: supplement.startDate, 
                                                           config: supplement.cycleConfig, 
                                                           at: date)
            
            // Chỉ tạo sự kiện nếu là ngày "On"
            guard status == .on else { continue }
            
            try createEvent(for: supplement, on: date)
        }
    }
    
    // MARK: - Private Helpers
    
    private func createEvent(for supplement: UserSupplement, on date: Date) throws(CalendarError) {
        let event = EKEvent(eventStore: eventStore)
        event.title = "Uống \(supplement.name)"
        event.notes = "Liều lượng: \(supplement.dailyDose)"
        event.calendar = eventStore.defaultCalendarForNewEvents
        
        // Thiết lập thời gian bắt đầu dựa trên intakeTime
        var components = Calendar.current.dateComponents([.year, .month, .day], from: date)
        setTimeComponents(&components, for: supplement.intakeTime)
        
        guard let startDate = Calendar.current.date(from: components) else { return }
        event.startDate = startDate
        event.endDate = startDate.addingTimeInterval(1800) // 30 phút
        
        do {
            try eventStore.save(event, span: .thisEvent)
        } catch {
            throw .syncFailed
        }
    }
    
    private func setTimeComponents(_ components: inout DateComponents, for time: IntakeTime) {
        switch time {
        case .morning: components.hour = 8
        case .afternoon: components.hour = 12
        case .evening: components.hour = 17
        case .night: components.hour = 21
        }
        components.minute = 0
    }
}
