package com.example.supplementtracker.service

enum class NotificationReliabilityLevel {
    HEALTHY,
    DEGRADED,
    NEEDS_REPAIR,
    INACTIVE
}

data class NotificationReliabilityInput(
    val permissionGranted: Boolean,
    val enabledByUser: Boolean,
    val hasActiveClient: Boolean,
    val activeSupplementCount: Int,
    val exactAlarmAvailable: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val scheduledCount: Int,
    val missingPendingIntentCount: Int,
    val staleEntryCount: Int
)

data class NotificationReliabilityReport(
    val level: NotificationReliabilityLevel,
    val missingPendingIntentCount: Int,
    val staleEntryCount: Int,
    val shouldOfferRepair: Boolean
)

object NotificationReliabilityEvaluator {
    fun evaluate(input: NotificationReliabilityInput): NotificationReliabilityReport {
        val inactive = !input.enabledByUser || !input.permissionGranted || !input.hasActiveClient
        if (inactive || input.activeSupplementCount == 0) {
            return NotificationReliabilityReport(
                level = NotificationReliabilityLevel.INACTIVE,
                missingPendingIntentCount = input.missingPendingIntentCount,
                staleEntryCount = input.staleEntryCount,
                shouldOfferRepair = false
            )
        }
        val hasBrokenSchedule = input.missingPendingIntentCount > 0 || input.staleEntryCount > 0
        val noFutureSchedule = input.scheduledCount == 0
        val level = when {
            hasBrokenSchedule -> NotificationReliabilityLevel.NEEDS_REPAIR
            noFutureSchedule || !input.exactAlarmAvailable || !input.batteryOptimizationIgnored -> {
                NotificationReliabilityLevel.DEGRADED
            }
            else -> NotificationReliabilityLevel.HEALTHY
        }
        return NotificationReliabilityReport(
            level = level,
            missingPendingIntentCount = input.missingPendingIntentCount,
            staleEntryCount = input.staleEntryCount,
            shouldOfferRepair = level == NotificationReliabilityLevel.NEEDS_REPAIR
        )
    }
}

data class NotificationAlarmAudit(
    val scheduledCount: Int,
    val missingPendingIntentCount: Int,
    val staleEntryCount: Int
)
