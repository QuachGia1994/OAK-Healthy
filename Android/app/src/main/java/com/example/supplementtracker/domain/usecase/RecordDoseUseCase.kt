package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.DoseEventKey
import java.time.ZoneId
import java.util.Locale

class RecordDoseUseCase(
    private val repository: SupplementRepository
) {
    enum class Action(val rawStatus: String) {
        TAKEN("Taken"),
        SKIPPED("Skipped")
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
                status = action.rawStatus,
                updatedAtEpochMs = now
            )
        )

        if (action == Action.TAKEN) {
            val supplement = repository.getSupplementById(normalizedSupplementId) ?: return
            val day = java.time.Instant.ofEpochMilli(scheduledAtEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            repository.updateSupplement(
                supplement.copy(lastTakenLocalDate = day, updatedAtEpochMs = now)
            )
        }
    }
}
