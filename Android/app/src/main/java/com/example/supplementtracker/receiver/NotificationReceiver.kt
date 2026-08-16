package com.example.supplementtracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.supplementtracker.MainActivity
import com.example.supplementtracker.R
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.service.ActiveClientStore
import com.example.supplementtracker.service.ActiveProfileNotificationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class NotificationReceiver : BroadcastReceiver() {
    private data class ReminderPayload(
        val name: String,
        val dose: String,
        val supplementId: String,
        val intakeTime: String,
        val scheduledAtMillis: Long
    )

    override fun onReceive(context: Context, intent: Intent) {
        val payload = parsePayload(context, intent) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliverIfEligible(appContext, payload)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parsePayload(context: Context, intent: Intent): ReminderPayload? {
        val supplementId = intent.getStringExtra("SUPPLEMENT_ID")?.trim().orEmpty()
        if (supplementId.isEmpty()) return null
        return ReminderPayload(
            name = intent.getStringExtra("SUPPLEMENT_NAME") ?: context.getString(R.string.notification_default_name),
            dose = intent.getStringExtra("DAILY_DOSE").orEmpty(),
            supplementId = supplementId,
            intakeTime = intent.getStringExtra("INTAKE_TIME").orEmpty(),
            scheduledAtMillis = intent.getLongExtra("SCHEDULED_AT_MILLIS", 0L)
        )
    }

    private suspend fun deliverIfEligible(context: Context, payload: ReminderPayload) {
        val database = SupplementDatabase.getInstance(context)
        val entity = database.supplementDao.getSupplementById(payload.supplementId) ?: return
        if (entity.deletedAtEpochMs != null) return
        val activeClientId = ActiveClientStore(context).currentClientId()
        if (!ActiveProfileNotificationPolicy.allows(activeClientId, entity.clientId)) return

        val supplement = entity.toDomain()
        val cycleStatus = CalculateCycleUseCase()(
            supplement.startDate,
            supplement.cycleConfig,
            LocalDate.now()
        )
        val dueDate = dueDate(payload.scheduledAtMillis)
        if (cycleStatus != CycleStatus.ON) return
        if (!matchesIntervalRecurrenceIfNeeded(supplement, dueDate)) return
        showNotification(context, payload)
    }

    private fun dueDate(scheduledAtMillis: Long): LocalDate {
        if (scheduledAtMillis <= 0L) return LocalDate.now()
        return Instant.ofEpochMilli(scheduledAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    private fun showNotification(context: Context, payload: ReminderPayload) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, manager)
        val notificationId = notificationId(payload)
        val notification = buildNotification(context, payload, notificationId)
        manager.notify(notificationId, notification)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun buildNotification(
        context: Context,
        payload: ReminderPayload,
        notificationId: Int
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(payload.name)
        .setContentText(notificationBody(context, payload))
        .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody(context, payload)))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent(context))
        .addAction(
            R.drawable.ic_action_taken,
            context.getString(R.string.notif_action_taken),
            actionPendingIntent(context, payload, MainActivity.IntakeAction.TAKEN, notificationId)
        )
        .addAction(
            R.drawable.ic_action_skipped,
            context.getString(R.string.notif_action_skip),
            actionPendingIntent(context, payload, MainActivity.IntakeAction.SKIPPED, notificationId)
        )
        .build()

    private fun notificationBody(context: Context, payload: ReminderPayload): String {
        return context.getString(R.string.notification_body_format, payload.name, payload.dose)
    }

    private fun contentPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(
        context: Context,
        payload: ReminderPayload,
        intakeAction: MainActivity.IntakeAction,
        notificationId: Int
    ): PendingIntent {
        val actionIntent = MainActivity.buildIntakeActionIntent(
            context = context,
            supplementId = payload.supplementId,
            intakeTime = payload.intakeTime,
            scheduledAtMillis = payload.scheduledAtMillis,
            action = intakeAction,
            notificationId = notificationId
        )
        return PendingIntent.getActivity(
            context,
            actionIntent.requestCode,
            actionIntent.intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(payload: ReminderPayload): Int {
        return "${payload.supplementId}|${payload.intakeTime}|${payload.scheduledAtMillis}".hashCode()
    }

    private fun matchesIntervalRecurrenceIfNeeded(
        supplement: UserSupplement,
        date: LocalDate
    ): Boolean {
        val interval = supplement.cycleConfig.intervalDays ?: return true
        if (interval <= 1) return true
        val lastTaken = supplement.lastTakenLocalDate
        if (lastTaken != null) {
            val days = ChronoUnit.DAYS.between(lastTaken, date).toInt()
            return days > 0 && days % interval == 0
        }
        if (date.isBefore(supplement.startDate)) return false
        val days = ChronoUnit.DAYS.between(supplement.startDate, date).toInt()
        return days % interval == 0
    }

    private companion object {
        const val CHANNEL_ID = "supplement_reminders"
    }
}
