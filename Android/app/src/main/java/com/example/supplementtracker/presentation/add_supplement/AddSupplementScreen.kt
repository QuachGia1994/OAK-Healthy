package com.example.supplementtracker.presentation.add_supplement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakCardVariant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Màn hình thêm mới chất bổ sung (Jetpack Compose).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSupplementScreen(
    viewModel: AddSupplementViewModel,
    supplementId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd") }

    val timePickerDialog = remember(context) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                viewModel.onSelectedTimeChange(String.format("%02d:%02d", hour, minute))
            },
            8, 0, true
        )
    }

    val isFormValid by remember(state) {
        derivedStateOf {
            state.name.isNotBlank() && (
                state.isContinuous || (
                    (state.daysOn.toIntOrNull() ?: 0) > 0 &&
                        (state.daysOff.toIntOrNull() ?: -1) >= 0
                    )
                ) && (
                !state.isWeeklyRecurrenceEnabled || (state.intervalWeeks.toIntOrNull() ?: 0) > 0
                ) && (
                !state.isIntervalDaysEnabled || (state.intervalDays.toIntOrNull() ?: 0) >= 2
                )
        }
    }

    LaunchedEffect(supplementId) {
        if (supplementId == null) {
            viewModel.resetForAdd()
            return@LaunchedEffect
        }
        viewModel.loadSupplementForEdit(supplementId)
    }
    
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    OakBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(if (supplementId == null) stringResource(R.string.add_supplement_title) else stringResource(R.string.edit_supplement_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_navigate_back))
                        }
                    },
                    actions = {
                        Button(onClick = onSave, enabled = isFormValid && !state.isLoading) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.save))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                OakCard(
                    modifier = Modifier.fillMaxSize(),
                    variant = OakCardVariant.Glass,
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(16.dp),
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Section: Thông tin cơ bản
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Hiển thị gợi ý
            if (state.suggestions.isNotEmpty()) {
                val suggestionListState = rememberLazyListState()
                OakCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    variant = OakCardVariant.Glass,
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(0.dp),
                    elevation = 1.dp
                ) {
                    LazyColumn(
                        state = suggestionListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(
                            items = state.suggestions,
                            key = { it.name },
                            contentType = { "suggestion" }
                        ) { suggestion ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(suggestion.name) },
                                supportingContent = {
                                    Text(suggestion.advice ?: stringResource(R.string.suggested, suggestion.preferredTime))
                                },
                                trailingContent = { Icon(Icons.Default.AddCircle, contentDescription = stringResource(R.string.a11y_add)) },
                                modifier = Modifier.clickable { viewModel.onSuggestionClick(suggestion) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.dailyDose,
                onValueChange = viewModel::onDailyDoseChange,
                label = { Text(stringResource(R.string.daily_dose_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Lịch trình & Chu kỳ
            Text(stringResource(R.string.schedule_cycle_title), style = MaterialTheme.typography.titleMedium)
            
            val timeShape = RoundedCornerShape(32.dp)
            val timeContainerColor = MaterialTheme.colorScheme.surfaceVariant
            val currentStartDate = state.startDate
            val startDateText = remember(currentStartDate) { currentStartDate.format(dateFormatter) }
            Card(
                onClick = {
                    val initialYear = currentStartDate.year
                    val initialMonth = currentStartDate.monthValue - 1
                    val initialDay = currentStartDate.dayOfMonth
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            viewModel.onStartDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        initialYear,
                        initialMonth,
                        initialDay
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = timeShape,
                colors = CardDefaults.cardColors(containerColor = timeContainerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.a11y_confirm))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_date_format, startDateText))
                }
            }

            OutlinedTextField(
                value = state.intakeTime,
                onValueChange = viewModel::onIntakeTimesChange,
                label = { Text(stringResource(R.string.intake_times_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    onClick = { timePickerDialog.show() },
                    modifier = Modifier.weight(1f),
                    shape = timeShape,
                    colors = CardDefaults.cardColors(containerColor = timeContainerColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.a11y_confirm))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.selected_time_format, state.selectedTime))
                    }
                }
                Button(onClick = viewModel::addSelectedTime) {
                    Text(stringResource(R.string.add_time))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.repeat_weekly))
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = state.isWeeklyRecurrenceEnabled,
                    onCheckedChange = viewModel::onWeeklyRecurrenceToggle
                )
            }
            
            if (state.isWeeklyRecurrenceEnabled) {
                val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                val selectedDays = labels.mapIndexedNotNull { index, label ->
                    if (((state.weekdaysMask shr index) and 1) == 1) label else null
                }
                val interval = (state.intervalWeeks.toIntOrNull() ?: 1).coerceAtLeast(1)
                Text(
                    text = stringResource(R.string.repeat_on_weekdays),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    labels.forEachIndexed { index, label ->
                        val selected = ((state.weekdaysMask shr index) and 1) == 1
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleWeekday(index) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.intervalWeeks,
                    onValueChange = viewModel::onIntervalWeeksChange,
                    label = { Text(stringResource(R.string.repeat_every_weeks)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    text = "${selectedDays.joinToString(", ")} • ${stringResource(R.string.every_x_weeks_format, interval)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.repeat_every_n_days))
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = state.isIntervalDaysEnabled,
                    onCheckedChange = viewModel::onIntervalDaysToggle
                )
            }

            if (state.isIntervalDaysEnabled) {
                OutlinedTextField(
                    value = state.intervalDays,
                    onValueChange = viewModel::onIntervalDaysChange,
                    label = { Text(stringResource(R.string.interval_days_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    text = stringResource(R.string.repeat_every_n_days_preview, state.intervalDays),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.continuous))
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = state.isContinuous,
                    onCheckedChange = viewModel::onContinuousToggle
                )
            }

            if (!state.isContinuous) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.daysOn,
                        onValueChange = viewModel::onDaysOnChange,
                        label = { Text(stringResource(R.string.on_days)) },
                        placeholder = { Text(stringResource(R.string.example_on_days)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.daysOff,
                        onValueChange = viewModel::onDaysOffChange,
                        label = { Text(stringResource(R.string.off_days)) },
                        placeholder = { Text(stringResource(R.string.example_off_days)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = state.durationMonths,
                onValueChange = viewModel::onDurationChange,
                label = { Text(stringResource(R.string.duration)) },
                placeholder = { Text(stringResource(R.string.duration_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
                    }
                }
            }
        }
    }
}
