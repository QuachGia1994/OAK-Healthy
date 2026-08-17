import Foundation

struct CloudSyncLogEntry: Codable, Identifiable, Equatable, Sendable {
    var id: String { "\(epochMs)_\(phase)" }
    let epochMs: Int64
    let phase: String
    let message: String
}

enum CloudSyncLogStore {
    static func load(manifestId: String, defaults: UserDefaults = .standard) -> [CloudSyncLogEntry] {
        let id = manifestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, let data = defaults.data(forKey: key(id)) else { return [] }
        return (try? JSONDecoder().decode([CloudSyncLogEntry].self, from: data)) ?? []
    }

    static func append(
        manifestId: String,
        phase: String,
        message: String,
        nowEpochMs: Int64,
        defaults: UserDefaults = .standard
    ) -> [CloudSyncLogEntry] {
        let id = manifestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return [] }
        var entries = load(manifestId: id, defaults: defaults)
        let entry = CloudSyncLogEntry(epochMs: nowEpochMs, phase: phase, message: message)
        guard !isRecentDuplicate(entry, entries: entries) else { return entries }
        entries.insert(entry, at: 0)
        if entries.count > 30 { entries = Array(entries.prefix(30)) }
        save(entries, manifestId: id, defaults: defaults)
        return entries
    }

    static func clear(manifestId: String, defaults: UserDefaults = .standard) {
        let id = manifestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return }
        defaults.removeObject(forKey: key(id))
    }

    private static func isRecentDuplicate(
        _ entry: CloudSyncLogEntry,
        entries: [CloudSyncLogEntry]
    ) -> Bool {
        guard let first = entries.first else { return false }
        return first.phase == entry.phase &&
            first.message == entry.message &&
            entry.epochMs - first.epochMs < 15_000
    }

    private static func save(
        _ entries: [CloudSyncLogEntry],
        manifestId: String,
        defaults: UserDefaults
    ) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: key(manifestId))
    }

    private static func key(_ id: String) -> String { "cloudSyncLog_\(id)" }
}
