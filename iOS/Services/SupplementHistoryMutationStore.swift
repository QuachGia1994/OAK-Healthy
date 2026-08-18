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
        let scheduledAtEpochMs = Int64(scheduledAt.timeIntervalSince1970 * 1_000)
        let canonicalScheduledAt = Date(timeIntervalSince1970: Double(scheduledAtEpochMs) / 1_000.0)
        let key = DoseEventKey.make(supplementId: supplement.id, scheduledAtEpochMs: scheduledAtEpochMs)
        let id = DoseEventKey.stableUUID(from: key)
        if let existing = supplement.intakeRecords.first(where: { record in
            let recordEpochMs = Int64(record.date.timeIntervalSince1970 * 1_000)
            return DoseEventKey.make(supplementId: supplement.id, scheduledAtEpochMs: recordEpochMs) == key
        }) {
            return DosePersistenceResult(record: existing, inserted: false)
        }
        if let existing = try fetchRecord(id: id, context: context) {
            return DosePersistenceResult(record: existing, inserted: false)
        }
        let record = IntakeRecord(
            id: id,
            date: canonicalScheduledAt,
            status: status.rawValue,
            intakeTime: intakeTime,
            updatedAtEpochMs: updatedAtEpochMs,
            supplement: supplement
        )
        context.insert(record)
        if status == .taken {
            supplement.lastTakenLocalDate = LocalDayCodec.string(from: canonicalScheduledAt)
            supplement.updatedAtEpochMs = updatedAtEpochMs
        }
        do {
            try context.save()
            return DosePersistenceResult(record: record, inserted: true)
        } catch {
            context.rollback()
            throw error
        }
    }

    public static func softDelete(
        _ supplement: UserSupplement,
        at epochMs: Int64,
        in context: ModelContext
    ) throws {
        supplement.deletedAtEpochMs = epochMs
        supplement.updatedAtEpochMs = epochMs
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
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
