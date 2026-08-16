package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.export.OAKBackupDataDTO
import com.example.supplementtracker.domain.export.OAKBackupJson
import com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.DoseEventKey
import com.example.supplementtracker.domain.util.StableId
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

data class BackupImportPreview(
    val sourceSchema: String,
    val supplementCount: Int,
    val historyCount: Int,
    val existingSupplementCount: Int,
    val existingHistoryCount: Int,
    val remappedSupplementIdCount: Int,
    val duplicateSupplementIdCount: Int,
    val duplicateHistoryCount: Int,
    val orphanHistoryCount: Int,
    val integrityVerified: Boolean,
    val canRestore: Boolean
)

class BackupImportPlan internal constructor(
    val preview: BackupImportPreview,
    internal val clientId: String,
    internal val supplements: List<UserSupplement>,
    internal val records: List<IntakeRecord>,
    internal val snapshot: BackupImportSnapshot
)

internal data class BackupImportSnapshot(
    val supplements: List<UserSupplement>,
    val records: List<IntakeRecord>
)

private data class PreparedSupplements(
    val supplements: List<UserSupplement>,
    val resolvedIds: Map<String, String>,
    val remappedIdCount: Int
)

private data class PreparedHistory(
    val records: List<IntakeRecord>,
    val duplicateCount: Int,
    val orphanCount: Int
)

/**
 * Builds a dry-run plan before replacing one client's data and restores a snapshot on failure.
 */
class ImportBackupUseCase(
    private val repository: SupplementRepository
) {
    suspend operator fun invoke(preparedJson: String, clientId: UUID): Result<Unit> {
        val plan = preview(preparedJson, clientId).getOrElse { return Result.failure(it) }
        return restore(plan)
    }

    suspend fun preview(preparedJson: String, clientId: UUID): Result<BackupImportPlan> = runCatching {
        val decoded = OAKBackupJson.decodeCompat(preparedJson).getOrThrow()
        val sourceSchema = OAKBackupJson.sourceSchema(preparedJson).getOrThrow()
        buildPlan(decoded, sourceSchema, clientId)
    }

    suspend fun restore(plan: BackupImportPlan): Result<Unit> {
        if (!plan.preview.canRestore) {
            return Result.failure(IllegalStateException("Backup import has blocking collisions"))
        }
        return try {
            repository.importBackupAtomic(plan.clientId, plan.supplements, plan.records)
            Result.success(Unit)
        } catch (importError: Throwable) {
            restoreSnapshot(plan, importError)
        }
    }

    private suspend fun restoreSnapshot(
        plan: BackupImportPlan,
        importError: Throwable
    ): Result<Unit> {
        return try {
            repository.importBackupAtomic(
                plan.clientId,
                plan.snapshot.supplements,
                plan.snapshot.records
            )
            Result.failure(importError)
        } catch (rollbackError: Throwable) {
            importError.addSuppressed(rollbackError)
            Result.failure(importError)
        }
    }

    private suspend fun buildPlan(
        decoded: OAKBackupDataDTO,
        sourceSchema: String,
        clientId: UUID
    ): BackupImportPlan {
        val snapshot = captureSnapshot(clientId)
        val preparedSupplements = prepareSupplements(decoded, clientId)
        val preparedHistory = prepareHistory(decoded, preparedSupplements.resolvedIds)
        val duplicateSupplementIds = duplicateSupplementIdCount(decoded)
        val preview = buildPreview(
            decoded,
            sourceSchema,
            snapshot,
            preparedSupplements,
            preparedHistory,
            duplicateSupplementIds
        )
        return BackupImportPlan(
            preview = preview,
            clientId = clientId.toString(),
            supplements = preparedSupplements.supplements,
            records = preparedHistory.records,
            snapshot = snapshot
        )
    }

    private suspend fun captureSnapshot(clientId: UUID): BackupImportSnapshot {
        val id = clientId.toString()
        return BackupImportSnapshot(
            supplements = repository.getAllSupplementsForSync(id),
            records = repository.getAllRecordsByClient(id)
        )
    }

    private suspend fun prepareSupplements(
        decoded: OAKBackupDataDTO,
        clientId: UUID
    ): PreparedSupplements {
        val resolvedIds = HashMap<String, String>(decoded.stack.size)
        val reservedIds = HashSet<String>(decoded.stack.size)
        var remappedIdCount = 0
        val supplements = decoded.stack.map { dto ->
            val sourceId = normalizeId(dto.id)
            val resolvedId = resolvedIds[sourceId]?.let(UUID::fromString)
                ?: resolveSupplementId(dto.id, clientId, reservedIds).also {
                    resolvedIds[sourceId] = normalizeId(it.toString())
                    if (normalizeId(it.toString()) != sourceId) remappedIdCount += 1
                }
            mapSupplement(dto, clientId, resolvedId)
        }
        return PreparedSupplements(supplements, resolvedIds, remappedIdCount)
    }

    private fun buildPreview(
        decoded: OAKBackupDataDTO,
        sourceSchema: String,
        snapshot: BackupImportSnapshot,
        supplements: PreparedSupplements,
        history: PreparedHistory,
        duplicateSupplementIds: Int
    ): BackupImportPreview {
        val canRestore = duplicateSupplementIds == 0 &&
            history.duplicateCount == 0 && history.orphanCount == 0
        return BackupImportPreview(
            sourceSchema = sourceSchema,
            supplementCount = decoded.stack.size,
            historyCount = decoded.history.size,
            existingSupplementCount = snapshot.supplements.size,
            existingHistoryCount = snapshot.records.size,
            remappedSupplementIdCount = supplements.remappedIdCount,
            duplicateSupplementIdCount = duplicateSupplementIds,
            duplicateHistoryCount = history.duplicateCount,
            orphanHistoryCount = history.orphanCount,
            integrityVerified = decoded.integrity != null,
            canRestore = canRestore
        )
    }

    private fun duplicateSupplementIdCount(decoded: OAKBackupDataDTO): Int {
        return decoded.stack
            .groupingBy { normalizeId(it.id) }
            .eachCount()
            .values
            .sumOf { count -> (count - 1).coerceAtLeast(0) }
    }

    private fun mapSupplement(
        dto: OAKBackupSupplementDTO,
        clientId: UUID,
        resolvedId: UUID
    ): UserSupplement {
        val cycle = CycleConfig(
            daysOn = dto.cycle.daysOn,
            daysOff = dto.cycle.daysOff,
            isContinuous = dto.cycle.isContinuous,
            durationMonths = dto.cycle.durationMonths,
            weeklyRecurrence = mapWeeklyRecurrence(dto),
            intervalDays = dto.cycle.intervalDays
        )
        val startDate = LocalDate.parse(dto.startDate)
        val lastTakenLocalDate = dto.lastTakenLocalDate?.let(LocalDate::parse)
        return UserSupplement(
            id = resolvedId,
            clientId = clientId,
            name = dto.name,
            startDate = startDate,
            cycleConfig = cycle,
            dailyDose = dto.dailyDose,
            intakeTime = dto.intakeTime,
            lastTakenLocalDate = lastTakenLocalDate,
            updatedAtEpochMs = dto.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            deletedAtEpochMs = dto.deletedAtEpochMs
        )
    }

    private fun mapWeeklyRecurrence(dto: OAKBackupSupplementDTO): WeeklyRecurrenceConfig? {
        val cycle = dto.cycle
        val hasWeeklyField = cycle.weeklyWeekdaysMask != null ||
            cycle.weeklyIntervalWeeks != null ||
            cycle.weeklyAnchorDate != null
        if (!hasWeeklyField) return null
        val mask = requireNotNull(cycle.weeklyWeekdaysMask) { "Missing weekly weekday mask" }
        val interval = requireNotNull(cycle.weeklyIntervalWeeks) { "Missing weekly interval" }
        val anchor = LocalDate.parse(requireNotNull(cycle.weeklyAnchorDate) { "Missing weekly anchor" })
        return WeeklyRecurrenceConfig(mask, interval, anchor)
    }

    private suspend fun resolveSupplementId(
        rawId: String,
        clientId: UUID,
        reservedIds: MutableSet<String>
    ): UUID {
        val normalizedRawId = normalizeId(rawId)
        val parsed = runCatching { UUID.fromString(rawId) }.getOrElse {
            StableId.uuidFromString(normalizedRawId)
        }
        if (reserveIfAvailable(parsed, clientId, reservedIds)) return parsed
        var attempt = 0
        while (true) {
            val key = "${clientId.toString().lowercase(Locale.ROOT)}|$normalizedRawId|$attempt"
            val candidate = StableId.uuidFromString(key)
            if (reserveIfAvailable(candidate, clientId, reservedIds)) return candidate
            attempt += 1
        }
    }

    private suspend fun reserveIfAvailable(
        candidate: UUID,
        clientId: UUID,
        reservedIds: MutableSet<String>
    ): Boolean {
        val key = normalizeId(candidate.toString())
        if (!reservedIds.add(key)) return false
        val existing = repository.getSupplementById(candidate.toString())
        return existing == null || existing.clientId == clientId
    }

    private fun prepareHistory(
        decoded: OAKBackupDataDTO,
        resolvedIds: Map<String, String>
    ): PreparedHistory {
        var orphanCount = 0
        val mapped = decoded.history.mapNotNull { record ->
            val supplementId = resolvedIds[normalizeId(record.supplementId)]
            if (supplementId == null) {
                orphanCount += 1
                return@mapNotNull null
            }
            IntakeRecord(
                id = DoseEventKey.make(supplementId, record.dateEpochMs),
                supplementId = supplementId,
                date = record.dateEpochMs,
                status = record.status,
                updatedAtEpochMs = record.updatedAtEpochMs.takeIf { it > 0L } ?: record.dateEpochMs
            )
        }
        val records = mapped.groupBy(IntakeRecord::id).mapNotNull { (_, values) ->
            values.maxByOrNull(IntakeRecord::updatedAtEpochMs)
        }
        return PreparedHistory(records, mapped.size - records.size, orphanCount)
    }

    private fun normalizeId(raw: String): String = raw.trim().lowercase(Locale.ROOT)
}
