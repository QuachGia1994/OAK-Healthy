package com.example.supplementtracker.service

import com.example.supplementtracker.domain.export.OAKBackupDataDTO
import com.example.supplementtracker.domain.export.OAKBackupHistoryDTO
import com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.util.DoseEventKey
import java.util.Locale

object SyncConflictAnalyzer {
    fun analyze(
        backup: OAKBackupDataDTO,
        localSupplements: Map<String, UserSupplement>,
        localRecords: Map<String, IntakeRecord>,
        localRecordsByDoseKey: Map<String, IntakeRecord>
    ): SyncConflictPreview {
        var preview = SyncConflictPreview()
        backup.stack.forEach { remote ->
            preview = add(preview, supplementDecision(remote, localSupplements))
        }
        backup.history.forEach { remote ->
            preview = add(preview, historyDecision(remote, localRecords, localRecordsByDoseKey))
        }
        return preview
    }

    private fun supplementDecision(
        remote: OAKBackupSupplementDTO,
        locals: Map<String, UserSupplement>
    ): SyncConflictDecision {
        val local = locals[remote.id.lowercase(Locale.ROOT)] ?: return SyncConflictDecision.NO_CONFLICT
        val localTs = maxOf(local.updatedAtEpochMs, local.deletedAtEpochMs ?: 0L)
        val remoteTs = maxOf(remote.updatedAtEpochMs, remote.deletedAtEpochMs ?: 0L)
        return SyncConflictPolicy.decide(true, localTs, remoteTs, supplementDiffers(remote, local))
    }

    private fun historyDecision(
        remote: OAKBackupHistoryDTO,
        locals: Map<String, IntakeRecord>,
        byDoseKey: Map<String, IntakeRecord>
    ): SyncConflictDecision {
        val normalizedSupplementId = remote.supplementId.lowercase(Locale.ROOT)
        val doseKey = DoseEventKey.make(normalizedSupplementId, remote.dateEpochMs)
        val local = byDoseKey[doseKey] ?: locals[remote.id.lowercase(Locale.ROOT)]
            ?: return SyncConflictDecision.NO_CONFLICT
        val remoteTs = remote.updatedAtEpochMs.takeIf { it > 0L } ?: remote.dateEpochMs
        val differs = local.status != remote.status ||
            local.supplementId.lowercase(Locale.ROOT) != normalizedSupplementId
        return SyncConflictPolicy.decide(true, local.updatedAtEpochMs, remoteTs, differs)
    }

    private fun supplementDiffers(remote: OAKBackupSupplementDTO, local: UserSupplement): Boolean {
        if (remote.deletedAtEpochMs != local.deletedAtEpochMs) return true
        val fields = remote.modifiedFields
        if (applies(fields, "name") && remote.name != local.name) return true
        if (applies(fields, "dailyDose") && remote.dailyDose != local.dailyDose) return true
        if (applies(fields, "intakeTime") && remote.intakeTime != local.intakeTime) return true
        if (applies(fields, "startDate") && remote.startDate != local.startDate.toString()) return true
        if (applies(fields, "lastTakenLocalDate") && remote.lastTakenLocalDate != local.lastTakenLocalDate?.toString()) return true
        return applies(fields, "cycle") && cycleDiffers(remote, local)
    }

    private fun cycleDiffers(remote: OAKBackupSupplementDTO, local: UserSupplement): Boolean {
        val cycle = local.cycleConfig
        return remote.cycle.isContinuous != cycle.isContinuous ||
            remote.cycle.daysOn != cycle.daysOn || remote.cycle.daysOff != cycle.daysOff ||
            remote.cycle.durationMonths != cycle.durationMonths || remote.cycle.intervalDays != cycle.intervalDays ||
            remote.cycle.weeklyWeekdaysMask != cycle.weeklyRecurrence?.weekdaysMask ||
            remote.cycle.weeklyIntervalWeeks != cycle.weeklyRecurrence?.intervalWeeks ||
            remote.cycle.weeklyAnchorDate != cycle.weeklyRecurrence?.anchorDate?.toString()
    }

    private fun applies(fields: Set<String>?, name: String): Boolean = fields == null || fields.contains(name)

    private fun add(preview: SyncConflictPreview, decision: SyncConflictDecision): SyncConflictPreview = when (decision) {
        SyncConflictDecision.REMOTE_WINS -> preview.copy(remoteWins = preview.remoteWins + 1)
        SyncConflictDecision.LOCAL_WINS -> preview.copy(localWins = preview.localWins + 1)
        SyncConflictDecision.TIE_LOCAL_WINS -> preview.copy(tieLocalWins = preview.tieLocalWins + 1)
        SyncConflictDecision.NO_CONFLICT -> preview
    }
}
