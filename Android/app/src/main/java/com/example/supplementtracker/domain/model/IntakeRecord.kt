package com.example.supplementtracker.domain.model

/**
 * Canonical intake-history read model.
 *
 * Identity/status/timestamps are the persisted event semantics. The optional supplement
 * name/dose/time fields are joined routine context for history/report presentation; they
 * are not duplicate persistence owners and must not be used as event identity.
 */
data class IntakeRecord(
    val id: String,
    val supplementId: String,
    val date: Long,
    val status: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val supplementName: String? = null,
    val dailyDose: String? = null,
    val intakeTime: String? = null
)
