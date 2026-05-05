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
    @MainActor func requestAuthorization() async throws(NotificationError)
    @MainActor func scheduleReminders(for supplement: UserSupplement) async throws(NotificationError)
    @MainActor func cancelReminders(for supplement: UserSupplement) async
}

/// Dịch vụ xử lý Local Notifications.
@preconcurrency
@MainActor
public struct NotificationService: NotificationManaging {
    
    private let center = UNUserNotificationCenter.current()
    private let cycleCalculator: any CycleCalculating
    
    public init(cycleCalculator: any CycleCalculating = CycleCalculator()) {
        self.cycleCalculator = cycleCalculator
    }
    
    /// Yêu cầu quyền gửi thông báo từ người dùng.
    @MainActor
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
    @MainActor
    public func scheduleReminders(for supplement: UserSupplement) async throws(NotificationError) {
        let calendar = Calendar.current
        
        // Parse intakeTime (HH:mm)
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        guard let timeDate = formatter.date(from: supplement.intakeTime) else { return }
        let timeComponents = calendar.dateComponents([.hour, .minute], from: timeDate)
        
        // Lên lịch cho 30 ngày tới (iOS giới hạn 64 thông báo local)
        for dayOffset in 0..<30 {
            guard let checkDate = calendar.date(byAdding: .day, value: dayOffset, to: .now),
                  let triggerDate = calendar.date(bySettingHour: timeComponents.hour ?? 8, 
                                                minute: timeComponents.minute ?? 0, 
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
    
    @MainActor
    public func cancelReminders(for supplement: UserSupplement) async {
        center.removePendingNotificationRequests(withIdentifiers: [supplement.id.uuidString])
    }
    
    // MARK: - Private Helpers
    
    @MainActor
    private func createNotificationRequest(for supplement: UserSupplement, at date: Date) async throws(NotificationError) {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "notification_title")
        content.body = String(
            format: String(localized: "notification_body_format"),
            supplement.name,
            supplement.dailyDose
        )
        content.sound = .default
        content.userInfo = ["supplementID": supplement.id.uuidString]
        
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
