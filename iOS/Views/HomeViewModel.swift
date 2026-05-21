import Foundation
import Observation
import SwiftData

/// ViewModel xử lý logic cho màn hình chính Dashboard trên iOS.
@Observable
@MainActor
public final class HomeViewModel {
    // MARK: - State
    public var activeSupplements: [String: [UserSupplement]] = [:]
    public var activeSupplementTimes: [String] = []
    public var restingSupplements: [RestingSupplementInfo] = []
    public var errorMessage: String?
    private var todayRecordStatusById: [UUID: String] = [:]
    private var recentRecordIds: Set<UUID> = []
    private var doseIdCache: [DoseIdKey: UUID] = [:]
    private var intakeTimesCache: [UUID: IntakeTimesCacheEntry] = [:]
    private var supplementsCache: [UserSupplement] = []
    
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

    public var cachedTodayCounts: TodayCounts = TodayCounts(planned: 0, taken: 0, skipped: 0, missed: 0)
    public var cachedStreakDays: Int = 0
    
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
        guard !supplements.isEmpty else { return 0 }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let seed = isDayComplete(day: today, supplements: supplements) ? today : (calendar.date(byAdding: .day, value: -1, to: today) ?? today)
        var streak = 0
        var cursor = seed
        var cache: [DoseIdKey: UUID] = [:]
        for _ in 0..<120 {
            guard isDayComplete(day: cursor, supplements: supplements, cache: &cache) else { break }
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return streak
    }

    private func isDayComplete(day: Date, supplements: [UserSupplement]) -> Bool {
        var cache: [DoseIdKey: UUID] = [:]
        return isDayComplete(day: day, supplements: supplements, cache: &cache)
    }

    private func isDayComplete(day: Date, supplements: [UserSupplement], cache: inout [DoseIdKey: UUID]) -> Bool {
        let calendar = isoWeekCalendar()
        for supplement in supplements {
            if supplement.deletedAtEpochMs != nil { continue }
            let status = try? cycleEngine.determineStatus(for: supplement.startDate, config: supplement.cycleConfig, at: day)
            guard status == .on else { continue }
            guard matchesWeeklyRecurrenceIfNeeded(config: supplement.cycleConfig, date: day, calendar: calendar) else { continue }
            for time in intakeTimes(for: supplement) {
                guard let scheduledAt = scheduledAtLocal(for: day, timeString: time) else { continue }
                let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1000)
                let recordId = recordIdForDoseCachedLocal(supplementId: supplement.id, scheduledAtEpochMs: scheduledAtEpochMs, cache: &cache)
                if !recentRecordIds.contains(recordId) { return false }
            }
        }
        return true
    }
    
    public func doseUrgency(_ supplement: UserSupplement, timeString: String, now: Date = .now) -> DoseUrgency {
        guard let time = normalizedTimeString(timeString) else { return .none }
        guard doseStatus(supplement, timeString: time, now: now) == .planned else { return .none }
        guard let scheduledAt = scheduledAtLocal(for: now, timeString: time) else { return .none }
        let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1000)
        let recordId = recordIdForDoseCached(supplementId: supplement.id, scheduledAtEpochMs: scheduledAtEpochMs)
        if todayRecordStatusById[recordId] != nil { return .none }
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
        guard let ctx = makeMarkDoseContext(supplement: supplement, timeString: timeString, action: action) else { return }
        let newRecord = IntakeRecord(
            id: ctx.recordId,
            date: ctx.scheduledAt,
            status: ctx.status,
            intakeTime: ctx.time,
            updatedAtEpochMs: ctx.nowEpochMs,
            supplement: supplement
        )
        guard insertAndSave(newRecord, context: context) else { return }
        applyMarkToCaches(ctx: ctx, action: action)
        scheduleMarkSideEffects(
            supplement: supplement,
            time: ctx.time,
            scheduledAt: ctx.scheduledAt,
            context: context,
            notificationService: notificationService
        )
    }

    private struct MarkDoseContext: Sendable {
        let time: String
        let scheduledAt: Date
        let scheduledAtEpochMs: Int64
        let recordId: UUID
        let previous: DoseStatus
        let status: String
        let nowEpochMs: Int64
    }

    private func makeMarkDoseContext(
        supplement: UserSupplement,
        timeString: String,
        action: DoseAction
    ) -> MarkDoseContext? {
        guard let time = normalizedTimeString(timeString) else { return nil }
        guard let scheduledAt = scheduledAtLocal(for: .now, timeString: time) else { return nil }
        let previous = doseStatus(supplement, timeString: time, now: .now)
        let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1000)
        let recordId = recordIdForDoseCached(supplementId: supplement.id, scheduledAtEpochMs: scheduledAtEpochMs)
        guard todayRecordStatusById[recordId] == nil else { return nil }
        let nowEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        let status: String = switch action {
        case .taken: IntakeStatus.taken.rawValue
        case .skipped: IntakeStatus.skipped.rawValue
        }
        return MarkDoseContext(
            time: time,
            scheduledAt: scheduledAt,
            scheduledAtEpochMs: scheduledAtEpochMs,
            recordId: recordId,
            previous: previous,
            status: status,
            nowEpochMs: nowEpochMs
        )
    }

    private func insertAndSave(_ record: IntakeRecord, context: ModelContext) -> Bool {
        context.insert(record)
        do {
            try context.save()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    private func applyMarkToCaches(ctx: MarkDoseContext, action: DoseAction) {
        todayRecordStatusById[ctx.recordId] = ctx.status
        recentRecordIds.insert(ctx.recordId)
        cachedTodayCounts = updateCountsAfterMark(previous: ctx.previous, action: action, current: cachedTodayCounts)
        cachedStreakDays = streakDays(supplements: supplementsCache, now: .now)
    }

    private func scheduleMarkSideEffects(
        supplement: UserSupplement,
        time: String,
        scheduledAt: Date,
        context: ModelContext,
        notificationService: NotificationService
    ) {
        Task { await notificationService.cancelReminder(for: supplement, timeString: time, day: scheduledAt) }
        Task { await CloudSyncAutoSync.syncIfEnabled(modelContext: context, clientId: supplement.client?.id) }
    }
    
    public func doseStatus(_ supplement: UserSupplement, timeString: String, now: Date = .now) -> DoseStatus {
        guard let time = normalizedTimeString(timeString) else { return .planned }
        
        let scheduledAt = scheduledAtLocal(for: now, timeString: time)
        guard let scheduledAt else { return .planned }
        
        let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1000)
        let recordId = recordIdForDoseCached(supplementId: supplement.id, scheduledAtEpochMs: scheduledAtEpochMs)
        if let status = todayRecordStatusById[recordId] {
            if status == IntakeStatus.skipped.rawValue { return .skipped }
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

    private func recordIdForDose(supplementId: UUID, scheduledAtEpochMs: Int64) -> UUID {
        let key = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)
        return DoseEventKey.stableUUID(from: key)
    }

    private func recordIdForDoseCached(supplementId: UUID, scheduledAtEpochMs: Int64) -> UUID {
        let key = DoseIdKey(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)
        if let cached = doseIdCache[key] { return cached }
        let computed = recordIdForDose(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)
        doseIdCache[key] = computed
        return computed
    }

    private func recordIdForDoseCachedLocal(
        supplementId: UUID,
        scheduledAtEpochMs: Int64,
        cache: inout [DoseIdKey: UUID]
    ) -> UUID {
        let key = DoseIdKey(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)
        if let cached = cache[key] { return cached }
        let computed = recordIdForDose(supplementId: supplementId, scheduledAtEpochMs: scheduledAtEpochMs)
        cache[key] = computed
        return computed
    }

    private struct DoseIdKey: Hashable {
        let supplementId: UUID
        let scheduledAtEpochMs: Int64
    }
    
    private struct IntakeTimesCacheEntry: Hashable {
        let raw: String
        let times: [String]
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
        let calendarCurrent = Calendar.current
        let calendar = isoWeekCalendar()
        let todayStart = calendarCurrent.startOfDay(for: today)
        let minDate = calendarCurrent.date(byAdding: .day, value: -130, to: todayStart) ?? todayStart
        var todayStatus: [UUID: String] = [:]
        var recent: Set<UUID> = []
        var active: [String: [UserSupplement]] = [:]
        var resting: [RestingSupplementInfo] = []
        var all: [UserSupplement] = []
        doseIdCache.removeAll(keepingCapacity: true)
        
        for supplement in supplements {
            if supplement.deletedAtEpochMs != nil { continue }
            all.append(supplement)
            for record in supplement.intakeRecords {
                if record.date >= minDate { recent.insert(record.id) }
                if calendarCurrent.isDate(record.date, inSameDayAs: todayStart) {
                    todayStatus[record.id] = record.status
                }
            }
            let status = try? cycleEngine.determineStatus(
                for: supplement.startDate,
                config: supplement.cycleConfig,
                at: today
            )
            
            if status == .on, matchesWeeklyRecurrenceIfNeeded(config: supplement.cycleConfig, date: today, calendar: calendar) {
                for time in intakeTimes(for: supplement) {
                    active[time, default: []].append(supplement)
                }
            } else if status == .off {
                let daysRemaining = calculateDaysRemaining(for: supplement, at: today)
                resting.append(RestingSupplementInfo(supplement: supplement, daysRemaining: daysRemaining))
            }
        }
        
        self.activeSupplements = active
        self.activeSupplementTimes = active.keys.sorted()
        self.restingSupplements = resting
        self.todayRecordStatusById = todayStatus
        self.recentRecordIds = recent
        self.supplementsCache = all
        self.cachedTodayCounts = todayCounts(now: today)
        self.cachedStreakDays = streakDays(supplements: all, now: today)
    }
    
    private func intakeTimes(for supplement: UserSupplement) -> [String] {
        let raw = supplement.intakeTime
        if let cached = intakeTimesCache[supplement.id], cached.raw == raw { return cached.times }
        let computed = intakeTimes(from: raw)
        intakeTimesCache[supplement.id] = IntakeTimesCacheEntry(raw: raw, times: computed)
        return computed
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

    private func updateCountsAfterMark(previous: DoseStatus, action: DoseAction, current: TodayCounts) -> TodayCounts {
        var updated = current
        if previous == .planned { updated.planned = max(0, updated.planned - 1) }
        if previous == .missed { updated.missed = max(0, updated.missed - 1) }
        if action == .taken { updated.taken += 1 }
        if action == .skipped { updated.skipped += 1 }
        return updated
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
