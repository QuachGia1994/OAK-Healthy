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
            let created = draft.makeModel(updatedAtEpochMs: epochMs)
            context.insert(created)
            do {
                try context.save()
                return SupplementRoutinePersistenceResult(supplement: created, wasEditing: false)
            } catch {
                context.rollback()
                throw error
            }
        }
        existing.name = draft.name
        existing.startDate = draft.startDate
        existing.cycleConfig = draft.cycleConfig
        existing.dailyDose = draft.dailyDose
        existing.intakeTime = draft.intakeTime
        existing.client = draft.client
        existing.updatedAtEpochMs = epochMs
        existing.deletedAtEpochMs = nil
        do {
            try context.save()
            return SupplementRoutinePersistenceResult(supplement: existing, wasEditing: true)
        } catch {
            context.rollback()
            throw error
        }
    }
}
