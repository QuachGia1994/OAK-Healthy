package com.example.supplementtracker.presentation.add_supplement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.domain.model.IntakeTime

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
                            supportingContent = { Text("Gợi ý: ${suggestion.preferredTime.label}") },
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
                        onValueChange = { /* Update daysOn in VM */ },
                        label = { Text("Ngày uống") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.daysOff,
                        onValueChange = { /* Update daysOff in VM */ },
                        label = { Text("Ngày nghỉ") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }
}
