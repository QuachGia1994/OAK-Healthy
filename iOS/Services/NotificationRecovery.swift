import Foundation

public enum NotificationRecoveryAction: String, Sendable, Equatable {
    case none
    case repairShadow
    case rebuild
}

public enum NotificationRecoveryReason: String, Sendable, Equatable {
    case inactive
    case healthy
    case shadowDrift
    case missingSchedules
    case scheduleError
    case environmentChanged
    case noFutureSchedule
}

public struct NotificationRecoveryInput: Sendable, Equatable {
    public let enabledByUser: Bool
    public let permissionGranted: Bool
    public let activeSupplementCount: Int
    public let pendingCount: Int
    public let pendingOnlyCount: Int
    public let shadowOnlyCount: Int
    public let shadowErrorCount: Int
    public let environmentChanged: Bool

    public init(
        enabledByUser: Bool,
        permissionGranted: Bool,
        activeSupplementCount: Int,
        pendingCount: Int,
        pendingOnlyCount: Int,
        shadowOnlyCount: Int,
        shadowErrorCount: Int,
        environmentChanged: Bool
    ) {
        self.enabledByUser = enabledByUser
        self.permissionGranted = permissionGranted
        self.activeSupplementCount = activeSupplementCount
        self.pendingCount = pendingCount
        self.pendingOnlyCount = pendingOnlyCount
        self.shadowOnlyCount = shadowOnlyCount
        self.shadowErrorCount = shadowErrorCount
        self.environmentChanged = environmentChanged
    }
}

public struct NotificationRecoveryDecision: Sendable, Equatable {
    public let action: NotificationRecoveryAction
    public let reason: NotificationRecoveryReason

    public init(action: NotificationRecoveryAction, reason: NotificationRecoveryReason) {
        self.action = action
        self.reason = reason
    }
}

public enum NotificationRecoveryPolicy {
    public static func decide(_ input: NotificationRecoveryInput) -> NotificationRecoveryDecision {
        guard input.enabledByUser, input.permissionGranted, input.activeSupplementCount > 0 else {
            return NotificationRecoveryDecision(action: .none, reason: .inactive)
        }
        if input.environmentChanged {
            return NotificationRecoveryDecision(action: .rebuild, reason: .environmentChanged)
        }
        if input.shadowErrorCount > 0 {
            return NotificationRecoveryDecision(action: .rebuild, reason: .scheduleError)
        }
        if input.shadowOnlyCount > 0 {
            return NotificationRecoveryDecision(action: .rebuild, reason: .missingSchedules)
        }
        if input.pendingOnlyCount > 0 {
            return NotificationRecoveryDecision(action: .repairShadow, reason: .shadowDrift)
        }
        if input.pendingCount == 0 {
            return NotificationRecoveryDecision(action: .none, reason: .noFutureSchedule)
        }
        return NotificationRecoveryDecision(action: .none, reason: .healthy)
    }
}
