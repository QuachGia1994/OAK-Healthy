package com.example.supplementtracker.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CommercialPlan {
    FREE,
    PRO,
    COACH
}

enum class CommercialFeature {
    BASIC_TRACKING,
    REMINDERS,
    RECENT_HISTORY,
    ADVANCED_CYCLES,
    UNLIMITED_HISTORY,
    ADHERENCE_ANALYTICS,
    ENCRYPTED_CLOUD_SYNC,
    DATA_EXPORT,
    MULTI_CLIENT,
    COACH_REPORTS
}

enum class BillingPeriod {
    MONTHLY,
    ANNUAL
}

data class CommercialProduct(
    val productId: String,
    val plan: CommercialPlan,
    val billingPeriod: BillingPeriod
)

data class EntitlementSnapshot(
    val plan: CommercialPlan,
    val activeProductId: String? = null
) {
    companion object {
        val Free = EntitlementSnapshot(CommercialPlan.FREE)
    }
}

object CommercialProductCatalog {
    const val PRO_MONTHLY = "oak_pro_monthly"
    const val PRO_ANNUAL = "oak_pro_annual"
    const val COACH_MONTHLY = "oak_coach_monthly"
    const val COACH_ANNUAL = "oak_coach_annual"

    val products = listOf(
        CommercialProduct(PRO_MONTHLY, CommercialPlan.PRO, BillingPeriod.MONTHLY),
        CommercialProduct(PRO_ANNUAL, CommercialPlan.PRO, BillingPeriod.ANNUAL),
        CommercialProduct(COACH_MONTHLY, CommercialPlan.COACH, BillingPeriod.MONTHLY),
        CommercialProduct(COACH_ANNUAL, CommercialPlan.COACH, BillingPeriod.ANNUAL)
    )
}

object EntitlementPolicy {
    private val freeFeatures = setOf(
        CommercialFeature.BASIC_TRACKING,
        CommercialFeature.REMINDERS,
        CommercialFeature.RECENT_HISTORY
    )
    private val proFeatures = freeFeatures + setOf(
        CommercialFeature.ADVANCED_CYCLES,
        CommercialFeature.UNLIMITED_HISTORY,
        CommercialFeature.ADHERENCE_ANALYTICS,
        CommercialFeature.ENCRYPTED_CLOUD_SYNC,
        CommercialFeature.DATA_EXPORT
    )

    fun allows(plan: CommercialPlan, feature: CommercialFeature): Boolean {
        return when (plan) {
            CommercialPlan.FREE -> feature in freeFeatures
            CommercialPlan.PRO -> feature in proFeatures
            CommercialPlan.COACH -> true
        }
    }

    fun maxClients(plan: CommercialPlan): Int? {
        return when (plan) {
            CommercialPlan.FREE, CommercialPlan.PRO -> 1
            CommercialPlan.COACH -> null
        }
    }
}

class EntitlementManager(
    initialSnapshot: EntitlementSnapshot = EntitlementSnapshot.Free
) {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)
    val snapshot: StateFlow<EntitlementSnapshot> = mutableSnapshot.asStateFlow()

    fun replaceFromStore(snapshot: EntitlementSnapshot) {
        mutableSnapshot.value = snapshot
    }

    fun resetToFree() {
        mutableSnapshot.value = EntitlementSnapshot.Free
    }
}
