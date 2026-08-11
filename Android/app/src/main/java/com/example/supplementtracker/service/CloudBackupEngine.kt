package com.example.supplementtracker.service

import android.content.Context
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.export.OAKBackupDataDTO
import com.example.supplementtracker.domain.export.OAKBackupHistoryDTO
import com.example.supplementtracker.domain.export.OAKBackupJson
import com.example.supplementtracker.domain.export.OAKBackupMetaDTO
import com.example.supplementtracker.domain.export.OAKBackupSchema
import com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
import com.example.supplementtracker.domain.export.SupplementExportCycleDTO
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.DoseEventKey
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Application service responsible for building backup payloads used by local export and cloud sync/hosting.
 * It owns backup serialization semantics but does not perform network transport or mutate UI state.
 */
class CloudBackupEngine(
    private val context: Context,
    private val repository: SupplementRepository,
    private val currentClientId: () -> Any?
) {
    suspend fun buildStackBackupJson(): Result<String> = buildBackupJson(includeStack = true, includeHistory = false)

    suspend fun buildHistoryBackupJson(): Result<String> = buildBackupJson(includeStack = false, includeHistory = true)

    suspend fun buildFullBackupJson(): Result<String> = buildBackupJson(includeStack = true, includeHistory = true)

    private suspend fun buildBackupJson(includeStack: Boolean, includeHistory: Boolean): Result<String> = runCatching {
        val clientId = currentClientId()
            ?: error(context.getString(R.string.missing_active_client))
        val clientIdString = clientId.toString()

        val prefs = OakPrefs.get(context)
        val deviceId = prefs.getString("cloudSyncDeviceId", null) ?: run {
            val created = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("cloudSyncDeviceId", created).apply()
            created
        }
        val now = System.currentTimeMillis()

        val stack = if (includeStack) {
            val allFields = setOf("name", "dailyDose", "intakeTime", "startDate", "cycle", "lastTakenLocalDate")
            repository.getAllSupplementsForSync(clientIdString).map { supplement ->
                OAKBackupSupplementDTO(
                    id = supplement.id.toString(),
                    name = supplement.name,
                    dailyDose = supplement.dailyDose,
                    intakeTime = supplement.intakeTime,
                    startDate = supplement.startDate.toString(),
                    cycle = SupplementExportCycleDTO(
                        isContinuous = supplement.cycleConfig.isContinuous,
                        daysOn = supplement.cycleConfig.daysOn,
                        daysOff = supplement.cycleConfig.daysOff,
                        durationMonths = supplement.cycleConfig.durationMonths,
                        weeklyWeekdaysMask = supplement.cycleConfig.weeklyRecurrence?.weekdaysMask,
                        weeklyIntervalWeeks = supplement.cycleConfig.weeklyRecurrence?.intervalWeeks,
                        weeklyAnchorDate = supplement.cycleConfig.weeklyRecurrence?.anchorDate?.toString(),
                        intervalDays = supplement.cycleConfig.intervalDays
                    ),
                    lastTakenLocalDate = supplement.lastTakenLocalDate?.toString(),
                    updatedAtEpochMs = supplement.updatedAtEpochMs,
                    deletedAtEpochMs = supplement.deletedAtEpochMs,
                    modifiedFields = allFields
                )
            }
        } else {
            emptyList()
        }

        val history = if (includeHistory) {
            val cutoffEpochMs = LocalDate.now()
                .minusDays(90)
                .atStartOfDay()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            repository.getAllRecordsForSync(clientIdString)
                .filter { it.date >= cutoffEpochMs }
                .groupBy { DoseEventKey.make(it.supplementId, it.date) }
                .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAtEpochMs } }
                .map { record ->
                    val key = DoseEventKey.make(record.supplementId, record.date)
                    OAKBackupHistoryDTO(
                        id = key,
                        supplementId = record.supplementId.lowercase(Locale.ROOT),
                        dateEpochMs = record.date,
                        status = record.status,
                        updatedAtEpochMs = record.updatedAtEpochMs
                    )
                }
        } else {
            emptyList()
        }

        OAKBackupJson.encode(
            OAKBackupDataDTO(
                version = OAKBackupSchema.VERSION,
                meta = OAKBackupMetaDTO(schemaVersion = 2, updatedAtEpochMs = now, deviceId = deviceId),
                stack = stack,
                history = history,
                historyZlibBase64 = null
            )
        )
    }
}
