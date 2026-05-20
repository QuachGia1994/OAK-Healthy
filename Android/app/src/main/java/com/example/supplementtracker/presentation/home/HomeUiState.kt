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
        val restingSupplements: List<RestingSupplementInfo>,
        val streakDays: Int
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

/**
 * Trạng thái UI cho mỗi thực phẩm bổ sung trên Dashboard.
 */
data class SupplementUiItem(
    val supplement: UserSupplement,
    val timeString: String,
    val scheduledAtEpochMs: Long,
    val doseStatus: DoseStatus,
    val advice: String? = null,
    val isDueSoon: Boolean = false,
    val isMissedSoon: Boolean = false
)

enum class DoseStatus {
    PLANNED,
    TAKEN,
    SKIPPED,
    MISSED
}

enum class DoseAction {
    TAKEN,
    SKIPPED
}

/**
 * Thông tin bổ sung cho chất đang nghỉ.
 */
data class RestingSupplementInfo(
    val supplement: UserSupplement,
    val daysRemaining: Int
)
