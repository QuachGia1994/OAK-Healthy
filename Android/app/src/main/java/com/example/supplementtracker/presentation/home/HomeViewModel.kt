package com.example.supplementtracker.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import com.example.supplementtracker.data.mock.SupplementDictionary

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
class HomeViewModel(
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = _refreshTrigger
        .flatMapLatest {
            repository.getSupplementsWithTakenToday(getStartOfDay(), getEndOfDay())
        }
        .map { supplements ->
            processSupplements(supplements)
        }
        .stateIn(
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
        supplements: List<UserSupplementTakenToday>
    ): HomeUiState {
        val today = LocalDate.now()
        val activeItems = supplements
            .filter { calculateCycleUseCase(it.supplement.startDate, it.supplement.cycleConfig, today) == CycleStatus.ON }
            .map { taken ->
                val advice = SupplementDictionary.references.find { it.name == taken.supplement.name }?.advice
                SupplementUiItem(taken.supplement, taken.isTakenToday, advice)
            }
            .groupBy { it.supplement.intakeTime }
            .toSortedMap()

        val restingList = supplements
            .filter { calculateCycleUseCase(it.supplement.startDate, it.supplement.cycleConfig, today) == CycleStatus.OFF }
            .map { RestingSupplementInfo(it.supplement, calculateDaysRemaining(it.supplement, today)) }

        return HomeUiState.Success(activeItems, restingList)
    }

    fun toggleIntake(supplementId: String, isChecked: Boolean) {
        viewModelScope.launch {
            val startOfDay = getStartOfDay()
            if (isChecked) {
                repository.logIntake(supplementId, startOfDay)
                return@launch
            }
            repository.removeIntake(supplementId, startOfDay)
        }
    }

    fun deleteItem(supplement: UserSupplement) {
        viewModelScope.launch {
            repository.deleteSupplement(supplement)
        }
    }

    fun deleteItem(supplementId: String) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
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
