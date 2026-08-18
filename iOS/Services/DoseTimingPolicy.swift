import Foundation

/// Canonical dose timing and adherence formulas shared by Home, History and reports.
public enum DoseTimingPolicy: Sendable {
    public static let soonWindowMilliseconds: Int64 = 20 * 60 * 1_000
    public static let missedAfterMilliseconds: Int64 = 2 * 60 * 60 * 1_000

    public static func isDueSoon(scheduledAtEpochMs: Int64, nowEpochMs: Int64) -> Bool {
        scheduledAtEpochMs > nowEpochMs && scheduledAtEpochMs - nowEpochMs <= soonWindowMilliseconds
    }

    public static func isMissed(scheduledAtEpochMs: Int64, nowEpochMs: Int64) -> Bool {
        scheduledAtEpochMs > 0 && nowEpochMs > scheduledAtEpochMs + missedAfterMilliseconds
    }

    public static func isMissedSoon(scheduledAtEpochMs: Int64, nowEpochMs: Int64) -> Bool {
        let missedAt = scheduledAtEpochMs + missedAfterMilliseconds
        return scheduledAtEpochMs > 0 && nowEpochMs >= missedAt - soonWindowMilliseconds && nowEpochMs < missedAt
    }

    public static func isLateTaken(status: String, scheduledAtEpochMs: Int64, updatedAtEpochMs: Int64) -> Bool {
        status == IntakeStatus.taken.rawValue && updatedAtEpochMs > 0 &&
            updatedAtEpochMs > scheduledAtEpochMs + soonWindowMilliseconds
    }

    public static func completionRate(taken: Int, skipped: Int) -> Double? {
        let total = taken + skipped
        guard total > 0 else { return nil }
        return Double(taken) / Double(total)
    }

    public static func completionPercent(taken: Int, skipped: Int) -> Int? {
        completionRate(taken: taken, skipped: skipped).map { Int($0 * 100) }
    }
}
