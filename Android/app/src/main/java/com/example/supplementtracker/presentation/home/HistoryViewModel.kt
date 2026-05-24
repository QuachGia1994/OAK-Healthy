package com.example.supplementtracker.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val sections: List<HistorySection>,
        val insights7: InsightsSummary?,
        val insights30: InsightsSummary?,
        val trend7: List<InsightsTrendPoint>,
        val trend30: List<InsightsTrendPoint>
    ) : HistoryUiState()
}

data class HistorySection(
    val date: LocalDate,
    val records: List<IntakeRecord>
)

data class InsightsTrendPoint(
    val date: LocalDate,
    val takenCount: Int,
    val skippedCount: Int
)

data class InsightsItem(
    val title: String,
    val count: Int
)

data class InsightsSummary(
    val windowDays: Int,
    val completionRate: Float,
    val takenCount: Int,
    val skippedCount: Int,
    val lateCount: Int,
    val topSkipped: List<InsightsItem>,
    val topLate: List<InsightsItem>,
    val topLateHour: InsightsItem?
)

/**
 * ViewModel xử lý lịch sử uống cho Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: SupplementRepository,
    private val activeClientManager: ActiveClientManager
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = activeClientManager.currentClientId
        .flatMapLatest { clientId ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(HistoryUiState.NoClient)
            repository.observeAllRecordsByClient(id)
                .mapLatest { records -> withContext(Dispatchers.Default) { processHistory(records) } }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )

    private fun processHistory(records: List<IntakeRecord>): HistoryUiState {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startDate = today.minusDays(6)
        val orderedRecords = records.sortedByDescending { it.date }
        val counts = mutableMapOf<LocalDate, Int>()
        val sections = mutableListOf<HistorySection>()
        var currentDate: LocalDate? = null
        var currentBucket = mutableListOf<IntakeRecord>()
        for (record in orderedRecords) {
            val date = java.time.Instant.ofEpochMilli(record.date).atZone(zoneId).toLocalDate()
            if (!date.isBefore(startDate) && !date.isAfter(today)) {
                counts[date] = (counts[date] ?: 0) + 1
            }
            if (currentDate == null) {
                currentDate = date
            }
            if (date != currentDate) {
                val finishedDate = currentDate ?: date
                sections.add(HistorySection(date = finishedDate, records = currentBucket.toList()))
                currentDate = date
                currentBucket = mutableListOf()
            }
            currentBucket.add(record)
        }
        val lastDate = currentDate
        if (lastDate != null) {
            sections.add(HistorySection(date = lastDate, records = currentBucket.toList()))
        }

        val chartData = mutableListOf<HistoryChartData>()
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            chartData.add(HistoryChartData(label = dayLabel(date), count = counts[date] ?: 0))
        }

        val insights7 = buildInsights(records = orderedRecords, windowDays = 7, zoneId = zoneId)
        val insights30 = buildInsights(records = orderedRecords, windowDays = 30, zoneId = zoneId)
        val trend7 = buildTrend(records = orderedRecords, windowDays = 7, zoneId = zoneId)
        val trend30 = buildTrend(records = orderedRecords, windowDays = 30, zoneId = zoneId)
        return HistoryUiState.Success(
            chartData = chartData,
            sections = sections,
            insights7 = insights7,
            insights30 = insights30,
            trend7 = trend7,
            trend30 = trend30
        )
    }

    private fun buildInsights(
        records: List<IntakeRecord>,
        windowDays: Long,
        zoneId: ZoneId
    ): InsightsSummary? {
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(windowDays - 1)
        val window = records.filter {
            val day = java.time.Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate()
            !day.isBefore(start) && !day.isAfter(today)
        }
        if (window.isEmpty()) return null
        val taken = window.count { it.status == "Taken" }
        val skipped = window.count { it.status == "Skipped" }
        val late = window.count { isLateTaken(it) }
        val denom = (taken + skipped).coerceAtLeast(1)
        val completion = taken.toFloat() / denom.toFloat()
        return InsightsSummary(
            windowDays = windowDays.toInt(),
            completionRate = completion,
            takenCount = taken,
            skippedCount = skipped,
            lateCount = late,
            topSkipped = topListBySupplement(window, "Skipped", limit = 3),
            topLate = topLateBySupplement(window, limit = 3),
            topLateHour = topLateHour(window, zoneId)
        )
    }

    private fun buildTrend(
        records: List<IntakeRecord>,
        windowDays: Long,
        zoneId: ZoneId
    ): List<InsightsTrendPoint> {
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(windowDays - 1)
        val counts = mutableMapOf<LocalDate, Pair<Int, Int>>()
        for (record in records) {
            val day = java.time.Instant.ofEpochMilli(record.date).atZone(zoneId).toLocalDate()
            if (day.isAfter(today)) continue
            if (day.isBefore(start)) break
            val current = counts[day] ?: (0 to 0)
            if (record.status == "Taken") {
                counts[day] = (current.first + 1) to current.second
            } else if (record.status == "Skipped") {
                counts[day] = current.first to (current.second + 1)
            }
        }
        val points = mutableListOf<InsightsTrendPoint>()
        for (i in (windowDays - 1) downTo 0) {
            val day = today.minusDays(i)
            val value = counts[day] ?: (0 to 0)
            points.add(InsightsTrendPoint(date = day, takenCount = value.first, skippedCount = value.second))
        }
        return points
    }

    private fun isLateTaken(record: IntakeRecord): Boolean {
        if (record.status != "Taken") return false
        if (record.updatedAtEpochMs <= 0L) return false
        val threshold = record.date + 20 * 60 * 1000
        return record.updatedAtEpochMs > threshold
    }

    private fun topListBySupplement(records: List<IntakeRecord>, status: String, limit: Int): List<InsightsItem> {
        if (limit <= 0) return emptyList()
        val grouped = records.filter { it.status == status }
            .groupBy { it.supplementName ?: "N/A" }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
        return grouped.map { InsightsItem(title = it.key, count = it.value) }
    }

    private fun topLateHour(records: List<IntakeRecord>, zoneId: ZoneId): InsightsItem? {
        val late = records.filter { isLateTaken(it) }
        if (late.isEmpty()) return null
        val grouped = late.groupBy {
            val hour = java.time.Instant.ofEpochMilli(it.updatedAtEpochMs).atZone(zoneId).hour
            "%02d:00".format(hour)
        }.mapValues { it.value.size }
        val best = grouped.maxByOrNull { it.value } ?: return null
        return InsightsItem(title = best.key, count = best.value)
    }

    private fun topLateBySupplement(records: List<IntakeRecord>, limit: Int): List<InsightsItem> {
        if (limit <= 0) return emptyList()
        val late = records.filter { isLateTaken(it) }
        if (late.isEmpty()) return emptyList()
        val grouped = late.groupBy { it.supplementName ?: "N/A" }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
        return grouped.map { InsightsItem(title = it.key, count = it.value) }
    }

    private fun dayLabel(date: LocalDate): String {
        val locale = Locale.getDefault()
        return date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }
}
