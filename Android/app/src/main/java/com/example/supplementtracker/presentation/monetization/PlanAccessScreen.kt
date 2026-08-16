package com.example.supplementtracker.presentation.monetization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.service.CommercialPlan
import com.example.supplementtracker.service.EntitlementManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanAccessScreen(
    entitlementManager: EntitlementManager,
    onBack: () -> Unit
) {
    val snapshot by entitlementManager.snapshot.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_access_title)) },
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
    ) { padding ->
        PlanAccessContent(snapshot.plan, Modifier.padding(padding))
    }
}

@Composable
private fun PlanAccessContent(currentPlan: CommercialPlan, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CurrentPlanCard(currentPlan) }
        item { PlanCard(CommercialPlan.FREE, currentPlan) }
        item { PlanCard(CommercialPlan.PRO, currentPlan) }
        item { PlanCard(CommercialPlan.COACH, currentPlan) }
        item { StoreConnectionNote() }
    }
}

@Composable
private fun CurrentPlanCard(plan: CommercialPlan) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.plan_access_current_plan), style = MaterialTheme.typography.labelLarge)
            Text(planTitle(plan), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlanCard(plan: CommercialPlan, currentPlan: CommercialPlan) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanHeader(plan, plan == currentPlan)
            planFeatureLabels(plan).forEach { label -> FeatureRow(label) }
        }
    }
}

@Composable
private fun PlanHeader(plan: CommercialPlan, isCurrent: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(planTitle(plan), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                planSubtitle(plan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isCurrent) Text(stringResource(R.string.plan_access_current_badge), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StoreConnectionNote() {
    Text(
        text = stringResource(R.string.plan_access_store_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun planTitle(plan: CommercialPlan): String = stringResource(
    when (plan) {
        CommercialPlan.FREE -> R.string.plan_free_title
        CommercialPlan.PRO -> R.string.plan_pro_title
        CommercialPlan.COACH -> R.string.plan_coach_title
    }
)

@Composable
private fun planSubtitle(plan: CommercialPlan): String = stringResource(
    when (plan) {
        CommercialPlan.FREE -> R.string.plan_free_subtitle
        CommercialPlan.PRO -> R.string.plan_pro_subtitle
        CommercialPlan.COACH -> R.string.plan_coach_subtitle
    }
)

@Composable
private fun planFeatureLabels(plan: CommercialPlan): List<String> = when (plan) {
    CommercialPlan.FREE -> listOf(
        stringResource(R.string.plan_feature_basic_tracking),
        stringResource(R.string.plan_feature_reminders),
        stringResource(R.string.plan_feature_recent_history)
    )
    CommercialPlan.PRO -> listOf(
        stringResource(R.string.plan_feature_advanced_cycles),
        stringResource(R.string.plan_feature_unlimited_history),
        stringResource(R.string.plan_feature_adherence_analytics),
        stringResource(R.string.plan_feature_encrypted_sync),
        stringResource(R.string.plan_feature_data_export)
    )
    CommercialPlan.COACH -> listOf(
        stringResource(R.string.plan_feature_all_pro),
        stringResource(R.string.plan_feature_multi_client),
        stringResource(R.string.plan_feature_coach_reports)
    )
}
