package com.example.supplementtracker.service

enum class SyncHealthLevel {
    UNLINKED,
    IDLE,
    HEALTHY,
    PENDING,
    NEEDS_KEY,
    RETRYABLE_ERROR,
    ACTION_REQUIRED
}

enum class SyncRecoveryAction {
    NONE,
    SYNC_NOW,
    IMPORT_KEY,
    CHECK_LINK
}

data class SyncHealthInput(
    val hasLink: Boolean,
    val autoSyncEnabled: Boolean,
    val hasPendingChanges: Boolean,
    val lastSyncEpochMs: Long,
    val lastAttemptEpochMs: Long,
    val lastError: String?,
    val encryptionEnabled: Boolean
)

data class SyncHealthReport(
    val level: SyncHealthLevel,
    val action: SyncRecoveryAction
)

object SyncHealthEvaluator {
    fun evaluate(input: SyncHealthInput): SyncHealthReport {
        if (!input.hasLink) return SyncHealthReport(SyncHealthLevel.UNLINKED, SyncRecoveryAction.NONE)
        val error = input.lastError.orEmpty().trim()
        if (error.isNotEmpty()) return errorReport(error, input.encryptionEnabled)
        if (input.hasPendingChanges) return SyncHealthReport(SyncHealthLevel.PENDING, SyncRecoveryAction.SYNC_NOW)
        if (input.lastSyncEpochMs > 0L) return SyncHealthReport(SyncHealthLevel.HEALTHY, SyncRecoveryAction.NONE)
        val action = if (input.autoSyncEnabled || input.lastAttemptEpochMs > 0L) SyncRecoveryAction.SYNC_NOW else SyncRecoveryAction.NONE
        return SyncHealthReport(SyncHealthLevel.IDLE, action)
    }

    private fun errorReport(error: String, encryptionEnabled: Boolean): SyncHealthReport {
        val normalized = error.lowercase()
        val keyFailure = encryptionEnabled && (
            normalized.contains("missing cloud sync key") || normalized.contains("decrypt failed") ||
                normalized.contains("missing key") || normalized.contains("giải mã")
            )
        if (keyFailure) return SyncHealthReport(SyncHealthLevel.NEEDS_KEY, SyncRecoveryAction.IMPORT_KEY)
        if (isTransient(normalized)) return SyncHealthReport(SyncHealthLevel.RETRYABLE_ERROR, SyncRecoveryAction.SYNC_NOW)
        return SyncHealthReport(SyncHealthLevel.ACTION_REQUIRED, SyncRecoveryAction.CHECK_LINK)
    }

    private fun isTransient(error: String): Boolean = listOf(
        "522",
        "timed out",
        "timeout",
        "no internet",
        "không có internet",
        "network",
        "lỗi mạng",
        "không thể kết nối"
    ).any(error::contains)
}
