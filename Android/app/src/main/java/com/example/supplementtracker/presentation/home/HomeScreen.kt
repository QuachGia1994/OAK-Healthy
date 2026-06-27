package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.ScrollableDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.domain.model.UserSupplement
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.service.UpdateService
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakCardVariant
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDay by viewModel.currentDay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val updateService = remember(context) { UpdateService(context.applicationContext) }
    val isUpdateAvailable by updateService.isUpdateAvailable.collectAsStateWithLifecycle()
    val updateInfo by updateService.updateInfo.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw) { clientsRaw.distinctBy { it.id } }
    val menuClients = remember(clients) { clients.distinctBy { it.name.trim().lowercase() } }
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val currentClientName = clients.firstOrNull { it.id == currentClientId }?.name
    var isClientMenuExpanded by remember { mutableStateOf(false) }
    var isAddClientDialogVisible by remember { mutableStateOf(false) }
    var newClientName by remember { mutableStateOf("") }
    val dateHeaderFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd MMMM") }

    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        updateService.checkForUpdates(packageInfo.versionName ?: "1.0.0")
    }

    if (isUpdateAvailable) {
        val isForceUpdate = updateInfo?.forceUpdate == true
        val notes = updateInfo?.releaseNotes?.takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = {
                if (!isForceUpdate) {
                    updateService.dismissUpdate()
                }
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(notes ?: stringResource(R.string.update_available_message, updateInfo?.version ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo?.updateUrl))
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = if (!isForceUpdate) {
                {
                    TextButton(onClick = { updateService.skipUpdate(updateInfo?.version.orEmpty()) }) {
                        Text(stringResource(R.string.later))
                    }
                }
            } else {
                null
            }
        )
    }

    OakBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { isClientMenuExpanded = true },
                                            onLongClick = { isClientMenuExpanded = true }
                                        )
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = currentClientName?.let { stringResource(R.string.student_prefix, it) } ?: stringResource(R.string.add_a_client),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        color = primaryTextColor
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = primaryTextColor)
                                }
                                DropdownMenu(
                                    expanded = isClientMenuExpanded,
                                    onDismissRequest = { isClientMenuExpanded = false }
                                ) {
                                    menuClients.forEach { client ->
                                        DropdownMenuItem(
                                            text = { Text(client.name) },
                                            onClick = {
                                                isClientMenuExpanded = false
                                                activeClientManager.setCurrentClientId(client.id)
                                            }
                                        )
                                    }
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.add_a_client)) },
                                        onClick = {
                                            isClientMenuExpanded = false
                                            isAddClientDialogVisible = true
                                        }
                                    )
                                }
                            }
                            Text(
                                text = currentDay.format(dateHeaderFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = primaryTextColor)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_supplement_title))
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is HomeUiState.Success -> HomeContent(
                        state = state,
                        currentDay = currentDay,
                        onToggleIntake = viewModel::toggleIntake,
                        onDelete = viewModel::deleteItem,
                        onEdit = onNavigateToEdit
                    )
                    is HomeUiState.NoClient -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(stringResource(R.string.add_client_to_start), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { isAddClientDialogVisible = true }) {
                                Text(stringResource(R.string.add_a_client))
                            }
                        }
                    }
                    is HomeUiState.Error -> Text(state.message, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    if (isAddClientDialogVisible) {
        AlertDialog(
            onDismissRequest = { isAddClientDialogVisible = false },
            title = { Text(stringResource(R.string.add_a_client)) },
            text = {
                OutlinedTextField(
                    value = newClientName,
                    onValueChange = { newClientName = it },
                    label = { Text(stringResource(R.string.client_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newClientName.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        val profile = ClientProfile(id = UUID.randomUUID(), name = trimmed, avatarColorArgb = 0)
                        viewModel.createClient(profile)
                        activeClientManager.setCurrentClientId(profile.id)
                        newClientName = ""
                        isAddClientDialogVisible = false
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { isAddClientDialogVisible = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    currentDay: LocalDate,
    onToggleIntake: (String, String, DoseAction) -> Unit,
    onDelete: (UserSupplement) -> Unit,
    onEdit: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf(HomeDoseFilter.ALL) }
    val missedItems = remember(state.activeSupplements) {
        state.activeSupplements.values.flatten().filter { it.doseStatus == DoseStatus.MISSED }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        flingBehavior = ScrollableDefaults.flingBehavior()
    ) {
        // Section: Active Today
        item(
            key = "today_title",
            contentType = "title"
        ) { SectionHeader(stringResource(R.string.today_intake_title)) }

        item(
            key = "today_summary",
            contentType = "summary"
        ) {
            val nowEpochMs = System.currentTimeMillis()
            var due = 0
            var taken = 0
            var skipped = 0
            var missed = 0
            state.activeSupplements.values.forEach { items ->
                items.forEach { item ->
                    when (item.doseStatus) {
                        DoseStatus.PLANNED -> {
                            if (item.scheduledAtEpochMs in 1..nowEpochMs) due += 1
                        }
                        DoseStatus.TAKEN -> taken += 1
                        DoseStatus.SKIPPED -> skipped += 1
                        DoseStatus.MISSED -> missed += 1
                    }
                }
            }
            val counts = TodayCounts(due = due, taken = taken, skipped = skipped, missed = missed)
            val total = counts.due + counts.missed + counts.taken + counts.skipped
            val current = when (filter) {
                HomeDoseFilter.ALL -> total
                HomeDoseFilter.DUE -> counts.due
                HomeDoseFilter.OVERDUE -> counts.missed
                HomeDoseFilter.TAKEN -> counts.taken
                HomeDoseFilter.SKIPPED -> counts.skipped
            }
            val other = (total - current).coerceAtLeast(0)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StreakPill(days = state.streakDays)
                }
                TodayStrip(counts = counts, selected = filter, onSelected = { filter = it })
                if (filter != HomeDoseFilter.ALL && other > 0) {
                    Text(
                        text = stringResource(R.string.home_filter_hint_format, other),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        if (state.activeSupplements.isEmpty()) {
            item(
                key = "today_empty",
                contentType = "empty"
            ) { EmptyStateMessage(stringResource(R.string.no_intake_today)) }
        }

        if (filter == HomeDoseFilter.ALL && missedItems.isNotEmpty()) {
            item(key = "overdue_title", contentType = "title") {
                SectionHeader("${stringResource(R.string.dose_status_missed)} (${missedItems.size})")
            }
            items(
                items = missedItems,
                key = { "overdue-${it.supplement.id}-${it.timeString}" },
                contentType = { "supplement" }
            ) { item ->
                Box(modifier = Modifier.animateItemPlacement()) {
                    DismissibleSupplementCard(
                        item = item,
                        onToggleIntake = onToggleIntake,
                        onDelete = onDelete,
                        onEdit = onEdit
                    )
                }
            }
        }

        if (filter == HomeDoseFilter.OVERDUE) {
            if (missedItems.isEmpty()) {
                item(key = "overdue_empty", contentType = "empty") {
                    EmptyStateMessage(stringResource(R.string.no_overdue_today))
                }
            }
            items(
                items = missedItems,
                key = { "overdue-${it.supplement.id}-${it.timeString}" },
                contentType = { "supplement" }
            ) { item ->
                Box(modifier = Modifier.animateItemPlacement()) {
                    DismissibleSupplementCard(
                        item = item,
                        onToggleIntake = onToggleIntake,
                        onDelete = onDelete,
                        onEdit = onEdit
                    )
                }
            }
        } else {
            state.activeSupplements.forEach { (time, items) ->
                val filtered = items.filter { item ->
                    when (filter) {
                        HomeDoseFilter.ALL -> item.doseStatus != DoseStatus.MISSED
                        HomeDoseFilter.DUE -> {
                            val nowEpochMs = System.currentTimeMillis()
                            item.doseStatus == DoseStatus.PLANNED && item.scheduledAtEpochMs in 1..nowEpochMs
                        }
                        HomeDoseFilter.TAKEN -> item.doseStatus == DoseStatus.TAKEN
                        HomeDoseFilter.SKIPPED -> item.doseStatus == DoseStatus.SKIPPED
                        HomeDoseFilter.OVERDUE -> item.doseStatus == DoseStatus.MISSED
                    }
                }
                if (filtered.isEmpty()) return@forEach
                item(
                    key = "time_$time",
                    contentType = "time"
                ) { TimeGroupHeader(time) }
                items(
                    items = filtered,
                    key = { "${it.supplement.id}-${it.timeString}" },
                    contentType = { "supplement" }
                ) { item ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        DismissibleSupplementCard(
                            item = item,
                            onToggleIntake = onToggleIntake,
                            onDelete = onDelete,
                            onEdit = onEdit
                        )
                    }
                }
            }
        }

        // Section: Resting
        if (state.restingSupplements.isNotEmpty()) {
            item(
                key = "resting_title",
                contentType = "title"
            ) { SectionHeader(stringResource(R.string.resting_title)) }
            items(
                items = state.restingSupplements,
                key = { it.supplement.id },
                contentType = { "resting" }
            ) { info ->
                RestingSupplementCard(
                    info = info,
                    currentDay = currentDay,
                    onDelete = onDelete,
                    onEdit = onEdit
                )
            }
        }
    }
}

private enum class HomeDoseFilter {
    ALL,
    OVERDUE,
    DUE,
    TAKEN,
    SKIPPED
}

@Composable
private fun TodayStrip(
    counts: TodayCounts,
    selected: HomeDoseFilter,
    onSelected: (HomeDoseFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TodayStripButton(
                title = stringResource(R.string.home_status_due),
                count = counts.due,
                tint = Color(0xFF42A5F5),
                selected = selected == HomeDoseFilter.DUE,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSelected(if (selected == HomeDoseFilter.DUE) HomeDoseFilter.ALL else HomeDoseFilter.DUE)
                }
            )
            TodayStripButton(
                title = stringResource(R.string.dose_status_missed),
                count = counts.missed,
                tint = Color(0xFFD32F2F),
                selected = selected == HomeDoseFilter.OVERDUE,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSelected(if (selected == HomeDoseFilter.OVERDUE) HomeDoseFilter.ALL else HomeDoseFilter.OVERDUE)
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TodayStripButton(
                title = stringResource(R.string.notif_action_taken),
                count = counts.taken,
                tint = Color(0xFF2E7D32),
                selected = selected == HomeDoseFilter.TAKEN,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSelected(if (selected == HomeDoseFilter.TAKEN) HomeDoseFilter.ALL else HomeDoseFilter.TAKEN)
                }
            )
            TodayStripButton(
                title = stringResource(R.string.notif_action_skip),
                count = counts.skipped,
                tint = Color(0xFFFF9800),
                selected = selected == HomeDoseFilter.SKIPPED,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSelected(if (selected == HomeDoseFilter.SKIPPED) HomeDoseFilter.ALL else HomeDoseFilter.SKIPPED)
                }
            )
        }
    }
}

@Composable
private fun TodayStripButton(
    title: String,
    count: Int,
    tint: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (isDark) Color.White else Color(0xFF111111)
    val borderColor = if (selected) tint.copy(alpha = 0.55f) else Color.Transparent
    GlassCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = tint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TimeGroupHeader(time: String) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
    Text(
        text = time,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 10.dp)
            .background(base, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private data class TodayCounts(
    val due: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int
)

@Composable
private fun TodaySummaryCard(counts: TodayCounts, streakDays: Int) {
    val dueTint = MaterialTheme.colorScheme.onSurfaceVariant
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            StreakPill(days = streakDays)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountPill(
                title = stringResource(R.string.notif_action_taken),
                value = counts.taken,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
            CountPill(
                title = stringResource(R.string.home_status_due),
                value = counts.due,
                tint = dueTint,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountPill(
                title = stringResource(R.string.notif_action_skip),
                value = counts.skipped,
                tint = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
            CountPill(
                title = stringResource(R.string.home_status_missed),
                value = counts.missed,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StreakPill(days: Int) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
    Box(
        modifier = Modifier
            .background(base, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(R.string.home_streak_format, days),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White else Color(0xFF111111),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CountPill(
    title: String,
    value: Int?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (isDark) Color.White else Color(0xFF111111)
    Box(
        modifier = modifier
            .background(Color.Transparent, RoundedCornerShape(18.dp))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(tint, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (value == null) title else "$title $value",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    OakCard(
        modifier = modifier,
        variant = OakCardVariant.Glass,
        accent = accent,
        shape = RoundedCornerShape(28.dp),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
private fun DismissibleSupplementCard(
    item: SupplementUiItem,
    onToggleIntake: (String, String, DoseAction) -> Unit,
    onDelete: (UserSupplement) -> Unit,
    onEdit: (String) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmVisible by remember { mutableStateOf(false) }
    val dismissState = rememberDismissState(confirmStateChange = { value: DismissValue ->
        when (value) {
            DismissValue.DismissedToStart -> {
                isMenuOpen = true
                false
            }
            DismissValue.DismissedToEnd -> {
                isMenuOpen = true
                false
            }
            else -> true
        }
    })

    LaunchedEffect(item.supplement.id, item.timeString) {
        if (dismissState.currentValue != DismissValue.Default || dismissState.dismissDirection != null) {
            dismissState.reset()
        }
    }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != DismissValue.Default) {
            dismissState.reset()
        }
    }

    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection
            val isDelete = direction == DismissDirection.EndToStart
            val backgroundColor = when (direction) {
                DismissDirection.EndToStart -> Color.Red
                DismissDirection.StartToEnd -> Color(0xFF2E7D32)
                null -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (direction != null) {
                    Icon(
                        imageVector = if (isDelete) Icons.Default.Delete else Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        },
        dismissContent = {
        Box {
            ActiveSupplementCard(
                item = item,
                onToggleIntake = onToggleIntake,
                modifier = Modifier.combinedClickable(
                    onClick = { isMenuOpen = true },
                    onLongClick = { isMenuOpen = true }
                )
            )
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        onEdit(item.supplement.id.toString())
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        isDeleteConfirmVisible = true
                    }
                )
            }
        }
        }
    )

    if (isDeleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmVisible = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmVisible = false
                        onDelete(item.supplement)
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ActiveSupplementCard(
    item: SupplementUiItem,
    onToggleIntake: (String, String, DoseAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    var showConfirmDialog by remember(item.supplement.id) { mutableStateOf(false) }
    val urgencyAccent = when {
        item.isMissedSoon -> Color(0xFFD32F2F)
        item.isDueSoon -> Color(0xFF42A5F5)
        else -> null
    }
    GlassCard(modifier = modifier.fillMaxWidth(), accent = urgencyAccent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.supplement.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = secondaryTextColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(item.timeString, style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                    Text(" • ", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                    Text(item.supplement.dailyDose, style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                }
            }
            val targetTint = when (item.doseStatus) {
                DoseStatus.TAKEN -> Color(0xFF2E7D32)
                DoseStatus.SKIPPED -> Color(0xFFFF9800)
                DoseStatus.MISSED -> Color(0xFFD32F2F)
                DoseStatus.PLANNED -> secondaryTextColor
            }
            val tint by androidx.compose.animation.animateColorAsState(targetValue = targetTint, label = "doseTint")
            val pulse = remember { Animatable(1f) }
            LaunchedEffect(item.doseStatus) {
                if (item.doseStatus == DoseStatus.TAKEN || item.doseStatus == DoseStatus.SKIPPED) {
                    pulse.snapTo(1f)
                    pulse.animateTo(1.16f, tween(140))
                    pulse.animateTo(1f, tween(200))
                }
            }
            IconButton(
                onClick = {
                    if (item.doseStatus == DoseStatus.TAKEN || item.doseStatus == DoseStatus.SKIPPED) {
                        Toast.makeText(context, context.getString(R.string.home_intake_toast_cannot_undo), Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    showConfirmDialog = true
                }
            ) {
                val icon = when (item.doseStatus) {
                    DoseStatus.TAKEN -> Icons.Default.CheckCircle
                    DoseStatus.SKIPPED -> Icons.Default.Cancel
                    DoseStatus.MISSED -> Icons.Default.Error
                    DoseStatus.PLANNED -> Icons.Default.RadioButtonUnchecked
                }
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.graphicsLayer(scaleX = pulse.value, scaleY = pulse.value)
                )
            }
        }
        if (item.doseStatus == DoseStatus.MISSED) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.dose_status_missed),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD32F2F)
            )
        } else if (item.doseStatus == DoseStatus.SKIPPED) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.dose_status_skipped),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800)
            )
        } else {
            AnimatedVisibility(
                visible = item.doseStatus == DoseStatus.PLANNED && (item.isMissedSoon || item.isDueSoon),
                enter = fadeIn(animationSpec = tween(160)),
                exit = fadeOut(animationSpec = tween(160))
            ) {
                val text = if (item.isMissedSoon) {
                    stringResource(R.string.home_almost_missed)
                } else {
                    stringResource(R.string.home_due_soon)
                }
                val color = if (item.isMissedSoon) Color(0xFFD32F2F) else Color(0xFF42A5F5)
                Spacer(modifier = Modifier.height(10.dp))
                CountPill(title = text, value = null, tint = color, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!item.advice.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.advice,
                style = MaterialTheme.typography.bodySmall,
                color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontStyle = FontStyle.Italic
            )
        }
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.home_intake_confirm_title)) },
            text = { Text(stringResource(R.string.home_intake_confirm_message)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleIntake(item.supplement.id.toString(), item.timeString, DoseAction.TAKEN)
                        }
                    ) { Text(stringResource(R.string.home_intake_confirm_taken)) }
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleIntake(item.supplement.id.toString(), item.timeString, DoseAction.SKIPPED)
                        }
                    ) { Text(stringResource(R.string.home_intake_confirm_skip)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.home_intake_confirm_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RestingSupplementCard(
    info: RestingSupplementInfo,
    currentDay: LocalDate,
    onDelete: (UserSupplement) -> Unit,
    onEdit: (String) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit(info.supplement.id.toString()) },
                onLongClick = { isMenuOpen = true }
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.supplement.name, style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                Text(
                    stringResource(
                        R.string.rest_until,
                        currentDay.plusDays(info.daysRemaining.toLong()).format(DateTimeFormatter.ofPattern("dd/MM"))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
            Badge(containerColor = Color.Transparent) {
                Text(stringResource(R.string.days_remaining, info.daysRemaining), modifier = Modifier.padding(4.dp))
            }
            Box {
                IconButton(onClick = { isMenuOpen = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), tint = secondaryTextColor)
                }
                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            isMenuOpen = false
                            onEdit(info.supplement.id.toString())
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            isMenuOpen = false
                            onDelete(info.supplement)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
