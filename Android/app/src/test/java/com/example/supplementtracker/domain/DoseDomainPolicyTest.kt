package com.example.supplementtracker.domain

import com.example.supplementtracker.domain.model.IntakeStatus
import com.example.supplementtracker.domain.util.DoseTimingPolicy
import com.example.supplementtracker.domain.util.HealthDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoseDomainPolicyTest {
    @Test
    fun timingPolicy_hasCanonicalThresholdsAndCompletion() {
        val scheduled = 1_000_000L
        assertTrue(DoseTimingPolicy.isDueSoon(scheduled, scheduled - DoseTimingPolicy.SOON_WINDOW_MS))
        assertFalse(DoseTimingPolicy.isMissed(scheduled, scheduled + DoseTimingPolicy.MISSED_AFTER_MS))
        assertTrue(DoseTimingPolicy.isMissed(scheduled, scheduled + DoseTimingPolicy.MISSED_AFTER_MS + 1))
        assertTrue(
            DoseTimingPolicy.isLateTaken(
                IntakeStatus.TAKEN.storageValue,
                scheduled,
                scheduled + DoseTimingPolicy.SOON_WINDOW_MS + 1
            )
        )
        assertEquals(75, DoseTimingPolicy.completionPercent(3, 1))
        assertNull(DoseTimingPolicy.completionPercent(0, 0))
    }

    @Test
    fun dayBoundary_preservesLocalDayAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8)
        val range = HealthDayBoundary.range(springForward, zone)
        assertEquals(23L * 60L * 60L * 1_000L, range.endExclusive - range.startInclusive)
        assertEquals(springForward, HealthDayBoundary.localDate(range.startInclusive, zone))
        assertEquals(springForward.plusDays(1), HealthDayBoundary.localDate(range.endExclusive, zone))
    }
}
