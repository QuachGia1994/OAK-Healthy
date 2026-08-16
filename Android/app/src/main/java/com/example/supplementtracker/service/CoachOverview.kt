package com.example.supplementtracker.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

data class CoachOverviewSummary(
    val totalClients: Int,
    val activeClients: Int,
    val needsCheckInCount: Int,
    val clients: List<CoachClientSummary>
)

object CoachOverviewBuilder {
    fun build(
        clients: List<CoachClientSnapshot>,
        recordsByClient: Map<UUID, List<CoachRecordSnapshot>>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CoachOverviewSummary {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val start = today.minusDays(6)
        val summaries = clients.map { client ->
            buildClientSummary(client, recordsByClient[client.id].orEmpty(), start, today, zoneId)
        }.sortedWith(summaryComparator)
        return CoachOverviewSummary(
            totalClients = summaries.size,
            activeClients = summaries.count { it.takenCount + it.skippedCount > 0 },
            needsCheckInCount = summaries.count { it.needsCheckIn },
            clients = summaries
        )
    }

    private fun buildClientSummary(
        client: CoachClientSnapshot,
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        today: LocalDate,
        zoneId: ZoneId
    ): CoachClientSummary {
        val window = records.filter { record ->
            val day = Instant.ofEpochMilli(record.epochMs).atZone(zoneId).toLocalDate()
            !day.isBefore(start) && !day.isAfter(today)
        }
        val taken = window.count { it.status == "Taken" }
        val skipped = window.count { it.status == "Skipped" }
        val total = taken + skipped
        val completion = if (total == 0) null else ((taken * 100.0) / total).toInt()
        return CoachClientSummary(
            clientId = client.id,
            name = client.name,
            takenCount = taken,
            skippedCount = skipped,
            completionPercent = completion,
            lastActivityEpochMs = window.maxOfOrNull { it.epochMs },
            needsCheckIn = total >= 3 && (completion ?: 100) < 70
        )
    }

    private val summaryComparator = compareByDescending<CoachClientSummary> { it.needsCheckIn }
        .thenBy { it.completionPercent ?: Int.MAX_VALUE }
        .thenBy { it.name.lowercase() }
}
