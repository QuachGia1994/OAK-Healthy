package com.example.supplementtracker.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Converts date-only health semantics to deterministic half-open epoch ranges. */
object HealthDayBoundary {
    data class EpochRange(val startInclusive: Long, val endExclusive: Long)

    fun localDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    fun range(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): EpochRange {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return EpochRange(start, end)
    }

    fun rangeFor(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): EpochRange =
        range(localDate(epochMs, zoneId), zoneId)
}
