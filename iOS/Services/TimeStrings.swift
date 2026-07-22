import Foundation

public struct TimeStrings: Sendable {
    public static func normalizeList(_ raw: String) -> [String] {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let tokens = trimmed
            .split(whereSeparator: { ",;|".contains($0) })
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let parsed = tokens.compactMap(parseLenientTime)
        let unique = Array(Set(parsed)).sorted()
        return unique.map(formatTime)
    }
    
    public static func normalizeString(_ raw: String) -> String {
        normalizeList(raw).joined(separator: ", ")
    }
    
    /// Returns normalized dose times after removing the requested time.
    public static func removingTime(_ time: String, from raw: String) -> [String] {
        guard let minutes = parseLenientTime(time) else { return normalizeList(raw) }
        let target = formatTime(minutes)
        return normalizeList(raw).filter { $0 != target }
    }

    public static func parseLenientTime(_ token: String) -> Int? {
        let parts = token.split(separator: ":").map { String($0) }
        guard parts.count == 2 else { return nil }
        guard let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        guard (0...23).contains(hour), (0...59).contains(minute) else { return nil }
        return hour * 60 + minute
    }
    
    public static func formatTime(_ minutesSinceMidnight: Int) -> String {
        let clamped = max(0, min(23 * 60 + 59, minutesSinceMidnight))
        let hour = clamped / 60
        let minute = clamped % 60
        return String(format: "%02d:%02d", hour, minute)
    }
}
