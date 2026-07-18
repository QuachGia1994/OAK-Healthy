package com.example.supplementtracker.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CloudAutoSyncWork {
    private const val UNIQUE_PERIODIC = "oak_cloud_autosync_periodic"

    fun setEnabled(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
            return
        }
        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudAutoSyncWorker>(15, TimeUnit.MINUTES)
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
