import Foundation
import SwiftData

@MainActor
enum SyncConflictAnalyzer {
    static func analyze(
        backup: OAKBackupData,
        clientId: UUID,
        context: ModelContext
    ) throws -> SyncConflictPreview {
        let supplements = try context.fetch(FetchDescriptor<UserSupplement>())
            .filter { $0.client?.id == clientId }
        let records = try context.fetch(FetchDescriptor<IntakeRecord>())
            .filter { $0.supplement?.client?.id == clientId }
        let locals = Dictionary(uniqueKeysWithValues: supplements.map { ($0.id, $0) })
        let byId = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0) })
        let byDoseKey = bestRecordsByDoseKey(records)
        return analyzeBackup(backup, supplements: locals, records: byId, byDoseKey: byDoseKey)
    }

    private static func analyzeBackup(
        _ backup: OAKBackupData,
        supplements: [UUID: UserSupplement],
        records: [UUID: IntakeRecord],
        byDoseKey: [String: IntakeRecord]
    ) -> SyncConflictPreview {
        var preview = SyncConflictPreview()
        for remote in backup.stack {
            preview = add(preview, decision(remote: remote, supplements: supplements))
        }
        for remote in backup.history {
            preview = add(preview, decision(remote: remote, records: records, byDoseKey: byDoseKey))
        }
        return preview
    }

    private static func decision(
        remote: OAKBackupSupplement,
        supplements: [UUID: UserSupplement]
    ) -> SyncConflictDecision {
        guard let id = UUID(uuidString: remote.id), let local = supplements[id] else { return .noConflict }
        let localTs = max(local.updatedAtEpochMs, local.deletedAtEpochMs ?? 0)
        let remoteTs = max(remote.updatedAtEpochMs, remote.deletedAtEpochMs ?? 0)
        return SyncConflictPolicy.decide(
            localExists: true,
            localTs: localTs,
            remoteTs: remoteTs,
            differs: supplementDiffers(remote, local)
        )
    }

    private static func decision(
        remote: OAKBackupHistory,
        records: [UUID: IntakeRecord],
        byDoseKey: [String: IntakeRecord]
    ) -> SyncConflictDecision {
        guard let supplementId = UUID(uuidString: remote.supplementId) else { return .noConflict }
        let doseKey = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: remote.dateEpochMs)
        let stableId = DoseEventKey.stableUUID(from: doseKey)
        guard let local = byDoseKey[doseKey] ?? records[stableId] else { return .noConflict }
        let remoteTs = remote.updatedAtEpochMs > 0 ? remote.updatedAtEpochMs : remote.dateEpochMs
        let differs = local.status != remote.status || local.supplement?.id != supplementId
        return SyncConflictPolicy.decide(
            localExists: true,
            localTs: local.updatedAtEpochMs,
            remoteTs: remoteTs,
            differs: differs
        )
    }

    private static func supplementDiffers(_ remote: OAKBackupSupplement, _ local: UserSupplement) -> Bool {
        if remote.deletedAtEpochMs != local.deletedAtEpochMs { return true }
        let fields = remote.modifiedFields
        if applies(fields, "name") && remote.name != local.name { return true }
        if applies(fields, "dailyDose") && remote.dailyDose != local.dailyDose { return true }
        if applies(fields, "intakeTime") && remote.intakeTime != local.intakeTime { return true }
        if applies(fields, "startDate") && remote.startDate != LocalDayCodec.string(from: local.startDate) { return true }
        if applies(fields, "lastTakenLocalDate") && remote.lastTakenLocalDate != local.lastTakenLocalDate { return true }
        return applies(fields, "cycle") && cycleDiffers(remote.cycle, local.cycleConfig)
    }

    private static func cycleDiffers(_ remote: SupplementExportCycle, _ local: CycleConfig) -> Bool {
        remote.isContinuous != local.isContinuous || remote.daysOn != local.daysOn ||
            remote.daysOff != local.daysOff || remote.durationMonths != local.durationMonths ||
            remote.intervalDays != local.intervalDays ||
            remote.weeklyWeekdaysMask != local.weeklyRecurrence?.weekdaysMask ||
            remote.weeklyIntervalWeeks != local.weeklyRecurrence?.intervalWeeks ||
            remote.weeklyAnchorDate != local.weeklyRecurrence.map { LocalDayCodec.string(from: $0.anchorDate) }
    }

    private static func applies(_ fields: Set<String>?, _ field: String) -> Bool {
        fields == nil || fields?.contains(field) == true
    }

    private static func bestRecordsByDoseKey(_ records: [IntakeRecord]) -> [String: IntakeRecord] {
        var best: [String: IntakeRecord] = [:]
        for record in records {
            guard let supplementId = record.supplement?.id else { continue }
            let epochMs = Int64(record.date.timeIntervalSince1970 * 1000)
            let key = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: epochMs)
            let currentUpdatedAt = best[key]?.updatedAtEpochMs ?? Int64.min
            if currentUpdatedAt < record.updatedAtEpochMs { best[key] = record }
        }
        return best
    }

    private static func add(_ preview: SyncConflictPreview, _ decision: SyncConflictDecision) -> SyncConflictPreview {
        var updated = preview
        switch decision {
        case .remoteWins: updated.remoteWins += 1
        case .localWins: updated.localWins += 1
        case .tieLocalWins: updated.tieLocalWins += 1
        case .noConflict: break
        }
        return updated
    }

}
