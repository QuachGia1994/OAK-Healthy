import Foundation
import SwiftData

/// ViewModel quản lý logic thêm mới thực phẩm bổ sung.
/// Sử dụng `@Observable` macro theo Swift 6.2+ standard.
@Observable
@MainActor
public final class AddSupplementViewModel {
    public enum SaveFailure: Error {
        case missingActiveClient
        case invalidName
        case invalidIntakeTime
        case invalidCycleDays
        case invalidIntervalDays
        case modelSaveFailed(Error)
        case notificationSyncFailed(Error)
        
        fileprivate var message: String {
            switch self {
            case .missingActiveClient:
                return "missing_active_client".localized
            case .invalidName:
                return "add_supplement_error_invalid_name".localized
            case .invalidIntakeTime:
                return "add_supplement_error_invalid_time".localized
            case .invalidCycleDays:
                return "add_supplement_error_invalid_cycle_days".localized
            case .invalidIntervalDays:
                return "add_supplement_error_invalid_interval_days".localized
            case .modelSaveFailed(let error), .notificationSyncFailed(let error):
                return String(format: "add_supplement_error_save_failed_format".localized, error.localizedDescription)
            }
        }
    }
    
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
    public var isIntervalDaysEnabled: Bool = false
    public var intervalDays: String = "2"
    
    public var suggestions: [SupplementReference] = []
    public var isLoading: Bool = false
    public var isSaving: Bool = false
    public var errorMessage: String? = nil
    
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
            if let interval = supplement.cycleConfig.intervalDays, interval > 1 {
                isIntervalDaysEnabled = true
                intervalDays = String(interval)
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
        guard !isSaving else { return nil }
        errorMessage = nil
        isSaving = true
        defer { isSaving = false }
        
        do {
            let result = try persistSupplement()
            try await syncNotifications(for: result.supplement, wasEditing: result.wasEditing)
            return result.supplement
        } catch let failure as SaveFailure {
            errorMessage = failure.message
            return nil
        } catch {
            errorMessage = String(format: "add_supplement_error_save_failed_format".localized, error.localizedDescription)
            return nil
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
        isIntervalDaysEnabled = false
        intervalDays = "2"
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
        do {
            return try buildSupplementOrThrow(id: id)
        } catch let failure as SaveFailure {
            errorMessage = failure.message
            return nil
        } catch {
            errorMessage = String(format: "add_supplement_error_save_failed_format".localized, error.localizedDescription)
            return nil
        }
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
    
    private func isValidTimeString(_ timeString: String) -> Bool {
        let list = TimeStrings.normalizeList(timeString)
        guard !list.isEmpty else { return false }
        for item in list {
            guard TimeStrings.parseLenientTime(item) != nil else { return false }
        }
        return true
    }
    
    private struct PersistResult {
        let supplement: UserSupplement
        let wasEditing: Bool
    }
    
    private func persistSupplement() throws -> PersistResult {
        guard editingSupplement != nil || activeClient != nil else { throw SaveFailure.missingActiveClient }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        
        if let existing = editingSupplement {
            let updated = try buildSupplementOrThrow(id: existing.id)
            existing.name = updated.name
            existing.startDate = updated.startDate
            existing.cycleConfig = updated.cycleConfig
            existing.dailyDose = updated.dailyDose
            existing.intakeTime = updated.intakeTime
            existing.updatedAtEpochMs = now
            existing.deletedAtEpochMs = nil
            do {
                try modelContext.save()
            } catch {
                throw SaveFailure.modelSaveFailed(error)
            }
            return PersistResult(supplement: existing, wasEditing: true)
        }
        
        let created = try buildSupplementOrThrow(id: UUID())
        modelContext.insert(created)
        do {
            try modelContext.save()
        } catch {
            throw SaveFailure.modelSaveFailed(error)
        }
        editingSupplement = created
        return PersistResult(supplement: created, wasEditing: false)
    }
    
    private func syncNotifications(for supplement: UserSupplement, wasEditing: Bool) async throws {
        do {
            if wasEditing {
                await notificationService.cancelReminders(for: supplement)
            }
            try await notificationService.scheduleReminders(for: supplement)
        } catch {
            throw SaveFailure.notificationSyncFailed(error)
        }
    }
    
    private func buildSupplementOrThrow(id: UUID) throws -> UserSupplement {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { throw SaveFailure.invalidName }
        let client = try resolveClientOrThrow()
        
        let weekly = makeWeeklyRecurrenceIfNeeded()
        let config = try buildCycleConfigOrThrow(weekly: weekly)
        let timeString = try buildIntakeTimesOrThrow()
        
        return UserSupplement(
            id: id,
            name: trimmedName,
            startDate: startDate,
            cycleConfig: config,
            dailyDose: dailyDose,
            intakeTime: timeString,
            updatedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            deletedAtEpochMs: nil,
            client: client
        )
    }
    
    private func resolveClientOrThrow() throws -> ClientProfile {
        if let activeClient { return activeClient }
        if let client = editingSupplement?.client { return client }
        throw SaveFailure.missingActiveClient
    }
    
    private func buildCycleConfigOrThrow(weekly: WeeklyRecurrenceConfig?) throws -> CycleConfig {
        let interval = try intervalDaysValueOrThrow()
        if isContinuous {
            return CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true, durationMonths: nil, weeklyRecurrence: weekly, intervalDays: interval)
        }
        let parsedDaysOn = Int(daysOn) ?? -1
        let parsedDaysOff = Int(daysOff) ?? -1
        guard parsedDaysOn > 0, parsedDaysOff >= 0 else { throw SaveFailure.invalidCycleDays }
        return CycleConfig(
            daysOn: parsedDaysOn,
            daysOff: parsedDaysOff,
            isContinuous: false,
            durationMonths: Int(durationMonths),
            weeklyRecurrence: weekly,
            intervalDays: interval
        )
    }

    private func intervalDaysValueOrThrow() throws -> Int? {
        guard isIntervalDaysEnabled else { return nil }
        let parsed = Int(intervalDays.trimmingCharacters(in: .whitespacesAndNewlines)) ?? -1
        guard parsed >= 2 else { throw SaveFailure.invalidIntervalDays }
        return parsed
    }
    
    private func buildIntakeTimesOrThrow() throws -> String {
        let timeString = TimeStrings.normalizeString(intakeTimes)
        guard isValidTimeString(timeString) else { throw SaveFailure.invalidIntakeTime }
        return timeString
    }
}
