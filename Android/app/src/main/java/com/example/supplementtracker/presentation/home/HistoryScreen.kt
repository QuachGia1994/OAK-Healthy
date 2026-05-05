package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.repository.IntakeRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundColor = Color(0xFFF2F2F7)
    val backgroundBrush = Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) backgroundBrush else backgroundColor)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.history_title)) }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val state = uiState) {
                    is HistoryUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is HistoryUiState.Success -> HistoryContent(state)
                    is HistoryUiState.NoClient -> Text("Add a Client to see history.", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(state: HistoryUiState.Success) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0x14000000)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.intake_frequency_last_7), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .shadow(if (isDark) 12.dp else 2.dp, shape),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SimpleBarChart(data = state.chartData)
            }
        }
        
        item {
            Text(stringResource(R.string.log_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (state.records.isEmpty()) {
            item { Text(stringResource(R.string.no_logs_yet), color = Color.Gray) }
        } else {
            items(state.records) { record ->
                HistoryRecordItem(record)
            }
        }
    }
}

@Composable
private fun SimpleBarChart(data: List<HistoryChartData>) {
    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 16.dp)) {
        val barWidth = size.width / (data.size * 1.5f)
        val space = (size.width - (barWidth * data.size)) / (data.size + 1)
        
        data.forEachIndexed { index, item ->
            val barHeight = (item.count.toFloat() / maxCount) * size.height
            val x = space + index * (barWidth + space)
            val y = size.height - barHeight
            
            drawRect(
                color = Color(0xFF42A5F5),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
private fun HistoryRecordItem(record: IntakeRecord) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.date), ZoneId.systemDefault())

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0x14000000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 10.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.supplementName ?: "N/A", style = MaterialTheme.typography.bodyLarge)
                Text(dateTime.format(formatter), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(stringResource(R.string.taken), color = Color.Green, fontWeight = FontWeight.Bold)
        }
    }
}
