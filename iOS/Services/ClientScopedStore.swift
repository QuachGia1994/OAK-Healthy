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
        var descriptor = FetchDescriptor<IntakeRecord>(
            predicate: #Predicate { $0.supplement?.client?.id == clientId },
            sortBy: [SortDescriptor(\IntakeRecord.date, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return try modelContext.fetch(descriptor)
    }

    static func recentHistoryRecords(
        modelContext: ModelContext,
        clientId: UUID,
        cutoff: Date,
        limit: Int
    ) throws -> [IntakeRecord] {
        var descriptor = FetchDescriptor<IntakeRecord>(
            predicate: #Predicate {
                $0.supplement?.client?.id == clientId && $0.date >= cutoff
            },
            sortBy: [SortDescriptor(\IntakeRecord.date, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return try modelContext.fetch(descriptor)
    }

    static func hasSupplementChanges(
        modelContext: ModelContext,
        clientId: UUID,
        since lastSyncEpochMs: Int64
    ) throws -> Bool {
        var descriptor = FetchDescriptor<UserSupplement>(
            predicate: #Predicate {
                $0.client?.id == clientId &&
                ($0.updatedAtEpochMs > lastSyncEpochMs ||
                 ($0.deletedAtEpochMs != nil && $0.deletedAtEpochMs! > lastSyncEpochMs))
            }
        )
        descriptor.fetchLimit = 1
        return try !modelContext.fetch(descriptor).isEmpty
    }

    static func hasHistoryChanges(
        modelContext: ModelContext,
        clientId: UUID,
        since lastSyncEpochMs: Int64
    ) throws -> Bool {
        var descriptor = FetchDescriptor<IntakeRecord>(
            predicate: #Predicate {
                $0.supplement?.client?.id == clientId &&
                $0.updatedAtEpochMs > lastSyncEpochMs
            }
        )
        descriptor.fetchLimit = 1
        return try !modelContext.fetch(descriptor).isEmpty
    }
}
