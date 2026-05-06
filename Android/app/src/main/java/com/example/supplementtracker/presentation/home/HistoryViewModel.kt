package com.example.supplementtracker.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dữ liệu cho biểu đồ lịch sử.
 */
data class HistoryChartData(
    val label: String,
    val count: Int
)

/**
 * Trạng thái UI cho màn hình lịch sử.
 */
sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data object NoClient : HistoryUiState()
    data class Success(
        val chartData: List<HistoryChartData>,
        val records: List<IntakeRecord>
    ) : HistoryUiState()
}

/**
 * ViewModel xử lý lịch sử uống cho Android.
 */
class HistoryViewModel(
    private val repository: SupplementRepository,
    private val activeClientManager: ActiveClientManager
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = activeClientManager.currentClientId
        .flatMapLatest { clientId ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(HistoryUiState.NoClient)
            repository.observeAllRecordsByClient(id)
                .map { records -> processHistory(records) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )

    private fun processHistory(records: List<IntakeRecord>): HistoryUiState {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val chartData = mutableListOf<HistoryChartData>()
        
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            
            val count = records.filter { it.date in startOfDay until endOfDay }.count()
            chartData.add(HistoryChartData(label = dayLabel(date), count = count))
        }

        val orderedRecords = records.sortedByDescending { it.date }
        return HistoryUiState.Success(chartData, orderedRecords)
    }

    private fun dayLabel(date: LocalDate): String {
        val locale = Locale.getDefault()
        if (locale.language == "vi") {
            val value = date.dayOfWeek.value
            return if (value == 7) "CN" else "Thứ ${value + 1}"
        }
        return date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }
}
