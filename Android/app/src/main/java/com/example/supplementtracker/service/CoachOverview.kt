package com.example.supplementtracker.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CoachClientSnapshot(
    val id: UUID,
    val name: String
)

data class CoachRecordSnapshot(
    val epochMs: Long,
    val status: String
)

data class CoachClientSummary(
    val clientId: UUID,
    val name: String,
    val takenCount: Int,
    val skippedCount: Int,
    val completionPercent: Int?,
    val lastActivityEpochMs: Long?,
    val needsCheckIn: Boolean
)

data class CoachTrendPoint(
    val bucketStartEpochMs: Long,
    val takenCount: Int,
    val skippedCount: Int,
    val completionPercent: Int?
)

data class CoachOverviewSummary(
    val totalClients: Int,
    val activeClients: Int,
    val needsCheckInCount: Int,
    val clients: List<CoachClientSummary>,
    val windowDays: Int = 7,
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val overallCompletionPercent: Int? = null,
    val trend: List<CoachTrendPoint> = emptyList()
)

object CoachOverviewBuilder {
    fun build(
        clients: List<CoachClientSnapshot>,
        recordsByClient: Map<UUID, List<CoachRecordSnapshot>>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        windowDays: Int = 7
    ): CoachOverviewSummary {
        val safeWindowDays = normalizeWindowDays(windowDays)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val start = today.minusDays((safeWindowDays - 1).toLong())
        val summaries = clients.map { client ->
            buildClientSummary(client, recordsByClient[client.id].orEmpty(), start, today, zoneId)
        }.sortedWith(summaryComparator)
        val allWindowRecords = windowRecords(recordsByClient.values.flatten(), start, today, zoneId)
        return makeOverview(summaries, allWindowRecords, start, safeWindowDays, zoneId)
    }

    private fun makeOverview(
        summaries: List<CoachClientSummary>,
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        windowDays: Int,
        zoneId: ZoneId
    ): CoachOverviewSummary {
        val taken = records.count { it.status == "Taken" }
        val skipped = records.count { it.status == "Skipped" }
        val completion = completionPercent(taken, skipped)
        return CoachOverviewSummary(
            totalClients = summaries.size,
            activeClients = summaries.count { it.takenCount + it.skippedCount > 0 },
            needsCheckInCount = summaries.count { it.needsCheckIn },
            clients = summaries,
            windowDays = windowDays,
            takenCount = taken,
            skippedCount = skipped,
            overallCompletionPercent = completion,
            trend = buildTrend(records, start, windowDays, zoneId)
        )
    }

    private fun buildClientSummary(
        client: CoachClientSnapshot,
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        today: LocalDate,
        zoneId: ZoneId
    ): CoachClientSummary {
        val window = windowRecords(records, start, today, zoneId)
        val taken = window.count { it.status == "Taken" }
        val skipped = window.count { it.status == "Skipped" }
        val completion = completionPercent(taken, skipped)
        return CoachClientSummary(
            clientId = client.id,
            name = client.name,
            takenCount = taken,
            skippedCount = skipped,
            completionPercent = completion,
            lastActivityEpochMs = window.maxOfOrNull { it.epochMs },
            needsCheckIn = taken + skipped >= 3 && (completion ?: 100) < 70
        )
    }

    private fun windowRecords(
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        today: LocalDate,
        zoneId: ZoneId
    ): List<CoachRecordSnapshot> = records.filter { record ->
        val day = Instant.ofEpochMilli(record.epochMs).atZone(zoneId).toLocalDate()
        !day.isBefore(start) && !day.isAfter(today)
    }

    private fun buildTrend(
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        windowDays: Int,
        zoneId: ZoneId
    ): List<CoachTrendPoint> {
        val bucketDays = if (windowDays <= 7) 1 else 7
        val grouped = records.groupBy { record ->
            val day = Instant.ofEpochMilli(record.epochMs).atZone(zoneId).toLocalDate()
            (ChronoUnit.DAYS.between(start, day).toInt() / bucketDays).coerceAtLeast(0)
        }
        val bucketCount = ((windowDays + bucketDays - 1) / bucketDays).coerceAtLeast(1)
        return (0 until bucketCount).map { index -> trendPoint(index, grouped[index].orEmpty(), start, bucketDays, zoneId) }
    }

    private fun trendPoint(
        index: Int,
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        bucketDays: Int,
        zoneId: ZoneId
    ): CoachTrendPoint {
        val taken = records.count { it.status == "Taken" }
        val skipped = records.count { it.status == "Skipped" }
        val bucketStart = start.plusDays((index * bucketDays).toLong())
        return CoachTrendPoint(
            bucketStartEpochMs = bucketStart.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            takenCount = taken,
            skippedCount = skipped,
            completionPercent = completionPercent(taken, skipped)
        )
    }

    private fun completionPercent(taken: Int, skipped: Int): Int? {
        val total = taken + skipped
        return if (total == 0) null else ((taken * 100.0) / total).toInt()
    }

    private fun normalizeWindowDays(windowDays: Int): Int = when (windowDays) {
        7, 30, 90 -> windowDays
        else -> 7
    }

    private val summaryComparator = compareByDescending<CoachClientSummary> { it.needsCheckIn }
        .thenBy { it.completionPercent ?: Int.MAX_VALUE }
        .thenBy { it.name.lowercase() }
}
