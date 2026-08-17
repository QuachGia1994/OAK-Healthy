package com.example.supplementtracker.presentation.coach

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.CoachOverviewUiState
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.service.CoachCheckInStore
import com.example.supplementtracker.service.CoachClientDetail
import com.example.supplementtracker.service.CoachClientSummary
import com.example.supplementtracker.service.CoachOverviewSummary
import com.example.supplementtracker.service.CoachRoutineFeeling
import com.example.supplementtracker.service.CoachTrendPoint
import com.example.supplementtracker.service.CoachWorkspaceBuilder
import com.example.supplementtracker.service.OakPrefs
import java.text.DateFormat
import java.util.Date

private enum class CoachSort { ATTENTION, NAME, COMPLETION }
private enum class CoachFilter { ALL, CHECK_IN, ACTIVE, INACTIVE }

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
            detailProvider = viewModel::coachClientDetail,
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
    detailProvider: (java.util.UUID) -> CoachClientDetail?,
    onOpenPlans: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        CoachOverviewUiState.Idle, CoachOverviewUiState.Loading -> LoadingState(modifier)
        CoachOverviewUiState.Locked -> LockedState(onOpenPlans, modifier)
        CoachOverviewUiState.Error -> ErrorState(onRetry, modifier)
        is CoachOverviewUiState.Ready -> ReadyState(
            state.summary,
            windowDays,
            onWindowSelected,
            detailProvider,
            modifier
        )
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
    detailProvider: (java.util.UUID) -> CoachClientDetail?,
    modifier: Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(CoachSort.ATTENTION) }
    var filter by rememberSaveable { mutableStateOf(CoachFilter.ALL) }
    var selectedClientId by rememberSaveable { mutableStateOf<String?>(null) }
    val clients = remember(summary.clients, query, sort, filter) {
        filteredClients(summary.clients, query, sort, filter)
    }
    val detail = selectedClientId?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }?.let(detailProvider)
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WindowSelector(windowDays, onWindowSelected) }
        item { CoachSummaryCard(summary) }
        item { CoachTrendCard(summary.trend) }
        if (detail != null) item { CoachClientDetailCard(detail) { selectedClientId = null } }
        item { ClientReportControls(query, sort, filter, { query = it }, { sort = it }, { filter = it }) }
        if (clients.isEmpty()) item { EmptyCoachCard() }
        items(clients, key = { it.clientId }) { client ->
            CoachClientCard(client) { selectedClientId = client.clientId.toString() }
        }
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
    filter: CoachFilter,
    onQueryChanged: (String) -> Unit,
    onSortChanged: (CoachSort) -> Unit,
    onFilterChanged: (CoachFilter) -> Unit
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == CoachFilter.ALL, onClick = { onFilterChanged(CoachFilter.ALL) }, label = { Text(stringResource(R.string.coach_filter_all)) })
            FilterChip(selected = filter == CoachFilter.CHECK_IN, onClick = { onFilterChanged(CoachFilter.CHECK_IN) }, label = { Text(stringResource(R.string.coach_filter_check_in)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == CoachFilter.ACTIVE, onClick = { onFilterChanged(CoachFilter.ACTIVE) }, label = { Text(stringResource(R.string.coach_filter_active)) })
            FilterChip(selected = filter == CoachFilter.INACTIVE, onClick = { onFilterChanged(CoachFilter.INACTIVE) }, label = { Text(stringResource(R.string.coach_filter_inactive)) })
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
    sort: CoachSort,
    filter: CoachFilter
): List<CoachClientSummary> {
    val searched = clients.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val filtered = searched.filter { client -> matchesCoachFilter(client, filter) }
    return when (sort) {
        CoachSort.ATTENTION -> filtered.sortedWith(compareByDescending<CoachClientSummary> { it.needsCheckIn }.thenBy { it.name })
        CoachSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        CoachSort.COMPLETION -> filtered.sortedByDescending { it.completionPercent ?: -1 }
    }
}

private fun matchesCoachFilter(client: CoachClientSummary, filter: CoachFilter): Boolean = when (filter) {
    CoachFilter.ALL -> true
    CoachFilter.CHECK_IN -> client.needsCheckIn
    CoachFilter.ACTIVE -> client.takenCount + client.skippedCount > 0
    CoachFilter.INACTIVE -> client.takenCount + client.skippedCount == 0
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
private fun CoachClientDetailCard(detail: CoachClientDetail, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CoachCheckInStore(OakPrefs.get(context)) }
    var version by rememberSaveable(detail.clientId) { mutableStateOf(0) }
    var note by rememberSaveable(detail.clientId) { mutableStateOf("") }
    var feeling by rememberSaveable(detail.clientId) { mutableStateOf(CoachRoutineFeeling.OKAY) }
    val entries = remember(detail.clientId, version) { store.entries(detail.clientId) }
    val report = remember(detail, entries) {
        CoachWorkspaceBuilder.reportDocument(detail, entries, System.currentTimeMillis())
    }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailHeader(detail, onClose)
            DetailComparison(detail)
            CoachTrendCard(detail.trend)
            CoachCheckInEditor(note, feeling, { note = it }, { feeling = it }) {
                store.add(detail.clientId, feeling, note, System.currentTimeMillis())
                note = ""
                version += 1
            }
            CoachCheckInHistory(entries)
            Text(stringResource(R.string.coach_report_ready_format, report.trend.size, report.checkIns.size), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailHeader(detail: CoachClientDetail, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(detail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.coach_detail_window_format, detail.windowDays), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onClose) { Text(stringResource(R.string.coach_close_detail)) }
    }
}

@Composable
private fun DetailComparison(detail: CoachClientDetail) {
    val current = detail.current.completionPercent?.let { "$it%" } ?: "—"
    val previous = detail.previous.completionPercent?.let { "$it%" } ?: "—"
    val delta = detail.completionDeltaPoints?.let { if (it >= 0) "+$it" else "$it" } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.coach_current_period_format, current, detail.current.activeDays))
        Text(stringResource(R.string.coach_previous_period_format, previous, detail.previous.activeDays))
        Text(stringResource(R.string.coach_delta_format, delta), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoachCheckInEditor(
    note: String,
    feeling: CoachRoutineFeeling,
    onNoteChanged: (String) -> Unit,
    onFeelingChanged: (CoachRoutineFeeling) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.coach_check_in_title), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FeelingChip(CoachRoutineFeeling.COMFORTABLE, feeling, R.string.coach_feeling_comfortable, onFeelingChanged)
            FeelingChip(CoachRoutineFeeling.OKAY, feeling, R.string.coach_feeling_okay, onFeelingChanged)
            FeelingChip(CoachRoutineFeeling.DIFFICULT, feeling, R.string.coach_feeling_difficult, onFeelingChanged)
        }
        OutlinedTextField(value = note, onValueChange = { onNoteChanged(it.take(500)) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.coach_note_hint)) })
        Button(onClick = onSave) { Text(stringResource(R.string.coach_save_check_in)) }
    }
}

@Composable
private fun FeelingChip(
    value: CoachRoutineFeeling,
    selected: CoachRoutineFeeling,
    labelRes: Int,
    onSelected: (CoachRoutineFeeling) -> Unit
) {
    FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(stringResource(labelRes)) })
}

@Composable
private fun CoachCheckInHistory(entries: List<com.example.supplementtracker.service.CoachCheckInEntry>) {
    if (entries.isEmpty()) {
        Text(stringResource(R.string.coach_check_in_empty), style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.take(3).forEach { entry ->
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.epochMs))
            Text("$date • ${feelingText(entry.feeling)} • ${entry.note}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun feelingText(feeling: CoachRoutineFeeling): String = when (feeling) {
    CoachRoutineFeeling.COMFORTABLE -> stringResource(R.string.coach_feeling_comfortable)
    CoachRoutineFeeling.OKAY -> stringResource(R.string.coach_feeling_okay)
    CoachRoutineFeeling.DIFFICULT -> stringResource(R.string.coach_feeling_difficult)
}

@Composable
private fun CoachClientCard(client: CoachClientSummary, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clickable(onClick = onOpen).semantics(mergeDescendants = true) {}
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(client.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (client.needsCheckIn) Text(stringResource(R.string.coach_check_in_badge))
            }
            Text(completionText(client), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(activityText(client), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.coach_open_detail), style = MaterialTheme.typography.labelMedium)
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
