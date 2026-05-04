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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId
import com.example.supplementtracker.data.mock.SupplementDictionary

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
class HomeViewModel(
    private val getAllSupplementsUseCase: GetAllSupplementsUseCase,
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = combine(
        getAllSupplementsUseCase(),
        _refreshTrigger
    ) { supplements, _ ->
        supplements
    }.combine(
        repository.getRecordsByDateRange(getStartOfDay(), getEndOfDay())
    ) { supplements, todayRecords ->
        processSupplements(supplements, todayRecords)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )

    fun refresh() {
        _refreshTrigger.value += 1
    }

    private fun getStartOfDay() = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun getEndOfDay() = LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun processSupplements(
        supplements: List<UserSupplement>, 
        todayRecords: List<com.example.supplementtracker.domain.repository.IntakeRecord>
    ): HomeUiState {
        val today = LocalDate.now()
        val activeItems = supplements
            .filter { calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.ON }
            .map { supplement ->
                val isTaken = todayRecords.any { it.supplementId == supplement.id.toString() }
                val advice = SupplementDictionary.references.find { it.name == supplement.name }?.advice
                SupplementUiItem(supplement, isTaken, advice)
            }
            .groupBy { it.supplement.intakeTime }
            .toSortedMap()

        val restingList = supplements
            .filter { calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.OFF }
            .map { RestingSupplementInfo(it, calculateDaysRemaining(it, today)) }

        return HomeUiState.Success(activeItems, restingList)
    }

    fun toggleIntake(item: SupplementUiItem) {
        viewModelScope.launch {
            if (item.isTaken) {
                repository.removeIntake(item.supplement.id.toString(), System.currentTimeMillis())
            } else {
                repository.logIntake(item.supplement.id.toString(), System.currentTimeMillis())
            }
        }
    }

    fun deleteSupplement(supplement: UserSupplement) {
        viewModelScope.launch {
            repository.deleteSupplement(supplement)
        }
    }

    private fun calculateDaysRemaining(supplement: UserSupplement, today: LocalDate): Int {
        val config = supplement.cycleConfig
        val totalCycleDays = config.daysOn + config.daysOff
        val daysElapsed = ChronoUnit.DAYS.between(supplement.startDate, today).toInt()
        val dayInCycle = daysElapsed % totalCycleDays
        
        return totalCycleDays - dayInCycle
    }
}
