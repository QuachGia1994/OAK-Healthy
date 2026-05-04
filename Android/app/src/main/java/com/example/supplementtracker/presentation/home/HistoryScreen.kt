package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lịch sử") }) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is HistoryUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is HistoryUiState.Success -> HistoryContent(state)
            }
        }
    }
}

@Composable
private fun HistoryContent(state: HistoryUiState.Success) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Tần suất uống (7 ngày qua)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SimpleBarChart(data = state.chartData)
        }
        
        item {
            Text("Chi tiết nhật ký", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (state.records.isEmpty()) {
            item { Text("Chưa có nhật ký nào.", color = Color.Gray) }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.supplementName ?: "N/A", style = MaterialTheme.typography.bodyLarge)
                Text(dateTime.format(formatter), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text("Đã uống", color = Color.Green, fontWeight = FontWeight.Bold)
        }
    }
}
