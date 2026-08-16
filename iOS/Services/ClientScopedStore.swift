import Foundation
import SwiftData

enum ClientScopedStore {
    static func supplements(
        modelContext: ModelContext,
        clientId: UUID
    ) throws -> [UserSupplement] {
        let descriptor = FetchDescriptor<UserSupplement>(
            predicate: #Predicate { $0.client?.id == clientId },
            sortBy: [SortDescriptor(\UserSupplement.name)]
        )
        return try modelContext.fetch(descriptor)
    }

    static func activeSupplements(
        modelContext: ModelContext,
        clientId: UUID
    ) throws -> [UserSupplement] {
        let descriptor = FetchDescriptor<UserSupplement>(
            predicate: #Predicate {
                $0.client?.id == clientId && $0.deletedAtEpochMs == nil
            },
            sortBy: [SortDescriptor(\UserSupplement.name)]
        )
        return try modelContext.fetch(descriptor)
    }

    static func historyRecords(
        modelContext: ModelContext,
        clientId: UUID,
        limit: Int
    ) throws -> [IntakeRecord] {
        let records = try recordsForClient(
            modelContext: modelContext,
            clientId: clientId
        )
        return newestRecords(records, limit: limit)
    }

    static func recentHistoryRecords(
        modelContext: ModelContext,
        clientId: UUID,
        cutoff: Date,
        limit: Int
    ) throws -> [IntakeRecord] {
        let records = try recordsForClient(
            modelContext: modelContext,
            clientId: clientId
        ).filter { $0.date >= cutoff }
        return newestRecords(records, limit: limit)
    }

    static func hasSupplementChanges(
        modelContext: ModelContext,
        clientId: UUID,
        since lastSyncEpochMs: Int64
    ) throws -> Bool {
        try supplements(modelContext: modelContext, clientId: clientId).contains {
            $0.updatedAtEpochMs > lastSyncEpochMs ||
                ($0.deletedAtEpochMs ?? 0) > lastSyncEpochMs
        }
    }

    static func hasHistoryChanges(
        modelContext: ModelContext,
        clientId: UUID,
        since lastSyncEpochMs: Int64
    ) throws -> Bool {
        try recordsForClient(modelContext: modelContext, clientId: clientId).contains {
            $0.updatedAtEpochMs > lastSyncEpochMs
        }
    }

    private static func recordsForClient(
        modelContext: ModelContext,
        clientId: UUID
    ) throws -> [IntakeRecord] {
        try supplements(modelContext: modelContext, clientId: clientId)
            .flatMap(\.intakeRecords)
    }

    private static func newestRecords(
        _ records: [IntakeRecord],
        limit: Int
    ) -> [IntakeRecord] {
        Array(records.sorted { $0.date > $1.date }.prefix(max(0, limit)))
    }
}
