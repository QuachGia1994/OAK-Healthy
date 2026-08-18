import Foundation
import SwiftData

public struct SupplementRoutineDraft {
    public let id: UUID
    public let name: String
    public let startDate: Date
    public let cycleConfig: CycleConfig
    public let dailyDose: String
    public let intakeTime: String
    public let client: ClientProfile

    public func makeModel(updatedAtEpochMs: Int64) -> UserSupplement {
        UserSupplement(
            id: id,
            name: name,
            startDate: startDate,
            cycleConfig: cycleConfig,
            dailyDose: dailyDose,
            intakeTime: intakeTime,
            updatedAtEpochMs: updatedAtEpochMs,
            deletedAtEpochMs: nil,
            client: client
        )
    }
}

public struct SupplementRoutinePersistenceResult {
    public let supplement: UserSupplement
    public let wasEditing: Bool
}

/// Persistence owner for creating and editing supplement routines.
@MainActor
public enum SupplementRoutineMutationStore {
    public static func persist(
        draft: SupplementRoutineDraft,
        editing existing: UserSupplement?,
        at epochMs: Int64,
        in context: ModelContext
    ) throws -> SupplementRoutinePersistenceResult {
        guard let existing else {
            return try create(draft: draft, at: epochMs, in: context)
        }
        apply(draft: draft, to: existing, at: epochMs)
        try saveOrRollback(context)
        return SupplementRoutinePersistenceResult(supplement: existing, wasEditing: true)
    }

    private static func create(
        draft: SupplementRoutineDraft,
        at epochMs: Int64,
        in context: ModelContext
    ) throws -> SupplementRoutinePersistenceResult {
        let created = draft.makeModel(updatedAtEpochMs: epochMs)
        context.insert(created)
        try saveOrRollback(context)
        return SupplementRoutinePersistenceResult(supplement: created, wasEditing: false)
    }

    private static func apply(
        draft: SupplementRoutineDraft,
        to existing: UserSupplement,
        at epochMs: Int64
    ) {
        existing.name = draft.name
        existing.startDate = draft.startDate
        existing.cycleConfig = draft.cycleConfig
        existing.dailyDose = draft.dailyDose
        existing.intakeTime = draft.intakeTime
        existing.client = draft.client
        existing.updatedAtEpochMs = epochMs
        existing.deletedAtEpochMs = nil
    }

    private static func saveOrRollback(_ context: ModelContext) throws {
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }
}
