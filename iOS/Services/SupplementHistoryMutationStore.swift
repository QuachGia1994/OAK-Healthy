import Foundation
import SwiftData

public struct DosePersistenceResult {
    public let record: IntakeRecord
    public let inserted: Bool
}

/// Persistence owner for routine tombstones and canonical intake-history mutations.
@MainActor
public enum SupplementHistoryMutationStore {
    public static func recordDose(
        supplement: UserSupplement,
        scheduledAt: Date,
        intakeTime: String,
        status: IntakeStatus,
        updatedAtEpochMs: Int64,
        in context: ModelContext
    ) throws -> DosePersistenceResult {
        let epochMs = Int64(scheduledAt.timeIntervalSince1970 * 1_000)
        let canonicalDate = Date(timeIntervalSince1970: Double(epochMs) / 1_000.0)
        let key = DoseEventKey.make(supplementId: supplement.id, scheduledAtEpochMs: epochMs)
        let id = DoseEventKey.stableUUID(from: key)
        if let existing = try existingRecord(supplement: supplement, key: key, id: id, context: context) {
            return DosePersistenceResult(record: existing, inserted: false)
        }
        return try insertDose(
            id: id,
            supplement: supplement,
            scheduledAt: canonicalDate,
            intakeTime: intakeTime,
            status: status,
            updatedAtEpochMs: updatedAtEpochMs,
            context: context
        )
    }

    private static func insertDose(
        id: UUID,
        supplement: UserSupplement,
        scheduledAt: Date,
        intakeTime: String,
        status: IntakeStatus,
        updatedAtEpochMs: Int64,
        context: ModelContext
    ) throws -> DosePersistenceResult {
        let record = makeRecord(
            id: id,
            scheduledAt: scheduledAt,
            intakeTime: intakeTime,
            status: status,
            updatedAtEpochMs: updatedAtEpochMs,
            supplement: supplement
        )
        context.insert(record)
        updateLastTakenIfNeeded(
            supplement: supplement,
            scheduledAt: scheduledAt,
            status: status,
            updatedAtEpochMs: updatedAtEpochMs
        )
        try saveOrRollback(context)
        return DosePersistenceResult(record: record, inserted: true)
    }

    private static func existingRecord(
        supplement: UserSupplement,
        key: String,
        id: UUID,
        context: ModelContext
    ) throws -> IntakeRecord? {
        if let related = supplement.intakeRecords.first(where: { record in
            let epochMs = Int64(record.date.timeIntervalSince1970 * 1_000)
            return DoseEventKey.make(supplementId: supplement.id, scheduledAtEpochMs: epochMs) == key
        }) {
            return related
        }
        return try fetchRecord(id: id, context: context)
    }

    private static func makeRecord(
        id: UUID,
        scheduledAt: Date,
        intakeTime: String,
        status: IntakeStatus,
        updatedAtEpochMs: Int64,
        supplement: UserSupplement
    ) -> IntakeRecord {
        IntakeRecord(
            id: id,
            date: scheduledAt,
            status: status.rawValue,
            intakeTime: intakeTime,
            updatedAtEpochMs: updatedAtEpochMs,
            supplement: supplement
        )
    }

    private static func updateLastTakenIfNeeded(
        supplement: UserSupplement,
        scheduledAt: Date,
        status: IntakeStatus,
        updatedAtEpochMs: Int64
    ) {
        guard status == .taken else { return }
        supplement.lastTakenLocalDate = LocalDayCodec.string(from: scheduledAt)
        supplement.updatedAtEpochMs = updatedAtEpochMs
    }

    public static func softDelete(
        _ supplement: UserSupplement,
        at epochMs: Int64,
        in context: ModelContext
    ) throws {
        supplement.deletedAtEpochMs = epochMs
        supplement.updatedAtEpochMs = epochMs
        try saveOrRollback(context)
    }

    public static func softDelete(
        _ supplements: [UserSupplement],
        at epochMs: Int64,
        in context: ModelContext
    ) throws {
        guard !supplements.isEmpty else { return }
        for supplement in supplements {
            supplement.deletedAtEpochMs = epochMs
            supplement.updatedAtEpochMs = epochMs
        }
        try saveOrRollback(context)
    }

    private static func saveOrRollback(_ context: ModelContext) throws {
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }

    private static func fetchRecord(id: UUID, context: ModelContext) throws -> IntakeRecord? {
        let descriptor = FetchDescriptor<IntakeRecord>(predicate: #Predicate { $0.id == id })
        return try context.fetch(descriptor).first
    }
}
