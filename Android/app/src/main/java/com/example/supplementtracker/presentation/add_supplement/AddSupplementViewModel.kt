package com.example.supplementtracker.presentation.add_supplement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.Resource
import com.example.supplementtracker.domain.model.SupplementReference
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.usecase.SaveSupplementUseCase
import com.example.supplementtracker.domain.usecase.SearchSupplementUseCase
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.service.CommercialFeature
import com.example.supplementtracker.service.EntitlementManager
import com.example.supplementtracker.service.NotificationSchedulerImpl
import com.example.supplementtracker.worker.CycleCheckWorker
import java.util.concurrent.TimeUnit
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.supplementtracker.domain.util.TimeStrings

/**
 * ViewModel xử lý logic UI cho màn hình thêm chất.
 */
class AddSupplementViewModel(
    private val saveSupplementUseCase: SaveSupplementUseCase,
    private val repository: SupplementRepository,
    private val context: Context,
    private val activeClientManager: ActiveClientManager,
    private val entitlementManager: EntitlementManager
) : ViewModel() {

    private val searchUseCase: SearchSupplementUseCase = SearchSupplementUseCase(context)
    private val _state = MutableStateFlow(AddSupplementState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            entitlementManager.snapshot.collect { snapshot ->
                val allowed = entitlementManager.canUse(CommercialFeature.ADVANCED_CYCLES)
                _state.update { it.copy(advancedCyclesAllowed = allowed) }
            }
        }
    }

    /**
     * Cập nhật tên và tìm kiếm gợi ý (Debounce).
     */
    fun onNameChange(newName: String) {
        _state.update { it.copy(name = newName) }
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce 300ms
            if (newName.length >= 2) {
                performSearch(newName)
            } else {
                _state.update { it.copy(suggestions = emptyList()) }
            }
        }
    }

    private suspend fun performSearch(query: String) {
        _state.update { it.copy(isLoading = true) }
        val result = runCatching { searchUseCase(query) }
            .getOrElse { Resource.Error(it.message ?: context.getString(R.string.error_unknown)) }
        when (result) {
            is Resource.Success -> {
                _state.update { it.copy(suggestions = result.data, isLoading = false) }
            }
            is Resource.Error -> {
                _state.update { it.copy(error = result.message, isLoading = false) }
            }
            else -> _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Áp dụng dữ liệu từ gợi ý.
     */
    fun onSuggestionClick(reference: SupplementReference) {
        val normalized = TimeStrings.normalizeString(reference.preferredTime)
        val firstTime = TimeStrings.normalizeList(normalized).firstOrNull() ?: "08:00"
        _state.update {
            it.copy(
                name = reference.name,
                selectedTime = firstTime,
                intakeTime = normalized,
                dailyDose = reference.preferredDose ?: it.dailyDose,
                isContinuous = reference.defaultCycle.isContinuous,
                daysOn = reference.defaultCycle.daysOn.toString(),
                daysOff = reference.defaultCycle.daysOff.toString(),
                isWeeklyRecurrenceEnabled = false,
                weekdaysMask = 127,
                intervalWeeks = "1",
                isIntervalDaysEnabled = false,
                intervalDays = "2",
                suggestions = emptyList()
            )
        }
    }

    fun onSelectedTimeChange(time: String) {
        val normalized = TimeStrings.normalizeString(time)
        if (normalized.isBlank()) return
        _state.update { it.copy(selectedTime = normalized, error = null) }
    }

    fun addSelectedTime() {
        val current = _state.value
        val candidate = TimeStrings.normalizeString(current.selectedTime)
        if (candidate.isBlank()) {
            _state.update { it.copy(error = context.getString(R.string.add_supplement_error_invalid_time)) }
            return
        }
        val merged = if (current.intakeTime.isBlank()) candidate else "${current.intakeTime}, $candidate"
        val normalized = TimeStrings.normalizeString(merged)
        if (normalized.isBlank()) {
            _state.update { it.copy(error = context.getString(R.string.add_supplement_error_invalid_time)) }
            return
        }
        _state.update { it.copy(intakeTime = normalized, error = null) }
    }

    fun removeIntakeTime(time: String) {
        val remaining = TimeStrings.normalizeList(_state.value.intakeTime).filterNot { it == time }
        _state.update { it.copy(intakeTime = remaining.joinToString(", "), error = null) }
    }

    fun onIntakeTimesChange(raw: String) {
        _state.update { it.copy(intakeTime = raw, error = null) }
    }

    fun onTimeChange(time: String) {
        _state.update { it.copy(intakeTime = TimeStrings.normalizeString(time)) }
    }

    fun onStartDateChange(date: LocalDate) {
        _state.update { it.copy(startDate = date) }
    }

    fun onDailyDoseChange(dose: String) {
        _state.update { it.copy(dailyDose = dose) }
    }

    fun onContinuousToggle(continuous: Boolean) {
        _state.update { it.copy(isContinuous = continuous) }
    }

    fun onWeeklyRecurrenceToggle(enabled: Boolean) {
        _state.update {
            it.copy(
                isWeeklyRecurrenceEnabled = enabled,
                weekdaysMask = if (enabled) it.weekdaysMask else it.weekdaysMask,
                intervalWeeks = if (enabled) it.intervalWeeks else it.intervalWeeks
            )
        }
    }
    
    fun toggleWeekday(bitIndex: Int) {
        if (bitIndex !in 0..6) return
        _state.update {
            val bit = 1 shl bitIndex
            val next = if ((it.weekdaysMask and bit) != 0) it.weekdaysMask and bit.inv() else it.weekdaysMask or bit
            it.copy(weekdaysMask = if (next == 0) bit else next)
        }
    }
    
    fun onIntervalWeeksChange(value: String) {
        _state.update { it.copy(intervalWeeks = value) }
    }

    fun onIntervalDaysToggle(enabled: Boolean) {
        _state.update { it.copy(isIntervalDaysEnabled = enabled) }
    }

    fun onIntervalDaysChange(value: String) {
        _state.update { it.copy(intervalDays = value) }
    }
    
    fun onDurationChange(duration: String) {
        _state.update { it.copy(durationMonths = duration) }
    }

    fun onDaysOnChange(days: String) {
        _state.update { it.copy(daysOn = days) }
    }

    fun onDaysOffChange(days: String) {
        _state.update { it.copy(daysOff = days) }
    }

    fun resetForAdd() {
        val allowed = entitlementManager.canUse(CommercialFeature.ADVANCED_CYCLES)
        _state.value = AddSupplementState(
            isContinuous = !allowed,
            advancedCyclesAllowed = allowed
        )
    }

    fun loadSupplementForEdit(supplementId: String) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
            val weekly = supplement.cycleConfig.weeklyRecurrence
            val intervalDays = supplement.cycleConfig.intervalDays
            val normalizedTime = TimeStrings.normalizeString(supplement.intakeTime)
            val firstTime = TimeStrings.normalizeList(normalizedTime).firstOrNull() ?: "08:00"
            _state.update {
                it.copy(
                    editingSupplementId = supplement.id.toString(),
                    name = supplement.name,
                    startDate = supplement.startDate,
                    selectedTime = firstTime,
                    intakeTime = normalizedTime,
                    isContinuous = supplement.cycleConfig.isContinuous,
                    daysOn = supplement.cycleConfig.daysOn.toString(),
                    daysOff = supplement.cycleConfig.daysOff.toString(),
                    durationMonths = supplement.cycleConfig.durationMonths?.toString() ?: "",
                    dailyDose = supplement.dailyDose,
                    isWeeklyRecurrenceEnabled = weekly != null,
                    weekdaysMask = weekly?.weekdaysMask ?: 127,
                    intervalWeeks = weekly?.intervalWeeks?.toString() ?: "1",
                    isIntervalDaysEnabled = intervalDays != null && intervalDays > 1,
                    intervalDays = (intervalDays ?: 2).toString(),
                    suggestions = emptyList(),
                    error = null
                )
            }
        }
    }

    /**
     * Lưu thực phẩm bổ sung vào Room Database.
     */
    fun saveSupplement(onSuccess: () -> Unit) {
        val currentState = _state.value
        val name = currentState.name.trim()
        if (name.isEmpty()) return
        if (usesAdvancedCycle(currentState) && !currentState.advancedCyclesAllowed) {
            _state.update { it.copy(error = context.getString(R.string.plan_advanced_cycles_required)) }
            return
        }

        val normalizedTime = TimeStrings.normalizeString(currentState.intakeTime)
        if (normalizedTime.isBlank()) {
            _state.update { it.copy(error = context.getString(R.string.add_supplement_error_invalid_time)) }
            return
        }
        
        val daysOn = if (currentState.isContinuous) null else currentState.daysOn.toIntOrNull()
        val daysOff = if (currentState.isContinuous) null else currentState.daysOff.toIntOrNull()
        val intervalDays = if (currentState.isIntervalDaysEnabled) currentState.intervalDays.toIntOrNull() else null
        
        if (!currentState.isContinuous) {
            val isInvalid = (daysOn == null || daysOn <= 0) || (daysOff == null || daysOff < 0)
            if (isInvalid) {
                _state.update { it.copy(error = context.getString(R.string.add_supplement_error_invalid_cycle_days)) }
                return
            }
        }
        if (currentState.isIntervalDaysEnabled) {
            if (intervalDays == null || intervalDays < 2) {
                _state.update { it.copy(error = context.getString(R.string.add_supplement_error_invalid_interval_days)) }
                return
            }
        }

        viewModelScope.launch {
            val clientId = activeClientManager.currentClientId.value
            if (clientId == null) {
                _state.update { it.copy(error = context.getString(R.string.missing_active_client)) }
                return@launch
            }

            _state.update { it.copy(isLoading = true) }
            try {
                val supplement = UserSupplement(
                    id = currentState.editingSupplementId?.let { java.util.UUID.fromString(it) } ?: java.util.UUID.randomUUID(),
                    clientId = clientId,
                    name = name,
                    startDate = currentState.startDate,
                    cycleConfig = if (currentState.isContinuous) {
                        CycleConfig(
                            daysOn = 1,
                            daysOff = 0,
                            isContinuous = true,
                            durationMonths = currentState.durationMonths.toIntOrNull(),
                            weeklyRecurrence = weeklyConfigIfNeeded(currentState),
                            intervalDays = intervalDays
                        )
                    } else {
                        CycleConfig(
                            daysOn = daysOn ?: 1,
                            daysOff = daysOff ?: 0,
                            durationMonths = currentState.durationMonths.toIntOrNull(),
                            weeklyRecurrence = weeklyConfigIfNeeded(currentState),
                            intervalDays = intervalDays
                        )
                    },
                    dailyDose = currentState.dailyDose,
                    intakeTime = normalizedTime,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                if (currentState.editingSupplementId == null) {
                    saveSupplementUseCase(supplement)
                    com.example.supplementtracker.service.ActivationRetentionStore(context)
                        .mark(com.example.supplementtracker.service.ActivationMilestone.ROUTINE_READY)
                } else {
                    repository.updateSupplement(supplement)
                }
                
                // Enqueue Worker để kiểm tra chu kỳ hàng ngày
                enqueueCycleCheckWorker()

                val supplements = repository.getAllSupplements(clientId.toString()).first()
                NotificationSchedulerImpl(context).rescheduleAll(supplements)
                
                _state.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(
                            R.string.add_supplement_error_save_failed_format,
                            e.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
            }
        }
    }

    private fun enqueueCycleCheckWorker() {
        val workRequest = PeriodicWorkRequestBuilder<CycleCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "CycleCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    // Các hàm update khác cho daysOn, daysOff, startDate...
    
    private fun usesAdvancedCycle(state: AddSupplementState): Boolean {
        return !state.isContinuous ||
            state.isWeeklyRecurrenceEnabled ||
            state.isIntervalDaysEnabled ||
            state.durationMonths.isNotBlank()
    }

    private fun weeklyConfigIfNeeded(state: AddSupplementState): WeeklyRecurrenceConfig? {
        if (!state.isWeeklyRecurrenceEnabled) return null
        val interval = state.intervalWeeks.toIntOrNull() ?: 1
        val safeInterval = if (interval <= 0) 1 else interval
        return WeeklyRecurrenceConfig(
            weekdaysMask = state.weekdaysMask,
            intervalWeeks = safeInterval,
            anchorDate = state.startDate
        )
    }
}
