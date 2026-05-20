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
    public var errorMessage: String?
    
    // MARK: - Dependencies
    private let cycleEngine: any CycleCalculating
    
    public init(cycleEngine: any CycleCalculating = CycleCalculator()) {
        self.cycleEngine = cycleEngine
    }
    
    // MARK: - Logic
    
    public enum DoseStatus: Sendable, Hashable {
        case planned
        case taken
        case skipped
        case missed
    }
    
    public enum DoseAction: Sendable, Hashable {
        case taken
        case skipped
    }

    public struct TodayCounts: Sendable, Hashable {
        public var planned: Int
        public var taken: Int
        public var skipped: Int
        public var missed: Int
    }
    
    public enum DoseUrgency: Sendable, Hashable {
        case none
        case dueSoon
        case missedSoon
    }

    public func todayCounts(now: Date = .now) -> TodayCounts {
        var planned = 0
        var taken = 0
        var skipped = 0
        var missed = 0
        for (timeString, supplements) in activeSupplements {
            for supplement in supplements {
                switch doseStatus(supplement, timeString: timeString, now: now) {
                case .planned: planned += 1
                case .taken: taken += 1
                case .skipped: skipped += 1
                case .missed: missed += 1
                }
            }
        }
        return TodayCounts(planned: planned, taken: taken, skipped: skipped, missed: missed)
    }
    
    public func streakDays(supplements: [UserSupplement], now: Date = .now) -> Int {
        let calendar = Calendar.current
        var takenDays: Set<Date> = []
        for supplement in supplements {
            for record in supplement.intakeRecords {
                guard record.status == IntakeStatus.taken.rawValue else { continue }
                takenDays.insert(calendar.startOfDay(for: record.date))
            }
        }
        let today = calendar.startOfDay(for: now)
        let start = takenDays.contains(today) ? today : (calendar.date(byAdding: .day, value: -1, to: today) ?? today)
        var streak = 0
        var cursor = start
        while takenDays.contains(cursor) {
            streak += 1
            cursor = calendar.date(byAdding: .day, value: -1, to: cursor) ?? cursor.addingTimeInterval(-86_400)
        }
        return streak
    }
    
    public func doseUrgency(_ supplement: UserSupplement, timeString: String, now: Date = .now) -> DoseUrgency {
        guard let time = normalizedTimeString(timeString) else { return .none }
        guard doseStatus(supplement, timeString: time, now: now) == .planned else { return .none }
        guard let scheduledAt = scheduledAtLocal(for: now, timeString: time) else { return .none }
        if todayRecord(for: supplement, scheduledAt: scheduledAt, timeString: time) != nil { return .none }
        let dueSoonSeconds: TimeInterval = 20 * 60
        if scheduledAt > now && scheduledAt.timeIntervalSince(now) <= dueSoonSeconds { return .dueSoon }
        let missedAfter = scheduledAt.addingTimeInterval(2 * 60 * 60)
        if now >= missedAfter.addingTimeInterval(-dueSoonSeconds) && now < missedAfter { return .missedSoon }
        return .none
    }
    
    public func toggleIntake(
        for supplement: UserSupplement,
        timeString: String,
        context: ModelContext,
        notificationService: NotificationService
    ) {
        markDose(
            for: supplement,
            timeString: timeString,
            action: .taken,
            context: context,
            notificationService: notificationService
        )
    }
    
    public func markDose(
        for supplement: UserSupplement,
        timeString: String,
        action: DoseAction,
        context: ModelContext,
        notificationService: NotificationService
    ) {
        guard let time = normalizedTimeString(timeString) else { return }
        
        let scheduledAt = scheduledAtLocal(for: .now, timeString: time)
        guard let scheduledAt else { return }
        guard todayRecord(for: supplement, scheduledAt: scheduledAt, timeString: time) == nil else { return }
        
        let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1000)
        let key = DoseEventKey.make(supplementId: supplement.id, scheduledAtEpochMs: scheduledAtEpochMs)
        let recordId = DoseEventKey.stableUUID(from: key)
        let nowEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        let status: String = switch action {
        case .taken: IntakeStatus.taken.rawValue
        case .skipped: IntakeStatus.skipped.rawValue
        }
        
        let newRecord = IntakeRecord(
            id: recordId,
            date: scheduledAt,
            status: status,
            intakeTime: time,
            updatedAtEpochMs: nowEpochMs,
            supplement: supplement
        )
        context.insert(newRecord)
        
        do {
            try context.save()
        } catch {
            errorMessage = error.localizedDescription
            return
        }
        
        Task { await notificationService.cancelReminder(for: supplement, timeString: time, day: scheduledAt) }
        Task { await CloudSyncAutoSync.syncIfEnabled(modelContext: context, clientId: supplement.client?.id) }
    }
    
    public func doseStatus(_ supplement: UserSupplement, timeString: String, now: Date = .now) -> DoseStatus {
        guard let time = normalizedTimeString(timeString) else { return .planned }
        
        let scheduledAt = scheduledAtLocal(for: now, timeString: time)
        guard let scheduledAt else { return .planned }
        
        if let record = todayRecord(for: supplement, scheduledAt: scheduledAt, timeString: time) {
            if record.status == IntakeStatus.skipped.rawValue { return .skipped }
            return .taken
        }
        
        let missedAfter = scheduledAt.addingTimeInterval(2 * 60 * 60)
        if now > missedAfter { return .missed }
        return .planned
    }
    
    private func scheduledAtLocal(for day: Date, timeString: String) -> Date? {
        guard let minutes = TimeStrings.parseLenientTime(timeString) else { return nil }
        return Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: day)
    }
    
    private func todayRecord(for supplement: UserSupplement, scheduledAt: Date, timeString: String) -> IntakeRecord? {
        let calendar = Calendar.current
        return supplement.intakeRecords.first { record in
            guard calendar.isDate(record.date, inSameDayAs: scheduledAt) else { return false }
            if record.intakeTime.isEmpty { return true }
            let recordTime = record.intakeTime.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !recordTime.isEmpty else { return true }
            if let recordNormalized = normalizedTimeString(recordTime), let scheduledNormalized = normalizedTimeString(timeString) {
                return recordNormalized == scheduledNormalized
            }
            return recordTime == timeString
        }
    }
    
    /// Xóa thực phẩm bổ sung.
    public func deleteSupplement(_ supplement: UserSupplement, context: ModelContext, notificationService: NotificationService) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        supplement.deletedAtEpochMs = now
        supplement.updatedAtEpochMs = now
        do {
            try context.save()
        } catch {
            errorMessage = error.localizedDescription
            return
        }
        
        Task {
            await notificationService.cancelReminders(for: supplement)
            await CloudSyncAutoSync.syncIfEnabled(modelContext: context, clientId: supplement.client?.id)
        }
    }
    
    /// Phân loại danh sách thực phẩm bổ sung.
    /// - Parameter supplements: Danh sách từ SwiftData.
    public func processSupplements(_ supplements: [UserSupplement]) {
        let today = Date.now
        let calendar = isoWeekCalendar()
        var active: [String: [UserSupplement]] = [:]
        var resting: [RestingSupplementInfo] = []
        
        for supplement in supplements {
            if supplement.deletedAtEpochMs != nil { continue }
            let status = try? cycleEngine.determineStatus(
                for: supplement.startDate,
                config: supplement.cycleConfig,
                at: today
            )
            
            if status == .on, matchesWeeklyRecurrenceIfNeeded(config: supplement.cycleConfig, date: today, calendar: calendar) {
                for time in intakeTimes(from: supplement.intakeTime) {
                    active[time, default: []].append(supplement)
                }
            } else if status == .off {
                let daysRemaining = calculateDaysRemaining(for: supplement, at: today)
                resting.append(RestingSupplementInfo(supplement: supplement, daysRemaining: daysRemaining))
            }
        }
        
        self.activeSupplements = active
        self.restingSupplements = resting
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

    private func normalizedTimeString(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard let minutes = TimeStrings.parseLenientTime(trimmed) else { return nil }
        return TimeStrings.formatTime(minutes)
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
    
    private func matchesWeeklyRecurrenceIfNeeded(config: CycleConfig, date: Date, calendar: Calendar) -> Bool {
        guard let weekly = config.weeklyRecurrence else { return true }
        guard let weekdayBit = weekdayBitIndex(for: date, calendar: calendar) else { return true }
        guard (weekly.weekdaysMask & (1 << weekdayBit)) != 0 else { return false }
        let interval = max(1, weekly.intervalWeeks)
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
