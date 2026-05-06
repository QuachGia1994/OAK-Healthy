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
    val cycle: SupplementExportCycleDTO
)

data class SupplementExportCycleDTO(
    val isContinuous: Boolean,
    val daysOn: Int,
    val daysOff: Int,
    val durationMonths: Int?
)

object SupplementExportSchema {
    const val VERSION = 1
}

data class OAKBackupDataDTO(
    val version: String,
    val stack: List<OAKBackupSupplementDTO>,
    val history: List<OAKBackupHistoryDTO>
)

data class OAKBackupSupplementDTO(
    val id: String,
    val name: String,
    val dailyDose: String,
    val intakeTime: String,
    val startDate: String,
    val cycle: SupplementExportCycleDTO
)

data class OAKBackupHistoryDTO(
    val id: String,
    val supplementId: String,
    val dateEpochMs: Long,
    val status: String
)

object OAKBackupSchema {
    const val VERSION = "1.1"
}
