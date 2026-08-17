package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakColors
import com.example.supplementtracker.presentation.designsystem.OakTypography
import com.example.supplementtracker.presentation.designsystem.oakBackgroundBrush
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
    val filteredItems = remember(stackItems, searchText) {
        filterStackItems(stackItems, searchText)
    }
    val restingCount = remember(stackItems) { stackItems.count { it.cycleStatus == CycleStatus.OFF } }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = { Text(stringResource(R.string.my_list_title)) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, stringResource(R.string.a11y_settings))
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, stringResource(R.string.a11y_add_supplement))
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "overview", contentType = "overview") {
                    StackOverviewCard(
                        totalCount = supplements.size,
                        activeCount = (supplements.size - restingCount).coerceAtLeast(0),
                        restingCount = restingCount
                    )
                }
                item(key = "quick_actions", contentType = "quick_actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StackQuickAction(
                            title = stringResource(R.string.sync_center_title),
                            icon = Icons.Default.Sync,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToSyncCenter
                        )
                        StackQuickAction(
                            title = stringResource(R.string.settings_guide_title),
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToUserGuide
                        )
                    }
                }
                item(key = "search", contentType = "search") {
                    StackSearchField(searchText = searchText, onSearchTextChange = { searchText = it })
                }
                item(key = "list_title", contentType = "title") {
                    Text(
                        text = stringResource(R.string.manage_stack),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (filteredItems.isEmpty()) {
                    item(key = "empty", contentType = "empty") { StackEmptyState() }
                } else {
                    items(filteredItems, key = { it.supplement.id }, contentType = { "supplement" }) { item ->
                        StackSupplementCard(
                            title = item.title,
                            summary = getCycleSummary(item.supplement, item.cycleStatus),
                            isOffCycle = item.cycleStatus == CycleStatus.OFF
                        )
                    }
                }
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
private fun StackOverviewCard(totalCount: Int, activeCount: Int, restingCount: Int) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.my_list_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = totalCount.toString(),
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = OakTypography.Display),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            StackMetric(stringResource(R.string.cycle_status_on), activeCount)
            StackMetric(stringResource(R.string.cycle_status_off), restingCount)
        }
    }
}

@Composable
private fun StackMetric(title: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun StackQuickAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    )
}

@Composable
private fun StackSupplementCard(title: String, summary: String, isOffCycle: Boolean) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (isOffCycle) OakColors.Neutral else if (isDark) OakColors.TakenDark else OakColors.Taken
    OakCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        accent = accent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 46.dp).background(accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                contentDescription = stringResource(if (isOffCycle) R.string.cycle_status_off else R.string.cycle_status_on),
                tint = accent
            )
        }
    }
}

@Composable
private fun StackEmptyState() {
    OakCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.no_supplements_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
