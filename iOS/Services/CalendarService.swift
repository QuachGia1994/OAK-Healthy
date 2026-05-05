import Foundation
@preconcurrency import EventKit

/// Các lỗi liên quan đến lịch hệ thống.
public enum CalendarError: Error, Sendable {
    case accessDenied
    case syncFailed
    case unknown(Error)
}

/// Giao thức quản lý đồng bộ lịch.
public protocol CalendarManaging: Sendable {
    @MainActor func requestAccess() async throws(CalendarError)
    @MainActor func syncCycleToCalendar(for supplement: UserSupplement) async throws(CalendarError)
}

/// Dịch vụ xử lý Apple Calendar.
@preconcurrency
public struct CalendarService: CalendarManaging {
    
    private let eventStore = EKEventStore()
    private let cycleCalculator: any CycleCalculating
    
    public init(cycleCalculator: any CycleCalculating = CycleCalculator()) {
        self.cycleCalculator = cycleCalculator
    }
    
    /// Yêu cầu quyền truy cập lịch.
    @MainActor
    public func requestAccess() async throws(CalendarError) {
        do {
            // Sử dụng API hiện đại cho iOS 17+
            let granted = try await eventStore.requestFullAccessToEvents()
            guard granted else { throw CalendarError.accessDenied }
        } catch let error as CalendarError {
            throw error
        } catch {
            throw CalendarError.unknown(error)
        }
    }
    
    /// Đồng bộ chu kỳ uống vào Apple Calendar.
    @MainActor
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
        event.title = String(format: "calendar_event_title_format".localized, supplement.name)
        event.notes = String(format: "calendar_event_notes_format".localized, supplement.dailyDose)
        event.calendar = eventStore.defaultCalendarForNewEvents
        
        // Thiết lập thời gian bắt đầu dựa trên intakeTime
        var components = Calendar.current.dateComponents([.year, .month, .day], from: date)
        setTimeComponents(&components, timeString: supplement.intakeTime)
        
        guard let startDate = Calendar.current.date(from: components) else { return }
        event.startDate = startDate
        event.endDate = startDate.addingTimeInterval(1800) // 30 phút
        
        do {
            try eventStore.save(event, span: .thisEvent)
        } catch {
            throw CalendarError.syncFailed
        }
    }
    
    private func setTimeComponents(_ components: inout DateComponents, timeString: String) {
        let parts = timeString.split(separator: ":")
        let hour = parts.first.flatMap { Int($0) } ?? 8
        let minute = parts.dropFirst().first.flatMap { Int($0) } ?? 0
        
        components.hour = hour
        components.minute = minute
    }
}
