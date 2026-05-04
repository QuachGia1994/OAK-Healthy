package com.example.supplementtracker.presentation.add_supplement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.Resource
import com.example.supplementtracker.domain.model.SupplementReference
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.usecase.SaveSupplementUseCase
import com.example.supplementtracker.domain.usecase.SearchSupplementUseCase
import com.example.supplementtracker.worker.CycleCheckWorker
import java.util.concurrent.TimeUnit
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel xử lý logic UI cho màn hình thêm chất.
 */
class AddSupplementViewModel(
    private val searchUseCase: SearchSupplementUseCase = SearchSupplementUseCase(),
    private val saveSupplementUseCase: SaveSupplementUseCase,
    private val repository: SupplementRepository,
    private val context: Context // Thêm context để dùng WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(AddSupplementState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

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
        when (val result = searchUseCase(query)) {
            is Resource.Success -> {
                _state.update { it.copy(suggestions = result.data, isLoading = false) }
            }
            is Resource.Error -> {
                _state.update { it.copy(error = result.message, isLoading = false) }
            }
            else -> {}
        }
    }

    /**
     * Áp dụng dữ liệu từ gợi ý.
     */
    fun onSuggestionClick(reference: SupplementReference) {
        _state.update {
            it.copy(
                name = reference.name,
                intakeTime = reference.preferredTime,
                isContinuous = reference.defaultCycle.isContinuous,
                daysOn = reference.defaultCycle.daysOn.toString(),
                daysOff = reference.defaultCycle.daysOff.toString(),
                suggestions = emptyList()
            )
        }
    }

    fun onTimeChange(time: String) {
        _state.update { it.copy(intakeTime = time) }
    }

    fun onDailyDoseChange(dose: String) {
        _state.update { it.copy(dailyDose = dose) }
    }

    fun onContinuousToggle(continuous: Boolean) {
        _state.update { it.copy(isContinuous = continuous) }
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
        _state.value = AddSupplementState()
    }

    fun loadSupplementForEdit(supplementId: String) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
            _state.update {
                it.copy(
                    editingSupplementId = supplement.id.toString(),
                    name = supplement.name,
                    startDate = supplement.startDate,
                    intakeTime = supplement.intakeTime,
                    isContinuous = supplement.cycleConfig.isContinuous,
                    daysOn = supplement.cycleConfig.daysOn.toString(),
                    daysOff = supplement.cycleConfig.daysOff.toString(),
                    durationMonths = supplement.cycleConfig.durationMonths?.toString() ?: "",
                    dailyDose = supplement.dailyDose,
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
        if (currentState.name.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val supplement = UserSupplement(
                    id = currentState.editingSupplementId?.let { java.util.UUID.fromString(it) } ?: java.util.UUID.randomUUID(),
                    name = currentState.name,
                    startDate = currentState.startDate,
                    cycleConfig = if (currentState.isContinuous) {
                        CycleConfig.Continuous
                    } else {
                        CycleConfig(
                            daysOn = currentState.daysOn.toIntOrNull() ?: 1,
                            daysOff = currentState.daysOff.toIntOrNull() ?: 0,
                            durationMonths = currentState.durationMonths.toIntOrNull()
                        )
                    },
                    dailyDose = currentState.dailyDose,
                    intakeTime = currentState.intakeTime
                )
                if (currentState.editingSupplementId == null) {
                    saveSupplementUseCase(supplement)
                } else {
                    repository.updateSupplement(supplement)
                }
                
                // Enqueue Worker để kiểm tra chu kỳ hàng ngày
                enqueueCycleCheckWorker()
                
                _state.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Lỗi khi lưu: ${e.message}") }
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
}
