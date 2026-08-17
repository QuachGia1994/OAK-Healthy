package com.example.supplementtracker.presentation.monetization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakRadius
import com.example.supplementtracker.presentation.designsystem.OakSpacing
import com.example.supplementtracker.presentation.designsystem.OakTypography
import com.example.supplementtracker.service.CommercialPlan
import com.example.supplementtracker.service.CommercialTelemetryFields
import com.example.supplementtracker.service.DiagnosticsReporter
import com.example.supplementtracker.service.EntitlementManager
import com.example.supplementtracker.service.GooglePlayBillingService
import com.example.supplementtracker.service.PlayBillingNotice
import com.example.supplementtracker.service.PlayStoreProduct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanAccessScreen(
    entitlementManager: EntitlementManager,
    billingService: GooglePlayBillingService,
    onBack: () -> Unit
) {
    val snapshot by entitlementManager.snapshot.collectAsStateWithLifecycle()
    val billingState by billingService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    LaunchedEffect(Unit) {
        DiagnosticsReporter.event(context, "plan_access_view", mapOf("plan" to snapshot.plan.name))
        billingService.refresh()
    }
    Scaffold(topBar = { PlanAccessTopBar(onBack) }) { padding ->
        PlanAccessContent(
            currentPlan = snapshot.plan,
            billingState = billingState,
            activity = activity,
            billingService = billingService,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanAccessTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.plan_access_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_navigate_back))
            }
        }
    )
}

@Composable
private fun PlanAccessContent(
    currentPlan: CommercialPlan,
    billingState: com.example.supplementtracker.service.PlayBillingState,
    activity: Activity?,
    billingService: GooglePlayBillingService,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(OakSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(OakSpacing.Section)
    ) {
        item { CurrentPlanHero(currentPlan) }
        item { PlanComparisonSurface(currentPlan) }
        item { StorePurchaseSurface(billingState, activity, billingService) }
    }
}

@Composable
private fun CurrentPlanHero(plan: CommercialPlan) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(OakSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(OakSpacing.Sm)
        ) {
            Text(
                stringResource(R.string.plan_access_current_plan),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                planTitle(plan),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = OakTypography.Display),
                fontWeight = FontWeight.SemiBold
            )
            Text(planSubtitle(plan), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanComparisonSurface(currentPlan: CommercialPlan) {
    val plans = listOf(CommercialPlan.FREE, CommercialPlan.PRO, CommercialPlan.COACH)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = OakSpacing.Lg)) {
            plans.forEachIndexed { index, plan ->
                PlanSection(plan, plan == currentPlan)
                if (index != plans.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PlanSection(plan: CommercialPlan, isCurrent: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = OakSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(OakSpacing.Sm)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OakSpacing.Xs)) {
                Text(planTitle(plan), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(planSubtitle(plan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCurrent) {
                Text(
                    stringResource(R.string.plan_access_current_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        planFeatureLabels(plan).forEach { label -> FeatureRow(label) }
    }
}

@Composable
private fun FeatureRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(OakSpacing.Sm))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StorePurchaseSurface(
    state: com.example.supplementtracker.service.PlayBillingState,
    activity: Activity?,
    billingService: GooglePlayBillingService
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OakRadius.Md),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(OakSpacing.Lg), verticalArrangement = Arrangement.spacedBy(OakSpacing.Md)) {
            Text(stringResource(R.string.billing_store_products), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!state.isLoading && state.products.isEmpty()) StorePreview()
            state.products.forEachIndexed { index, product ->
                PurchaseRow(product, state.purchasingProductId, activity, billingService)
                if (index != state.products.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RestoreAndStatus(state.notice, billingService)
        }
    }
}

@Composable
private fun StorePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Xs)) {
        Text(stringResource(R.string.plan_preview_title), fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.plan_preview_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PurchaseRow(
    product: PlayStoreProduct,
    purchasingProductId: String?,
    activity: Activity?,
    billingService: GooglePlayBillingService
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OakSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(product.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(product.formattedPrice, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            enabled = activity != null && purchasingProductId == null,
            onClick = {
                DiagnosticsReporter.event(
                    context,
                    "billing_purchase_started",
                    CommercialTelemetryFields.product(product.productId, "play_store")
                )
                activity?.let { billingService.purchase(it, product.productId) }
            }
        ) { Text(stringResource(R.string.billing_buy)) }
    }
}

@Composable
private fun RestoreAndStatus(notice: PlayBillingNotice?, billingService: GooglePlayBillingService) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(OakSpacing.Sm)) {
        Button(
            onClick = {
                DiagnosticsReporter.event(context, "billing_restore_started", mapOf("source" to "play_store"))
                billingService.restorePurchases()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.billing_restore))
        }
        notice?.let {
            Text(noticeText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.billing_store_authoritative_note_play),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun noticeText(notice: PlayBillingNotice): String = stringResource(
    when (notice) {
        PlayBillingNotice.PURCHASE_COMPLETED -> R.string.billing_purchase_completed
        PlayBillingNotice.PURCHASE_PENDING -> R.string.billing_purchase_pending
        PlayBillingNotice.PURCHASE_CANCELLED -> R.string.billing_purchase_cancelled
        PlayBillingNotice.RESTORE_COMPLETED -> R.string.billing_restore_completed
        PlayBillingNotice.VERIFICATION_FAILED -> R.string.billing_verification_failed
        PlayBillingNotice.VERIFICATION_NOT_CONFIGURED -> R.string.billing_verification_not_configured
        PlayBillingNotice.STORE_UNAVAILABLE -> R.string.billing_store_unavailable_play
    }
)

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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
