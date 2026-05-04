package com.example.supplementtracker.presentation.add_supplement

import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.SupplementReference
import java.time.LocalDate

/**
 * Trạng thái UI cho màn hình thêm chất.
 */
data class AddSupplementState(
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val intakeTime: IntakeTime = IntakeTime.MORNING,
    val daysOn: String = "30",
    val daysOff: String = "7",
    val dailyDose: String = "",
    val isContinuous: Boolean = false,
    val suggestions: List<SupplementReference> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
