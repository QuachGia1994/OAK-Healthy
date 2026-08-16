import Foundation

public enum NotificationReliabilityLevel: String, Sendable {
    case healthy
    case degraded
    case needsRepair
    case inactive
}

public struct NotificationReliabilityInput: Sendable, Equatable {
    public let permissionGranted: Bool
    public let enabledByUser: Bool
    public let hasActiveClient: Bool
    public let activeSupplementCount: Int
    public let pendingCount: Int
    public let pendingOnlyCount: Int
    public let shadowOnlyCount: Int
    public let shadowErrorCount: Int

    public init(
        permissionGranted: Bool,
        enabledByUser: Bool,
        hasActiveClient: Bool,
        activeSupplementCount: Int,
        pendingCount: Int,
        pendingOnlyCount: Int,
        shadowOnlyCount: Int,
        shadowErrorCount: Int
    ) {
        self.permissionGranted = permissionGranted
        self.enabledByUser = enabledByUser
        self.hasActiveClient = hasActiveClient
        self.activeSupplementCount = activeSupplementCount
        self.pendingCount = pendingCount
        self.pendingOnlyCount = pendingOnlyCount
        self.shadowOnlyCount = shadowOnlyCount
        self.shadowErrorCount = shadowErrorCount
    }
}

public struct NotificationReliabilityReport: Sendable, Equatable {
    public let level: NotificationReliabilityLevel
    public let mismatchCount: Int
    public let shouldOfferRepair: Bool
}

public enum NotificationReliabilityEvaluator {
    public static func evaluate(_ input: NotificationReliabilityInput) -> NotificationReliabilityReport {
        let inactive = !input.enabledByUser || !input.permissionGranted || !input.hasActiveClient
        if inactive || input.activeSupplementCount == 0 {
            return NotificationReliabilityReport(level: .inactive, mismatchCount: mismatchCount(input), shouldOfferRepair: false)
        }
        let mismatches = mismatchCount(input)
        if mismatches > 0 {
            return NotificationReliabilityReport(level: .needsRepair, mismatchCount: mismatches, shouldOfferRepair: true)
        }
        if input.pendingCount == 0 {
            return NotificationReliabilityReport(level: .degraded, mismatchCount: 0, shouldOfferRepair: false)
        }
        return NotificationReliabilityReport(level: .healthy, mismatchCount: 0, shouldOfferRepair: false)
    }

    private static func mismatchCount(_ input: NotificationReliabilityInput) -> Int {
        input.pendingOnlyCount + input.shadowOnlyCount + input.shadowErrorCount
    }
}
