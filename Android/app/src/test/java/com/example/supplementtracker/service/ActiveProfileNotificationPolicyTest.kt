package com.example.supplementtracker.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ActiveProfileNotificationPolicyTest {
    private val activeClientId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherClientId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun matchingClient_isAllowed() {
        assertTrue(ActiveProfileNotificationPolicy.allows(activeClientId, activeClientId))
        assertTrue(ActiveProfileNotificationPolicy.allows(activeClientId, activeClientId.toString()))
    }

    @Test
    fun missingOrDifferentClient_isRejected() {
        assertFalse(ActiveProfileNotificationPolicy.allows(null, activeClientId))
        assertFalse(ActiveProfileNotificationPolicy.allows(activeClientId, otherClientId))
        assertFalse(ActiveProfileNotificationPolicy.allows(activeClientId, otherClientId.toString()))
        assertFalse(ActiveProfileNotificationPolicy.allows(activeClientId, "invalid"))
    }
}
