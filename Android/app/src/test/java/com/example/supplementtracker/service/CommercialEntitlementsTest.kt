package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialEntitlementsTest {
    @Test
    fun freePlanAllowsOnlyCoreTrackingFeatures() {
        assertTrue(EntitlementPolicy.allows(CommercialPlan.FREE, CommercialFeature.BASIC_TRACKING))
        assertTrue(EntitlementPolicy.allows(CommercialPlan.FREE, CommercialFeature.REMINDERS))
        assertFalse(EntitlementPolicy.allows(CommercialPlan.FREE, CommercialFeature.ADVANCED_CYCLES))
        assertFalse(EntitlementPolicy.allows(CommercialPlan.FREE, CommercialFeature.MULTI_CLIENT))
    }

    @Test
    fun proPlanAllowsPremiumIndividualFeaturesButNotCoachFeatures() {
        assertTrue(EntitlementPolicy.allows(CommercialPlan.PRO, CommercialFeature.ADVANCED_CYCLES))
        assertTrue(EntitlementPolicy.allows(CommercialPlan.PRO, CommercialFeature.ENCRYPTED_CLOUD_SYNC))
        assertTrue(EntitlementPolicy.allows(CommercialPlan.PRO, CommercialFeature.DATA_EXPORT))
        assertFalse(EntitlementPolicy.allows(CommercialPlan.PRO, CommercialFeature.MULTI_CLIENT))
        assertFalse(EntitlementPolicy.allows(CommercialPlan.PRO, CommercialFeature.COACH_REPORTS))
    }

    @Test
    fun coachPlanAllowsEveryDefinedFeature() {
        CommercialFeature.entries.forEach { feature ->
            assertTrue(EntitlementPolicy.allows(CommercialPlan.COACH, feature))
        }
    }

    @Test
    fun clientLimitsMatchCommercialPositioning() {
        assertEquals(1, EntitlementPolicy.maxClients(CommercialPlan.FREE))
        assertEquals(1, EntitlementPolicy.maxClients(CommercialPlan.PRO))
        assertNull(EntitlementPolicy.maxClients(CommercialPlan.COACH))
    }

    @Test
    fun historyWindowsMatchPlanLimits() {
        assertEquals(7L, EntitlementPolicy.historyDays(CommercialPlan.FREE))
        assertEquals(90L, EntitlementPolicy.historyDays(CommercialPlan.PRO))
        assertEquals(365L, EntitlementPolicy.historyDays(CommercialPlan.COACH))
    }

    @Test
    fun productCatalogUsesUniqueStableIdentifiers() {
        val ids = CommercialProductCatalog.products.map { it.productId }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            setOf("oak_pro_monthly", "oak_pro_annual", "oak_coach_monthly", "oak_coach_annual"),
            ids.toSet()
        )
    }

    @Test
    fun resolverFailsClosedForUnknownOrMissingProducts() {
        assertEquals(EntitlementSnapshot.Free, CommercialEntitlementResolver.resolve(emptyList()))
        assertEquals(EntitlementSnapshot.Free, CommercialEntitlementResolver.resolve(listOf("unknown")))
    }

    @Test
    fun resolverChoosesHighestOwnedPlan() {
        val resolved = CommercialEntitlementResolver.resolve(
            listOf(CommercialProductCatalog.PRO_ANNUAL, CommercialProductCatalog.COACH_MONTHLY)
        )

        assertEquals(CommercialPlan.COACH, resolved.plan)
        assertEquals(CommercialProductCatalog.COACH_MONTHLY, resolved.activeProductId)
    }

    @Test
    fun entitlementManagerFailsClosedToFree() {
        val manager = EntitlementManager()

        assertEquals(CommercialPlan.FREE, manager.snapshot.value.plan)
        manager.replaceFromStore(EntitlementSnapshot(CommercialPlan.PRO, CommercialProductCatalog.PRO_ANNUAL))
        assertEquals(CommercialPlan.PRO, manager.snapshot.value.plan)
        manager.resetToFree()
        assertEquals(EntitlementSnapshot.Free, manager.snapshot.value)
        assertFalse(manager.canUse(CommercialFeature.ENCRYPTED_CLOUD_SYNC))
        assertEquals(1, manager.maxClients())
        assertEquals(7L, manager.historyDays())
    }
}
