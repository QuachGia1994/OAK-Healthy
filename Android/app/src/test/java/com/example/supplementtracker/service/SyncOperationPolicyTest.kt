package com.example.supplementtracker.service

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOperationPolicyTest {
    @Test
    fun staleRemoteDeletionCannotEraseNewerLocalEdit() {
        val decision = SyncConflictPolicy.decide(
            localExists = true,
            localTs = 2_000L,
            remoteTs = 1_500L,
            differs = true
        )

        assertEquals(SyncConflictDecision.LOCAL_WINS, decision)
        assertFalse(SyncConflictPolicy.remoteMayApply(2_000L, 1_500L))
    }

    @Test
    fun equalTimestampConflictDeterministicallyKeepsLocal() {
        assertEquals(
            SyncConflictDecision.TIE_LOCAL_WINS,
            SyncConflictPolicy.decide(true, 2_000L, 2_000L, differs = true)
        )
    }

    @Test
    fun newerRemoteMayApply() {
        assertTrue(SyncConflictPolicy.remoteMayApply(1_500L, 2_000L))
        assertEquals(
            SyncConflictDecision.REMOTE_WINS,
            SyncConflictPolicy.decide(true, 1_500L, 2_000L, differs = true)
        )
    }

    @Test
    fun backoffGrowsAndCaps() {
        assertEquals(15_000L, SyncBackoffPolicy.delayMs(1))
        assertEquals(30_000L, SyncBackoffPolicy.delayMs(2))
        assertEquals(10 * 60_000L, SyncBackoffPolicy.delayMs(16))
    }

    @Test
    fun mutationQueuedAfterSyncStartSurvivesSuccessClear() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("sync-queue-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val queue = SyncMutationQueue(prefs)
        val clientId = UUID.randomUUID().toString()
        queue.markDirty(clientId, SyncMutationPart.STACK, 100L)
        queue.markDirty(clientId, SyncMutationPart.HISTORY, 300L)

        queue.clearSynced(clientId, SyncMutationPart.entries.toSet(), 200L)

        val pending = queue.pending(clientId)
        assertEquals(listOf(SyncMutationPart.HISTORY), pending.map { it.part })
        assertEquals(300L, pending.single().enqueuedAtEpochMs)
    }
}
