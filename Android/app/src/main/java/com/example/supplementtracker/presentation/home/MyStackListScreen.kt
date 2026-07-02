package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.presentation.designsystem.oakBackgroundBrush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
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
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val backgroundBrush = oakBackgroundBrush()
    var searchText by rememberSaveable { mutableStateOf("") }
    val filteredSupplements by remember(supplements, searchText) {
        derivedStateOf {
            val q = searchText.trim().lowercase(Locale.ROOT)
            if (q.isEmpty()) return@derivedStateOf supplements
            supplements.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val listState = rememberLazyListState()
    val calculateCycleUseCase = remember { CalculateCycleUseCase() }

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
                    title = { Text(stringResource(R.string.my_list_title), color = primaryTextColor) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.a11y_settings), tint = primaryTextColor)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.a11y_add_supplement))
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "quick_actions", contentType = "quick_actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCardNavigationRow(
                            title = stringResource(R.string.sync_center_title),
                            subtitle = "",
                            onClick = onNavigateToSyncCenter
                        )
                        InfoCardNavigationRow(
                            title = stringResource(R.string.settings_guide_title),
                            subtitle = "",
                            onClick = onNavigateToUserGuide
                        )
                    }
                }
                item(key = "search", contentType = "search") {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.history_search_placeholder), color = secondaryTextColor) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryTextColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                            focusedPlaceholderColor = secondaryTextColor,
                            unfocusedPlaceholderColor = secondaryTextColor
                        )
                    )
                }
                items(
                    items = filteredSupplements,
                    key = { it.id },
                    contentType = { "supplement" }
                ) { supplement ->
                    val time = supplement.intakeTime.trim()
                    val title = if (time.isEmpty()) supplement.name else "${supplement.name} ($time)"
                    val isOffCycle = calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, currentDay) == CycleStatus.OFF
                    InfoCard(
                        title = title,
                        content = getCycleSummary(
                            supplement = supplement,
                            calculateCycleUseCase = calculateCycleUseCase,
                            today = currentDay
                        ),
                        isOffCycle = isOffCycle
                    )
                }
            }
        }
    }
}
