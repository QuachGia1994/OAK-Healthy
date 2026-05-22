package com.example.supplementtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.service.NotificationSchedulerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        val shouldReschedule = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        if (!shouldReschedule) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = SupplementDatabase.getInstance(context)

            val supplements = db.supplementDao.getAllActiveSupplements().map { it.toDomain() }
            NotificationSchedulerImpl(context.applicationContext).rescheduleAll(supplements)
        }
    }
}
