package com.example.supplementtracker.presentation.home

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.service.NotificationDebugStore
import com.example.supplementtracker.service.ScheduledAlarmInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var upcoming by remember { mutableStateOf(emptyList<ScheduledAlarmInfo>()) }
    val grouped = remember(upcoming) { groupByDate(upcoming) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val reload: () -> Unit = { upcoming = NotificationDebugStore.getUpcoming(context) }
    LaunchedEffect(Unit) { reload() }

    NotificationCheckScaffold(onBack = onBack) { padding ->
        NotificationCheckContent(
            context = context,
            grouped = grouped,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onReload = reload,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCheckScaffold(
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush())) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { NotificationCheckTopBar(onBack = onBack) },
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCheckTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = { Text("Danh sách thông báo") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
            }
        }
    )
}

@Composable
private fun NotificationCheckContent(
    context: Context,
    grouped: Map<LocalDate, List<ScheduledAlarmInfo>>,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().then(modifier),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusCards(context = context) }
        item { OutlinedButton(onClick = onReload) { Text("Làm mới danh sách") } }
        if (grouped.isEmpty()) item { EmptyNotificationCard() }
        grouped.keys.sorted().forEach { day ->
            item { Text(day.format(dateFormatter), style = MaterialTheme.typography.titleMedium) }
            items(items = grouped[day].orEmpty(), key = { it.requestCode }) { item ->
                AlarmCard(item = item, timeFormatter = timeFormatter)
            }
        }
    }
}

@Composable
private fun EmptyNotificationCard() {
    ElevatedCard {
        Text(
            text = "Không có thông báo nào đang được lên lịch.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusCards(context: Context) {
    val canScheduleExactAlarms = canScheduleExactAlarms(context)
    val isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExactAlarmStatusCard(canScheduleExactAlarms = canScheduleExactAlarms)
        BatteryOptimizationCard(isIgnoring = isIgnoringBatteryOptimizations)
        if (!isIgnoringBatteryOptimizations) BatteryOptimizationButton(context = context)
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val packageName = context.packageName
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(packageName)
    } else {
        true
    }
}

@Composable
private fun ExactAlarmStatusCard(canScheduleExactAlarms: Boolean) {
    ElevatedCard {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
            Spacer(modifier = Modifier.size(10.dp))
            Text("Exact Alarms: ${if (canScheduleExactAlarms) "Đã bật" else "Chưa bật"}")
        }
    }
}

@Composable
private fun BatteryOptimizationCard(isIgnoring: Boolean) {
    ElevatedCard {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null)
            Spacer(modifier = Modifier.size(10.dp))
            Text("Battery Optimization: ${if (isIgnoring) "Đã bỏ tối ưu" else "Đang bị tối ưu"}")
        }
    }
}

@Composable
private fun BatteryOptimizationButton(context: Context) {
    OutlinedButton(
        onClick = {
            runCatching {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    ) {
        Text("Mở cài đặt Battery Optimization")
    }
}

@Composable
private fun AlarmCard(item: ScheduledAlarmInfo, timeFormatter: DateTimeFormatter) {
    val time = Instant.ofEpochMilli(item.scheduledAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    ElevatedCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconForName(item.title),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                Text(text = "Liều lượng: ${item.dose}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = item.cycleText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = time.format(timeFormatter), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

private fun groupByDate(items: List<ScheduledAlarmInfo>): Map<LocalDate, List<ScheduledAlarmInfo>> {
    return items.groupBy {
        Instant.ofEpochMilli(it.scheduledAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }.mapValues { (_, list) -> list.sortedBy { it.scheduledAtMillis } }
}

@Composable
private fun backgroundBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
    }
}

private fun iconForName(name: String): ImageVector {
    val n = name.lowercase()
    if (n.contains("omega")) return Icons.Default.Spa
    if (n.contains("magnesium") || n.contains("zinc")) return Icons.Default.Medication
    if (n.contains("creatine")) return Icons.Default.FitnessCenter
    if (n.contains("caffeine")) return Icons.Default.Bolt
    return Icons.Default.Science
}
