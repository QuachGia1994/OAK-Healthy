import Foundation
@preconcurrency import UserNotifications

/// Các lỗi liên quan đến thông báo.
public enum NotificationError: Error, Sendable {
    case authorizationDenied
    case schedulingFailed
    case unknown(Error)
}

actor NotificationShadowLogStore {
    static let shared = NotificationShadowLogStore()
    private let key = "notificationShadowLogTimes"
    
    func read() -> [String] {
        UserDefaults.standard.stringArray(forKey: key) ?? []
    }
    
    func append(time: String) {
        var items = read()
        guard !items.contains(time) else { return }
        items.append(time)
        UserDefaults.standard.set(items, forKey: key)
    }
    
    func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }
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
        try? await Task.sleep(for: .seconds(1))
        // let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
        // guard granted else { throw NotificationError.authorizationDenied }
    }
    
    /// Lên lịch nhắc nhở cho thực phẩm bổ sung.
    /// Chỉ nhắc nhở vào những ngày "On".
    @MainActor
    public func scheduleReminders(for supplement: UserSupplement) async throws(NotificationError) {
        guard UserDefaults.standard.bool(forKey: "isNotificationEnabledByUser") else { return }
        let intakeTime = supplement.intakeTime
        await NotificationShadowLogStore.shared.append(time: intakeTime)
        let calendar = Calendar.current
        guard let timeComponents = intakeTimeComponents(from: intakeTime, calendar: calendar) else { return }
        for triggerDate in upcomingTriggerDates(calendar: calendar, timeComponents: timeComponents) {
            let status = try? cycleCalculator.determineStatus(for: supplement.startDate, 
                                                           config: supplement.cycleConfig, 
                                                           at: triggerDate)
            guard status == .on else { continue }
            try await createNotificationRequest(for: supplement, at: triggerDate)
        }
    }
    
    @MainActor
    public func cancelReminders(for supplement: UserSupplement) async {
        center.removeAllPendingNotificationRequests()
        await NotificationShadowLogStore.shared.clear()
    }
    
    @MainActor
    public func shadowScheduledTimes() async -> [String] {
        await NotificationShadowLogStore.shared.read()
    }
    
    // MARK: - Private Helpers
    
    private func intakeTimeComponents(from time: String, calendar: Calendar) -> DateComponents? {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        guard let timeDate = formatter.date(from: time) else { return nil }
        return calendar.dateComponents([.hour, .minute], from: timeDate)
    }
    
    private func upcomingTriggerDates(
        calendar: Calendar,
        timeComponents: DateComponents
    ) -> [Date] {
        (0..<7).compactMap { dayOffset in
            guard let day = calendar.date(byAdding: .day, value: dayOffset, to: .now) else { return nil }
            let hour = timeComponents.hour ?? 8
            let minute = timeComponents.minute ?? 0
            let date = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day)
            guard let date, date > .now else { return nil }
            return date
        }
    }
    
    @MainActor
    private func createNotificationRequest(for supplement: UserSupplement, at date: Date) async throws(NotificationError) {
        let content = UNMutableNotificationContent()
        content.title = "notification_title".localized
        content.body = String(
            format: "notification_body_format".localized,
            supplement.name,
            supplement.dailyDose
        )
        content.sound = .default
        content.userInfo = [
            "supplementID": supplement.id.uuidString,
            "supplementName": supplement.name,
            "intakeTime": supplement.intakeTime,
            "dailyDose": supplement.dailyDose
        ]
        
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
