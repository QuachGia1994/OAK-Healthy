package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.SupplementRepository

/** Owns persisted mutations for supplement routines outside create/import flows. */
class SupplementMutationUseCase(
    private val repository: SupplementRepository
) {
    suspend fun updateRoutine(supplement: UserSupplement) {
        repository.updateSupplement(supplement)
    }

    suspend fun updateIntakeTimes(supplement: UserSupplement, intakeTime: String) {
        repository.updateSupplement(
            supplement.copy(
                intakeTime = intakeTime,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun softDeleteRoutine(supplement: UserSupplement) {
        repository.deleteSupplement(supplement)
    }
}
