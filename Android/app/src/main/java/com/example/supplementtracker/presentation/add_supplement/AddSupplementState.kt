package com.example.supplementtracker.presentation.add_supplement

import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.SupplementReference
import java.time.LocalDate

/**
 * Trạng thái UI cho màn hình thêm chất.
 */
data class AddSupplementState(
    val editingSupplementId: String? = null,
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val intakeTime: String = "08:00",
    val daysOn: String = "30",
    val daysOff: String = "7",
    val durationMonths: String = "",
    val dailyDose: String = "",
    val isContinuous: Boolean = false,
    val isWeeklyRecurrenceEnabled: Boolean = false,
    val weekdaysMask: Int = 127,
    val intervalWeeks: String = "1",
    val suggestions: List<SupplementReference> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
