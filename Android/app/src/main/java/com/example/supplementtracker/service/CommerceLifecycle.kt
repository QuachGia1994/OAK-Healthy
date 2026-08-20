package com.example.supplementtracker.service

enum class CommerceSource {
    APP_STORE,
    PLAY_STORE,
    SANDBOX_FIXTURE
}

enum class CommerceLifecycleState {
    ACTIVE,
    GRACE_PERIOD,
    ON_HOLD,
    EXPIRED,
    REVOKED,
    REFUNDED,
    UNVERIFIED
}

data class CommerceLifecycleEvent(
    val eventId: String,
    val source: CommerceSource,
    val productIds: Set<String>,
    val state: CommerceLifecycleState,
    val observedAtEpochMs: Long
)

sealed interface CommerceVerificationResult {
    data class Verified(val productIds: Set<String>) : CommerceVerificationResult
    data object Unverified : CommerceVerificationResult
    data object Unavailable : CommerceVerificationResult
}

fun interface CommerceEntitlementVerifier {
    fun verify(event: CommerceLifecycleEvent): CommerceVerificationResult
}

enum class CommerceProcessResult {
    APPLIED,
    REJECTED,
    DEFERRED,
    DUPLICATE
}

class CommerceReplayLedger(private val capacity: Int = 256) {
    private val eventIds = LinkedHashSet<String>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun contains(eventId: String): Boolean = eventIds.contains(eventId)

    fun record(eventId: String) {
        if (eventId.isBlank() || !eventIds.add(eventId)) return
        while (eventIds.size > capacity) {
            eventIds.remove(eventIds.first())
        }
    }
}

class CommerceLifecycleProcessor(
    private val verifier: CommerceEntitlementVerifier,
    private val replayLedger: CommerceReplayLedger = CommerceReplayLedger(),
    private val applySnapshot: (EntitlementSnapshot) -> Unit
) {
    fun process(event: CommerceLifecycleEvent): CommerceProcessResult {
        if (event.eventId.isBlank()) return CommerceProcessResult.REJECTED
        if (replayLedger.contains(event.eventId)) return CommerceProcessResult.DUPLICATE
        return when (val verification = verifier.verify(event)) {
            CommerceVerificationResult.Unavailable -> CommerceProcessResult.DEFERRED
            CommerceVerificationResult.Unverified -> reject(event)
            is CommerceVerificationResult.Verified -> applyVerified(event, verification.productIds)
        }
    }

    private fun reject(event: CommerceLifecycleEvent): CommerceProcessResult {
        replayLedger.record(event.eventId)
        applySnapshot(EntitlementSnapshot.Free)
        return CommerceProcessResult.REJECTED
    }

    private fun applyVerified(
        event: CommerceLifecycleEvent,
        verifiedProductIds: Set<String>
    ): CommerceProcessResult {
        replayLedger.record(event.eventId)
        val snapshot = when (event.state) {
            CommerceLifecycleState.ACTIVE,
            CommerceLifecycleState.GRACE_PERIOD -> CommercialEntitlementResolver.resolve(verifiedProductIds)
            CommerceLifecycleState.ON_HOLD,
            CommerceLifecycleState.EXPIRED,
            CommerceLifecycleState.REVOKED,
            CommerceLifecycleState.REFUNDED,
            CommerceLifecycleState.UNVERIFIED -> EntitlementSnapshot.Free
        }
        applySnapshot(snapshot)
        return if (snapshot == EntitlementSnapshot.Free && verifiedProductIds.isNotEmpty()) {
            CommerceProcessResult.REJECTED
        } else {
            CommerceProcessResult.APPLIED
        }
    }
}
