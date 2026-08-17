import Foundation
import SwiftData

struct SyncTransferMetrics: Equatable, Sendable {
    let bytesDownloaded: Int
    let bytesUploaded: Int
    let pullMs: Int
    let mergeMs: Int
    let pushMs: Int
    let totalMs: Int
}

struct SyncConflictMetrics: Equatable, Sendable {
    let retryCount: Int
    let remoteWins: Int
    let localWins: Int
    let tieLocalWins: Int
}

struct SyncCenterStatusSnapshot: Equatable, Sendable {
    let lastSyncEpochMs: Int64
    let lastAttemptEpochMs: Int64
    let hasPendingChanges: Bool
    let queuedMutationCount: Int
    let nextRetryEpochMs: Int64
    let journalCount: Int
    let lastError: String?
    let conflict: SyncConflictMetrics
    let transfer: SyncTransferMetrics
    let stackBinId: String
    let historyBinId: String
}

enum SyncCenterStatusReader {
    @MainActor
    static func read(
        manifestId: String,
        clientId: UUID?,
        modelContext: ModelContext,
        defaults: UserDefaults = .standard
    ) -> SyncCenterStatusSnapshot {
        let id = manifestId.trimmingCharacters(in: .whitespacesAndNewlines)
        let lastSync = Int64(defaults.double(forKey: "cloudSyncLastSyncEpochMs_\(id)"))
        let pending = clientId.map {
            CloudSyncAutoSync.hasLocalChangesSince(
                modelContext: modelContext, clientId: $0, lastSyncEpochMs: lastSync
            )
        } ?? false
        let queueCount = clientId.map {
            SyncMutationQueueStore.pending(clientId: $0, defaults: defaults).count
        } ?? 0
        return SyncCenterStatusSnapshot(
            lastSyncEpochMs: lastSync,
            lastAttemptEpochMs: Int64(defaults.double(forKey: "cloudSyncLastAttemptEpochMs_\(id)")),
            hasPendingChanges: pending,
            queuedMutationCount: queueCount,
            nextRetryEpochMs: Int64(defaults.double(forKey: "cloudSyncNextRetryEpochMs_\(id)")),
            journalCount: SyncOperationJournalStore.count(manifestId: id, defaults: defaults),
            lastError: normalizedError(id: id, defaults: defaults),
            conflict: conflictMetrics(id: id, defaults: defaults),
            transfer: transferMetrics(id: id, defaults: defaults),
            stackBinId: trimmedValue(key: "cloudSyncStackBinId_\(id)", defaults: defaults),
            historyBinId: trimmedValue(key: "cloudSyncHistoryBinId_\(id)", defaults: defaults)
        )
    }

    private static func conflictMetrics(id: String, defaults: UserDefaults) -> SyncConflictMetrics {
        SyncConflictMetrics(
            retryCount: defaults.integer(forKey: "cloudSyncConflictRetryCount_\(id)"),
            remoteWins: defaults.integer(forKey: "cloudSyncConflictRemoteWins_\(id)"),
            localWins: defaults.integer(forKey: "cloudSyncConflictLocalWins_\(id)"),
            tieLocalWins: defaults.integer(forKey: "cloudSyncConflictTieLocalWins_\(id)")
        )
    }

    private static func transferMetrics(id: String, defaults: UserDefaults) -> SyncTransferMetrics {
        SyncTransferMetrics(
            bytesDownloaded: defaults.integer(forKey: "cloudSyncBytesDownloaded_\(id)"),
            bytesUploaded: defaults.integer(forKey: "cloudSyncBytesUploaded_\(id)"),
            pullMs: defaults.integer(forKey: "cloudSyncPullMs_\(id)"),
            mergeMs: defaults.integer(forKey: "cloudSyncMergeMs_\(id)"),
            pushMs: defaults.integer(forKey: "cloudSyncPushMs_\(id)"),
            totalMs: defaults.integer(forKey: "cloudSyncTotalMs_\(id)")
        )
    }

    private static func normalizedError(id: String, defaults: UserDefaults) -> String? {
        let value = defaults.string(forKey: "cloudSyncLastError_\(id)") ?? ""
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func trimmedValue(key: String, defaults: UserDefaults) -> String {
        (defaults.string(forKey: key) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
