package com.example.supplementtracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.supplementtracker.R

/**
 * Receiver xử lý khi AlarmManager kích hoạt.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("SUPPLEMENT_NAME") ?: "Thực phẩm bổ sung"
        val dose = intent.getStringExtra("DAILY_DOSE") ?: ""
        val id = intent.getStringExtra("SUPPLEMENT_ID") ?: ""

        showNotification(context, name, dose, id.hashCode())
    }

    private fun showNotification(context: Context, name: String, dose: String, notificationId: Int) {
        val channelId = "supplement_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nhắc nhở uống thuốc",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Thay bằng icon của app
            .setContentTitle("Đến giờ uống $name")
            .setContentText("Liều lượng: $dose")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
