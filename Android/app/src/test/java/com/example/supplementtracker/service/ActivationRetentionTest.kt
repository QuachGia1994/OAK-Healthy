package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationRetentionTest {
    @Test
    fun firstValueRequiresClientRoutineAndFirstActionOnly() {
        val progress = ActivationProgress(
            setOf(
                ActivationMilestone.CLIENT_READY,
                ActivationMilestone.ROUTINE_READY,
                ActivationMilestone.FIRST_ACTION
            )
        )

        assertTrue(progress.firstValueReached)
        assertEquals(3, progress.coreCompletedCount)
        assertFalse(ActivationMilestone.REMINDER_READY in progress.completed)
    }

    @Test
    fun nextCoreMilestoneIsDeterministic() {
        val progress = ActivationProgress(setOf(ActivationMilestone.CLIENT_READY))

        assertEquals(ActivationMilestone.ROUTINE_READY, progress.nextCoreMilestone)
        assertEquals(1, progress.coreCompletedCount)
    }

    @Test
    fun activationTelemetryDropsSensitiveFields() {
        val sanitized = DiagnosticsPrivacyPolicy.sanitize(
            "activation_milestone",
            mapOf(
                "milestone" to "FIRST_ACTION",
                "state" to "REACHED",
                "client_id" to "secret-client",
                "supplement" to "Creatine",
                "dose" to "5g",
                "note" to "private",
                "product_id" to "oak_pro_monthly",
                "plan" to "PRO"
            )
        )

        assertEquals("first_action", sanitized?.second?.get("milestone"))
        assertEquals("reached", sanitized?.second?.get("state"))
        assertEquals(setOf("milestone", "state"), sanitized?.second?.keys)
    }
}
