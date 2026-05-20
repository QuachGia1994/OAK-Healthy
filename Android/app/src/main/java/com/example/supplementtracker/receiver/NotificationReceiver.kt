package com.example.supplementtracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.supplementtracker.R

import androidx.room.Room
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

import android.app.PendingIntent
import com.example.supplementtracker.MainActivity

/**
 * Receiver xử lý khi AlarmManager kích hoạt.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("SUPPLEMENT_NAME") ?: context.getString(R.string.notification_default_name)
        val dose = intent.getStringExtra("DAILY_DOSE") ?: ""
        val id = intent.getStringExtra("SUPPLEMENT_ID") ?: ""
        val intakeTime = intent.getStringExtra("INTAKE_TIME") ?: ""
        val scheduledAtMillis = intent.getLongExtra("SCHEDULED_AT_MILLIS", 0L)

        if (id.isEmpty()) return

        // Kiểm tra chu kỳ On/Off trước khi hiển thị
        CoroutineScope(Dispatchers.IO).launch {
            val db = Room.databaseBuilder(
                context.applicationContext,
                SupplementDatabase::class.java,
                SupplementDatabase.DATABASE_NAME
            )
                .addMigrations(
                    SupplementDatabase.MIGRATION_2_3,
                    SupplementDatabase.MIGRATION_3_4,
                    SupplementDatabase.MIGRATION_4_5
                )
                .fallbackToDestructiveMigration()
                .build()
            
            val supplementEntity = db.supplementDao.getSupplementById(id)
            if (supplementEntity != null) {
                if (supplementEntity.deletedAtEpochMs != null) return@launch
                val supplement = supplementEntity.toDomain()
                val calculateCycleUseCase = CalculateCycleUseCase()
                val status = calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, LocalDate.now())
                
                if (status == com.example.supplementtracker.domain.model.CycleStatus.ON) {
                    showNotification(context, name, dose, id, intakeTime, scheduledAtMillis)
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        name: String,
        dose: String,
        supplementId: String,
        intakeTime: String,
        scheduledAtMillis: Long
    ) {
        val channelId = "supplement_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = "$supplementId|$intakeTime|$scheduledAtMillis".hashCode()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Tạo intent để mở app khi nhấn vào thông báo
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takenIntent = MainActivity.buildIntakeActionIntent(
            context = context,
            supplementId = supplementId,
            intakeTime = intakeTime,
            scheduledAtMillis = scheduledAtMillis,
            action = MainActivity.IntakeAction.TAKEN,
            notificationId = notificationId
        )
        val skippedIntent = MainActivity.buildIntakeActionIntent(
            context = context,
            supplementId = supplementId,
            intakeTime = intakeTime,
            scheduledAtMillis = scheduledAtMillis,
            action = MainActivity.IntakeAction.SKIPPED,
            notificationId = notificationId
        )
        val actionFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val takenPendingIntent = PendingIntent.getActivity(context, takenIntent.requestCode, takenIntent.intent, actionFlags)
        val skippedPendingIntent = PendingIntent.getActivity(context, skippedIntent.requestCode, skippedIntent.intent, actionFlags)

        val body = context.getString(R.string.notification_body_format, name, dose)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_action_taken, context.getString(R.string.notif_action_taken), takenPendingIntent)
            .addAction(R.drawable.ic_action_skipped, context.getString(R.string.notif_action_skip), skippedPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
