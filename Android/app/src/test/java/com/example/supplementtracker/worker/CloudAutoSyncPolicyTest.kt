package com.example.supplementtracker.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAutoSyncPolicyTest {
    @Test
    fun hostedManifestTakesPrecedenceOverLinkedManifest() {
        assertEquals(
            "hosted-id",
            CloudAutoSyncPolicy.selectManifestId(true, " hosted-id ", "linked-id")
        )
    }

    @Test
    fun linkedManifestIsUsedWhenHostedManifestIsBlank() {
        assertEquals(
            "linked-id",
            CloudAutoSyncPolicy.selectManifestId(true, " ", " linked-id ")
        )
    }

    @Test
    fun disabledOrMissingManifestSkipsSync() {
        assertNull(CloudAutoSyncPolicy.selectManifestId(false, "hosted-id", "linked-id"))
        assertNull(CloudAutoSyncPolicy.selectManifestId(true, " ", null))
    }

    @Test
    fun recentAttemptIsThrottledForTwentySeconds() {
        assertTrue(CloudAutoSyncPolicy.isThrottled(1_000L, 20_999L))
        assertFalse(CloudAutoSyncPolicy.isThrottled(1_000L, 21_000L))
        assertFalse(CloudAutoSyncPolicy.isThrottled(0L, 5_000L))
    }

    @Test
    fun failedSyncRetriesUnlessAutoSyncWasDisabledDuringRun() {
        assertEquals(
            CloudAutoSyncPolicy.Outcome.RETRY,
            CloudAutoSyncPolicy.outcome(syncSucceeded = false, autoSyncStillEnabled = true)
        )
        assertEquals(
            CloudAutoSyncPolicy.Outcome.SUCCESS,
            CloudAutoSyncPolicy.outcome(syncSucceeded = false, autoSyncStillEnabled = false)
        )
        assertEquals(
            CloudAutoSyncPolicy.Outcome.SUCCESS,
            CloudAutoSyncPolicy.outcome(syncSucceeded = true, autoSyncStillEnabled = true)
        )
    }
}
