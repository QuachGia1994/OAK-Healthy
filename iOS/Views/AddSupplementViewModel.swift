import SwiftData

/// ViewModel quản lý logic thêm mới thực phẩm bổ sung.
/// Sử dụng `@Observable` macro theo Swift 6.2+ standard.
@Observable
@MainActor
public final class AddSupplementViewModel {
    // MARK: - State
    public var name: String = ""
    public var startDate: Date = .now
    public var intakeTime: IntakeTime = .morning
    public var daysOn: String = "30"
    public var daysOff: String = "7"
    public var dailyDose: String = ""
    public var isContinuous: Bool = false
    
    public var suggestions: [SupplementReference] = []
    public var isLoading: Bool = false
    
    // MARK: - Dependencies
    private let suggestService: any AutoSuggestService
    private let notificationService: any NotificationManaging
    private let calendarService: any CalendarManaging
    private let modelContext: ModelContext
    
    public init(
        modelContext: ModelContext,
        suggestService: any AutoSuggestService = SupplementAutoSuggester(),
        notificationService: any NotificationManaging = NotificationService(),
        calendarService: any CalendarManaging = CalendarService()
    ) {
        self.modelContext = modelContext
        self.suggestService = suggestService
        self.notificationService = notificationService
        self.calendarService = calendarService
    }
    
    // MARK: - Actions

    /// Lưu thực phẩm bổ sung và thiết lập thông báo/lịch.
    public func saveSupplement() async -> UserSupplement? {
        guard let supplement = createSupplement() else { return nil }
        
        // Lưu vào SwiftData
        modelContext.insert(supplement)
        
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
        intakeTime = reference.preferredTime
        isContinuous = reference.defaultCycle.isContinuous
        daysOn = String(reference.defaultCycle.daysOn)
        daysOff = String(reference.defaultCycle.daysOff)
        suggestions = []
    }
    
    /// Tạo đối tượng UserSupplement hoàn chỉnh.
    public func createSupplement() -> UserSupplement? {
        guard !name.isEmpty else { return nil }
        
        let config = isContinuous 
            ? CycleConfig.continuous 
            : CycleConfig(daysOn: Int(daysOn) ?? 1, daysOff: Int(daysOff) ?? 0)
        
        return UserSupplement(
            name: name,
            startDate: startDate,
            cycleConfig: config,
            dailyDose: dailyDose,
            intakeTime: intakeTime
        )
    }
}
