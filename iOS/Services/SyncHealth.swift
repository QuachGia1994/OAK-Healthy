import Foundation

public enum SyncHealthLevel: String, Sendable {
    case unlinked
    case idle
    case healthy
    case pending
    case needsKey
    case retryableError
    case actionRequired
}

public enum SyncRecoveryAction: String, Sendable {
    case none
    case syncNow
    case importKey
    case checkLink
}

public struct SyncHealthInput: Sendable, Equatable {
    public let hasLink: Bool
    public let autoSyncEnabled: Bool
    public let hasPendingChanges: Bool
    public let lastSyncEpochMs: Int64
    public let lastAttemptEpochMs: Int64
    public let lastError: String?
    public let encryptionEnabled: Bool

    public init(
        hasLink: Bool,
        autoSyncEnabled: Bool,
        hasPendingChanges: Bool,
        lastSyncEpochMs: Int64,
        lastAttemptEpochMs: Int64,
        lastError: String?,
        encryptionEnabled: Bool
    ) {
        self.hasLink = hasLink
        self.autoSyncEnabled = autoSyncEnabled
        self.hasPendingChanges = hasPendingChanges
        self.lastSyncEpochMs = lastSyncEpochMs
        self.lastAttemptEpochMs = lastAttemptEpochMs
        self.lastError = lastError
        self.encryptionEnabled = encryptionEnabled
    }
}

public struct SyncHealthReport: Sendable, Equatable {
    public let level: SyncHealthLevel
    public let action: SyncRecoveryAction
}

public enum SyncHealthEvaluator {
    public static func evaluate(_ input: SyncHealthInput) -> SyncHealthReport {
        guard input.hasLink else { return SyncHealthReport(level: .unlinked, action: .none) }
        let error = input.lastError?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !error.isEmpty { return errorReport(error, encryptionEnabled: input.encryptionEnabled) }
        if input.hasPendingChanges { return SyncHealthReport(level: .pending, action: .syncNow) }
        if input.lastSyncEpochMs > 0 { return SyncHealthReport(level: .healthy, action: .none) }
        let action: SyncRecoveryAction = input.autoSyncEnabled || input.lastAttemptEpochMs > 0 ? .syncNow : .none
        return SyncHealthReport(level: .idle, action: action)
    }

    private static func errorReport(_ error: String, encryptionEnabled: Bool) -> SyncHealthReport {
        let normalized = error.lowercased()
        let keyFailure = encryptionEnabled && (
            normalized.contains("missing cloud sync key") ||
            normalized.contains("decrypt failed") ||
            normalized.contains("missing key") ||
            normalized.contains("giải mã")
        )
        if keyFailure { return SyncHealthReport(level: .needsKey, action: .importKey) }
        if isTransient(normalized) { return SyncHealthReport(level: .retryableError, action: .syncNow) }
        return SyncHealthReport(level: .actionRequired, action: .checkLink)
    }

    private static func isTransient(_ error: String) -> Bool {
        [
            "522", "timed out", "timeout", "no internet", "không có internet",
            "network", "lỗi mạng", "không thể kết nối"
        ].contains { error.contains($0) }
    }
}
