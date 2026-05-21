package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.repository.IntakeRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
        } else {
            Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.history_title)) }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val state = uiState) {
                    is HistoryUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is HistoryUiState.Success -> HistoryContent(state)
                    is HistoryUiState.NoClient -> {
                        Text(
                            text = stringResource(R.string.add_client_to_start),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryContent(state: HistoryUiState.Success) {
    val shape = RoundedCornerShape(28.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val listState = rememberLazyListState()
    var searchText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val filteredSections = remember(state.sections, searchText, filter) {
        val query = searchText.trim().lowercase(Locale.ROOT)
        state.sections.mapNotNull { section ->
            val records = section.records.filter { record ->
                if (filter == HistoryFilter.TAKEN && record.status != "Taken") return@filter false
                if (filter == HistoryFilter.SKIPPED && record.status != "Skipped") return@filter false
                if (query.isEmpty()) return@filter true
                val name = record.supplementName?.lowercase(Locale.ROOT).orEmpty()
                name.contains(query)
            }
            if (records.isEmpty()) return@mapNotNull null
            HistorySection(date = section.date, records = records)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(
            key = "chart",
            contentType = "chart"
        ) {
            Text(stringResource(R.string.intake_frequency_last_7), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                PremiumBarChart(data = state.chartData)
            }
        }

        item(
            key = "insights",
            contentType = "insights"
        ) {
            InsightsPanel(insights7 = state.insights7, insights30 = state.insights30)
        }
        
        item(
            key = "details_title",
            contentType = "title"
        ) {
            Text(stringResource(R.string.log_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        
        item(
            key = "filters",
            contentType = "filters"
        ) {
            HistoryFilterBar(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                filter = filter,
                onFilterChange = { filter = it }
            )
        }

        if (filteredSections.isEmpty()) {
            item(
                key = "empty",
                contentType = "empty"
            ) {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = base),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        Text(stringResource(R.string.no_logs_yet), color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            filteredSections.forEach { section ->
                val date = section.date
                stickyHeader(
                    key = "header_${date}",
                    contentType = "header"
                ) {
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = historySectionTitle(date),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                
                items(
                    items = section.records,
                    key = { it.id },
                    contentType = { "record" }
                ) { record ->
                    HistoryRecordItem(record)
                }
            }
        }
    }
}

@Composable
private fun InsightsPanel(insights7: InsightsSummary?, insights30: InsightsSummary?) {
    val shape = RoundedCornerShape(28.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightsWindowCard(title = stringResource(R.string.insights_last_7), summary = insights7, modifier = Modifier.weight(1f))
            InsightsWindowCard(title = stringResource(R.string.insights_last_30), summary = insights30, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun InsightsWindowCard(title: String, summary: InsightsSummary?, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
    Column(
        modifier = modifier
            .background(base, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (summary == null) {
            Text(stringResource(R.string.insights_no_data), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            return
        }
        val completion = (summary.completionRate * 100f).toInt()
        Text(stringResource(R.string.insights_completion_format, completion), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(
            stringResource(R.string.insights_taken_skipped_format, summary.takenCount, summary.skippedCount),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(stringResource(R.string.insights_late_format, summary.lateCount), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        val hour = summary.topLateHour
        if (hour != null) {
            Text(stringResource(R.string.insights_top_late_hour_format, hour.title, hour.count), style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val topLate = summary.topLate
        if (topLate.isNotEmpty()) {
            val first = topLate.first()
            Text(stringResource(R.string.insights_top_late_format, first.title, first.count), style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            topLate.drop(1).forEach { item ->
                Text(stringResource(R.string.insights_item_bullet_format, item.title, item.count), style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val skipped = summary.topSkipped
        if (skipped.isNotEmpty()) {
            val first = skipped.first()
            Text(stringResource(R.string.insights_top_skipped_format, first.title, first.count), style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            skipped.drop(1).forEach { item ->
                Text(stringResource(R.string.insights_item_bullet_format, item.title, item.count), style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private enum class HistoryFilter {
    ALL,
    TAKEN,
    SKIPPED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(base, shape)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = filter == HistoryFilter.ALL,
                onClick = { onFilterChange(HistoryFilter.ALL) },
                label = { Text(stringResource(R.string.history_filter_all), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
            FilterChip(
                selected = filter == HistoryFilter.TAKEN,
                onClick = { onFilterChange(HistoryFilter.TAKEN) },
                label = { Text(stringResource(R.string.notif_action_taken), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
            FilterChip(
                selected = filter == HistoryFilter.SKIPPED,
                onClick = { onFilterChange(HistoryFilter.SKIPPED) },
                label = { Text(stringResource(R.string.notif_action_skip), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun PremiumBarChart(data: List<HistoryChartData>) {
    val axis = remember(data) {
        val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(0) ?: 0
        val step = chooseStep(maxCount)
        val maxAxis = if (maxCount == 0) step * 4 else ceil(maxCount.toFloat() / step).toInt() * step
        val yLabels = (0..(maxAxis / step)).map { it * step }
        ChartAxis(maxCount = maxCount, maxAxis = maxAxis, yLabels = yLabels)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gridColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.22f)
    val axisTextColor = if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF374151)
    val barColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF2196F3)
    val axisWidth = 40.dp
    val chartHeight = 180.dp

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(
                modifier = Modifier.width(axisWidth).fillMaxHeight(),
                horizontalAlignment = Alignment.End
            ) {
                axis.yLabels.reversed().forEach { value ->
                    Text(
                        text = value.toString(),
                        fontSize = 11.sp,
                        color = axisTextColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                val chartHeight = size.height
                val chartWidth = size.width

                val lineCount = axis.yLabels.size
                for (i in 0 until lineCount) {
                    val y = chartHeight * (i.toFloat() / (lineCount - 1).coerceAtLeast(1))
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (data.isEmpty()) return@Canvas

                val spacing = 10.dp.toPx()
                val barWidth = ((chartWidth - spacing * (data.size + 1)) / data.size).coerceAtLeast(8.dp.toPx())
                val radius = 8.dp.toPx()

                data.forEachIndexed { index, item ->
                    val ratio = if (axis.maxAxis == 0) 0f else item.count.toFloat() / axis.maxAxis.toFloat()
                    val barHeight = ratio * (chartHeight - 6.dp.toPx())
                    val left = spacing + index * (barWidth + spacing)
                    val top = chartHeight - barHeight
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = axisWidth + 10.dp)
        ) {
            data.forEach { item ->
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    color = axisTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class ChartAxis(
    val maxCount: Int,
    val maxAxis: Int,
    val yLabels: List<Int>
)

private fun chooseStep(maxCount: Int): Int {
    return when {
        maxCount <= 4 -> 1
        maxCount <= 8 -> 2
        maxCount <= 20 -> 5
        else -> 10
    }
}

@Composable
private fun HistoryRecordItem(record: IntakeRecord) {
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }
    val displayTime = remember(record.date, record.intakeTime) {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.date), zoneId)
        val timeText = dateTime.format(timeFormatter)
        val intake = record.intakeTime?.takeIf { it.isNotBlank() }
        if (timeText == "00:00" && intake != null) intake else timeText
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
    val isSkipped = record.status == "Skipped"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = base),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.width(56.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.supplementName ?: stringResource(R.string.history_not_available),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Icon(
                imageVector = if (isSkipped) Icons.Default.Cancel else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isSkipped) Color(0xFFFF9800) else Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
private fun historySectionTitle(date: LocalDate): String {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    
    if (date == today) return stringResource(R.string.history_today)
    if (date == today.minusDays(1)) return stringResource(R.string.history_yesterday)
    
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM, yyyy", locale) }
    return date.format(formatter)
}
