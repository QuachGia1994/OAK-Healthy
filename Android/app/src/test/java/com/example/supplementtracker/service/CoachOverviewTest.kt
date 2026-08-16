package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class CoachOverviewTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 16)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun summaryUsesOnlyRecentSevenDayWindow() {
        val client = CoachClientSnapshot(UUID.randomUUID(), "Alex")
        val records = listOf(
            record(today, "Taken"),
            record(today.minusDays(1), "Taken"),
            record(today.minusDays(2), "Skipped"),
            record(today.minusDays(8), "Skipped")
        )

        val result = CoachOverviewBuilder.build(
            listOf(client),
            mapOf(client.id to records),
            now,
            zone
        )

        val summary = result.clients.single()
        assertEquals(2, summary.takenCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(66, summary.completionPercent)
        assertTrue(summary.needsCheckIn)
    }

    @Test
    fun lowVolumeDoesNotTriggerCheckIn() {
        val client = CoachClientSnapshot(UUID.randomUUID(), "Casey")
        val records = listOf(record(today, "Skipped"), record(today.minusDays(1), "Taken"))

        val result = CoachOverviewBuilder.build(
            listOf(client),
            mapOf(client.id to records),
            now,
            zone
        )

        assertFalse(result.clients.single().needsCheckIn)
        assertEquals(1, result.activeClients)
        assertEquals(0, result.needsCheckInCount)
    }

    @Test
    fun clientsNeedingCheckInSortFirst() {
        val good = CoachClientSnapshot(UUID.randomUUID(), "Good")
        val checkIn = CoachClientSnapshot(UUID.randomUUID(), "Check")
        val result = CoachOverviewBuilder.build(
            listOf(good, checkIn),
            mapOf(
                good.id to listOf(record(today, "Taken"), record(today.minusDays(1), "Taken"), record(today.minusDays(2), "Taken")),
                checkIn.id to listOf(record(today, "Skipped"), record(today.minusDays(1), "Skipped"), record(today.minusDays(2), "Taken"))
            ),
            now,
            zone
        )

        assertEquals(checkIn.id, result.clients.first().clientId)
        assertEquals(2, result.totalClients)
    }

    private fun record(day: LocalDate, status: String): CoachRecordSnapshot {
        return CoachRecordSnapshot(
            epochMs = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            status = status
        )
    }
}
