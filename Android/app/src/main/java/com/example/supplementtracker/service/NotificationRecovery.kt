package com.example.supplementtracker.service

enum class NotificationRecoveryAction {
    NONE,
    REVIEW,
    REBUILD
}

enum class NotificationRecoveryReason {
    INACTIVE,
    HEALTHY,
    NO_FUTURE_SCHEDULE,
    ENVIRONMENT_CHANGED,
    OS_SCHEDULE_MISMATCH
}

data class NotificationRecoveryInput(
    val enabledByUser: Boolean,
    val permissionGranted: Boolean,
    val activeSupplementCount: Int,
    val scheduledCount: Int,
    val missingPendingIntentCount: Int,
    val staleEntryCount: Int,
    val environmentChanged: Boolean
)

data class NotificationRecoveryDecision(
    val action: NotificationRecoveryAction,
    val reason: NotificationRecoveryReason
)

object NotificationRecoveryPolicy {
    fun decide(input: NotificationRecoveryInput): NotificationRecoveryDecision {
        if (!input.enabledByUser || !input.permissionGranted || input.activeSupplementCount == 0) {
            return NotificationRecoveryDecision(NotificationRecoveryAction.NONE, NotificationRecoveryReason.INACTIVE)
        }
        if (input.environmentChanged) {
            return NotificationRecoveryDecision(NotificationRecoveryAction.REBUILD, NotificationRecoveryReason.ENVIRONMENT_CHANGED)
        }
        if (input.missingPendingIntentCount > 0 || input.staleEntryCount > 0) {
            return NotificationRecoveryDecision(NotificationRecoveryAction.REBUILD, NotificationRecoveryReason.OS_SCHEDULE_MISMATCH)
        }
        if (input.scheduledCount == 0) {
            return NotificationRecoveryDecision(NotificationRecoveryAction.REVIEW, NotificationRecoveryReason.NO_FUTURE_SCHEDULE)
        }
        return NotificationRecoveryDecision(NotificationRecoveryAction.NONE, NotificationRecoveryReason.HEALTHY)
    }
}
