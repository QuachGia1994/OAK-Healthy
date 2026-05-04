package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import java.time.LocalDate

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import com.example.supplementtracker.presentation.navigation.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ... Logo Section ...

            item {
                AppearanceCard(
                    appTheme = appTheme,
                    onThemeChange = onThemeChange
                )
            }

            // Section: Danh sách của tôi
            if (uiState is HomeUiState.Success) {
                val successState = uiState as HomeUiState.Success
                val allSupplements = (successState.activeSupplements.values.flatten().map { it.supplement } + 
                                     successState.restingSupplements.map { it.supplement })
                    .distinctBy { it.id }
                    .sortedBy { it.name }

                item {
                    Text(
                        text = "Danh sách của tôi",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold
                    )
                }

                items(items = allSupplements) { supplement ->
                    InfoCard(
                        title = supplement.name,
                        content = getCycleSummary(supplement)
                    )
                }
            }

            item {
                InfoCard(
                    title = stringResource(R.string.settings_guide_title),
                    content = stringResource(R.string.settings_guide_content)
                )
            }
            
            item {
                InfoCard(
                    title = stringResource(R.string.settings_intro_title),
                    content = stringResource(R.string.settings_intro_content)
                )
            }
            
            item {
                InfoCard(
                    title = stringResource(R.string.settings_copyright_title),
                    content = """
                        ${stringResource(R.string.settings_app_version)}
                        ${stringResource(R.string.settings_author)}
                        ${stringResource(R.string.settings_copyright)}
                    """.trimIndent()
                )
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Giao diện", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                ThemeRow("Sáng", appTheme == AppTheme.LIGHT) { onThemeChange(AppTheme.LIGHT) }
                ThemeRow("Tối", appTheme == AppTheme.DARK) { onThemeChange(AppTheme.DARK) }
                ThemeRow("Hệ thống", appTheme == AppTheme.SYSTEM) { onThemeChange(AppTheme.SYSTEM) }
            }
        }
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

private fun getCycleSummary(supplement: UserSupplement): String {
    val config = supplement.cycleConfig
    val calculateCycleUseCase = CalculateCycleUseCase()
    val status = calculateCycleUseCase(supplement.startDate, config, LocalDate.now())
    val statusText = if (status == CycleStatus.ON) "Đang trong chu kỳ" else "Đang trong kỳ nghỉ"

    return if (config.isContinuous) {
        "Uống liên tục"
    } else {
        "$statusText: ${config.daysOn} ngày uống / ${config.daysOff} ngày nghỉ"
    }
}

@Composable
private fun InfoCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
            )
        }
    }
}
