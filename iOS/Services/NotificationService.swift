import Foundation
@preconcurrency import UserNotifications

/// Các lỗi liên quan đến thông báo.
public enum NotificationError: Error, Sendable {
    case authorizationDenied
    case schedulingFailed
    case unknown(Error)
}

public struct PendingNotificationSnapshot: Sendable, Hashable {
    public let id: String
    public let title: String
    public let scheduledAt: Date
    public let dosage: String
    public let cycle: String
    
    public init(id: String, title: String, scheduledAt: Date, dosage: String, cycle: String) {
        self.id = id
        self.title = title
        self.scheduledAt = scheduledAt
        self.dosage = dosage
        self.cycle = cycle
    }
}

actor NotificationShadowLogStore {
    static let shared = NotificationShadowLogStore()
    private let key = "notificationShadowLogEntries"
    
    func read() -> [String] {
        UserDefaults.standard.stringArray(forKey: key) ?? []
    }
    
    func append(entry: String) {
        var items = read()
        guard !items.contains(entry) else { return }
        items.append(entry)
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
    
    public static let shared = NotificationService()
    
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
        let calendar = Calendar.current
        guard let timeComponents = intakeTimeComponents(from: supplement.intakeTime, calendar: calendar) else { return }
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
    
    @MainActor
    public func scheduleAll(supplements: [UserSupplement]) async {
        guard UserDefaults.standard.bool(forKey: "isNotificationEnabledByUser") else { return }
        center.removeAllPendingNotificationRequests()
        await NotificationShadowLogStore.shared.clear()
        for supplement in supplements {
            try? await scheduleReminders(for: supplement)
        }
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
        content.title = supplement.name
        content.body = String(
            format: "notification_body_format".localized,
            supplement.name,
            supplement.dailyDose
        )
        content.sound = .default
        let cycleText = cycleText(for: supplement, at: date)
        let dosage = supplement.dailyDose
        content.userInfo = [
            "supplementID": supplement.id.uuidString,
            "supplementName": supplement.name,
            "intakeTime": supplement.intakeTime,
            "dosage": dosage,
            "cycle": cycleText,
            "dailyDose": dosage,
            "cycleText": cycleText
        ]
        
        let triggerComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
        
        let identifier = "\(supplement.id.uuidString)-\(date.timeIntervalSince1970)"
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        await logShadowEntry(from: request)
        
        do {
            try await center.add(request)
        } catch {
            throw NotificationError.schedulingFailed
        }
    }
    
    @MainActor
    public func pendingRequestSnapshots() async -> [PendingNotificationSnapshot] {
        let requests = await center.pendingNotificationRequests()
        return requests.compactMap { snapshot(from: $0) }.sorted { $0.scheduledAt < $1.scheduledAt }
    }
    
    private func snapshot(from request: UNNotificationRequest) -> PendingNotificationSnapshot? {
        guard let scheduledAt = scheduledDate(from: request.trigger) else { return nil }
        let title = request.content.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return nil }
        let info = request.content.userInfo
        let dosage = (info["dosage"] as? String) ?? (info["dailyDose"] as? String) ?? ""
        let cycle = (info["cycle"] as? String) ?? (info["cycleText"] as? String) ?? ""
        return PendingNotificationSnapshot(id: request.identifier, title: title, scheduledAt: scheduledAt, dosage: dosage, cycle: cycle)
    }
    
    private func scheduledDate(from trigger: UNNotificationTrigger?) -> Date? {
        guard let trigger = trigger as? UNCalendarNotificationTrigger else { return nil }
        return Calendar.current.date(from: trigger.dateComponents)
    }
    
    private func logShadowEntry(from request: UNNotificationRequest) async {
        guard let trigger = request.trigger as? UNCalendarNotificationTrigger else { return }
        let calendar = Calendar.current
        guard let scheduled = calendar.date(from: trigger.dateComponents) else { return }
        let formatted = shadowDateFormatter().string(from: scheduled)
        let dose = (request.content.userInfo["dailyDose"] as? String) ?? ""
        let cycleText = (request.content.userInfo["cycleText"] as? String) ?? ""
        let entry = "\(request.content.title)||\(dose)||\(cycleText)||\(formatted)"
        await NotificationShadowLogStore.shared.append(entry: entry)
    }
    
    private func shadowDateFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter
    }
    
    private func cycleText(for supplement: UserSupplement, at date: Date) -> String {
        let config = supplement.cycleConfig
        guard !config.isContinuous else { return "Liên tục" }
        let total = config.daysOn + config.daysOff
        guard total > 0 else { return "" }
        
        let calendar = Calendar.current
        let startDay = calendar.startOfDay(for: supplement.startDate)
        let currentDay = calendar.startOfDay(for: date)
        let elapsed = calendar.dateComponents([.day], from: startDay, to: currentDay).day ?? 0
        let dayInCycle = (elapsed % total) + 1
        
        if dayInCycle <= config.daysOn { return "Ngày \(dayInCycle)/\(config.daysOn)" }
        let dayInOff = dayInCycle - config.daysOn
        let offTotal = max(config.daysOff, 1)
        return "Nghỉ \(dayInOff)/\(offTotal)"
    }
}
