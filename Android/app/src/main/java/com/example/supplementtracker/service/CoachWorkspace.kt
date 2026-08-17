package com.example.supplementtracker.service

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CoachWindowStats(
    val takenCount: Int,
    val skippedCount: Int,
    val completionPercent: Int?,
    val activeDays: Int
)

data class CoachClientDetail(
    val clientId: UUID,
    val name: String,
    val windowDays: Int,
    val current: CoachWindowStats,
    val previous: CoachWindowStats,
    val completionDeltaPoints: Int?,
    val lastActivityEpochMs: Long?,
    val trend: List<CoachTrendPoint>
)

enum class CoachRoutineFeeling { COMFORTABLE, OKAY, DIFFICULT }

data class CoachCheckInEntry(
    val epochMs: Long,
    val feeling: CoachRoutineFeeling,
    val note: String
)

data class CoachReportDocument(
    val clientName: String,
    val windowDays: Int,
    val generatedAtEpochMs: Long,
    val current: CoachWindowStats,
    val previous: CoachWindowStats,
    val completionDeltaPoints: Int?,
    val trend: List<CoachTrendPoint>,
    val checkIns: List<CoachCheckInEntry>
)

fun interface CoachReportRenderer<Output> {
    fun render(document: CoachReportDocument): Output
}

object CoachWorkspaceBuilder {
    fun buildDetail(
        client: CoachClientSnapshot,
        records: List<CoachRecordSnapshot>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        windowDays: Int = 7
    ): CoachClientDetail {
        val days = normalizeWindowDays(windowDays)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val currentStart = today.minusDays((days - 1).toLong())
        val previousStart = currentStart.minusDays(days.toLong())
        val currentRecords = recordsBetween(records, currentStart, today, zoneId)
        val previousEnd = currentStart.minusDays(1)
        val previousRecords = recordsBetween(records, previousStart, previousEnd, zoneId)
        return detail(client, days, currentRecords, previousRecords, currentStart, zoneId)
    }

    fun reportDocument(
        detail: CoachClientDetail,
        checkIns: List<CoachCheckInEntry>,
        generatedAtEpochMs: Long
    ): CoachReportDocument {
        return CoachReportDocument(
            clientName = detail.name,
            windowDays = detail.windowDays,
            generatedAtEpochMs = generatedAtEpochMs,
            current = detail.current,
            previous = detail.previous,
            completionDeltaPoints = detail.completionDeltaPoints,
            trend = detail.trend,
            checkIns = checkIns.take(5)
        )
    }

    private fun detail(
        client: CoachClientSnapshot,
        days: Int,
        currentRecords: List<CoachRecordSnapshot>,
        previousRecords: List<CoachRecordSnapshot>,
        currentStart: LocalDate,
        zoneId: ZoneId
    ): CoachClientDetail {
        val current = stats(currentRecords, zoneId)
        val previous = stats(previousRecords, zoneId)
        return CoachClientDetail(
            clientId = client.id,
            name = client.name,
            windowDays = days,
            current = current,
            previous = previous,
            completionDeltaPoints = delta(current.completionPercent, previous.completionPercent),
            lastActivityEpochMs = currentRecords.maxOfOrNull { it.epochMs },
            trend = trend(currentRecords, currentStart, days, zoneId)
        )
    }

    private fun stats(records: List<CoachRecordSnapshot>, zoneId: ZoneId): CoachWindowStats {
        val taken = records.count { it.status == "Taken" }
        val skipped = records.count { it.status == "Skipped" }
        val activeDays = records.map {
            Instant.ofEpochMilli(it.epochMs).atZone(zoneId).toLocalDate()
        }.distinct().size
        return CoachWindowStats(taken, skipped, completion(taken, skipped), activeDays)
    }

    private fun recordsBetween(
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        end: LocalDate,
        zoneId: ZoneId
    ): List<CoachRecordSnapshot> = records.filter { record ->
        val day = Instant.ofEpochMilli(record.epochMs).atZone(zoneId).toLocalDate()
        !day.isBefore(start) && !day.isAfter(end)
    }

    private fun trend(
        records: List<CoachRecordSnapshot>,
        start: LocalDate,
        windowDays: Int,
        zoneId: ZoneId
    ): List<CoachTrendPoint> {
        val bucketDays = if (windowDays <= 7) 1 else 7
        val grouped = records.groupBy {
            val day = Instant.ofEpochMilli(it.epochMs).atZone(zoneId).toLocalDate()
            (ChronoUnit.DAYS.between(start, day).toInt() / bucketDays).coerceAtLeast(0)
        }
        val count = ((windowDays + bucketDays - 1) / bucketDays).coerceAtLeast(1)
        return (0 until count).map { trendPoint(it, grouped[it].orEmpty(), start, bucketDays, zoneId) }
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
            bucketStart.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            taken,
            skipped,
            completion(taken, skipped)
        )
    }

    private fun completion(taken: Int, skipped: Int): Int? {
        val total = taken + skipped
        return if (total == 0) null else ((taken * 100.0) / total).toInt()
    }

    private fun delta(current: Int?, previous: Int?): Int? {
        if (current == null || previous == null) return null
        return current - previous
    }

    private fun normalizeWindowDays(days: Int): Int = if (days in setOf(7, 30, 90)) days else 7
}

object CoachCheckInPolicy {
    const val MAX_NOTE_LENGTH = 500
    const val MAX_ENTRIES = 20

    fun normalizedNote(raw: String): String = raw.trim().take(MAX_NOTE_LENGTH)

    fun add(existing: List<CoachCheckInEntry>, entry: CoachCheckInEntry): List<CoachCheckInEntry> {
        val normalized = entry.copy(note = normalizedNote(entry.note))
        return (listOf(normalized) + existing).distinctBy { it.epochMs }.take(MAX_ENTRIES)
    }
}

class CoachCheckInStore(private val prefs: SharedPreferences) {
    fun entries(clientId: UUID): List<CoachCheckInEntry> {
        val raw = prefs.getString(key(clientId), null).orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { decode(array.optJSONObject(it)) }
    }

    fun add(clientId: UUID, feeling: CoachRoutineFeeling, note: String, epochMs: Long) {
        val updated = CoachCheckInPolicy.add(entries(clientId), CoachCheckInEntry(epochMs, feeling, note))
        prefs.edit().putString(key(clientId), encode(updated).toString()).apply()
    }

    private fun encode(entries: List<CoachCheckInEntry>): JSONArray = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().put("ts", entry.epochMs).put("feeling", entry.feeling.name).put("note", entry.note))
        }
    }

    private fun decode(obj: JSONObject?): CoachCheckInEntry? {
        obj ?: return null
        val feeling = runCatching { CoachRoutineFeeling.valueOf(obj.optString("feeling")) }.getOrNull() ?: return null
        return CoachCheckInEntry(obj.optLong("ts"), feeling, obj.optString("note").orEmpty())
    }

    private fun key(clientId: UUID): String = "coachCheckIns_${clientId.toString().lowercase()}"
}
