import Foundation
import SwiftData

/// ViewModel quản lý logic thêm mới thực phẩm bổ sung.
/// Sử dụng `@Observable` macro theo Swift 6.2+ standard.
@Observable
@MainActor
public final class AddSupplementViewModel {
    // MARK: - State
    public var name: String = ""
    public var startDate: Date = .now
    public var selectedTime: Date = Calendar.current.date(bySettingHour: 8, minute: 0, second: 0, of: .now) ?? .now
    public var intakeTimes: String = "08:00"
    public var daysOn: String = "30"
    public var daysOff: String = "7"
    public var durationMonths: String = ""
    public var dailyDose: String = ""
    public var isContinuous: Bool = false
    public var isWeeklyRecurrenceEnabled: Bool = false
    public var weekdaysMask: Int = 127
    public var intervalWeeks: String = "1"
    
    public var suggestions: [SupplementReference] = []
    public var isLoading: Bool = false
    
    // MARK: - Dependencies
    private let suggestService: any AutoSuggestService
    private let notificationService: any NotificationManaging
    private let modelContext: ModelContext
    private var editingSupplement: UserSupplement?
    private let activeClient: ClientProfile?
    
    public init(
        modelContext: ModelContext,
        editingSupplement: UserSupplement? = nil,
        activeClient: ClientProfile? = nil,
        suggestService: any AutoSuggestService = SupplementAutoSuggester(),
        notificationService: any NotificationManaging = NotificationService()
    ) {
        self.modelContext = modelContext
        self.editingSupplement = editingSupplement
        self.activeClient = activeClient ?? editingSupplement?.client
        self.suggestService = suggestService
        self.notificationService = notificationService
        
        if let supplement = editingSupplement {
            name = supplement.name
            startDate = supplement.startDate
            dailyDose = supplement.dailyDose
            isContinuous = supplement.cycleConfig.isContinuous
            daysOn = String(supplement.cycleConfig.daysOn)
            daysOff = String(supplement.cycleConfig.daysOff)
            durationMonths = supplement.cycleConfig.durationMonths.map(String.init) ?? ""
            if let weekly = supplement.cycleConfig.weeklyRecurrence {
                isWeeklyRecurrenceEnabled = true
                weekdaysMask = weekly.weekdaysMask
                intervalWeeks = String(weekly.intervalWeeks)
            }
            intakeTimes = TimeStrings.normalizeString(supplement.intakeTime)
            if let first = TimeStrings.normalizeList(supplement.intakeTime).first,
               let minutes = TimeStrings.parseLenientTime(first) {
                selectedTime = Calendar.current.date(
                    bySettingHour: minutes / 60,
                    minute: minutes % 60,
                    second: 0,
                    of: .now
                ) ?? .now
            }
        } else {
            intakeTimes = TimeStrings.normalizeString(intakeTimes)
        }
    }
    
    // MARK: - Actions

    /// Lưu thực phẩm bổ sung và thiết lập thông báo.
    public func saveSupplement() async -> UserSupplement? {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let supplement: UserSupplement
        if let existing = editingSupplement {
            guard let updated = createSupplement(id: existing.id) else { return nil }
            existing.name = updated.name
            existing.startDate = updated.startDate
            existing.cycleConfig = updated.cycleConfig
            existing.dailyDose = updated.dailyDose
            existing.intakeTime = updated.intakeTime
            existing.updatedAtEpochMs = now
            existing.deletedAtEpochMs = nil
            supplement = existing
        } else {
            guard let created = createSupplement(id: UUID()) else { return nil }
            modelContext.insert(created)
            supplement = created
        }
        
        do {
            try modelContext.save()
            
            if editingSupplement != nil {
                await notificationService.cancelReminders(for: supplement)
            }
            try await notificationService.scheduleReminders(for: supplement)
            
            return supplement
        } catch {
            print("Lỗi khi lưu và đồng bộ: \(error)")
            return supplement
        }
    }
    
    /// Cập nhật danh sách gợi ý khi tên thay đổi.
    public func updateSuggestions() async {
        guard name.count >= 2 else {
            suggestions = []
            return
        }
        
        isLoading = true
        do {
            suggestions = try await suggestService.fetchSuggestions(for: name)
        } catch {
            suggestions = []
        }
        isLoading = false
    }
    
    /// Áp dụng cấu hình từ gợi ý được chọn.
    public func selectSuggestion(_ reference: SupplementReference) {
        name = reference.name
        if let dose = reference.preferredDose {
            dailyDose = dose
        }
        let normalized = TimeStrings.normalizeString(reference.preferredTime)
        intakeTimes = normalized.isEmpty ? intakeTimes : normalized
        if let first = TimeStrings.normalizeList(intakeTimes).first,
           let minutes = TimeStrings.parseLenientTime(first) {
            selectedTime = Calendar.current.date(
                bySettingHour: minutes / 60,
                minute: minutes % 60,
                second: 0,
                of: .now
            ) ?? .now
        }
        
        isContinuous = reference.defaultCycle.isContinuous
        daysOn = String(reference.defaultCycle.daysOn)
        daysOff = String(reference.defaultCycle.daysOff)
        isWeeklyRecurrenceEnabled = false
        weekdaysMask = 127
        intervalWeeks = "1"
        suggestions = []
    }
    
    public func toggleWeekday(bitIndex: Int) {
        guard bitIndex >= 0, bitIndex <= 6 else { return }
        let current = sanitizedWeekdaysMask()
        let bit = 1 << bitIndex
        let toggled = current ^ bit
        weekdaysMask = max(1, min(127, toggled == 0 ? bit : toggled))
    }
    
    /// Tạo đối tượng UserSupplement hoàn chỉnh.
    public func createSupplement(id: UUID) -> UserSupplement? {
        guard !name.isEmpty else { return nil }
        guard let activeClient else { return nil }
        
        let weekly = makeWeeklyRecurrenceIfNeeded()
        let config = isContinuous
            ? CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true, durationMonths: nil, weeklyRecurrence: weekly)
            : CycleConfig(
                daysOn: Int(daysOn) ?? 1,
                daysOff: Int(daysOff) ?? 0,
                isContinuous: false,
                durationMonths: Int(durationMonths),
                weeklyRecurrence: weekly
            )
        let timeString = TimeStrings.normalizeString(intakeTimes)
        guard !timeString.isEmpty else { return nil }
        
        return UserSupplement(
            id: id,
            name: name,
            startDate: startDate,
            cycleConfig: config,
            dailyDose: dailyDose,
            intakeTime: timeString,
            updatedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            deletedAtEpochMs: nil,
            client: activeClient
        )
    }

    public func addSelectedTime() {
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: selectedTime)
        let minute = calendar.component(.minute, from: selectedTime)
        let candidate = String(format: "%02d:%02d", hour, minute)
        let merged = intakeTimes.isEmpty ? candidate : "\(intakeTimes), \(candidate)"
        intakeTimes = TimeStrings.normalizeString(merged)
    }
    
    private func makeWeeklyRecurrenceIfNeeded() -> WeeklyRecurrenceConfig? {
        guard isWeeklyRecurrenceEnabled else { return nil }
        let interval = max(1, Int(intervalWeeks) ?? 1)
        let anchor = Calendar.current.startOfDay(for: startDate)
        return WeeklyRecurrenceConfig(
            weekdaysMask: sanitizedWeekdaysMask(),
            intervalWeeks: interval,
            anchorDate: anchor
        )
    }
    
    private func sanitizedWeekdaysMask() -> Int {
        let clamped = max(1, min(127, weekdaysMask))
        return clamped
    }
}
