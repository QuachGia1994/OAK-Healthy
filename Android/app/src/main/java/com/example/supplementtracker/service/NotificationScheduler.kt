package com.example.supplementtracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.receiver.NotificationReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ScheduledAlarmInfo(
    val requestCode: Int,
    val title: String,
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
        updated.add("${info.requestCode}|${info.title}|${info.scheduledAtMillis}")
        prefs.edit().putStringSet(keyEntries, updated).apply()
    }

    fun recordCancelled(context: Context, requestCode: Int) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(keyEntries, emptySet()).orEmpty()
        val updated = current.filterNot { it.startsWith("$requestCode|") }.toSet()
        prefs.edit().putStringSet(keyEntries, updated).apply()
    }

    fun getUpcoming(context: Context, nowMillis: Long = System.currentTimeMillis()): List<ScheduledAlarmInfo> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val entries = prefs.getStringSet(keyEntries, emptySet()).orEmpty()
        return entries.mapNotNull { parse(it) }
            .filter { it.scheduledAtMillis >= nowMillis }
            .sortedBy { it.scheduledAtMillis }
    }

    private fun parse(raw: String): ScheduledAlarmInfo? {
        val parts = raw.split("|")
        val requestCode = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val title = parts.getOrNull(1) ?: return null
        val millis = parts.getOrNull(2)?.toLongOrNull() ?: return null
        return ScheduledAlarmInfo(requestCode = requestCode, title = title, scheduledAtMillis = millis)
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

    override fun schedule(supplement: UserSupplement) {
        cancelLegacy(supplement)
        
        val time = parseTime(supplement.intakeTime)
        val now = LocalDateTime.now()
        val today = LocalDate.now()

        for (dayOffset in 0..6) {
            val date = today.plusDays(dayOffset.toLong())
            val scheduledTime = LocalDateTime.of(date, time)
            if (scheduledTime.isBefore(now)) continue
            
            val status = cycleUseCase(
                startDate = supplement.startDate,
                config = supplement.cycleConfig,
                currentDate = date
            )
            
            val requestCode = requestCode(supplement, date)
            if (status == CycleStatus.ON) {
                val scheduledAtMillis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    buildIntent(supplement),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledAtMillis,
                    pendingIntent
                )
                NotificationDebugStore.recordScheduled(
                    context = context,
                    info = ScheduledAlarmInfo(
                        requestCode = requestCode,
                        title = supplement.name,
                        scheduledAtMillis = scheduledAtMillis
                    )
                )
            } else {
                cancelByRequestCode(requestCode)
            }
        }
    }

    override fun cancel(supplement: UserSupplement) {
        cancelLegacy(supplement)
        val today = LocalDate.now()
        for (dayOffset in 0..6) {
            val date = today.plusDays(dayOffset.toLong())
            cancelByRequestCode(requestCode(supplement, date))
        }
    }

    private fun buildIntent(supplement: UserSupplement): Intent {
        return Intent(context, NotificationReceiver::class.java).apply {
            putExtra("SUPPLEMENT_NAME", supplement.name)
            putExtra("DAILY_DOSE", supplement.dailyDose)
            putExtra("SUPPLEMENT_ID", supplement.id.toString())
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

    private fun requestCode(supplement: UserSupplement, date: LocalDate): Int {
        return "${supplement.id}-${date.toEpochDay()}".hashCode()
    }

    private fun cancelLegacy(supplement: UserSupplement) {
        cancelByRequestCode(supplement.id.hashCode())
    }

    private fun parseTime(intakeTime: String): LocalTime {
        val parts = intakeTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of(hour, minute)
    }
}
