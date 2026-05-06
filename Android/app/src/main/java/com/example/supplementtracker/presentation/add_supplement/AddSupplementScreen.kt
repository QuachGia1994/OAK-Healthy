package com.example.supplementtracker.presentation.add_supplement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.domain.model.IntakeTime

import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R

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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundColor = Color(0xFFF2F2F7)
    val backgroundBrush = Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            viewModel.onTimeChange(String.format("%02d:%02d", hour, minute))
        },
        8, 0, true
    )

    LaunchedEffect(supplementId) {
        if (supplementId == null) {
            viewModel.resetForAdd()
            return@LaunchedEffect
        }
        viewModel.loadSupplementForEdit(supplementId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(if (supplementId == null) stringResource(R.string.add_supplement_title) else stringResource(R.string.edit_supplement_title)) },
                    actions = {
                        val isFormValid = state.name.isNotBlank() && (
                            state.isContinuous || (
                                (state.daysOn.toIntOrNull() ?: 0) > 0 &&
                                    (state.daysOff.toIntOrNull() ?: -1) >= 0
                                )
                            )
                        Button(onClick = onSave, enabled = isFormValid) {
                            Text(stringResource(R.string.save))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
            // Section: Thông tin cơ bản
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Hiển thị gợi ý
            if (state.suggestions.isNotEmpty()) {
                val shape = RoundedCornerShape(32.dp)
                val containerColor = MaterialTheme.colorScheme.surfaceVariant
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    state.suggestions.forEach { suggestion ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(suggestion.name) },
                            supportingContent = { 
                                Text(suggestion.advice ?: stringResource(R.string.suggested, suggestion.preferredTime)) 
                            },
                            trailingContent = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.onSuggestionClick(suggestion) }
                        )
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
            Card(
                onClick = { timePickerDialog.show() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = timeShape,
                colors = CardDefaults.cardColors(containerColor = timeContainerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.intake_time, state.intakeTime))
                }
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
