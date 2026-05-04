import Foundation
@preconcurrency import UserNotifications

/// Các lỗi liên quan đến thông báo.
public enum NotificationError: Error, Sendable {
    case authorizationDenied
    case schedulingFailed
    case unknown(Error)
}

/// Giao thức quản lý thông báo nhắc nhở.
public protocol NotificationManaging: Sendable {
    func requestAuthorization() async throws(NotificationError)
    func scheduleReminders(for supplement: UserSupplement) async throws(NotificationError)
    func cancelReminders(for supplement: UserSupplement) async
}

/// Dịch vụ xử lý Local Notifications.
@preconcurrency
public struct NotificationService: NotificationManaging {
    
    private let center = UNUserNotificationCenter.current()
    private let cycleCalculator: any CycleCalculating
    
    public init(cycleCalculator: any CycleCalculating = CycleCalculator()) {
        self.cycleCalculator = cycleCalculator
    }
    
    /// Yêu cầu quyền gửi thông báo từ người dùng.
    public func requestAuthorization() async throws(NotificationError) {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            guard granted else { throw NotificationError.authorizationDenied }
        } catch let error as NotificationError {
            throw error
        } catch {
            throw NotificationError.unknown(error)
        }
    }
    
    /// Lên lịch nhắc nhở cho thực phẩm bổ sung.
    /// Chỉ nhắc nhở vào những ngày "On".
    public func scheduleReminders(for supplement: UserSupplement) async throws(NotificationError) {
        let calendar = Calendar.current
        var dateComponents = calendar.dateComponents([.hour, .minute], from: supplement.startDate)
        
        // Thiết lập giờ dựa trên intakeTime
        setTimeComponents(&dateComponents, for: supplement.intakeTime)
        
        // Lên lịch cho 30 ngày tới (iOS giới hạn 64 thông báo local)
        for dayOffset in 0..<30 {
            guard let checkDate = calendar.date(byAdding: .day, value: dayOffset, to: .now),
                  let triggerDate = calendar.date(bySettingHour: dateComponents.hour ?? 8, 
                                                minute: dateComponents.minute ?? 0, 
                                                second: 0, 
                                                of: checkDate) else { continue }
            
            // Chỉ lên lịch nếu ngày đó là "On"
            let status = try? cycleCalculator.determineStatus(for: supplement.startDate, 
                                                           config: supplement.cycleConfig, 
                                                           at: triggerDate)
            
            guard status == .on else { continue }
            
            try await createNotificationRequest(for: supplement, at: triggerDate)
        }
    }
    
    public func cancelReminders(for supplement: UserSupplement) async {
        center.removePendingNotificationRequests(withIdentifiers: [supplement.id.uuidString])
    }
    
    // MARK: - Private Helpers
    
    private func setTimeComponents(_ components: inout DateComponents, for time: IntakeTime) {
        switch time {
        case .morning: components.hour = 8; components.minute = 0
        case .afternoon: components.hour = 12; components.minute = 0
        case .evening: components.hour = 17; components.minute = 0
        case .night: components.hour = 21; components.minute = 0
        }
    }
    
    private func createNotificationRequest(for supplement: UserSupplement, at date: Date) async throws(NotificationError) {
        let content = UNMutableNotificationContent()
        content.title = "Đến giờ uống \(supplement.name)"
        content.body = "Liều lượng: \(supplement.dailyDose)"
        content.sound = .default
        
        let triggerComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
        
        let identifier = "\(supplement.id.uuidString)-\(date.timeIntervalSince1970)"
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        
        do {
            try await center.add(request)
        } catch {
            throw NotificationError.schedulingFailed
        }
    }
}
