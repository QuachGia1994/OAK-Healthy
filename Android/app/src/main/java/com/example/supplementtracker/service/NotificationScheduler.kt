package com.example.supplementtracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.receiver.NotificationReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import com.example.supplementtracker.domain.util.TimeStrings

data class ScheduledAlarmInfo(
    val requestCode: Int,
    val supplementId: String,
    val title: String,
    val dose: String,
    val cycleText: String,
    val scheduledAtMillis: Long
)

object NotificationDebugStore {
    private const val prefsName = "oak_notification_debug"
    private const val keyEntries = "scheduled_entries"

    fun recordScheduled(context: Context, info: ScheduledAlarmInfo) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(keyEntries, emptySet()).orEmpty()
        val updated = current
            .filterNot { it.startsWith("${info.requestCode}|") }
            .toMutableSet()
        updated.add("${info.requestCode}|${info.supplementId}|${info.title}|${info.dose}|${info.cycleText}|${info.scheduledAtMillis}")
        prefs.edit().putStringSet(keyEntries, updated).apply()
    }

    fun recordCancelled(context: Context, requestCode: Int) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(keyEntries, emptySet()).orEmpty()
        val updated = current.filterNot { it.startsWith("$requestCode|") }.toSet()
        prefs.edit().putStringSet(keyEntries, updated).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(keyEntries, emptySet()).apply()
    }

    fun getAll(context: Context): List<ScheduledAlarmInfo> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val entries = prefs.getStringSet(keyEntries, emptySet()).orEmpty()
        return entries.mapNotNull { parse(it) }.sortedBy { it.scheduledAtMillis }
    }

    fun getUpcoming(context: Context, nowMillis: Long = System.currentTimeMillis()): List<ScheduledAlarmInfo> {
        return getAll(context).filter { it.scheduledAtMillis >= nowMillis }
    }

    private fun parse(raw: String): ScheduledAlarmInfo? {
        val parts = raw.split("|")
        val requestCode = parts.getOrNull(0)?.toIntOrNull() ?: return null
        if (parts.size >= 6) {
            val supplementId = parts.getOrNull(1).orEmpty()
            val title = parts.getOrNull(2) ?: return null
            val dose = parts.getOrNull(3).orEmpty()
            val cycleText = parts.getOrNull(4).orEmpty()
            val millis = parts.getOrNull(5)?.toLongOrNull() ?: return null
            return ScheduledAlarmInfo(
                requestCode = requestCode,
                supplementId = supplementId,
                title = title,
                dose = dose,
                cycleText = cycleText,
                scheduledAtMillis = millis
            )
        }
        val title = parts.getOrNull(1) ?: return null
        val dose = parts.getOrNull(2).orEmpty()
        val cycleText = parts.getOrNull(3).orEmpty()
        val millis = parts.getOrNull(4)?.toLongOrNull() ?: return null
        return ScheduledAlarmInfo(
            requestCode = requestCode,
            supplementId = "",
            title = title,
            dose = dose,
            cycleText = cycleText,
            scheduledAtMillis = millis
        )
    }
}

/**
 * Interface quản lý việc lên lịch thông báo.
 */
interface NotificationScheduler {
    fun schedule(supplement: UserSupplement)
    fun cancel(supplement: UserSupplement)
}

/**
 * Triển khai NotificationScheduler sử dụng AlarmManager.
 */
class NotificationSchedulerImpl(private val context: Context) : NotificationScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val cycleUseCase = CalculateCycleUseCase()
    private val settingsPrefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)

    override fun schedule(supplement: UserSupplement) {
        cancelKnownForSupplement(supplement.id.toString())
        cancelLegacy(supplement)
        if (!isNotificationsEnabledByUser()) return

        val times = parseTimes(supplement.intakeTime)
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val horizonDays = schedulingHorizonDays(supplement)

        for (dayOffset in 0 until horizonDays) {
            val date = today.plusDays(dayOffset.toLong())

            if (!matchesWeeklyRecurrenceIfNeeded(supplement, date)) {
                times.forEach { timeString ->
                    cancelByRequestCode(requestCode(supplement, date, timeString))
                }
                continue
            }

            val status = cycleUseCase(
                startDate = supplement.startDate,
                config = supplement.cycleConfig,
                currentDate = date
            )

            for (timeString in times) {
                val time = parseTime(timeString) ?: continue
                val requestCode = requestCode(supplement, date, timeString)
                val plan = applyQuietHoursIfNeeded(date = date, time = time)

                if (plan.triggerAt.isBefore(now) || status != CycleStatus.ON) {
                    cancelByRequestCode(requestCode)
                    continue
                }

                val scheduledAtMillis = plan.scheduledAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val triggerAtMillis = plan.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    buildIntent(supplement, timeString, scheduledAtMillis),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                NotificationDebugStore.recordScheduled(
                    context = context,
                    info = ScheduledAlarmInfo(
                        requestCode = requestCode,
                        supplementId = supplement.id.toString(),
                        title = supplement.name,
                        dose = supplement.dailyDose,
                        cycleText = cycleLabel(supplement, date),
                        scheduledAtMillis = triggerAtMillis
                    )
                )
            }
        }
    }

    private data class QuietHoursPlan(
        val scheduledAt: LocalDateTime,
        val triggerAt: LocalDateTime
    )

    private fun applyQuietHoursIfNeeded(date: LocalDate, time: LocalTime): QuietHoursPlan {
        val quietStart = LocalTime.of(22, 0)
        val quietEnd = LocalTime.of(7, 0)
        val scheduledAt = LocalDateTime.of(date, time)
        val triggerAt = when {
            !time.isBefore(quietStart) -> LocalDateTime.of(date.plusDays(1), quietEnd)
            time.isBefore(quietEnd) -> LocalDateTime.of(date, quietEnd)
            else -> scheduledAt
        }
        return QuietHoursPlan(scheduledAt = scheduledAt, triggerAt = triggerAt)
    }

    override fun cancel(supplement: UserSupplement) {
        cancelKnownForSupplement(supplement.id.toString())
        cancelLegacy(supplement)
        val times = parseTimes(supplement.intakeTime)
        val today = LocalDate.now()
        val horizonDays = schedulingHorizonDays(supplement)
        for (dayOffset in 0 until horizonDays) {
            val date = today.plusDays(dayOffset.toLong())
            times.forEach { timeString ->
                cancelByRequestCode(requestCode(supplement, date, timeString))
            }
        }
    }

    private fun buildIntent(supplement: UserSupplement, timeString: String, scheduledAtMillis: Long): Intent {
        return Intent(context, NotificationReceiver::class.java).apply {
            putExtra("SUPPLEMENT_NAME", supplement.name)
            putExtra("DAILY_DOSE", supplement.dailyDose)
            putExtra("SUPPLEMENT_ID", supplement.id.toString())
            putExtra("INTAKE_TIME", timeString)
            putExtra("SCHEDULED_AT_MILLIS", scheduledAtMillis)
        }
    }

    private fun cancelByRequestCode(requestCode: Int) {
        NotificationDebugStore.recordCancelled(context, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
    }

    private fun requestCode(supplement: UserSupplement, date: LocalDate, timeString: String): Int {
        return "${supplement.id}-${date.toEpochDay()}-${timeString.trim()}".hashCode()
    }

    fun rescheduleAll(supplements: List<UserSupplement>) {
        if (!isNotificationsEnabledByUser()) {
            clearAll(supplements)
            return
        }
        cancelAllKnown()
        supplements.forEach { schedule(it) }
    }

    fun clearAll(supplements: List<UserSupplement>) {
        cancelAllKnown()
        supplements.forEach { cancel(it) }
    }

    private fun cancelAllKnown() {
        NotificationDebugStore.getAll(context).forEach { info -> cancelByRequestCode(info.requestCode) }
        NotificationDebugStore.clearAll(context)
    }

    private fun cancelKnownForSupplement(supplementId: String) {
        val trimmed = supplementId.trim()
        if (trimmed.isEmpty()) return
        NotificationDebugStore.getAll(context)
            .filter { it.supplementId.equals(trimmed, ignoreCase = true) }
            .forEach { info -> cancelByRequestCode(info.requestCode) }
    }

    private fun cancelLegacy(supplement: UserSupplement) {
        cancelByRequestCode(supplement.id.hashCode())
    }

    private fun parseTimes(raw: String): List<String> {
        return TimeStrings.normalizeList(raw)
    }

    private fun parseTime(timeString: String): LocalTime? {
        return TimeStrings.parseLenient(timeString)
    }

    private fun isNotificationsEnabledByUser(): Boolean {
        return settingsPrefs.getBoolean("isNotificationEnabledByUser", false)
    }

    private fun cycleLabel(supplement: UserSupplement, date: LocalDate): String {
        val config = supplement.cycleConfig
        if (config.isContinuous) return context.getString(R.string.cycle_continuous)
        val total = config.daysOn + config.daysOff
        if (total <= 0) return ""
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(supplement.startDate, date).toInt()
        val dayInCycle = (elapsed % total) + 1
        if (dayInCycle <= config.daysOn) return context.getString(R.string.cycle_label_day_format, dayInCycle, config.daysOn)
        val dayInOff = dayInCycle - config.daysOn
        val offTotal = if (config.daysOff <= 0) 1 else config.daysOff
        return context.getString(R.string.cycle_label_rest_format, dayInOff, offTotal)
    }
    
    private fun schedulingHorizonDays(supplement: UserSupplement): Int {
        val weekly = supplement.cycleConfig.weeklyRecurrence ?: return 7
        val interval = weekly.intervalWeeks.coerceAtLeast(1)
        return (interval * 7).coerceIn(7, 56)
    }
    
    private fun matchesWeeklyRecurrenceIfNeeded(supplement: UserSupplement, date: LocalDate): Boolean {
        val weekly = supplement.cycleConfig.weeklyRecurrence ?: return true
        val bitIndex = weekdayBitIndex(date) ?: return true
        if ((weekly.weekdaysMask and (1 shl bitIndex)) == 0) return false
        val interval = weekly.intervalWeeks.coerceAtLeast(1)
        val anchorStart = startOfIsoWeek(weekly.anchorDate)
        val dateStart = startOfIsoWeek(date)
        val weeks = ChronoUnit.WEEKS.between(anchorStart, dateStart).toInt()
        val mod = ((weeks % interval) + interval) % interval
        return mod == 0
    }
    
    private fun weekdayBitIndex(date: LocalDate): Int? {
        return when (date.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> 0
            java.time.DayOfWeek.TUESDAY -> 1
            java.time.DayOfWeek.WEDNESDAY -> 2
            java.time.DayOfWeek.THURSDAY -> 3
            java.time.DayOfWeek.FRIDAY -> 4
            java.time.DayOfWeek.SATURDAY -> 5
            java.time.DayOfWeek.SUNDAY -> 6
            else -> null
        }
    }
    
    private fun startOfIsoWeek(date: LocalDate): LocalDate {
        val fields = WeekFields.ISO
        return date.with(fields.dayOfWeek(), 1)
    }
}
