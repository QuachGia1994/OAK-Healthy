package com.example.supplementtracker.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import com.example.supplementtracker.data.mock.SupplementDictionary
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.export.SupplementExportJson
import com.example.supplementtracker.domain.export.SupplementExportSchema
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.R

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
class HomeViewModel(
    private val context: Context,
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val activeClientManager: ActiveClientManager,
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)
    private val _dataTransferMessage = MutableStateFlow<String?>(null)
    val dataTransferMessage: StateFlow<String?> = _dataTransferMessage
    private val adviceByName: Map<String, String?> =
        SupplementDictionary.localizedReferences(context).associate { it.name to it.advice }

    val uiState: StateFlow<HomeUiState> = combine(
        activeClientManager.currentClientId,
        _refreshTrigger
    ) { clientId, _ -> clientId }
        .flatMapLatest { clientId ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(HomeUiState.NoClient)
            repository.getSupplementsWithTakenToday(id, getStartOfDay(), getEndOfDay())
                .map { supplements -> processSupplements(supplements) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun refresh() {
        _refreshTrigger.value += 1
    }
    
    fun clearDataTransferMessage() {
        _dataTransferMessage.value = null
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
                val advice = adviceByName[taken.supplement.name]
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
                repository.logIntake(supplementId, System.currentTimeMillis())
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
    
    fun importSupplementsFromJson(json: String) {
        viewModelScope.launch {
            val clientId = activeClientManager.currentClientId.value
            if (clientId == null) {
                _dataTransferMessage.value = context.getString(R.string.missing_active_client)
                return@launch
            }
            
            val decoded = SupplementExportJson.decode(json).getOrElse {
                _dataTransferMessage.value = context.getString(R.string.invalid_json)
                return@launch
            }
            
            if (decoded.schemaVersion != SupplementExportSchema.VERSION) {
                _dataTransferMessage.value = context.getString(R.string.invalid_json)
                return@launch
            }
            
            val existing = repository.getAllSupplements(clientId.toString()).first()
            val byName = existing.associateBy { it.name.trim().lowercase() }
            
            decoded.supplements.forEach { dto ->
                val key = dto.name.trim().lowercase()
                val cycle = CycleConfig(
                    daysOn = dto.cycle.daysOn,
                    daysOff = dto.cycle.daysOff,
                    isContinuous = dto.cycle.isContinuous,
                    durationMonths = dto.cycle.durationMonths
                )
                val startDate = runCatching { LocalDate.parse(dto.startDate) }.getOrElse { LocalDate.now() }
                
                val updated = byName[key]?.copy(
                    name = dto.name,
                    startDate = startDate,
                    cycleConfig = cycle,
                    dailyDose = dto.dailyDose,
                    intakeTime = dto.intakeTime
                ) ?: UserSupplement(
                    clientId = clientId,
                    name = dto.name,
                    startDate = startDate,
                    cycleConfig = cycle,
                    dailyDose = dto.dailyDose,
                    intakeTime = dto.intakeTime
                )
                
                if (byName.containsKey(key)) {
                    repository.updateSupplement(updated)
                } else {
                    repository.saveSupplement(updated)
                }
            }
            
            refresh()
            _dataTransferMessage.value = context.getString(R.string.import_success)
        }
    }

    fun createClient(profile: ClientProfile) {
        viewModelScope.launch {
            val newName = profile.name.trim()
            val alreadyExists = activeClientManager.clients.value.any { existing ->
                existing.name.trim().equals(newName, ignoreCase = true)
            }
            if (alreadyExists) return@launch
            repository.saveClient(profile)
        }
    }

    fun deleteClient(profile: ClientProfile) {
        viewModelScope.launch {
            repository.deleteClient(profile)
        }
    }

    fun updateClient(profile: ClientProfile) {
        viewModelScope.launch {
            val newName = profile.name.trim()
            val alreadyExists = activeClientManager.clients.value.any { existing ->
                existing.id != profile.id && existing.name.trim().equals(newName, ignoreCase = true)
            }
            if (alreadyExists) return@launch
            repository.updateClient(profile)
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
