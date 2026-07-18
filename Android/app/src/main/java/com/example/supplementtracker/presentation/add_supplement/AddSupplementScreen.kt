package com.example.supplementtracker.presentation.add_supplement

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.SupplementReference
import com.example.supplementtracker.domain.util.TimeStrings
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSupplementScreen(
    viewModel: AddSupplementViewModel,
    supplementId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isFormValid by remember(state) { derivedStateOf { isFormValid(state) } }

    AddSupplementEffects(viewModel, supplementId, state.error, snackbarHostState)
    OakBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { SupplementTopBar(supplementId != null, onBack) },
            bottomBar = { SaveSupplementBar(isFormValid, state.isLoading, onSave) }
        ) { padding ->
            AddSupplementContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun AddSupplementEffects(
    viewModel: AddSupplementViewModel,
    supplementId: String?,
    error: String?,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(supplementId) {
        if (supplementId == null) viewModel.resetForAdd() else viewModel.loadSupplementForEdit(supplementId)
    }
    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementTopBar(isEditing: Boolean, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(stringResource(if (isEditing) R.string.edit_supplement_title else R.string.add_supplement_title))
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_navigate_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun AddSupplementContent(
    state: AddSupplementState,
    viewModel: AddSupplementViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item("intro") { SupplementIntro() }
        item("details") { DetailsSection(state, viewModel) }
        item("timing") { TimingSection(state, viewModel) }
        item("rhythm") { RhythmSection(state, viewModel) }
        item("bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SupplementIntro() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(15.dp)) {
            Icon(
                Icons.Default.AddCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(13.dp).size(26.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.add_supplement_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.add_supplement_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailsSection(state: AddSupplementState, viewModel: AddSupplementViewModel) {
    SupplementSectionCard(
        title = stringResource(R.string.supplement_details_title),
        subtitle = stringResource(R.string.supplement_details_body),
        icon = Icons.Default.AddCircle
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        SuggestionRow(state.suggestions, viewModel::onSuggestionClick)
        OutlinedTextField(
            value = state.dailyDose,
            onValueChange = viewModel::onDailyDoseChange,
            label = { Text(stringResource(R.string.daily_dose_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SuggestionRow(
    suggestions: List<SupplementReference>,
    onSelect: (SupplementReference) -> Unit
) {
    if (suggestions.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions, key = { it.name }) { suggestion ->
            Card(
                onClick = { onSelect(suggestion) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(suggestion.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        suggestion.advice ?: stringResource(R.string.suggested, suggestion.preferredTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingSection(state: AddSupplementState, viewModel: AddSupplementViewModel) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd") }
    SupplementSectionCard(
        title = stringResource(R.string.supplement_timing_title),
        subtitle = stringResource(R.string.supplement_timing_body),
        icon = Icons.Default.Schedule
    ) {
        DateSelector(state.startDate.format(formatter)) {
            showDatePicker(context, state.startDate, viewModel::onStartDateChange)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        IntakeTimeChips(state.intakeTime, viewModel::removeIntakeTime)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.selectedTime, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { showTimePicker(context, viewModel::onSelectedTimeChange) }) {
                Text(stringResource(R.string.change_time))
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = viewModel::addSelectedTime) {
                Icon(Icons.Default.Add, stringResource(R.string.add_time))
            }
        }
    }
}

@Composable
private fun DateSelector(dateText: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.start_date_format, dateText))
        }
    }
}

@Composable
private fun IntakeTimeChips(intakeTime: String, onRemove: (String) -> Unit) {
    val times = remember(intakeTime) { TimeStrings.normalizeList(intakeTime) }
    if (times.isEmpty()) {
        Text(stringResource(R.string.intake_times_empty), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(times, key = { it }) { time ->
            AssistChip(
                onClick = { onRemove(time) },
                label = { Text(time) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_time), Modifier.size(16.dp)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RhythmSection(state: AddSupplementState, viewModel: AddSupplementViewModel) {
    SupplementSectionCard(
        title = stringResource(R.string.supplement_rhythm_title),
        subtitle = stringResource(R.string.supplement_rhythm_body),
        icon = Icons.Default.Tune
    ) {
        CycleModePicker(state.isContinuous, viewModel::onContinuousToggle)
        if (!state.isContinuous) CycleFields(state, viewModel)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        WeeklyControls(state, viewModel)
        IntervalControls(state, viewModel)
        NumberField(
            value = state.durationMonths,
            onValueChange = viewModel::onDurationChange,
            label = stringResource(R.string.duration_months_label)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleModePicker(isContinuous: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !isContinuous,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2)
        ) { Text(stringResource(R.string.schedule_mode_cycle)) }
        SegmentedButton(
            selected = isContinuous,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2)
        ) { Text(stringResource(R.string.continuous)) }
    }
}

@Composable
private fun CycleFields(state: AddSupplementState, viewModel: AddSupplementViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(state.daysOn, viewModel::onDaysOnChange, stringResource(R.string.on_days), Modifier.weight(1f))
        NumberField(state.daysOff, viewModel::onDaysOffChange, stringResource(R.string.off_days), Modifier.weight(1f))
    }
}

@Composable
private fun WeeklyControls(state: AddSupplementState, viewModel: AddSupplementViewModel) {
    SettingSwitch(stringResource(R.string.repeat_weekly), state.isWeeklyRecurrenceEnabled, viewModel::onWeeklyRecurrenceToggle)
    if (!state.isWeeklyRecurrenceEnabled) return
    val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels.size) { index ->
            FilterChip(
                selected = ((state.weekdaysMask shr index) and 1) == 1,
                onClick = { viewModel.toggleWeekday(index) },
                label = { Text(labels[index]) }
            )
        }
    }
    NumberField(state.intervalWeeks, viewModel::onIntervalWeeksChange, stringResource(R.string.repeat_every_weeks))
}

@Composable
private fun IntervalControls(state: AddSupplementState, viewModel: AddSupplementViewModel) {
    SettingSwitch(stringResource(R.string.repeat_every_n_days), state.isIntervalDaysEnabled, viewModel::onIntervalDaysToggle)
    if (!state.isIntervalDaysEnabled) return
    NumberField(state.intervalDays, viewModel::onIntervalDaysChange, stringResource(R.string.interval_days_label))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun SupplementSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun SaveSupplementBar(enabled: Boolean, loading: Boolean, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 4.dp) {
        Button(
            onClick = onSave,
            enabled = enabled && !loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.save))
        }
    }
}

private fun showDatePicker(context: Context, date: LocalDate, onSelect: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    ).show()
}

private fun showTimePicker(context: Context, onSelect: (String) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelect(String.format(Locale.ROOT, "%02d:%02d", hour, minute)) },
        8,
        0,
        true
    ).show()
}

private fun isFormValid(state: AddSupplementState): Boolean {
    if (state.name.isBlank() || TimeStrings.normalizeList(state.intakeTime).isEmpty()) return false
    if (!state.isContinuous && ((state.daysOn.toIntOrNull() ?: 0) <= 0 || (state.daysOff.toIntOrNull() ?: -1) < 0)) return false
    if (state.isWeeklyRecurrenceEnabled && (state.intervalWeeks.toIntOrNull() ?: 0) <= 0) return false
    if (state.isIntervalDaysEnabled && (state.intervalDays.toIntOrNull() ?: 0) < 2) return false
    return true
}
