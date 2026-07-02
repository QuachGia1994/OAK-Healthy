package com.example.supplementtracker.worker

import android.content.Context
import com.example.supplementtracker.service.OakPrefs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CloudAutoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = OakPrefs.get(applicationContext)
        if (!prefs.getBoolean("isAutoSyncEnabled", false)) return@withContext Result.success()

        val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
        val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
        val manifestId = (if (hosted.isNotEmpty()) hosted else linked).trim()
        if (manifestId.isEmpty()) return@withContext Result.success()

        val lastAttemptKey = "cloudSyncLastAttemptEpochMs_$manifestId"
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(lastAttemptKey, 0L)
        if (lastAttempt > 0L && (now - lastAttempt) < 20_000L) return@withContext Result.success()

        return@withContext runCatching {
            val db = SupplementDatabase.getInstance(applicationContext)
            val repository = SupplementRepositoryImpl(db.supplementDao)
            val activeClientManager = ActiveClientManager(applicationContext, repository)
            val viewModel = HomeViewModel(
                context = applicationContext,
                repository = repository,
                activeClientManager = activeClientManager
            )
            viewModel.runSyncTwoWayNow(manifestId)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

