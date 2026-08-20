package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.presentation.designsystem.OakColors
import com.example.supplementtracker.presentation.designsystem.oakBackgroundBrush
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.supplementtracker.domain.model.IntakeStatus
import com.example.supplementtracker.domain.model.IntakeRecord
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakFeedbackCard
import com.example.supplementtracker.presentation.designsystem.OakRadius
import com.example.supplementtracker.presentation.designsystem.OakSpacing
import com.example.supplementtracker.presentation.designsystem.OakTypeScale
import com.example.supplementtracker.presentation.designsystem.OakTypography
import com.example.supplementtracker.presentation.designsystem.oakTouchTarget
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
    onNavigateToSettings: () -> Unit,
    onNavigateToPlanAccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
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
                    is HistoryUiState.Success -> HistoryContent(state, onNavigateToPlanAccess)
                    is HistoryUiState.Error -> HistoryLoadError(viewModel::retryHistory)
                    is HistoryUiState.NoClient -> {
                        OakFeedbackCard(
                            title = stringResource(R.string.client_management),
                            body = stringResource(R.string.add_client_to_start),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            actionLabel = stringResource(R.string.settings_title),
                            onAction = onNavigateToSettings
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryContent(
    state: HistoryUiState.Success,
    onNavigateToPlanAccess: () -> Unit
) {
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val listState = rememberLazyListState()
    val zoneId = remember { ZoneId.systemDefault() }
    var searchText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val filteredSections = remember(state.sections, searchText, filter) {
        val query = searchText.trim().lowercase(Locale.ROOT)
        state.sections.mapNotNull { section ->
            val records = section.records.filter { record ->
                val status = IntakeStatus.fromStorage(record.status)
                if (filter == HistoryFilter.TAKEN && status != IntakeStatus.TAKEN) return@filter false
                if (filter == HistoryFilter.SKIPPED && status != IntakeStatus.SKIPPED) return@filter false
                if (query.isEmpty()) return@filter true
                val name = record.supplementName?.lowercase(Locale.ROOT).orEmpty()
                name.contains(query)
            }
            if (records.isEmpty()) return@mapNotNull null
            HistorySection(date = section.date, records = records)
        }
    }

    val hasAnyRecords = state.sections.any { it.records.isNotEmpty() }
    val visibleRecordCount = filteredSections.sumOf { it.records.size }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = OakSpacing.Lg,
            top = OakSpacing.Sm,
            end = OakSpacing.Lg,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(OakSpacing.Section)
    ) {
        item(
            key = "insights_trend",
            contentType = "insights_trend"
        ) {
            if (state.analyticsAvailable) {
                InsightsTrendCard(
                    trend7 = state.trend7,
                    trend30 = state.trend30,
                    insights7 = state.insights7,
                    insights30 = state.insights30
                )
            } else {
                OutlinedButton(
                    onClick = onNavigateToPlanAccess,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.plan_unlock_analytics))
                }
            }
        }

        item(
            key = "chart",
            contentType = "chart"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Md)) {
                Text(
                    stringResource(R.string.intake_frequency_last_7),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = OakTypeScale.SectionTitle),
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
                PremiumBarChart(data = state.chartData)
            }
        }
        
        item(
            key = "details_title",
            contentType = "title"
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.log_details),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = OakTypeScale.SectionTitle),
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.history_results_count_format, visibleRecordCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(OakRadius.Lg),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(OakSpacing.Xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OakSpacing.Sm)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.a11y_search), tint = muted)
                        Text(
                            stringResource(if (hasAnyRecords) R.string.history_no_matches_title else R.string.history_empty_title),
                            color = muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(if (hasAnyRecords) R.string.history_no_matches_body else R.string.history_empty_body),
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
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = OakSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = historySectionTitle(date),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = section.records.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
private fun HistoryLoadError(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        OakFeedbackCard(
            title = stringResource(R.string.history_load_failed_title),
            body = stringResource(R.string.history_load_failed_body),
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry
        )
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
    val summary = if (window == 7) insights7 else insights30
    val trend = if (window == 7) trend7 else trend30
    val completion = ((summary?.completionRate ?: 0f) * 100f).toInt()
    val total = (summary?.takenCount ?: 0) + (summary?.skippedCount ?: 0)
    val late = summary?.lateCount ?: 0
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    OakCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Lg),
        contentPadding = PaddingValues(OakSpacing.Xl)
    ) {
        HistoryInsightsHeader(summary != null) { isDetailsVisible = true }
        Spacer(modifier = Modifier.height(OakSpacing.Md))
        SegmentedDaysPicker(window, { window = it }, Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(OakSpacing.Xl))
        HistoryCompletionOverview(completion, total, late)
        Spacer(modifier = Modifier.height(OakSpacing.Lg))
        Text(
            text = stringResource(R.string.history_signal_window_format, window, completion, late),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(OakSpacing.Md))
        TrendLineChart(
            points = trend,
            takenColor = MaterialTheme.colorScheme.primary,
            skippedColor = if (isDark) OakColors.SkippedDark else OakColors.Skipped,
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        if (summary == null) {
            Text(
                text = stringResource(R.string.insights_no_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (isDetailsVisible && summary != null) {
        InsightsDetailsDialog(summary = summary, onDismiss = { isDetailsVisible = false })
    }
}

@Composable
private fun HistoryInsightsHeader(enabled: Boolean, onOpenDetails: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.insights_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = OakTypography.Display,
                fontSize = OakTypeScale.SectionTitle
            ),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenDetails, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.a11y_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
    }
}

@Composable
private fun HistoryCompletionOverview(completion: Int, total: Int, late: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OakSpacing.Xl)
    ) {
        HistoryCompletionRing(completion)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OakSpacing.Lg)) {
            HistoryMetric(stringResource(R.string.history_metric_recorded), NumberFormat.getInstance().format(total))
            HistoryMetric(stringResource(R.string.history_metric_late), NumberFormat.getInstance().format(late))
        }
    }
}

@Composable
private fun HistoryCompletionRing(completion: Int) {
    val progress = completion.coerceIn(0, 100) / 100f
    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 9.dp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$completion%",
                fontFamily = OakTypography.Display,
                fontSize = OakTypeScale.HeroNumber,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.history_completion_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = OakTypography.Display,
            fontSize = OakTypeScale.Metric,
            fontWeight = FontWeight.SemiBold
        )
    }
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(OakRadius.Md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(OakSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(OakSpacing.Xs)
    ) {
        SegmentedDaysPill(
            label = stringResource(R.string.insights_last_7),
            selected = selected == 7,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(7) }
        )
        SegmentedDaysPill(
            label = stringResource(R.string.insights_last_30),
            selected = selected == 30,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(30) }
        )
    }
}

@Composable
private fun SegmentedDaysPill(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(OakRadius.Sm))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .oakTouchTarget(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = OakSpacing.Md, vertical = OakSpacing.Sm)
        )
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
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Md)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.a11y_search)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedLeadingIconColor = secondaryTextColor,
                unfocusedLeadingIconColor = secondaryTextColor
            ),
            shape = RoundedCornerShape(OakRadius.Md),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OakRadius.Md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(OakSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(OakSpacing.Xs)
        ) {
            HistoryFilterSegment(stringResource(R.string.history_filter_all), filter == HistoryFilter.ALL, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                onFilterChange(HistoryFilter.ALL)
            }
            HistoryFilterSegment(stringResource(R.string.notif_action_taken), filter == HistoryFilter.TAKEN, if (isDark) OakColors.TakenDark else OakColors.Taken, Modifier.weight(1f)) {
                onFilterChange(HistoryFilter.TAKEN)
            }
            HistoryFilterSegment(stringResource(R.string.notif_action_skip), filter == HistoryFilter.SKIPPED, if (isDark) OakColors.SkippedDark else OakColors.Skipped, Modifier.weight(1f)) {
                onFilterChange(HistoryFilter.SKIPPED)
            }
        }
    }
}

@Composable
private fun HistoryFilterSegment(
    label: String,
    selected: Boolean,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(OakRadius.Sm))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .oakTouchTarget(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = OakSpacing.Sm, vertical = OakSpacing.Sm)
        )
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

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val axisWidth = 36.dp
    val chartHeight = 168.dp

    Column(modifier = Modifier.fillMaxWidth()) {
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
    val isSkipped = IntakeStatus.fromStorage(record.status) == IntakeStatus.SKIPPED

    val accent = if (isSkipped) {
        if (isDark) OakColors.SkippedDark else OakColors.Skipped
    } else {
        if (isDark) OakColors.TakenDark else OakColors.Taken
    }
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = OakSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(accent, RoundedCornerShape(OakRadius.Pill)))
            Spacer(modifier = Modifier.width(OakSpacing.Md))
            Text(
                text = displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.width(52.dp)
            )
            Text(
                text = record.supplementName ?: stringResource(R.string.history_not_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isSkipped) Icons.Default.Cancel else Icons.Default.CheckCircle,
                contentDescription = stringResource(if (isSkipped) R.string.dose_status_skipped else R.string.home_confirm_intake_action),
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
