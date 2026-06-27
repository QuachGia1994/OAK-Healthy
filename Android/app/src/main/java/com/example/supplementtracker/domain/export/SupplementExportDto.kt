package com.example.supplementtracker.domain.export

data class SupplementExportFileDTO(
    val schemaVersion: Int,
    val exportedAtEpochMs: Long,
    val supplements: List<SupplementExportSupplementDTO>
)

data class SupplementExportSupplementDTO(
    val name: String,
    val dailyDose: String,
    val intakeTime: String,
    val startDate: String,
    val category: String?,
    val cycle: SupplementExportCycleDTO,
    val lastTakenLocalDate: String? = null
)

data class SupplementExportCycleDTO(
    val isContinuous: Boolean,
    val daysOn: Int,
    val daysOff: Int,
    val durationMonths: Int?,
    val weeklyWeekdaysMask: Int? = null,
    val weeklyIntervalWeeks: Int? = null,
    val weeklyAnchorDate: String? = null,
    val intervalDays: Int? = null
)

object SupplementExportSchema {
    const val VERSION = 1
}

data class OAKBackupDataDTO(
    val version: String,
    val meta: OAKBackupMetaDTO? = null,
    val stack: List<OAKBackupSupplementDTO>,
    val history: List<OAKBackupHistoryDTO>,
    val historyZlibBase64: String? = null
)

data class OAKBackupMetaDTO(
    val schemaVersion: Int,
    val updatedAtEpochMs: Long,
    val deviceId: String
)

data class OAKBackupSupplementDTO(
    val id: String,
    val name: String,
    val dailyDose: String,
    val intakeTime: String,
    val startDate: String,
    val cycle: SupplementExportCycleDTO,
    val lastTakenLocalDate: String? = null,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modifiedFields: Set<String>? = null
)

data class OAKBackupHistoryDTO(
    val id: String,
    val supplementId: String,
    val dateEpochMs: Long,
    val status: String,
    val updatedAtEpochMs: Long = 0L
)

object OAKBackupSchema {
    const val VERSION = "2.0"
}
