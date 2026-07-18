import Foundation
@preconcurrency import UserNotifications

struct NotificationDoseAction: Codable, Hashable, Sendable, Identifiable {
    let supplementId: UUID
    let intakeTime: String
    let actionIdentifier: String
    let requestIdentifier: String
    let scheduledAtEpochMs: Int64

    var id: String {
        "\(supplementId.uuidString)|\(scheduledAtEpochMs)|\(actionIdentifier)"
    }

    init(
        supplementId: UUID,
        intakeTime: String,
        actionIdentifier: String,
        requestIdentifier: String,
        scheduledAtEpochMs: Int64
    ) {
        self.supplementId = supplementId
        self.intakeTime = intakeTime
        self.actionIdentifier = actionIdentifier
        self.requestIdentifier = requestIdentifier
        self.scheduledAtEpochMs = scheduledAtEpochMs
    }

    init?(response: UNNotificationResponse) {
        let action = response.actionIdentifier
        guard NotificationService.Action(rawValue: action) != nil else { return nil }
        let info = response.notification.request.content.userInfo
        guard let rawId = info["supplementID"] as? String,
              let supplementId = UUID(uuidString: rawId) else { return nil }
        let epoch = (info["scheduledAtEpochMs"] as? NSNumber)?.int64Value
            ?? (info["scheduledAtEpochMs"] as? Int64)
            ?? 0
        guard epoch > 0 else { return nil }
        self.init(
            supplementId: supplementId,
            intakeTime: info["intakeTime"] as? String ?? "",
            actionIdentifier: action,
            requestIdentifier: response.notification.request.identifier,
            scheduledAtEpochMs: epoch
        )
    }

    @discardableResult
    func savePending(defaults: UserDefaults = .standard) -> Bool {
        do {
            defaults.set(try JSONEncoder().encode(self), forKey: Self.pendingKey)
            return true
        } catch {
            DebugReporter.report("dose_action_queue_save_failed", fields: ["error": error.localizedDescription])
            return false
        }
    }

    static func pending(defaults: UserDefaults = .standard) -> NotificationDoseAction? {
        guard let data = defaults.data(forKey: pendingKey) else { return nil }
        do {
            return try JSONDecoder().decode(NotificationDoseAction.self, from: data)
        } catch {
            defaults.removeObject(forKey: pendingKey)
            DebugReporter.report("dose_action_queue_decode_failed", fields: ["error": error.localizedDescription])
            return nil
        }
    }

    func clearPending(defaults: UserDefaults = .standard) {
        guard Self.pending(defaults: defaults)?.id == id else { return }
        defaults.removeObject(forKey: Self.pendingKey)
    }

    private static let pendingKey = "oakPendingNotificationDoseAction"
}
