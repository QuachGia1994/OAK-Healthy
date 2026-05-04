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
        val name = intent.getStringExtra("SUPPLEMENT_NAME") ?: "Thực phẩm bổ sung"
        val dose = intent.getStringExtra("DAILY_DOSE") ?: ""
        val id = intent.getStringExtra("SUPPLEMENT_ID") ?: ""

        if (id.isEmpty()) return

        // Kiểm tra chu kỳ On/Off trước khi hiển thị
        CoroutineScope(Dispatchers.IO).launch {
            val db = Room.databaseBuilder(
                context.applicationContext,
                SupplementDatabase::class.java,
                SupplementDatabase.DATABASE_NAME
            ).build()
            
            val supplementEntity = db.supplementDao.getSupplementById(id)
            if (supplementEntity != null) {
                val supplement = supplementEntity.toDomain()
                val calculateCycleUseCase = CalculateCycleUseCase()
                val status = calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, LocalDate.now())
                
                if (status == com.example.supplementtracker.domain.model.CycleStatus.ON) {
                    showNotification(context, name, dose, id.hashCode())
                }
            }
        }
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

        // Tạo intent để mở app khi nhấn vào thông báo
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Thay bằng icon của app
            .setContentTitle("Đến giờ uống rồi! 🌿")
            .setContentText("Bạn cần nạp $name - Liều lượng: $dose. Chúc bạn một phiên giao dịch/làm việc hiệu quả!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
