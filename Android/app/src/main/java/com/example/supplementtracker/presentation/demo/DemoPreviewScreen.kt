package com.example.supplementtracker.presentation.demo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.rememberOakAdaptiveLayout

private data class DemoRoutine(
    val name: String,
    val detail: String,
    @StringRes val statusRes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoPreviewScreen(onBack: () -> Unit) {
    OakBackground {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.demo_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_navigate_back)
                        )
                    }
                }
            )
        }
    ) { padding -> DemoPreviewContent(Modifier.padding(padding)) }
    }
}

@Composable
private fun DemoPreviewContent(modifier: Modifier) {
    val routines = listOf(
        DemoRoutine("Vitamin D3", "08:00 • 1000 IU", R.string.notif_action_taken),
        DemoRoutine("Creatine", "12:30 • 5 g", R.string.home_status_due),
        DemoRoutine("Magnesium", "21:30 • 200 mg", R.string.dose_status_missed)
    )
    val adaptive = rememberOakAdaptiveLayout()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = adaptive.horizontalPadding,
            top = 12.dp,
            end = adaptive.horizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DemoNoticeCard() }
        item { DemoSummaryCard() }
        routines.forEach { routine -> item(key = routine.name) { DemoRoutineCard(routine) } }
    }
}

@Composable
private fun DemoNoticeCard() {
    OakCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.demo_preview_privacy_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.demo_preview_client), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.demo_preview_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.demo_preview_presentation_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DemoSummaryCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.demo_preview_streak), fontWeight = FontWeight.SemiBold)
            DemoSummaryMetrics()
        }
    }
}

@Composable
private fun DemoSummaryMetrics() {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DemoMetric(R.string.demo_preview_due)
            DemoMetric(R.string.demo_preview_overdue)
            DemoMetric(R.string.demo_preview_taken)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DemoMetric(R.string.demo_preview_due, Modifier.weight(1f))
        DemoMetric(R.string.demo_preview_overdue, Modifier.weight(1f))
        DemoMetric(R.string.demo_preview_taken, Modifier.weight(1f))
    }
}

@Composable
private fun DemoMetric(labelRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(labelRes),
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DemoRoutineCard(routine: DemoRoutine) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(routine.name, fontWeight = FontWeight.SemiBold)
                Text(routine.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(routine.statusRes))
        }
    }
}
