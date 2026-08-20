package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncHealthTest {
    @Test
    fun unlinkedIsNeutral() {
        val report = SyncHealthEvaluator.evaluate(baseInput().copy(hasLink = false))
        assertEquals(SyncHealthLevel.UNLINKED, report.level)
        assertEquals(SyncRecoveryAction.NONE, report.action)
    }

    @Test
    fun pendingLocalChangesOfferSyncNow() {
        val report = SyncHealthEvaluator.evaluate(baseInput().copy(hasPendingChanges = true))
        assertEquals(SyncHealthLevel.PENDING, report.level)
        assertEquals(SyncRecoveryAction.SYNC_NOW, report.action)
    }

    @Test
    fun missingKeyOffersImportKeyWithoutTouchingData() {
        val report = SyncHealthEvaluator.evaluate(
            baseInput().copy(lastError = "Missing cloud sync key", encryptionEnabled = true)
        )
        assertEquals(SyncHealthLevel.NEEDS_KEY, report.level)
        assertEquals(SyncRecoveryAction.IMPORT_KEY, report.action)
    }

    @Test
    fun networkFailureIsRetryable() {
        val report = SyncHealthEvaluator.evaluate(baseInput().copy(lastError = "Network timeout"))
        assertEquals(SyncHealthLevel.RETRYABLE_ERROR, report.level)
        assertEquals(SyncRecoveryAction.SYNC_NOW, report.action)
    }

    @Test
    fun successfulSyncIsHealthy() {
        val report = SyncHealthEvaluator.evaluate(baseInput())
        assertEquals(SyncHealthLevel.HEALTHY, report.level)
        assertEquals(SyncRecoveryAction.NONE, report.action)
    }

    private fun baseInput() = SyncHealthInput(
        hasLink = true,
        autoSyncEnabled = true,
        hasPendingChanges = false,
        lastSyncEpochMs = 1_000L,
        lastAttemptEpochMs = 1_000L,
        lastError = null,
        encryptionEnabled = true
    )
}
