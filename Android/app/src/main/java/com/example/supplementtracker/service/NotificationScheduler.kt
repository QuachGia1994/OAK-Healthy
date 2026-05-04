package com.example.supplementtracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.receiver.NotificationReceiver
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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

    override fun schedule(supplement: UserSupplement) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("SUPPLEMENT_NAME", supplement.name)
            putExtra("DAILY_DOSE", supplement.dailyDose)
            putExtra("SUPPLEMENT_ID", supplement.id.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            supplement.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getTriggerTime(supplement.intakeTime)
        
        // Sử dụng setExactAndAllowWhileIdle để đảm bảo chuông reo đúng giờ ngay cả khi máy sleep
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    override fun cancel(supplement: UserSupplement) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            supplement.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun getTriggerTime(intakeTime: String): Long {
        val parts = intakeTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        
        val time = LocalTime.of(hour, minute)

        var scheduledTime = LocalDateTime.of(java.time.LocalDate.now(), time)
        if (scheduledTime.isBefore(LocalDateTime.now())) {
            scheduledTime = scheduledTime.plusDays(1)
        }

        return scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
