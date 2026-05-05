package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.domain.model.UserSupplement

/**
 * Trạng thái UI cho màn hình Home Dashboard.
 */
sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object NoClient : HomeUiState()
    data class Success(
        val activeSupplements: Map<String, List<SupplementUiItem>>,
        val restingSupplements: List<RestingSupplementInfo>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

/**
 * Trạng thái UI cho mỗi thực phẩm bổ sung trên Dashboard.
 */
data class SupplementUiItem(
    val supplement: UserSupplement,
    val isTaken: Boolean,
    val advice: String? = null
)

/**
 * Thông tin bổ sung cho chất đang nghỉ.
 */
data class RestingSupplementInfo(
    val supplement: UserSupplement,
    val daysRemaining: Int
)
