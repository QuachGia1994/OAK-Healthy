package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.presentation.designsystem.OakColors
import com.example.supplementtracker.presentation.designsystem.OakFeedbackCard
import com.example.supplementtracker.presentation.designsystem.OakRadius
import com.example.supplementtracker.presentation.designsystem.OakSpacing
import com.example.supplementtracker.presentation.designsystem.OakTypeScale
import com.example.supplementtracker.presentation.designsystem.OakTypography
import com.example.supplementtracker.presentation.designsystem.oakBackgroundBrush
import com.example.supplementtracker.presentation.designsystem.oakTouchTarget
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStackListScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    onNavigateToAdd: () -> Unit,
    onNavigateToSyncCenter: () -> Unit,
    onNavigateToUserGuide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val supplements by homeViewModel.allClientSupplements.collectAsStateWithLifecycle()
    val currentDay by homeViewModel.currentDay.collectAsStateWithLifecycle()
    val backgroundBrush = oakBackgroundBrush()
    var searchText by rememberSaveable { mutableStateOf("") }
    val calculateCycleUseCase = remember { CalculateCycleUseCase() }
    val stackItems = remember(supplements, currentDay) {
        buildStackItems(supplements, currentDay, calculateCycleUseCase)
    }
    val filteredItems = remember(stackItems, searchText) { filterStackItems(stackItems, searchText) }
    val restingCount = remember(stackItems) { stackItems.count { it.cycleStatus == CycleStatus.OFF } }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = { StackTopBar(onOpenSettings) },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, stringResource(R.string.a11y_add_supplement))
                }
            }
        ) { padding ->
            StackContent(
                items = filteredItems,
                totalCount = supplements.size,
                restingCount = restingCount,
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onNavigateToSyncCenter = onNavigateToSyncCenter,
                onNavigateToUserGuide = onNavigateToUserGuide,
                onNavigateToAdd = onNavigateToAdd,
                modifier = Modifier.padding(padding),
                listState = listState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StackTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = { Text(stringResource(R.string.my_list_title)) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.a11y_settings))
            }
        }
    )
}

@Composable
private fun StackContent(
    items: List<StackSupplementItem>,
    totalCount: Int,
    restingCount: Int,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onNavigateToSyncCenter: () -> Unit,
    onNavigateToUserGuide: () -> Unit,
    onNavigateToAdd: () -> Unit,
    modifier: Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = OakSpacing.Lg,
            top = OakSpacing.Sm,
            end = OakSpacing.Lg,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(OakSpacing.Section)
    ) {
        item(key = "overview") {
            StackOverviewSurface(totalCount, (totalCount - restingCount).coerceAtLeast(0), restingCount)
        }
        item(key = "actions") {
            StackActionStrip(onNavigateToSyncCenter, onNavigateToUserGuide)
        }
        item(key = "search") {
            StackSearchField(searchText, onSearchTextChange)
        }
        item(key = "list_title") {
            Text(
                text = stringResource(R.string.manage_stack),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = OakTypeScale.SectionTitle),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (items.isEmpty()) {
            item(key = "empty") { StackEmptyState(onNavigateToAdd) }
        } else {
            items(items, key = { it.supplement.id }) { item ->
                StackSupplementRow(
                    title = item.title,
                    summary = getCycleSummary(item.supplement, item.cycleStatus),
                    isOffCycle = item.cycleStatus == CycleStatus.OFF
                )
            }
        }
    }
}

private data class StackSupplementItem(
    val supplement: UserSupplement,
    val title: String,
    val cycleStatus: CycleStatus
)

private fun buildStackItems(
    supplements: List<UserSupplement>,
    currentDay: java.time.LocalDate,
    calculateCycle: CalculateCycleUseCase
): List<StackSupplementItem> = supplements.map { supplement ->
    val intakeTime = supplement.intakeTime.trim()
    StackSupplementItem(
        supplement = supplement,
        title = if (intakeTime.isEmpty()) supplement.name else "${supplement.name} ($intakeTime)",
        cycleStatus = calculateCycle(supplement.startDate, supplement.cycleConfig, currentDay)
    )
}

private fun filterStackItems(
    items: List<StackSupplementItem>,
    searchText: String
): List<StackSupplementItem> {
    val query = searchText.trim().lowercase(Locale.ROOT)
    if (query.isEmpty()) return items
    return items.filter { it.supplement.name.lowercase(Locale.ROOT).contains(query) }
}

@Composable
private fun StackOverviewSurface(totalCount: Int, activeCount: Int, restingCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(OakSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(OakSpacing.Md)
        ) {
            Text(
                text = stringResource(R.string.my_list_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = totalCount.toString(),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = OakTypography.Display),
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(OakSpacing.Xxl)) {
                StackMetric(stringResource(R.string.cycle_status_on), activeCount)
                StackMetric(stringResource(R.string.cycle_status_off), restingCount)
            }
        }
    }
}

@Composable
private fun StackMetric(title: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Xs)) {
        Text(
            value.toString(),
            fontFamily = OakTypography.Display,
            fontSize = OakTypeScale.Metric,
            fontWeight = FontWeight.SemiBold
        )
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StackActionStrip(onSync: () -> Unit, onGuide: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Md),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StackActionRow(
                title = stringResource(R.string.sync_center_title),
                icon = Icons.Default.Sync,
                modifier = Modifier.weight(1f),
                onClick = onSync
            )
            Box(Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
            StackActionRow(
                title = stringResource(R.string.settings_guide_title),
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                modifier = Modifier.weight(1f),
                onClick = onGuide
            )
        }
    }
}

@Composable
private fun StackActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(onClick = onClick).oakTouchTarget().padding(horizontal = OakSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OakSpacing.Sm)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StackSearchField(searchText: String, onSearchTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = searchText,
        onValueChange = onSearchTextChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.a11y_search)) },
        singleLine = true,
        shape = RoundedCornerShape(OakRadius.Md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun StackSupplementRow(title: String, summary: String, isOffCycle: Boolean) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isOffCycle) OakColors.Neutral else if (isDark) OakColors.TakenDark else OakColors.Taken
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = OakSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(OakRadius.Pill)))
            Spacer(Modifier.width(OakSpacing.Md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OakSpacing.Xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (isOffCycle) Icons.Default.Schedule else Icons.Default.CheckCircle,
                stringResource(if (isOffCycle) R.string.cycle_status_off else R.string.cycle_status_on),
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun StackEmptyState(onAdd: () -> Unit) {
    OakFeedbackCard(
        title = stringResource(R.string.no_supplements_yet),
        body = stringResource(R.string.add_supplement_intro),
        actionLabel = stringResource(R.string.add_supplement_title),
        onAction = onAdd
    )
}
