package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.SupplementRepository

/**
 * UseCase lưu thực phẩm bổ sung vào Database.
 */
class SaveSupplementUseCase(
    private val repository: SupplementRepository
) {
    suspend operator fun invoke(supplement: UserSupplement) {
        repository.saveSupplement(supplement)
    }
}
