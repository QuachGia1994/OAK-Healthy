package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.export.OAKBackupJson
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

/**
 * Imports a prepared OAK backup payload into one client without owning UI or Android side effects.
 */
class ImportBackupUseCase(
    private val repository: SupplementRepository
) {
    suspend operator fun invoke(preparedJson: String, clientId: UUID): Result<Unit> = runCatching {
        val decoded = OAKBackupJson.decodeCompat(preparedJson).getOrThrow()
        val clientIdString = clientId.toString()
        val resolvedIds = HashMap<String, String>(decoded.stack.size)
        val reservedIds = HashSet<String>(decoded.stack.size)
        val supplementsToImport = decoded.stack.map { dto ->
            val sourceId = normalizeId(dto.id)
            val resolvedId = resolvedIds[sourceId]?.let(UUID::fromString)
                ?: resolveSupplementId(dto.id, clientId, reservedIds).also {
                    resolvedIds[sourceId] = normalizeId(it.toString())
                }
            mapSupplement(dto, clientId, resolvedId)
        }
        val recordsToImport = mapHistoryRecords(decoded.history, resolvedIds)
        repository.importBackupAtomic(clientIdString, supplementsToImport, recordsToImport)
    }

    private fun mapSupplement(
        dto: com.example.supplementtracker.domain.export.OAKBackupSupplementDTO,
        clientId: UUID,
        resolvedId: UUID
    ): UserSupplement {
        val weekly = mapWeeklyRecurrence(dto)
        val cycle = CycleConfig(
            daysOn = dto.cycle.daysOn,
            daysOff = dto.cycle.daysOff,
            isContinuous = dto.cycle.isContinuous,
            durationMonths = dto.cycle.durationMonths,
            weeklyRecurrence = weekly,
            intervalDays = dto.cycle.intervalDays
        )
        val startDate = runCatching { LocalDate.parse(dto.startDate) }.getOrElse { LocalDate.now() }
        val lastTakenLocalDate = dto.lastTakenLocalDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
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

    private fun mapWeeklyRecurrence(
        dto: com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
    ): WeeklyRecurrenceConfig? {
        val mask = dto.cycle.weeklyWeekdaysMask ?: return null
        val interval = dto.cycle.weeklyIntervalWeeks ?: return null
        val anchor = dto.cycle.weeklyAnchorDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        return WeeklyRecurrenceConfig(
            weekdaysMask = mask,
            intervalWeeks = interval,
            anchorDate = anchor
        )
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
            val candidate = StableId.uuidFromString("${clientId.toString().lowercase(Locale.ROOT)}|$normalizedRawId|$attempt")
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

    private fun mapHistoryRecords(
        history: List<com.example.supplementtracker.domain.export.OAKBackupHistoryDTO>,
        resolvedIds: Map<String, String>
    ): List<IntakeRecord> = history.mapNotNull { record ->
        val supplementId = resolvedIds[normalizeId(record.supplementId)] ?: return@mapNotNull null
        IntakeRecord(
            id = DoseEventKey.make(supplementId, record.dateEpochMs),
            supplementId = supplementId,
            date = record.dateEpochMs,
            status = record.status,
            updatedAtEpochMs = record.updatedAtEpochMs.takeIf { it > 0L } ?: record.dateEpochMs
        )
    }.groupBy { it.id }.mapNotNull { (_, records) ->
        records.maxByOrNull { it.updatedAtEpochMs }
    }

    private fun normalizeId(raw: String): String = raw.trim().lowercase(Locale.ROOT)
}
