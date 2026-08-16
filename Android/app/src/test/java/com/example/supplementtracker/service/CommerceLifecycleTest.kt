package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Test

class CommerceLifecycleTest {
    @Test
    fun activeVerifiedEventAppliesHighestPlanOnce() {
        var snapshot = EntitlementSnapshot.Free
        val processor = processor(
            CommerceVerificationResult.Verified(
                setOf(CommercialProductCatalog.PRO_ANNUAL, CommercialProductCatalog.COACH_MONTHLY)
            )
        ) { snapshot = it }
        val event = event("active-1", CommerceLifecycleState.ACTIVE)

        assertEquals(CommerceProcessResult.APPLIED, processor.process(event))
        assertEquals(CommercialPlan.COACH, snapshot.plan)
        assertEquals(CommerceProcessResult.DUPLICATE, processor.process(event))
    }

    @Test
    fun gracePeriodRetainsVerifiedPaidAccess() {
        var snapshot = EntitlementSnapshot.Free
        val processor = processor(
            CommerceVerificationResult.Verified(setOf(CommercialProductCatalog.PRO_MONTHLY))
        ) { snapshot = it }

        processor.process(event("grace-1", CommerceLifecycleState.GRACE_PERIOD))

        assertEquals(CommercialPlan.PRO, snapshot.plan)
    }

    @Test
    fun holdRefundAndUnverifiedFailClosed() {
        val states = listOf(
            CommerceLifecycleState.ON_HOLD,
            CommerceLifecycleState.REFUNDED,
            CommerceLifecycleState.REVOKED
        )
        states.forEachIndexed { index, state ->
            var snapshot = EntitlementSnapshot(CommercialPlan.COACH, CommercialProductCatalog.COACH_ANNUAL)
            val processor = processor(
                CommerceVerificationResult.Verified(setOf(CommercialProductCatalog.COACH_ANNUAL))
            ) { snapshot = it }
            processor.process(event("closed-$index", state))
            assertEquals(EntitlementSnapshot.Free, snapshot)
        }

        var unverifiedSnapshot = EntitlementSnapshot(CommercialPlan.PRO, CommercialProductCatalog.PRO_MONTHLY)
        val unverified = processor(CommerceVerificationResult.Unverified) { unverifiedSnapshot = it }
        assertEquals(
            CommerceProcessResult.REJECTED,
            unverified.process(event("unverified-1", CommerceLifecycleState.ACTIVE))
        )
        assertEquals(EntitlementSnapshot.Free, unverifiedSnapshot)
    }

    @Test
    fun unavailableVerificationDefersWithoutConsumingReplayId() {
        var verification: CommerceVerificationResult = CommerceVerificationResult.Unavailable
        var snapshot = EntitlementSnapshot.Free
        val processor = CommerceLifecycleProcessor(
            verifier = CommerceEntitlementVerifier { verification },
            applySnapshot = { snapshot = it }
        )
        val event = event("retryable-1", CommerceLifecycleState.ACTIVE)

        assertEquals(CommerceProcessResult.DEFERRED, processor.process(event))
        verification = CommerceVerificationResult.Verified(setOf(CommercialProductCatalog.PRO_ANNUAL))
        assertEquals(CommerceProcessResult.APPLIED, processor.process(event))
        assertEquals(CommercialPlan.PRO, snapshot.plan)
    }

    @Test
    fun unknownVerifiedProductNeverGrantsPaidAccess() {
        var snapshot = EntitlementSnapshot(CommercialPlan.COACH, CommercialProductCatalog.COACH_MONTHLY)
        val processor = processor(
            CommerceVerificationResult.Verified(setOf("unknown_product"))
        ) { snapshot = it }

        assertEquals(
            CommerceProcessResult.REJECTED,
            processor.process(event("unknown-1", CommerceLifecycleState.ACTIVE))
        )
        assertEquals(EntitlementSnapshot.Free, snapshot)
    }

    private fun processor(
        verification: CommerceVerificationResult,
        applySnapshot: (EntitlementSnapshot) -> Unit
    ): CommerceLifecycleProcessor {
        return CommerceLifecycleProcessor(
            verifier = CommerceEntitlementVerifier { verification },
            applySnapshot = applySnapshot
        )
    }

    private fun event(id: String, state: CommerceLifecycleState): CommerceLifecycleEvent {
        return CommerceLifecycleEvent(
            eventId = id,
            source = CommerceSource.SANDBOX_FIXTURE,
            productIds = setOf(CommercialProductCatalog.PRO_ANNUAL),
            state = state,
            observedAtEpochMs = 1_700_000_000_000L
        )
    }
}
