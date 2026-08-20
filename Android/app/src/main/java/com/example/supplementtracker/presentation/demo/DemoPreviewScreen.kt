package com.example.supplementtracker.presentation.demo

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakRadius
import com.example.supplementtracker.presentation.designsystem.OakSpacing
import com.example.supplementtracker.presentation.designsystem.rememberOakAdaptiveLayout

private data class DemoRoutine(
    val name: String,
    val detail: String,
    @StringRes val statusRes: Int
)

private val demoRoutines = listOf(
    DemoRoutine("Vitamin D3", "08:00 • 1000 IU", R.string.notif_action_taken),
    DemoRoutine("Creatine", "12:30 • 5 g", R.string.home_status_due),
    DemoRoutine("Magnesium", "21:30 • 200 mg", R.string.dose_status_missed)
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
    val adaptive = rememberOakAdaptiveLayout()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = adaptive.horizontalPadding,
            top = OakSpacing.Md,
            end = adaptive.horizontalPadding,
            bottom = OakSpacing.Xl
        ),
        verticalArrangement = Arrangement.spacedBy(OakSpacing.Section)
    ) {
        item { DemoNoticeSurface() }
        item { DemoSummarySurface(adaptive.stackMetrics) }
        item { DemoRoutineSurface() }
    }
}

@Composable
private fun DemoNoticeSurface() {
    OakCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Sm)) {
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
private fun DemoSummarySurface(stacked: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(OakSpacing.Xl), verticalArrangement = Arrangement.spacedBy(OakSpacing.Md)) {
            Text(stringResource(R.string.demo_preview_streak), fontWeight = FontWeight.SemiBold)
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Sm)) { DemoSummaryMetrics() }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OakSpacing.Md)) {
                    DemoSummaryMetrics(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DemoSummaryMetrics(modifier: Modifier = Modifier) {
    DemoMetric(R.string.demo_preview_due, modifier)
    DemoMetric(R.string.demo_preview_overdue, modifier)
    DemoMetric(R.string.demo_preview_taken, modifier)
}

@Composable
private fun DemoMetric(@StringRes labelRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(labelRes),
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DemoRoutineSurface() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Md),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = OakSpacing.Lg)) {
            demoRoutines.forEachIndexed { index, routine ->
                DemoRoutineRow(routine)
                if (index != demoRoutines.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun DemoRoutineRow(routine: DemoRoutine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OakSpacing.Md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OakSpacing.Md)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OakSpacing.Xs)) {
            Text(routine.name, fontWeight = FontWeight.SemiBold)
            Text(routine.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(routine.statusRes), style = MaterialTheme.typography.labelMedium)
    }
}
