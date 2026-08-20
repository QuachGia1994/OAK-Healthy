import Foundation

enum SyncMutationPart: String, Codable, CaseIterable, Hashable, Sendable {
    case stack
    case history
}

struct SyncMutationEntry: Codable, Equatable, Sendable {
    let part: SyncMutationPart
    let enqueuedAtEpochMs: Int64
}

enum SyncMutationQueueStore {
    static func markDirty(
        clientId: UUID,
        part: SyncMutationPart,
        nowEpochMs: Int64,
        defaults: UserDefaults = .standard
    ) {
        var entries = Dictionary(uniqueKeysWithValues: pending(clientId: clientId, defaults: defaults).map { ($0.part, $0) })
        let current = entries[part]?.enqueuedAtEpochMs ?? 0
        entries[part] = SyncMutationEntry(part: part, enqueuedAtEpochMs: max(current, nowEpochMs))
        save(Array(entries.values), clientId: clientId, defaults: defaults)
    }

    static func pending(clientId: UUID, defaults: UserDefaults = .standard) -> [SyncMutationEntry] {
        guard let data = defaults.data(forKey: key(clientId)) else { return [] }
        return ((try? JSONDecoder().decode([SyncMutationEntry].self, from: data)) ?? [])
            .sorted { $0.part.rawValue < $1.part.rawValue }
    }

    static func clearSynced(
        clientId: UUID,
        parts: Set<SyncMutationPart>,
        syncStartedEpochMs: Int64,
        defaults: UserDefaults = .standard
    ) {
        let remaining = pending(clientId: clientId, defaults: defaults).filter {
            !parts.contains($0.part) || $0.enqueuedAtEpochMs > syncStartedEpochMs
        }
        save(remaining, clientId: clientId, defaults: defaults)
    }

    private static func save(_ entries: [SyncMutationEntry], clientId: UUID, defaults: UserDefaults) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: key(clientId))
    }

    private static func key(_ clientId: UUID) -> String {
        "cloudSyncMutationQueue_\(clientId.uuidString.lowercased())"
    }
}

enum SyncConflictDecision: String, Equatable, Sendable {
    case remoteWins
    case localWins
    case tieLocalWins
    case noConflict
}

struct SyncConflictPreview: Equatable, Sendable {
    var remoteWins: Int = 0
    var localWins: Int = 0
    var tieLocalWins: Int = 0

    var materialConflictCount: Int { remoteWins + localWins + tieLocalWins }
}

enum SyncConflictPolicy {
    static func decide(localExists: Bool, localTs: Int64, remoteTs: Int64, differs: Bool) -> SyncConflictDecision {
        guard localExists, differs else { return .noConflict }
        if remoteTs > localTs { return .remoteWins }
        if remoteTs < localTs { return .localWins }
        return .tieLocalWins
    }

    static func remoteMayApply(localTs: Int64, remoteTs: Int64) -> Bool {
        remoteTs > localTs
    }
}

struct SyncRetryState: Equatable, Sendable {
    let failureCount: Int
    let nextRetryEpochMs: Int64
}

enum SyncBackoffPolicy {
    private static let baseDelayMs: Int64 = 15_000
    private static let maxDelayMs: Int64 = 10 * 60_000

    static func nextState(previousFailures: Int, nowEpochMs: Int64) -> SyncRetryState {
        let failures = min(max(previousFailures + 1, 1), 16)
        return SyncRetryState(
            failureCount: failures,
            nextRetryEpochMs: nowEpochMs + delayMs(failureCount: failures)
        )
    }

    static func delayMs(failureCount: Int) -> Int64 {
        guard failureCount > 0 else { return 0 }
        let exponent = min(failureCount - 1, 6)
        let delay = baseDelayMs * Int64(1 << exponent)
        return min(delay, maxDelayMs)
    }

    static func canAttempt(_ state: SyncRetryState, nowEpochMs: Int64) -> Bool {
        state.nextRetryEpochMs <= 0 || nowEpochMs >= state.nextRetryEpochMs
    }
}

enum SyncRetryStore {
    static func state(manifestId: String, defaults: UserDefaults = .standard) -> SyncRetryState {
        SyncRetryState(
            failureCount: defaults.integer(forKey: failureKey(manifestId)),
            nextRetryEpochMs: Int64(defaults.double(forKey: nextRetryKey(manifestId)))
        )
    }

    static func recordFailure(
        manifestId: String,
        nowEpochMs: Int64,
        defaults: UserDefaults = .standard
    ) -> SyncRetryState {
        let next = SyncBackoffPolicy.nextState(
            previousFailures: state(manifestId: manifestId, defaults: defaults).failureCount,
            nowEpochMs: nowEpochMs
        )
        defaults.set(next.failureCount, forKey: failureKey(manifestId))
        defaults.set(Double(next.nextRetryEpochMs), forKey: nextRetryKey(manifestId))
        return next
    }

    static func clear(manifestId: String, defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: failureKey(manifestId))
        defaults.removeObject(forKey: nextRetryKey(manifestId))
    }

    private static func failureKey(_ id: String) -> String { "cloudSyncRetryFailureCount_\(id)" }
    private static func nextRetryKey(_ id: String) -> String { "cloudSyncNextRetryEpochMs_\(id)" }
}

enum SyncJournalEvent: String, Codable, Sendable {
    case start
    case conflict
    case backoff
    case success
    case failure
}

struct SyncJournalEntry: Codable, Equatable, Sendable {
    let epochMs: Int64
    let event: SyncJournalEvent
    var stackDirty: Bool = false
    var historyDirty: Bool = false
    var retryCount: Int = 0
    var nextRetryEpochMs: Int64 = 0
    var remoteWins: Int = 0
    var localWins: Int = 0
    var tieLocalWins: Int = 0
}

enum SyncOperationJournalStore {
    static func append(
        manifestId: String,
        entry: SyncJournalEntry,
        defaults: UserDefaults = .standard
    ) {
        let id = manifestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return }
        var entries = read(manifestId: id, defaults: defaults)
        entries.append(entry)
        if entries.count > 40 { entries = Array(entries.suffix(40)) }
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: key(id))
    }

    static func count(manifestId: String, defaults: UserDefaults = .standard) -> Int {
        read(manifestId: manifestId, defaults: defaults).count
    }

    private static func read(manifestId: String, defaults: UserDefaults) -> [SyncJournalEntry] {
        guard let data = defaults.data(forKey: key(manifestId)) else { return [] }
        return (try? JSONDecoder().decode([SyncJournalEntry].self, from: data)) ?? []
    }

    private static func key(_ id: String) -> String { "cloudSyncJournal_\(id)" }
}
