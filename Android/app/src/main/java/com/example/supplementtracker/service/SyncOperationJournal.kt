package com.example.supplementtracker.service

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class SyncJournalEvent {
    START,
    CONFLICT,
    BACKOFF,
    SUCCESS,
    FAILURE
}

data class SyncConflictPreview(
    val remoteWins: Int = 0,
    val localWins: Int = 0,
    val tieLocalWins: Int = 0
) {
    val materialConflictCount: Int get() = remoteWins + localWins + tieLocalWins
}

enum class SyncConflictDecision {
    REMOTE_WINS,
    LOCAL_WINS,
    TIE_LOCAL_WINS,
    NO_CONFLICT
}

object SyncConflictPolicy {
    fun decide(localExists: Boolean, localTs: Long, remoteTs: Long, differs: Boolean): SyncConflictDecision {
        if (!localExists || !differs) return SyncConflictDecision.NO_CONFLICT
        if (remoteTs > localTs) return SyncConflictDecision.REMOTE_WINS
        if (remoteTs < localTs) return SyncConflictDecision.LOCAL_WINS
        return SyncConflictDecision.TIE_LOCAL_WINS
    }

    fun remoteMayApply(localTs: Long, remoteTs: Long): Boolean = remoteTs > localTs
}

data class SyncRetryState(
    val failureCount: Int,
    val nextRetryEpochMs: Long
)

object SyncBackoffPolicy {
    private const val BASE_DELAY_MS = 15_000L
    private const val MAX_DELAY_MS = 10 * 60_000L

    fun nextState(previousFailures: Int, nowEpochMs: Long): SyncRetryState {
        val failures = (previousFailures + 1).coerceAtMost(16)
        return SyncRetryState(failures, nowEpochMs + delayMs(failures))
    }

    fun delayMs(failureCount: Int): Long {
        if (failureCount <= 0) return 0L
        val exponent = (failureCount - 1).coerceAtMost(6)
        return (BASE_DELAY_MS * (1L shl exponent)).coerceAtMost(MAX_DELAY_MS)
    }

    fun canAttempt(state: SyncRetryState, nowEpochMs: Long): Boolean =
        state.nextRetryEpochMs <= 0L || nowEpochMs >= state.nextRetryEpochMs
}

class SyncRetryStore(private val prefs: SharedPreferences) {
    fun state(manifestId: String): SyncRetryState = SyncRetryState(
        prefs.getInt(failureKey(manifestId), 0),
        prefs.getLong(nextRetryKey(manifestId), 0L)
    )

    fun recordFailure(manifestId: String, nowEpochMs: Long = System.currentTimeMillis()): SyncRetryState {
        val next = SyncBackoffPolicy.nextState(state(manifestId).failureCount, nowEpochMs)
        prefs.edit().putInt(failureKey(manifestId), next.failureCount)
            .putLong(nextRetryKey(manifestId), next.nextRetryEpochMs).apply()
        return next
    }

    fun clear(manifestId: String) {
        prefs.edit().remove(failureKey(manifestId)).remove(nextRetryKey(manifestId)).apply()
    }

    private fun failureKey(id: String): String = "cloudSyncRetryFailureCount_${id.trim()}"
    private fun nextRetryKey(id: String): String = "cloudSyncNextRetryEpochMs_${id.trim()}"
}

data class SyncJournalEntry(
    val epochMs: Long,
    val event: SyncJournalEvent,
    val stackDirty: Boolean = false,
    val historyDirty: Boolean = false,
    val retryCount: Int = 0,
    val nextRetryEpochMs: Long = 0L,
    val remoteWins: Int = 0,
    val localWins: Int = 0,
    val tieLocalWins: Int = 0
)

class SyncOperationJournalStore(private val prefs: SharedPreferences) {
    fun append(manifestId: String, entry: SyncJournalEntry) {
        val id = manifestId.trim()
        if (id.isEmpty()) return
        val array = readArray(id)
        array.put(encode(entry))
        prefs.edit().putString(key(id), trim(array).toString()).apply()
    }

    fun count(manifestId: String): Int = readArray(manifestId.trim()).length()

    private fun readArray(id: String): JSONArray {
        val raw = prefs.getString(key(id), null)
        return runCatching { if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw) }
            .getOrElse { JSONArray() }
    }

    private fun encode(entry: SyncJournalEntry): JSONObject = JSONObject()
        .put("ts", entry.epochMs)
        .put("event", entry.event.name)
        .put("stackDirty", entry.stackDirty)
        .put("historyDirty", entry.historyDirty)
        .put("retryCount", entry.retryCount)
        .put("nextRetryEpochMs", entry.nextRetryEpochMs)
        .put("remoteWins", entry.remoteWins)
        .put("localWins", entry.localWins)
        .put("tieLocalWins", entry.tieLocalWins)

    private fun trim(array: JSONArray): JSONArray {
        val result = JSONArray()
        val start = (array.length() - 40).coerceAtLeast(0)
        for (index in start until array.length()) result.put(array.getJSONObject(index))
        return result
    }

    private fun key(id: String): String = "cloudSyncJournal_$id"
}
