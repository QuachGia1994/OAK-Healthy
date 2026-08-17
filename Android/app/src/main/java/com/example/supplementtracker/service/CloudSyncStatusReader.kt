package com.example.supplementtracker.service

import android.content.SharedPreferences

data class CloudSyncStatusSnapshot(
    val lastSyncEpochMs: Long,
    val lastAttemptEpochMs: Long,
    val phaseName: String,
    val conflictRetryCount: Int,
    val queuedMutationCount: Int,
    val nextRetryEpochMs: Long,
    val conflictRemoteWins: Int,
    val conflictLocalWins: Int,
    val conflictTieLocalWins: Int,
    val journalCount: Int,
    val bytesDownloaded: Long,
    val bytesUploaded: Long,
    val pullMs: Long,
    val mergeMs: Long,
    val pushMs: Long,
    val totalMs: Long,
    val lastError: String?
)

interface CloudSyncStatusSource {
    fun read(manifestId: String, clientId: String): CloudSyncStatusSnapshot
}

class CloudSyncStatusReader(
    private val prefs: SharedPreferences
) : CloudSyncStatusSource {
    override fun read(manifestId: String, clientId: String): CloudSyncStatusSnapshot {
        val id = manifestId.trim()
        return CloudSyncStatusSnapshot(
            lastSyncEpochMs = prefs.getLong("cloudSyncLastSyncEpochMs_$id", 0L),
            lastAttemptEpochMs = prefs.getLong("cloudSyncLastAttemptEpochMs_$id", 0L),
            phaseName = prefs.getString("cloudSyncPhase_$id", "").orEmpty(),
            conflictRetryCount = prefs.getInt("cloudSyncConflictRetryCount_$id", 0),
            queuedMutationCount = SyncMutationQueue(prefs).pending(clientId).size,
            nextRetryEpochMs = prefs.getLong("cloudSyncNextRetryEpochMs_$id", 0L),
            conflictRemoteWins = prefs.getInt("cloudSyncConflictRemoteWins_$id", 0),
            conflictLocalWins = prefs.getInt("cloudSyncConflictLocalWins_$id", 0),
            conflictTieLocalWins = prefs.getInt("cloudSyncConflictTieLocalWins_$id", 0),
            journalCount = SyncOperationJournalStore(prefs).count(id),
            bytesDownloaded = prefs.getLong("cloudSyncBytesDownloaded_$id", 0L),
            bytesUploaded = prefs.getLong("cloudSyncBytesUploaded_$id", 0L),
            pullMs = prefs.getLong("cloudSyncPullMs_$id", 0L),
            mergeMs = prefs.getLong("cloudSyncMergeMs_$id", 0L),
            pushMs = prefs.getLong("cloudSyncPushMs_$id", 0L),
            totalMs = prefs.getLong("cloudSyncTotalMs_$id", 0L),
            lastError = normalizedError(id)
        )
    }

    private fun normalizedError(id: String): String? {
        return prefs.getString("cloudSyncLastError_$id", null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
