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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
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
                    is HistoryUiState.NoClient -> Text(stringResource(R.string.add_client_to_start), modifier = Modifier.align(Alignment.Center))
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.intake_frequency_last_7), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                PremiumBarChart(data = state.chartData)
            }
        }
        
        item {
            Text(stringResource(R.string.log_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (state.records.isEmpty()) {
            item { Text(stringResource(R.string.no_logs_yet), color = Color.Gray) }
        } else {
            val grouped = state.records
                .groupBy { record ->
                    LocalDateTime
                        .ofInstant(Instant.ofEpochMilli(record.date), ZoneId.systemDefault())
                        .toLocalDate()
                }
                .toSortedMap(compareByDescending { it })
            
            grouped.forEach { (date, records) ->
                stickyHeader {
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
                
                val sorted = records.sortedByDescending { it.date }
                items(items = sorted, key = { it.id }) { record ->
                    HistoryRecordItem(record)
                }
            }
        }
    }
}

@Composable
private fun PremiumBarChart(data: List<HistoryChartData>) {
    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(0) ?: 0
    val step = chooseStep(maxCount)
    val maxAxis = if (maxCount == 0) step * 4 else ceil(maxCount.toFloat() / step).toInt() * step
    val yLabels = (0..(maxAxis / step)).map { it * step }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gridColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.22f)
    val axisTextColor = if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF374151)
    val barColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF2196F3)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(32.dp),
                horizontalAlignment = Alignment.End
            ) {
                yLabels.reversed().forEach { value ->
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
                    .height(180.dp)
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                val chartHeight = size.height
                val chartWidth = size.width

                val lineCount = yLabels.size
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
                    val ratio = if (maxAxis == 0) 0f else item.count.toFloat() / maxAxis.toFloat()
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
                .padding(start = 32.dp + 10.dp)
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
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.date), ZoneId.systemDefault())
    val timeText = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val displayTime = if (timeText == "00:00" && !record.intakeTime.isNullOrBlank()) record.intakeTime else timeText

    val shape = RoundedCornerShape(28.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.width(56.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(record.supplementName ?: "N/A", style = MaterialTheme.typography.bodyLarge)
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32)
            )
        }
    }
}

private fun historySectionTitle(date: LocalDate): String {
    val locale = Locale.getDefault()
    val isVietnamese = locale.language == "vi"
    val today = LocalDate.now()
    
    if (date == today) return if (isVietnamese) "Hôm nay" else "Today"
    if (date == today.minusDays(1)) return if (isVietnamese) "Hôm qua" else "Yesterday"
    
    val formatter = DateTimeFormatter.ofPattern("d MMMM, yyyy", locale)
    return date.format(formatter)
}
