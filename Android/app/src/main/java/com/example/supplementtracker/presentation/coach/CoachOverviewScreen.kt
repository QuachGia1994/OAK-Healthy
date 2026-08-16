package com.example.supplementtracker.presentation.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.CoachOverviewUiState
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.service.CoachClientSummary
import com.example.supplementtracker.service.CoachOverviewSummary
import com.example.supplementtracker.service.CoachTrendPoint
import java.text.DateFormat
import java.util.Date

private enum class CoachSort { ATTENTION, NAME, COMPLETION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachOverviewScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenPlans: () -> Unit
) {
    val state by viewModel.coachOverview.collectAsStateWithLifecycle()
    val windowDays by viewModel.coachWindowDays.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCoachOverview() }
    Scaffold(topBar = { CoachTopBar(onBack) }) { padding ->
        CoachOverviewContent(
            state = state,
            windowDays = windowDays,
            onWindowSelected = viewModel::selectCoachWindow,
            onOpenPlans = onOpenPlans,
            onRetry = viewModel::refreshCoachOverview,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.coach_overview_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_navigate_back))
            }
        }
    )
}

@Composable
private fun CoachOverviewContent(
    state: CoachOverviewUiState,
    windowDays: Int,
    onWindowSelected: (Int) -> Unit,
    onOpenPlans: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        CoachOverviewUiState.Idle, CoachOverviewUiState.Loading -> LoadingState(modifier)
        CoachOverviewUiState.Locked -> LockedState(onOpenPlans, modifier)
        CoachOverviewUiState.Error -> ErrorState(onRetry, modifier)
        is CoachOverviewUiState.Ready -> ReadyState(state.summary, windowDays, onWindowSelected, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
    }
}

@Composable
private fun LockedState(onOpenPlans: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.coach_overview_locked_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.coach_overview_locked_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpenPlans) { Text(stringResource(R.string.plan_access_manage)) }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.coach_overview_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text(stringResource(R.string.coach_retry)) }
    }
}

@Composable
private fun ReadyState(
    summary: CoachOverviewSummary,
    windowDays: Int,
    onWindowSelected: (Int) -> Unit,
    modifier: Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(CoachSort.ATTENTION) }
    val clients = remember(summary.clients, query, sort) { filteredClients(summary.clients, query, sort) }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WindowSelector(windowDays, onWindowSelected) }
        item { CoachSummaryCard(summary) }
        item { CoachTrendCard(summary.trend) }
        item { ClientReportControls(query, sort, { query = it }, { sort = it }) }
        if (clients.isEmpty()) item { EmptyCoachCard() }
        items(clients, key = { it.clientId }) { client -> CoachClientCard(client) }
    }
}

@Composable
private fun WindowSelector(selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(7, 30, 90).forEach { days ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelected(days) },
                label = { Text(stringResource(R.string.coach_window_days_format, days)) }
            )
        }
    }
}

@Composable
private fun CoachSummaryCard(summary: CoachOverviewSummary) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.coach_report_window_format, summary.windowDays), fontWeight = FontWeight.SemiBold)
            CoachSummaryMetrics(summary)
            Text(overallCompletionText(summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoachSummaryMetrics(summary: CoachOverviewSummary) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric(stringResource(R.string.coach_metric_clients), summary.totalClients.toString())
            SummaryMetric(stringResource(R.string.coach_metric_active), summary.activeClients.toString())
            SummaryMetric(stringResource(R.string.coach_metric_check_in), summary.needsCheckInCount.toString())
        }
        return
    }
    Row(Modifier.fillMaxWidth()) {
        SummaryMetric(stringResource(R.string.coach_metric_clients), summary.totalClients.toString(), Modifier.weight(1f))
        SummaryMetric(stringResource(R.string.coach_metric_active), summary.activeClients.toString(), Modifier.weight(1f))
        SummaryMetric(stringResource(R.string.coach_metric_check_in), summary.needsCheckInCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun overallCompletionText(summary: CoachOverviewSummary): String {
    val completion = summary.overallCompletionPercent ?: return stringResource(R.string.coach_no_recent_records)
    return stringResource(
        R.string.coach_report_total_format,
        completion,
        summary.takenCount,
        summary.skippedCount
    )
}

@Composable
private fun CoachTrendCard(trend: List<CoachTrendPoint>) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.coach_trend_title), fontWeight = FontWeight.SemiBold)
            trend.takeLast(6).forEach { point -> TrendRow(point) }
        }
    }
}

@Composable
private fun TrendRow(point: CoachTrendPoint) {
    val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(point.bucketStartEpochMs))
    val completion = point.completionPercent?.let { "$it%" } ?: "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(date, style = MaterialTheme.typography.bodySmall)
        Text(completion, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ClientReportControls(
    query: String,
    sort: CoachSort,
    onQueryChanged: (String) -> Unit,
    onSortChanged: (CoachSort) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.coach_search_clients)) },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip(CoachSort.ATTENTION, sort, R.string.coach_sort_attention, onSortChanged)
            SortChip(CoachSort.NAME, sort, R.string.coach_sort_name, onSortChanged)
            SortChip(CoachSort.COMPLETION, sort, R.string.coach_sort_completion, onSortChanged)
        }
    }
}

@Composable
private fun SortChip(value: CoachSort, selected: CoachSort, labelRes: Int, onSelected: (CoachSort) -> Unit) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelected(value) },
        label = { Text(stringResource(labelRes)) }
    )
}

private fun filteredClients(
    clients: List<CoachClientSummary>,
    query: String,
    sort: CoachSort
): List<CoachClientSummary> {
    val filtered = clients.filter { it.name.contains(query.trim(), ignoreCase = true) }
    return when (sort) {
        CoachSort.ATTENTION -> filtered.sortedWith(compareByDescending<CoachClientSummary> { it.needsCheckIn }.thenBy { it.name })
        CoachSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        CoachSort.COMPLETION -> filtered.sortedByDescending { it.completionPercent ?: -1 }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyCoachCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.coach_overview_empty), modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun CoachClientCard(client: CoachClientSummary) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(client.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (client.needsCheckIn) Text(stringResource(R.string.coach_check_in_badge))
            }
            Text(completionText(client), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(activityText(client), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun completionText(client: CoachClientSummary): String {
    val completion = client.completionPercent ?: return stringResource(R.string.coach_no_recent_records)
    return stringResource(R.string.coach_completion_format, completion, client.takenCount, client.skippedCount)
}

@Composable
private fun activityText(client: CoachClientSummary): String {
    val epoch = client.lastActivityEpochMs ?: return stringResource(R.string.coach_last_activity_none)
    return stringResource(R.string.coach_last_activity_format, DateFormat.getDateInstance().format(Date(epoch)))
}
