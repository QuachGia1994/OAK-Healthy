package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRecoveryTest {
    @Test
    fun missingOsAlarmTriggersRebuild() {
        val decision = NotificationRecoveryPolicy.decide(baseInput().copy(missingPendingIntentCount = 1))

        assertEquals(NotificationRecoveryAction.REBUILD, decision.action)
        assertEquals(NotificationRecoveryReason.OS_SCHEDULE_MISMATCH, decision.reason)
    }

    @Test
    fun environmentChangeTriggersRebuild() {
        val decision = NotificationRecoveryPolicy.decide(baseInput().copy(environmentChanged = true))

        assertEquals(NotificationRecoveryAction.REBUILD, decision.action)
        assertEquals(NotificationRecoveryReason.ENVIRONMENT_CHANGED, decision.reason)
    }

    @Test
    fun zeroFutureScheduleOnlyRequestsReview() {
        val decision = NotificationRecoveryPolicy.decide(baseInput().copy(scheduledCount = 0))

        assertEquals(NotificationRecoveryAction.REVIEW, decision.action)
        assertEquals(NotificationRecoveryReason.NO_FUTURE_SCHEDULE, decision.reason)
    }

    @Test
    fun disabledNotificationsNeverAutoRepair() {
        val decision = NotificationRecoveryPolicy.decide(
            baseInput().copy(enabledByUser = false, missingPendingIntentCount = 2)
        )

        assertEquals(NotificationRecoveryAction.NONE, decision.action)
        assertEquals(NotificationRecoveryReason.INACTIVE, decision.reason)
    }

    private fun baseInput() = NotificationRecoveryInput(
        enabledByUser = true,
        permissionGranted = true,
        activeSupplementCount = 2,
        scheduledCount = 4,
        missingPendingIntentCount = 0,
        staleEntryCount = 0,
        environmentChanged = false
    )
}
