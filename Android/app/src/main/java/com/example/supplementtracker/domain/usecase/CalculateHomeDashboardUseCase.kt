package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.IntakeStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.IntakeRecord
import com.example.supplementtracker.domain.util.DoseEventKey
import com.example.supplementtracker.domain.util.DoseTimingPolicy
import com.example.supplementtracker.domain.util.TimeStrings
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.time.DayOfWeek
import java.util.concurrent.ConcurrentHashMap

class CalculateHomeDashboardUseCase(
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) {
    enum class DoseStatus { PLANNED, TAKEN, SKIPPED, MISSED }

    data class ActiveDose(
        val supplement: UserSupplement,
        val timeString: String,
        val scheduledAtEpochMs: Long,
        val doseStatus: DoseStatus,
        val isDueSoon: Boolean,
        val isMissedSoon: Boolean
    )

    data class RestingSupplement(
        val supplement: UserSupplement,
        val daysRemaining: Int
    )

    data class Result(
        val activeDoses: Map<String, List<ActiveDose>>,
        val restingSupplements: List<RestingSupplement>,
        val streakDays: Int,
        val hasAnyIntakeRecord: Boolean
    )

    private val intakeTimesCache = ConcurrentHashMap<String, List<String>>()

    operator fun invoke(
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>,
        today: LocalDate,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result {
        val recordIndex = buildRecordIndex(records)
        val liveSupplements = supplements.filter { it.deletedAtEpochMs == null && !isExpired(it, today) }
        val hasAnyRecord = records.isNotEmpty() || liveSupplements.any { it.lastTakenLocalDate != null }
        if (liveSupplements.isEmpty()) return Result(emptyMap(), emptyList(), 0, hasAnyRecord)

        val streakDays = computeStreakDays(today, liveSupplements, recordIndex.hasRecordByDose, zoneId)
        val activeDoses = buildActiveDoses(liveSupplements, today, nowEpochMs, recordIndex.statusByDose, zoneId)
        val resting = buildRestingList(liveSupplements, today)
        return Result(activeDoses, resting, streakDays, hasAnyRecord)
    }

    fun isExpired(supplement: UserSupplement, today: LocalDate): Boolean =
        calculateCycleUseCase.isExpired(supplement.startDate, supplement.cycleConfig, today)

    private data class RecordIndex(
        val statusByDose: Map<String, String>,
        val hasRecordByDose: Set<String>
    )

    private fun buildRecordIndex(records: List<IntakeRecord>): RecordIndex {
        val statusByDose = HashMap<String, String>(records.size)
        val hasRecordByDose = HashSet<String>(records.size)
        records.forEach { record ->
            val key = DoseEventKey.make(record.supplementId, record.date)
            statusByDose[key] = record.status
            hasRecordByDose.add(key)
        }
        return RecordIndex(statusByDose, hasRecordByDose)
    }

    private fun computeStreakDays(
        today: LocalDate,
        supplements: List<UserSupplement>,
        hasRecordByDose: Set<String>,
        zoneId: ZoneId
    ): Int {
        val seedDay = if (isDayComplete(today, supplements, hasRecordByDose, zoneId)) today else today.minusDays(1)
        var streakDays = 0
        var cursor = seedDay
        var remaining = 120
        while (remaining > 0 && isDayComplete(cursor, supplements, hasRecordByDose, zoneId)) {
            streakDays += 1
            cursor = cursor.minusDays(1)
            remaining -= 1
        }
        return streakDays
    }

    private fun buildActiveDoses(
        supplements: List<UserSupplement>,
        today: LocalDate,
        nowEpochMs: Long,
        statusByDose: Map<String, String>,
        zoneId: ZoneId
    ): Map<String, List<ActiveDose>> {
        val items = supplements
            .filter { isActiveOnDay(it, today) }
            .flatMap { supplement ->
                effectiveTimes(supplement).map { time ->
                    buildActiveDose(supplement, time, today, nowEpochMs, statusByDose, zoneId)
                }
            }
        return items.groupBy { it.timeString }.toSortedMap()
    }

    private fun isActiveOnDay(supplement: UserSupplement, today: LocalDate): Boolean =
        calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, today) == CycleStatus.ON &&
            matchesWeeklyRecurrenceIfNeeded(supplement, today) &&
            matchesIntervalRecurrenceIfNeeded(supplement, today)

    private fun buildActiveDose(
        supplement: UserSupplement,
        time: String,
        today: LocalDate,
        nowEpochMs: Long,
        statusByDose: Map<String, String>,
        zoneId: ZoneId
    ): ActiveDose {
        val scheduledAt = scheduledAtEpochMs(today, time, zoneId) ?: 0L
        val status = statusByDose[DoseEventKey.make(supplement.id.toString(), scheduledAt)]
        val doseStatus = doseStatus(scheduledAt, status, nowEpochMs)
        val isDueSoon = doseStatus == DoseStatus.PLANNED &&
            DoseTimingPolicy.isDueSoon(scheduledAt, nowEpochMs)
        val isMissedSoon = doseStatus == DoseStatus.PLANNED &&
            DoseTimingPolicy.isMissedSoon(scheduledAt, nowEpochMs)
        return ActiveDose(supplement, time, scheduledAt, doseStatus, isDueSoon, isMissedSoon)
    }

    private fun buildRestingList(
        supplements: List<UserSupplement>,
        today: LocalDate
    ): List<RestingSupplement> = supplements
        .filter {
            val duration = it.cycleConfig.durationMonths
            val unlimited = duration == null || duration <= 0
            unlimited && !today.isBefore(it.startDate) && !isExpired(it, today) &&
                calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.OFF
        }
        .map { RestingSupplement(it, calculateDaysRemaining(it, today)) }

    private fun calculateDaysRemaining(supplement: UserSupplement, today: LocalDate): Int {
        val config = supplement.cycleConfig
        val totalCycleDays = config.daysOn + config.daysOff
        if (totalCycleDays <= 0) return 0
        val daysElapsed = ChronoUnit.DAYS.between(supplement.startDate, today).toInt()
        val dayInCycle = Math.floorMod(daysElapsed, totalCycleDays)
        return totalCycleDays - dayInCycle
    }

    private fun isDayComplete(
        day: LocalDate,
        supplements: List<UserSupplement>,
        hasRecordByDose: Set<String>,
        zoneId: ZoneId
    ): Boolean {
        for (supplement in supplements) {
            if (supplement.deletedAtEpochMs != null) continue
            if (calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, day) != CycleStatus.ON) continue
            if (!matchesWeeklyRecurrenceIfNeeded(supplement, day)) continue
            if (!matchesIntervalRecurrenceIfNeeded(supplement, day)) continue
            for (time in effectiveTimes(supplement)) {
                val scheduledAt = scheduledAtEpochMs(day, time, zoneId) ?: continue
                if (!hasRecordByDose.contains(DoseEventKey.make(supplement.id.toString(), scheduledAt))) return false
            }
        }
        return true
    }

    private fun doseStatus(scheduledAtEpochMs: Long, recordedStatus: String?, nowEpochMs: Long): DoseStatus {
        when (IntakeStatus.fromStorage(recordedStatus)) {
            IntakeStatus.SKIPPED -> return DoseStatus.SKIPPED
            IntakeStatus.TAKEN -> return DoseStatus.TAKEN
            null -> Unit
        }
        if (scheduledAtEpochMs <= 0L) return DoseStatus.PLANNED
        return if (DoseTimingPolicy.isMissed(scheduledAtEpochMs, nowEpochMs)) DoseStatus.MISSED else DoseStatus.PLANNED
    }

    private fun parseTimes(raw: String): List<String> {
        val key = raw.trim()
        if (key.isEmpty()) return emptyList()
        return intakeTimesCache.computeIfAbsent(key) { TimeStrings.normalizeList(it) }
    }

    private fun effectiveTimes(supplement: UserSupplement): List<String> {
        val times = parseTimes(supplement.intakeTime)
        val interval = supplement.cycleConfig.intervalDays ?: return times
        if (interval <= 1) return times
        return times.take(1)
    }

    private fun scheduledAtEpochMs(date: LocalDate, timeString: String, zoneId: ZoneId): Long? {
        val parsed = TimeStrings.parseLenient(timeString) ?: return null
        return date.atTime(parsed).atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun matchesWeeklyRecurrenceIfNeeded(supplement: UserSupplement, date: LocalDate): Boolean {
        val weekly = supplement.cycleConfig.weeklyRecurrence ?: return true
        val bitIndex = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        if ((weekly.weekdaysMask and (1 shl bitIndex)) == 0) return false
        val interval = weekly.intervalWeeks.coerceAtLeast(1)
        val fields = WeekFields.ISO
        val anchorStart = weekly.anchorDate.with(fields.dayOfWeek(), 1)
        val dateStart = date.with(fields.dayOfWeek(), 1)
        val weeks = ChronoUnit.WEEKS.between(anchorStart, dateStart).toInt()
        return Math.floorMod(weeks, interval) == 0
    }

    private fun matchesIntervalRecurrenceIfNeeded(supplement: UserSupplement, date: LocalDate): Boolean {
        val interval = supplement.cycleConfig.intervalDays ?: return true
        if (interval <= 1) return true
        val lastTaken = supplement.lastTakenLocalDate
        if (lastTaken != null) {
            val days = ChronoUnit.DAYS.between(lastTaken, date).toInt()
            return days >= 0 && days % interval == 0
        }
        if (date.isBefore(supplement.startDate)) return false
        return ChronoUnit.DAYS.between(supplement.startDate, date).toInt() % interval == 0
    }
}
