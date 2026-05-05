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

