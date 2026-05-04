package com.example.supplementtracker.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.domain.usecase.GetAllSupplementsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
class HomeViewModel(
    private val getAllSupplementsUseCase: GetAllSupplementsUseCase,
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = getAllSupplementsUseCase()
        .map { supplements ->
            processSupplements(supplements)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun logIntake(supplement: UserSupplement) {
        viewModelScope.launch {
            repository.logIntake(supplement.id.toString(), System.currentTimeMillis())
        }
    }

    private fun processSupplements(supplements: List<UserSupplement>): HomeUiState {
        val today = LocalDate.now()
        
        val activeMap = supplements
            .filter { calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.ON }
            .groupBy { it.intakeTime }
            .toSortedMap(compareBy { it.ordinal })

        val restingList = supplements
            .filter { calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.OFF }
            .map { RestingSupplementInfo(it, calculateDaysRemaining(it, today)) }

        return HomeUiState.Success(activeMap, restingList)
    }

    private fun calculateDaysRemaining(supplement: UserSupplement, today: LocalDate): Int {
        val config = supplement.cycleConfig
        val totalCycleDays = config.daysOn + config.daysOff
        val daysElapsed = ChronoUnit.DAYS.between(supplement.startDate, today).toInt()
        val dayInCycle = daysElapsed % totalCycleDays
        
        return totalCycleDays - dayInCycle
    }
}
