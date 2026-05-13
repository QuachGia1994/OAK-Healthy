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
            try? await Task.sleep(for: .seconds(1))
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
        
        for dayOffset in 0..<7 {
            guard let checkDate = calendar.date(byAdding: .day, value: dayOffset, to: .now),
                  let triggerDate = calendar.date(bySettingHour: timeComponents.hour ?? 8, 
                                                minute: timeComponents.minute ?? 0, 
                                                second: 0, 
                                                of: checkDate) else { continue }
            
            guard triggerDate > .now else { continue }
            
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
        let prefix = "\(supplement.id.uuidString)-"
        let identifiers = await pendingRequestIdentifiers()
        let identifiersToRemove = identifiers.filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: identifiersToRemove)
    }
    
    @MainActor
    public func pendingRequestsSummary() async -> String {
        await withCheckedContinuation { continuation in
            center.getPendingNotificationRequests { requests in
                let calendar = Calendar.current
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd HH:mm"
                
                let lines = requests.map { request -> String in
                    let triggerDate: String
                    if let trigger = request.trigger as? UNCalendarNotificationTrigger,
                       let date = calendar.date(from: trigger.dateComponents) {
                        triggerDate = formatter.string(from: date)
                    } else {
                        triggerDate = "unknown"
                    }
                    
                    let name = request.content.userInfo["supplementName"] as? String ?? "unknown"
                    let intakeTime = request.content.userInfo["intakeTime"] as? String ?? "unknown"
                    return "\(name) | \(intakeTime) | \(triggerDate) | \(request.identifier)"
                }
                continuation.resume(returning: lines.sorted().joined(separator: "\n"))
            }
        }
    }
    
    @MainActor
    public func pendingRequestIdentifiers() async -> [String] {
        await withCheckedContinuation { continuation in
            center.getPendingNotificationRequests { requests in
                continuation.resume(returning: requests.map(\.identifier))
            }
        }
    }
    
    // MARK: - Private Helpers
    
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
