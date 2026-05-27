package com.example.supplementtracker.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CloudAutoSyncWork {
    private const val UNIQUE_PERIODIC = "oak_cloud_autosync_periodic"
    private const val UNIQUE_NOW = "oak_cloud_autosync_now"

    fun setEnabled(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
            wm.cancelUniqueWork(UNIQUE_NOW)
            return
        }
        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudAutoSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(defaultConstraints())
                .build()
        )
        enqueueNow(context)
    }

    fun enqueueNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CloudAutoSyncWorker>()
                .setConstraints(defaultConstraints())
                .build()
        )
    }

    private fun defaultConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

