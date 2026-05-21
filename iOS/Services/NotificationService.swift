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
    
    func removeEntries(withPrefixes prefixes: [String]) {
        guard !prefixes.isEmpty else { return }
        let items = read()
        let filtered = items.compactMap { raw -> String? in
            let parts = raw.components(separatedBy: "||")
            guard parts.count >= 5 else { return nil }
            let identifier = parts[0]
            guard prefixes.contains(where: { identifier.hasPrefix($0) }) else { return raw }
            return nil
        }
        UserDefaults.standard.set(filtered, forKey: key)
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
    
    public enum Action: String, Sendable {
        case taken = "OAK_DOSE_TAKEN"
        case skipped = "OAK_DOSE_SKIPPED"
    }
    
    public enum Category: String, Sendable {
        case dose = "OAK_DOSE_CATEGORY"
    }
    
    private let center = UNUserNotificationCenter.current()
    private let cycleCalculator: any CycleCalculating
    
    public init(cycleCalculator: any CycleCalculating = CycleCalculator()) {
        self.cycleCalculator = cycleCalculator
    }
    
    @MainActor
    public func registerNotificationActions() async {
        let taken = UNNotificationAction(
            identifier: Action.taken.rawValue,
            title: "notif_action_taken".localized,
            options: [.authenticationRequired]
        )
        let skipped = UNNotificationAction(
            identifier: Action.skipped.rawValue,
            title: "notif_action_skip".localized,
            options: [.authenticationRequired]
        )
        let category = UNNotificationCategory(
            identifier: Category.dose.rawValue,
            actions: [taken, skipped],
            intentIdentifiers: [],
            options: []
        )
        center.setNotificationCategories([category])
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
        guard UserDefaults.standard.bool(forKey: "isNotificationEnabledByUser") else { return }
        await cancelReminders(for: supplement)
        let calendar = isoWeekCalendar()
        let horizonDays = schedulingHorizonDays(for: supplement)
        for timeString in intakeTimes(from: supplement.intakeTime) {
            guard let timeComponents = intakeTimeComponents(from: timeString) else { continue }
            for plan in upcomingTriggerPlans(calendar: calendar, timeComponents: timeComponents, horizonDays: horizonDays) {
                guard matchesWeeklyRecurrenceIfNeeded(supplement: supplement, date: plan.scheduledAt, calendar: calendar) else { continue }
                let status = try? cycleCalculator.determineStatus(
                    for: supplement.startDate,
                    config: supplement.cycleConfig,
                    at: plan.scheduledAt
                )
                guard status == .on else { continue }
                try await createNotificationRequest(for: supplement, triggerAt: plan.triggerAt, scheduledAt: plan.scheduledAt, timeString: timeString)
            }
        }
    }
    
    @MainActor
    public func cancelReminders(for supplement: UserSupplement) async {
        let prefix = "\(supplement.id.uuidString)-"
        let requests = await center.pendingNotificationRequests()
        let ids = requests.map(\.identifier).filter { $0.hasPrefix(prefix) }
        guard !ids.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }
    
    @MainActor
    public func shadowScheduledTimes() async -> [String] {
        await NotificationShadowLogStore.shared.read()
    }
    
    @MainActor
    public func scheduleAll(supplements: [UserSupplement]) async {
        guard UserDefaults.standard.bool(forKey: "isNotificationEnabledByUser") else { return }
        let prefixes = supplements.map { "\($0.id.uuidString)-" }
        if !prefixes.isEmpty {
            let requests = await center.pendingNotificationRequests()
            let ids = requests.map(\.identifier).filter { id in prefixes.contains(where: { id.hasPrefix($0) }) }
            if !ids.isEmpty { center.removePendingNotificationRequests(withIdentifiers: ids) }
            await NotificationShadowLogStore.shared.removeEntries(withPrefixes: prefixes)
        }
        for supplement in supplements {
            do {
                try await scheduleReminders(for: supplement)
            } catch {
                await logShadowScheduleFailure(supplement: supplement, error: error)
            }
        }
    }

    @MainActor
    public func clearAllPendingNotifications() async {
        center.removeAllPendingNotificationRequests()
        await NotificationShadowLogStore.shared.clear()
    }
    
    // MARK: - Private Helpers
    
    private struct TriggerPlan: Sendable, Hashable {
        let scheduledAt: Date
        let triggerAt: Date
    }

    private func intakeTimeComponents(from time: String) -> DateComponents? {
        let trimmed = time.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let minutes = TimeStrings.parseLenientTime(trimmed) else { return nil }
        return DateComponents(hour: minutes / 60, minute: minutes % 60)
    }
    
    private func upcomingTriggerPlans(
        calendar: Calendar,
        timeComponents: DateComponents,
        horizonDays: Int
    ) -> [TriggerPlan] {
        let maxDays = max(1, min(56, horizonDays))
        return (0..<maxDays).compactMap { dayOffset in
            guard let day = calendar.date(byAdding: .day, value: dayOffset, to: .now) else { return nil }
            let hour = timeComponents.hour ?? 8
            let minute = timeComponents.minute ?? 0
            guard let scheduledAt = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day) else { return nil }
            let triggerAt = applyQuietHoursIfNeeded(scheduledAt: scheduledAt, calendar: calendar)
            guard triggerAt > .now else { return nil }
            return TriggerPlan(scheduledAt: scheduledAt, triggerAt: triggerAt)
        }
    }

    private func applyQuietHoursIfNeeded(scheduledAt: Date, calendar: Calendar) -> Date {
        let quietStartMinutes = 22 * 60
        let quietEndMinutes = 7 * 60
        let hour = calendar.component(.hour, from: scheduledAt)
        let minute = calendar.component(.minute, from: scheduledAt)
        let minutesSinceMidnight = hour * 60 + minute
        if minutesSinceMidnight >= quietStartMinutes {
            guard let nextDay = calendar.date(byAdding: .day, value: 1, to: scheduledAt) else { return scheduledAt }
            return calendar.date(bySettingHour: quietEndMinutes / 60, minute: quietEndMinutes % 60, second: 0, of: nextDay) ?? scheduledAt
        }
        if minutesSinceMidnight < quietEndMinutes {
            return calendar.date(bySettingHour: quietEndMinutes / 60, minute: quietEndMinutes % 60, second: 0, of: scheduledAt) ?? scheduledAt
        }
        return scheduledAt
    }
    
    @MainActor
    public func cancelReminder(for supplement: UserSupplement, timeString: String, day: Date = .now) async {
        let identifier = requestIdentifier(supplementId: supplement.id, timeString: timeString, day: day)
        center.removePendingNotificationRequests(withIdentifiers: [identifier])
    }
    
    @MainActor
    private func createNotificationRequest(
        for supplement: UserSupplement,
        triggerAt: Date,
        scheduledAt: Date,
        timeString: String
    ) async throws(NotificationError) {
        let content = notificationContent(for: supplement, timeString: timeString, scheduledAt: scheduledAt)
        let triggerComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: triggerAt)
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
        
        let identifier = requestIdentifier(supplementId: supplement.id, timeString: timeString, day: scheduledAt)
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        do {
            try await center.add(request)
            await logShadowEntry(from: request)
        } catch {
            throw NotificationError.unknown(error)
        }
    }
    
    private func notificationContent(
        for supplement: UserSupplement,
        timeString: String,
        scheduledAt date: Date
    ) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = supplement.name
        content.body = String(
            format: "notification_body_format".localized,
            supplement.name,
            supplement.dailyDose
        )
        content.sound = .default
        let cycle = cycleText(for: supplement, at: date)
        content.userInfo = notificationUserInfo(for: supplement, timeString: timeString, cycle: cycle, scheduledAt: date)
        content.categoryIdentifier = Category.dose.rawValue
        return content
    }
    
    private func notificationUserInfo(
        for supplement: UserSupplement,
        timeString: String,
        cycle: String,
        scheduledAt: Date
    ) -> [AnyHashable: Any] {
        let dosage = supplement.dailyDose
        let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1000)
        return [
            "supplementID": supplement.id.uuidString,
            "supplementName": supplement.name,
            "intakeTime": timeString,
            "dosage": dosage,
            "cycle": cycle,
            "dailyDose": dosage,
            "cycleText": cycle,
            "scheduledAtEpochMs": scheduledAtEpochMs
        ]
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
        let entry = "\(request.identifier)||\(request.content.title)||\(dose)||\(cycleText)||\(formatted)"
        await NotificationShadowLogStore.shared.append(entry: entry)
    }
    
    private func logShadowScheduleFailure(supplement: UserSupplement, error: Error) async {
        let formatted = shadowDateFormatter().string(from: .now)
        let entry = "\(supplement.id.uuidString)-ERROR||\(supplement.name)||ERROR||\(String(describing: error))||\(formatted)"
        await NotificationShadowLogStore.shared.append(entry: entry)
    }
    
    private func shadowDateFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter
    }
    
    private func requestIdentifier(supplementId: UUID, timeString: String, day: Date) -> String {
        let dayKey = dayKeyString(day)
        let trimmed = timeString.trimmingCharacters(in: .whitespacesAndNewlines)
        let safeTime: String
        if let minutes = TimeStrings.parseLenientTime(trimmed) {
            safeTime = TimeStrings.formatTime(minutes)
        } else {
            safeTime = trimmed
        }
        return "\(supplementId.uuidString)-\(safeTime)-\(dayKey)"
    }
    
    private func dayKeyString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        return formatter.string(from: date)
    }
    
    private func intakeTimes(from raw: String) -> [String] {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let normalized = TimeStrings.normalizeList(trimmed)
        if !normalized.isEmpty { return normalized }
        let parts = trimmed.split(whereSeparator: { ",;|".contains($0) })
        let times = parts.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        return times.isEmpty ? [trimmed] : times
    }
    
    private func cycleText(for supplement: UserSupplement, at date: Date) -> String {
        let config = supplement.cycleConfig
        guard !config.isContinuous else { return "cycle_continuous".localized }
        let total = config.daysOn + config.daysOff
        guard total > 0 else { return "" }
        
        let calendar = Calendar.current
        let startDay = calendar.startOfDay(for: supplement.startDate)
        let currentDay = calendar.startOfDay(for: date)
        let elapsed = calendar.dateComponents([.day], from: startDay, to: currentDay).day ?? 0
        let dayInCycle = (elapsed % total) + 1
        
        if dayInCycle <= config.daysOn {
            return String(format: "cycle_label_day_format".localized, dayInCycle, config.daysOn)
        }
        let dayInOff = dayInCycle - config.daysOn
        let offTotal = max(config.daysOff, 1)
        return String(format: "cycle_label_rest_format".localized, dayInOff, offTotal)
    }
    
    private func schedulingHorizonDays(for supplement: UserSupplement) -> Int {
        guard let weekly = supplement.cycleConfig.weeklyRecurrence else { return 7 }
        let safeInterval = min(8, max(1, weekly.intervalWeeks))
        return max(7, safeInterval * 7)
    }
    
    private func matchesWeeklyRecurrenceIfNeeded(
        supplement: UserSupplement,
        date: Date,
        calendar: Calendar
    ) -> Bool {
        guard let weekly = supplement.cycleConfig.weeklyRecurrence else { return true }
        guard let weekdayBit = weekdayBitIndex(for: date, calendar: calendar) else { return true }
        guard (weekly.weekdaysMask & (1 << weekdayBit)) != 0 else { return false }
        let interval = min(52, max(1, weekly.intervalWeeks))
        let anchorWeekStart = startOfISOWeek(for: weekly.anchorDate, calendar: calendar)
        let dateWeekStart = startOfISOWeek(for: date, calendar: calendar)
        let days = calendar.dateComponents([.day], from: anchorWeekStart, to: dateWeekStart).day ?? 0
        let weeks = days / 7
        let mod = ((weeks % interval) + interval) % interval
        return mod == 0
    }
    
    private func weekdayBitIndex(for date: Date, calendar: Calendar) -> Int? {
        let weekday = calendar.component(.weekday, from: date)
        return switch weekday {
        case 2: 0
        case 3: 1
        case 4: 2
        case 5: 3
        case 6: 4
        case 7: 5
        case 1: 6
        default: nil
        }
    }
    
    private func startOfISOWeek(for date: Date, calendar: Calendar) -> Date {
        let components = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return calendar.date(from: components) ?? calendar.startOfDay(for: date)
    }
    
    private func isoWeekCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.firstWeekday = 2
        calendar.minimumDaysInFirstWeek = 4
        calendar.timeZone = .current
        return calendar
    }
}
