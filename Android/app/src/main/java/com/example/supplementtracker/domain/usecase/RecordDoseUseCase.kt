package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.IntakeStatus
import com.example.supplementtracker.domain.model.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.DoseEventKey
import java.time.ZoneId
import java.util.Locale

class RecordDoseUseCase(
    private val repository: SupplementRepository
) {
    enum class Action(val status: IntakeStatus) {
        TAKEN(IntakeStatus.TAKEN),
        SKIPPED(IntakeStatus.SKIPPED)
    }

    suspend operator fun invoke(
        supplementId: String,
        scheduledAtEpochMs: Long,
        action: Action
    ) {
        if (scheduledAtEpochMs <= 0L) return

        val now = System.currentTimeMillis()
        val normalizedSupplementId = supplementId.lowercase(Locale.ROOT)
        repository.insertIntakeRecord(
            IntakeRecord(
                id = DoseEventKey.make(normalizedSupplementId, scheduledAtEpochMs),
                supplementId = normalizedSupplementId,
                date = scheduledAtEpochMs,
                status = action.status.storageValue,
                updatedAtEpochMs = now
            )
        )

        if (action == Action.TAKEN) {
            val supplement = repository.getSupplementById(normalizedSupplementId) ?: return
            val day = com.example.supplementtracker.domain.util.HealthDayBoundary.localDate(
                scheduledAtEpochMs,
                ZoneId.systemDefault()
            )
            repository.updateSupplement(
                supplement.copy(lastTakenLocalDate = day, updatedAtEpochMs = now)
            )
        }
    }
}
