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
import java.time.ZoneOffset
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
            repository.getRecordsByDateRange(
                clientId = id,
                startDate = LocalDate.now().minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                endDate = System.currentTimeMillis()
            ).map { records -> processHistory(records) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )

    private fun processHistory(records: List<IntakeRecord>): HistoryUiState {
        val today = LocalDate.now()
        val chartData = mutableListOf<HistoryChartData>()
        
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            
            val count = records.filter { it.date in startOfDay until endOfDay }.count()
            chartData.add(HistoryChartData(label = dayLabel(date), count = count))
        }

        return HistoryUiState.Success(chartData, records)
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
