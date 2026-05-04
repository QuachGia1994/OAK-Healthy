package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement

/**
 * Trạng thái UI cho màn hình Home Dashboard.
 */
sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val activeSupplements: Map<IntakeTime, List<UserSupplement>>,
        val restingSupplements: List<RestingSupplementInfo>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

/**
 * Thông tin bổ sung cho chất đang nghỉ.
 */
data class RestingSupplementInfo(
    val supplement: UserSupplement,
    val daysRemaining: Int
)
