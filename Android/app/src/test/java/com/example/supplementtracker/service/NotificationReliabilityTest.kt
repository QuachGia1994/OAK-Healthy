package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReliabilityTest {
    @Test
    fun healthyRequiresFutureSchedulesAndNoMissingAlarms() {
        val report = NotificationReliabilityEvaluator.evaluate(baseInput())

        assertEquals(NotificationReliabilityLevel.HEALTHY, report.level)
        assertFalse(report.shouldOfferRepair)
    }

    @Test
    fun missingPendingIntentRequiresRepair() {
        val report = NotificationReliabilityEvaluator.evaluate(
            baseInput().copy(missingPendingIntentCount = 2)
        )

        assertEquals(NotificationReliabilityLevel.NEEDS_REPAIR, report.level)
        assertTrue(report.shouldOfferRepair)
        assertEquals(2, report.missingPendingIntentCount)
    }

    @Test
    fun staleRegistryEntryRequiresRepairAfterConsumedAlarmCleanup() {
        val report = NotificationReliabilityEvaluator.evaluate(
            baseInput().copy(staleEntryCount = 1)
        )

        assertEquals(NotificationReliabilityLevel.NEEDS_REPAIR, report.level)
        assertTrue(report.shouldOfferRepair)
    }

    @Test
    fun zeroFutureScheduleIsDegradedWithoutAssumingCorruption() {
        val report = NotificationReliabilityEvaluator.evaluate(baseInput().copy(scheduledCount = 0))

        assertEquals(NotificationReliabilityLevel.DEGRADED, report.level)
        assertFalse(report.shouldOfferRepair)
    }

    @Test
    fun platformLimitationsAreDegradedNotBroken() {
        val report = NotificationReliabilityEvaluator.evaluate(
            baseInput().copy(exactAlarmAvailable = false)
        )

        assertEquals(NotificationReliabilityLevel.DEGRADED, report.level)
        assertFalse(report.shouldOfferRepair)
    }

    @Test
    fun disabledNotificationsAreInactive() {
        val report = NotificationReliabilityEvaluator.evaluate(
            baseInput().copy(enabledByUser = false, scheduledCount = 0)
        )

        assertEquals(NotificationReliabilityLevel.INACTIVE, report.level)
        assertFalse(report.shouldOfferRepair)
    }

    private fun baseInput() = NotificationReliabilityInput(
        permissionGranted = true,
        enabledByUser = true,
        hasActiveClient = true,
        activeSupplementCount = 2,
        exactAlarmAvailable = true,
        batteryOptimizationIgnored = true,
        scheduledCount = 4,
        missingPendingIntentCount = 0,
        staleEntryCount = 0
    )
}
