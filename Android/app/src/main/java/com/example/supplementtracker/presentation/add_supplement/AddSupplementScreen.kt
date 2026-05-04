package com.example.supplementtracker.presentation.add_supplement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.domain.model.IntakeTime

import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext

/**
 * Màn hình thêm mới chất bổ sung (Jetpack Compose).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSupplementScreen(
    viewModel: AddSupplementViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            viewModel.onTimeChange(String.format("%02d:%02d", hour, minute))
        },
        8, 0, true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thêm chất mới") },
                actions = {
                    Button(onClick = onSave, enabled = state.name.isNotBlank()) {
                        Text("Lưu")
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
                label = { Text("Tên chất (VD: Vitamin D3)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Hiển thị gợi ý
            if (state.suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    state.suggestions.forEach { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.name) },
                            supportingContent = { 
                                Text(suggestion.advice ?: "Gợi ý: ${suggestion.preferredTime}") 
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
                label = { Text("Liều lượng hàng ngày") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Lịch trình & Chu kỳ
            Text("Lịch trình & Chu kỳ", style = MaterialTheme.typography.titleMedium)
            
            OutlinedCard(
                onClick = { timePickerDialog.show() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Giờ uống: ${state.intakeTime}")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Uống liên tục")
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
                        label = { Text("Số ngày uống (On Days)") },
                        placeholder = { Text("Ví dụ: 14 ngày") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.daysOff,
                        onValueChange = viewModel::onDaysOffChange,
                        label = { Text("Số ngày nghỉ (Off Days)") },
                        placeholder = { Text("Ví dụ: 7 ngày") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = state.durationMonths,
                onValueChange = viewModel::onDurationChange,
                label = { Text("Tổng thời hạn (Duration)") },
                placeholder = { Text("Ví dụ: 3 tháng hoặc để trống") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}
