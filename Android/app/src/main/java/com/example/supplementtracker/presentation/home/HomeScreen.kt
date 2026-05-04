package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.example.supplementtracker.service.UpdateService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    updateService: UpdateService = UpdateService(), // Nên được inject qua DI
    onNavigateToAdd: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isUpdateAvailable by updateService.isUpdateAvailable.collectAsStateWithLifecycle()
    val updateInfo by updateService.updateInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        updateService.checkForUpdates(packageInfo.versionName ?: "1.0.0")
    }

    if (isUpdateAvailable) {
        AlertDialog(
            onDismissRequest = { /* Handle if force update */ },
            title = { Text("Đã có phiên bản mới!") },
            text = { Text("Hãy cập nhật để trải nghiệm những tính năng mới nhất và tăng cường bảo mật (v${updateInfo?.version}).") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo?.updateUrl))
                    context.startActivity(intent)
                }) {
                    Text("Cập nhật ngay")
                }
            },
            dismissButton = {
                TextButton(onClick = { /* Hide alert */ }) {
                    Text("Để sau")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text("Dashboard")
                        Text(
                            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Thêm chất mới")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is HomeUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is HomeUiState.Success -> HomeContent(state, onLogIntake = viewModel::logIntake)
                is HomeUiState.Error -> Text(state.message, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onLogIntake: (UserSupplement) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Active Today
        item { SectionHeader("Cần uống hôm nay") }
        
        if (state.activeSupplements.isEmpty()) {
            item { EmptyStateMessage("Hôm nay bạn không có lịch uống nào.") }
        }

        state.activeSupplements.forEach { (time, supplements) ->
            item { TimeGroupHeader(time) }
            items(supplements) { supplement ->
                ActiveSupplementCard(supplement, onLogIntake)
            }
        }

        // Section: Resting
        if (state.restingSupplements.isNotEmpty()) {
            item { SectionHeader("Đang trong chu kỳ nghỉ") }
            items(state.restingSupplements) { info ->
                RestingSupplementCard(info)
            }
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
private fun TimeGroupHeader(time: IntakeTime) {
    Text(
        text = time.label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ActiveSupplementCard(
    supplement: UserSupplement,
    onLogIntake: (UserSupplement) -> Unit
) {
    var isTaken by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(supplement.name, style = MaterialTheme.typography.titleMedium)
                Text("Liều lượng: ${supplement.dailyDose}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { 
                if (!isTaken) {
                    isTaken = true
                    onLogIntake(supplement)
                }
            }) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Đã uống",
                    tint = if (isTaken) Color.Green else Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestingSupplementCard(info: RestingSupplementInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.supplement.name, style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text("Nghỉ đến: ${LocalDate.now().plusDays(info.daysRemaining.toLong()).format(DateTimeFormatter.ofPattern("dd/MM"))}", 
                     style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Còn ${info.daysRemaining} ngày", modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
