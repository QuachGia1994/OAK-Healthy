package com.example.supplementtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.service.ActiveClientStore
import com.example.supplementtracker.service.NotificationScheduleEngine
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

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reschedule(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reschedule(appContext: Context) {
        val database = SupplementDatabase.getInstance(appContext)
        val repository = SupplementRepositoryImpl(database.supplementDao)
        val activeClientStore = ActiveClientStore(appContext)
        NotificationScheduleEngine(
            appContext,
            repository,
            activeClientStore::currentClientId
        ).rescheduleAll()
    }
}
