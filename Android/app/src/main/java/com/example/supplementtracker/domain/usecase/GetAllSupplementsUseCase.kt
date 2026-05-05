package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.Flow

/**
 * UseCase lấy toàn bộ danh sách thực phẩm bổ sung.
 */
class GetAllSupplementsUseCase(
    private val repository: SupplementRepository
) {
    operator fun invoke(clientId: String): Flow<List<UserSupplement>> {
        return repository.getAllSupplements(clientId)
    }
}
