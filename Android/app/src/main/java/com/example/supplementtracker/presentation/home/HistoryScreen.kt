package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.presentation.designsystem.OakColors
import com.example.supplementtracker.presentation.designsystem.oakBackgroundBrush
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakCardVariant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

private val historyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    val backgroundBrush = oakBackgroundBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.history_title), color = primaryTextColor) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.a11y_settings), tint = primaryTextColor)
                        }
                    }
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
                            color = secondaryTextColor
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
    val shape = RoundedCornerShape(20.dp)
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val listState = rememberLazyListState()
    val zoneId = remember { ZoneId.systemDefault() }
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
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(
            key = "insights_trend",
            contentType = "insights_trend"
        ) {
            InsightsTrendCard(
                trend7 = state.trend7,
                trend30 = state.trend30,
                insights7 = state.insights7,
                insights30 = state.insights30
            )
        }

        item(
            key = "chart",
            contentType = "chart"
        ) {
            Text(
                stringResource(R.string.intake_frequency_last_7),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
                OakCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    variant = OakCardVariant.Glass,
                    shape = shape,
                contentPadding = PaddingValues(0.dp),
                elevation = 2.dp
            ) {
                PremiumBarChart(data = state.chartData)
            }
        }
        
        item(
            key = "details_title",
            contentType = "title"
        ) {
            Text(
                stringResource(R.string.log_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
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
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = base),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.a11y_search), tint = muted)
                        Text(
                            stringResource(R.string.no_logs_yet),
                            color = muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.history_search_placeholder),
                            color = muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = historySectionTitle(date),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                
                items(
                    items = section.records,
                    key = { it.id },
                    contentType = { "record" }
                ) { record ->
                    HistoryRecordItem(record, zoneId)
                }
            }
        }
    }
}

@Composable
private fun InsightsTrendCard(
    trend7: List<InsightsTrendPoint>,
    trend30: List<InsightsTrendPoint>,
    insights7: InsightsSummary?,
    insights30: InsightsSummary?
) {
    var window by rememberSaveable { mutableStateOf(30) }
    var isDetailsVisible by rememberSaveable { mutableStateOf(false) }
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val summary = if (window == 7) insights7 else insights30
    val trend = if (window == 7) trend7 else trend30
    val total = (summary?.takenCount ?: 0) + (summary?.skippedCount ?: 0)
    val completion = ((summary?.completionRate ?: 0f) * 100f).toInt()
    val late = summary?.lateCount ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.insights_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(OakColors.InsightCardStart, OakColors.InsightCardEnd)),
                    RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.insights_total_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { isDetailsVisible = true },
                        enabled = summary != null
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.a11y_more_options),
                            tint = Color.White.copy(alpha = if (summary != null) 0.75f else 0.35f)
                        )
                    }
                }
                Text(
                    text = NumberFormat.getInstance().format(total),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InsightsChip(
                        text = stringResource(R.string.insights_completion_chip_format, completion),
                        background = Color.Black.copy(alpha = 0.25f)
                    )
                    InsightsChip(
                        text = stringResource(R.string.insights_late_chip_format, late),
                        background = OakColors.SkippedBg.copy(alpha = 0.35f)
                    )
                }
                TrendLineChart(
                    points = trend,
                    takenColor = Color.White,
                    skippedColor = OakColors.SkippedDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
                SegmentedDaysPicker(
                    selected = window,
                    onSelected = { window = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (summary == null) {
                    Text(
                        text = stringResource(R.string.insights_no_data),
                        color = Color.White.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    if (isDetailsVisible && summary != null) {
        InsightsDetailsDialog(
            summary = summary,
            onDismiss = { isDetailsVisible = false }
        )
    }
}

@Composable
private fun InsightsChip(text: String, background: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .background(background, RoundedCornerShape(99.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun InsightsDetailsDialog(summary: InsightsSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.insights_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InsightsDetailsSection(
                    title = stringResource(R.string.insights_details_top_late_title),
                    items = summary.topLate
                )
                val hour = summary.topLateHour
                if (hour != null) {
                    InsightsDetailsSection(
                        title = stringResource(R.string.insights_details_top_late_hour_title),
                        items = listOf(hour)
                    )
                }
                InsightsDetailsSection(
                    title = stringResource(R.string.insights_details_top_skipped_title),
                    items = summary.topSkipped
                )
            }
        }
    )
}

@Composable
private fun InsightsDetailsSection(title: String, items: List<InsightsItem>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
        if (items.isEmpty()) {
            Text(text = stringResource(R.string.insights_no_data), style = MaterialTheme.typography.bodySmall, color = muted)
            return
        }
        items.forEach { item ->
            Text(
                text = stringResource(R.string.insights_item_bullet_format, item.title, item.count),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SegmentedDaysPicker(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val outer = Color.White.copy(alpha = 0.18f)
    val selectedColor = Color.White.copy(alpha = 0.24f)
    Row(
        modifier = modifier
            .background(outer, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SegmentedDaysPill(
            label = stringResource(R.string.insights_last_7),
            selected = selected == 7,
            selectedColor = selectedColor,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(7) }
        )
        SegmentedDaysPill(
            label = stringResource(R.string.insights_last_30),
            selected = selected == 30,
            selectedColor = selectedColor,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(30) }
        )
    }
}

@Composable
private fun SegmentedDaysPill(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (selected) selectedColor else Color.Transparent, RoundedCornerShape(14.dp))
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrendLineChart(
    points: List<InsightsTrendPoint>,
    takenColor: Color,
    skippedColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val maxValue = points.maxOfOrNull { maxOf(it.takenCount, it.skippedCount) }?.coerceAtLeast(1) ?: 1
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1).toFloat()

        fun y(value: Int): Float = h - (value.toFloat() / maxValue.toFloat()) * h

        val takenPath = Path()
        val skippedPath = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val ty = y(point.takenCount)
            val sy = y(point.skippedCount)
            if (index == 0) {
                takenPath.moveTo(x, ty)
                skippedPath.moveTo(x, sy)
            } else {
                takenPath.lineTo(x, ty)
                skippedPath.lineTo(x, sy)
            }
        }

        drawPath(
            path = takenPath,
            color = takenColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = skippedPath,
            color = skippedColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
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
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            placeholder = { Text(stringResource(R.string.history_search_placeholder), color = secondaryTextColor) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.a11y_search), tint = secondaryTextColor) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryTextColor),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = primaryTextColor,
                unfocusedTextColor = primaryTextColor,
                focusedPlaceholderColor = secondaryTextColor,
                unfocusedPlaceholderColor = secondaryTextColor,
                focusedLeadingIconColor = secondaryTextColor,
                unfocusedLeadingIconColor = secondaryTextColor,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(base, shape)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HistoryFilterChip(
                label = stringResource(R.string.history_filter_all),
                selected = filter == HistoryFilter.ALL,
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onFilterChange(HistoryFilter.ALL) }
            )
            HistoryFilterChip(
                label = stringResource(R.string.notif_action_taken),
                selected = filter == HistoryFilter.TAKEN,
                tint = if (isDark) OakColors.TakenDark else OakColors.Taken,
                onClick = { onFilterChange(HistoryFilter.TAKEN) }
            )
            HistoryFilterChip(
                label = stringResource(R.string.notif_action_skip),
                selected = filter == HistoryFilter.SKIPPED,
                tint = if (isDark) OakColors.SkippedDark else OakColors.Skipped,
                onClick = { onFilterChange(HistoryFilter.SKIPPED) }
            )
        }
    }
}

@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = tint.copy(alpha = 0.16f),
            selectedLabelColor = tint
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = tint.copy(alpha = 0.55f)
        )
    )
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
    val gridColor = remember(isDark) { if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.22f) }
    val axisTextColor = remember(isDark) { if (isDark) Color.White.copy(alpha = 0.75f) else OakColors.TextSecondary }
    val barColor = remember(isDark) { if (isDark) OakColors.ChartBarDark else OakColors.ChartBar }
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
private fun HistoryRecordItem(record: IntakeRecord, zoneId: ZoneId) {
    val displayTime = remember(record.date, record.intakeTime) {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.date), zoneId)
        val timeText = dateTime.format(historyTimeFormatter)
        val intake = record.intakeTime?.takeIf { it.isNotBlank() }
        if (timeText == "00:00" && intake != null) intake else timeText
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val isSkipped = record.status == "Skipped"

    val accent = if (isSkipped) {
        if (isDark) OakColors.SkippedDark else OakColors.Skipped
    } else {
        if (isDark) OakColors.TakenDark else OakColors.Taken
    }
    OakCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        accent = accent,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 38.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.width(56.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.supplementName ?: stringResource(R.string.history_not_available),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (isSkipped) Icons.Default.Cancel else Icons.Default.CheckCircle,
                contentDescription = stringResource(if (isSkipped) R.string.dose_status_skipped else R.string.home_confirm_intake_action),
                tint = accent
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
    val raw = date.format(formatter)
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
