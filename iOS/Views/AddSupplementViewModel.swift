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
    public var daysOn: String = "30"
    public var daysOff: String = "7"
    public var durationMonths: String = ""
    public var dailyDose: String = ""
    public var isContinuous: Bool = false
    
    public var suggestions: [SupplementReference] = []
    public var isLoading: Bool = false
    
    // MARK: - Dependencies
    private let suggestService: any AutoSuggestService
    private let notificationService: any NotificationManaging
    private let calendarService: any CalendarManaging
    private let modelContext: ModelContext
    private var editingSupplement: UserSupplement?
    private let activeClient: ClientProfile?
    
    public init(
        modelContext: ModelContext,
        editingSupplement: UserSupplement? = nil,
        activeClient: ClientProfile? = nil,
        suggestService: any AutoSuggestService = SupplementAutoSuggester(),
        notificationService: any NotificationManaging = NotificationService(),
        calendarService: any CalendarManaging = CalendarService()
    ) {
        self.modelContext = modelContext
        self.editingSupplement = editingSupplement
        self.activeClient = activeClient ?? editingSupplement?.client
        self.suggestService = suggestService
        self.notificationService = notificationService
        self.calendarService = calendarService
        
        if let supplement = editingSupplement {
            name = supplement.name
            startDate = supplement.startDate
            dailyDose = supplement.dailyDose
            isContinuous = supplement.cycleConfig.isContinuous
            daysOn = String(supplement.cycleConfig.daysOn)
            daysOff = String(supplement.cycleConfig.daysOff)
            durationMonths = supplement.cycleConfig.durationMonths.map(String.init) ?? ""
            
            let formatter = DateFormatter()
            formatter.dateFormat = "HH:mm"
            if let timeDate = formatter.date(from: supplement.intakeTime) {
                let calendar = Calendar.current
                let components = calendar.dateComponents([.hour, .minute], from: timeDate)
                selectedTime = calendar.date(
                    bySettingHour: components.hour ?? 8,
                    minute: components.minute ?? 0,
                    second: 0,
                    of: .now
                ) ?? .now
            }
        }
    }
    
    // MARK: - Actions

    /// Lưu thực phẩm bổ sung và thiết lập thông báo/lịch.
    public func saveSupplement() async -> UserSupplement? {
        let supplement: UserSupplement
        if let existing = editingSupplement {
            guard let updated = createSupplement(id: existing.id) else { return nil }
            existing.name = updated.name
            existing.startDate = updated.startDate
            existing.cycleConfig = updated.cycleConfig
            existing.dailyDose = updated.dailyDose
            existing.intakeTime = updated.intakeTime
            supplement = existing
        } else {
            guard let created = createSupplement(id: UUID()) else { return nil }
            modelContext.insert(created)
            supplement = created
        }
        
        do {
            try modelContext.save()
            
            // 1. Xin quyền & Lên lịch thông báo
            try await notificationService.requestAuthorization()
            try await notificationService.scheduleReminders(for: supplement)
            
            // 2. Xin quyền & Đồng bộ lịch
            try await calendarService.requestAccess()
            try await calendarService.syncCycleToCalendar(for: supplement)
            
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
        
        // Chuyển đổi String HH:mm sang Date
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        if let date = formatter.date(from: reference.preferredTime) {
            let calendar = Calendar.current
            let components = calendar.dateComponents([.hour, .minute], from: date)
            selectedTime = calendar.date(bySettingHour: components.hour ?? 8, 
                                       minute: components.minute ?? 0, 
                                       second: 0, 
                                       of: .now) ?? .now
        }
        
        isContinuous = reference.defaultCycle.isContinuous
        daysOn = String(reference.defaultCycle.daysOn)
        daysOff = String(reference.defaultCycle.daysOff)
        suggestions = []
    }
    
    /// Tạo đối tượng UserSupplement hoàn chỉnh.
    public func createSupplement(id: UUID) -> UserSupplement? {
        guard !name.isEmpty else { return nil }
        guard let activeClient else { return nil }
        
        let config = isContinuous 
            ? CycleConfig.continuous 
            : CycleConfig(
                daysOn: Int(daysOn) ?? 1, 
                daysOff: Int(daysOff) ?? 0,
                durationMonths: Int(durationMonths)
            )
        
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        let timeString = formatter.string(from: selectedTime)
        
        return UserSupplement(
            id: id,
            name: name,
            startDate: startDate,
            cycleConfig: config,
            dailyDose: dailyDose,
            intakeTime: timeString,
            client: activeClient
        )
    }
}
