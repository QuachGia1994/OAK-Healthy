package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.export.OAKBackupJson
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
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
        val importedSupplementIds = HashSet<String>(decoded.stack.size)

        val supplementsToImport = decoded.stack.mapNotNull { dto ->
            val weekly = run {
                val mask = dto.cycle.weeklyWeekdaysMask ?: return@run null
                val interval = dto.cycle.weeklyIntervalWeeks ?: return@run null
                val anchor = dto.cycle.weeklyAnchorDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@run null
                WeeklyRecurrenceConfig(
                    weekdaysMask = mask,
                    intervalWeeks = interval,
                    anchorDate = anchor
                )
            }
            val cycle = CycleConfig(
                daysOn = dto.cycle.daysOn,
                daysOff = dto.cycle.daysOff,
                isContinuous = dto.cycle.isContinuous,
                durationMonths = dto.cycle.durationMonths,
                weeklyRecurrence = weekly
            )
            val startDate = runCatching { LocalDate.parse(dto.startDate) }
                .getOrElse { LocalDate.now() }
            val imported = UserSupplement(
                id = runCatching { UUID.fromString(dto.id) }.getOrElse {
                    StableId.uuidFromString(dto.id.trim().lowercase(Locale.ROOT))
                },
                clientId = clientId,
                name = dto.name,
                startDate = startDate,
                cycleConfig = cycle,
                dailyDose = dto.dailyDose,
                intakeTime = dto.intakeTime,
                updatedAtEpochMs = dto.updatedAtEpochMs.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                deletedAtEpochMs = dto.deletedAtEpochMs
            )
            importedSupplementIds.add(imported.id.toString().lowercase(Locale.ROOT))
            imported
        }

        val recordsToImport = decoded.history.mapNotNull { record ->
            val normalizedSupplementId = record.supplementId.lowercase(Locale.ROOT)
            if (!importedSupplementIds.contains(normalizedSupplementId)) return@mapNotNull null
            IntakeRecord(
                id = record.id,
                supplementId = normalizedSupplementId,
                date = record.dateEpochMs,
                status = record.status,
                updatedAtEpochMs = record.updatedAtEpochMs.takeIf { it > 0L }
                    ?: record.dateEpochMs
            )
        }

        repository.importBackupAtomic(clientIdString, supplementsToImport, recordsToImport)
    }
}
