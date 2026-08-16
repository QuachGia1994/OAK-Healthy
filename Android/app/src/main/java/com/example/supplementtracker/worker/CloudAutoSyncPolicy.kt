package com.example.supplementtracker.worker

internal object CloudAutoSyncPolicy {
    private const val MIN_ATTEMPT_INTERVAL_MS = 20_000L

    enum class Outcome { SUCCESS, RETRY }

    fun selectManifestId(enabled: Boolean, hosted: String?, linked: String?): String? {
        if (!enabled) return null
        val hostedId = hosted.orEmpty().trim()
        val linkedId = linked.orEmpty().trim()
        return hostedId.ifEmpty { linkedId }.takeIf { it.isNotEmpty() }
    }

    fun isThrottled(lastAttemptEpochMs: Long, nowEpochMs: Long): Boolean {
        if (lastAttemptEpochMs <= 0L) return false
        return nowEpochMs - lastAttemptEpochMs < MIN_ATTEMPT_INTERVAL_MS
    }

    fun outcome(syncSucceeded: Boolean, autoSyncStillEnabled: Boolean): Outcome {
        return if (syncSucceeded || !autoSyncStillEnabled) Outcome.SUCCESS else Outcome.RETRY
    }
}
