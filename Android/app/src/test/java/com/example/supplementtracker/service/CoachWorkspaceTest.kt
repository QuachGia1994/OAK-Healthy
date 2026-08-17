package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class CoachWorkspaceTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 17)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun detailComparesClientWithOwnPreviousWindow() {
        val client = CoachClientSnapshot(UUID.randomUUID(), "Alex")
        val records = listOf(
            record(today, "Taken"), record(today.minusDays(1), "Taken"), record(today.minusDays(2), "Skipped"),
            record(today.minusDays(7), "Taken"), record(today.minusDays(8), "Skipped"), record(today.minusDays(9), "Skipped")
        )
        val detail = CoachWorkspaceBuilder.buildDetail(client, records, now, zone, 7)
        assertEquals(66, detail.current.completionPercent)
        assertEquals(33, detail.previous.completionPercent)
        assertEquals(33, detail.completionDeltaPoints)
        assertEquals(3, detail.current.activeDays)
    }

    @Test
    fun missingPreviousWindowProducesNoDelta() {
        val client = CoachClientSnapshot(UUID.randomUUID(), "Casey")
        val detail = CoachWorkspaceBuilder.buildDetail(client, listOf(record(today, "Taken")), now, zone, 30)
        assertNull(detail.previous.completionPercent)
        assertNull(detail.completionDeltaPoints)
    }

    @Test
    fun checkInPolicyBoundsNoteAndHistory() {
        val longNote = "x".repeat(700)
        var entries = emptyList<CoachCheckInEntry>()
        repeat(25) { index ->
            entries = CoachCheckInPolicy.add(entries, CoachCheckInEntry(index.toLong(), CoachRoutineFeeling.OKAY, longNote))
        }
        assertEquals(20, entries.size)
        assertEquals(500, entries.first().note.length)
    }

    private fun record(day: LocalDate, status: String) = CoachRecordSnapshot(
        epochMs = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        status = status
    )
}
