package com.example.supplementtracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.service.ActiveClientStore
import com.example.supplementtracker.service.CloudBackupEngine
import com.example.supplementtracker.service.CloudSyncEngine
import com.example.supplementtracker.service.CloudSyncLogStore
import com.example.supplementtracker.service.CloudSyncProfileStore
import com.example.supplementtracker.service.NotificationScheduleEngine
import com.example.supplementtracker.service.OakPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CloudAutoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val activeClientStore by lazy { ActiveClientStore(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = OakPrefs.get(applicationContext)
        val clientId = activeClientStore.currentClientId() ?: return@withContext Result.success()
        val links = CloudSyncProfileStore(applicationContext).links(clientId)
        val manifestId = CloudAutoSyncPolicy.selectManifestId(
            enabled = prefs.getBoolean("isAutoSyncEnabled", false),
            hosted = links.hostedBinId,
            linked = links.linkedBinId
        ) ?: return@withContext Result.success()
        if (isRecentlyAttempted(prefs, manifestId)) return@withContext Result.success()
        val syncSucceeded = runCatching { buildSyncEngine().syncTwoWay(manifestId) }.getOrDefault(false)
        when (CloudAutoSyncPolicy.outcome(
            syncSucceeded = syncSucceeded,
            autoSyncStillEnabled = prefs.getBoolean("isAutoSyncEnabled", false)
        )) {
            CloudAutoSyncPolicy.Outcome.SUCCESS -> Result.success()
            CloudAutoSyncPolicy.Outcome.RETRY -> Result.retry()
        }
    }

    private fun isRecentlyAttempted(
        prefs: android.content.SharedPreferences,
        manifestId: String
    ): Boolean {
        val lastAttempt = prefs.getLong("cloudSyncLastAttemptEpochMs_$manifestId", 0L)
        return CloudAutoSyncPolicy.isThrottled(lastAttempt, System.currentTimeMillis())
    }

    private fun buildSyncEngine(): CloudSyncEngine {
        val database = SupplementDatabase.getInstance(applicationContext)
        val repository = SupplementRepositoryImpl(database.supplementDao)
        val backupEngine = CloudBackupEngine(applicationContext, repository, activeClientStore::currentClientId)
        val notificationEngine = NotificationScheduleEngine(
            applicationContext,
            repository,
            activeClientStore::currentClientId
        )
        return CloudSyncEngine(
            context = applicationContext,
            repository = repository,
            currentClientId = activeClientStore::currentClientId,
            buildFullBackupJson = { clientId -> backupEngine.buildFullBackupJson(clientId) },
            buildStackBackupJson = { clientId -> backupEngine.buildStackBackupJson(clientId) },
            buildHistoryBackupJson = { clientId -> backupEngine.buildHistoryBackupJson(clientId) },
            updateUi = {},
            setLoading = {},
            rescheduleNotifications = { notificationEngine.rescheduleAll() },
            disableAutoSync = { CloudAutoSyncWork.setEnabled(applicationContext, false) },
            appendLog = CloudSyncLogStore::append
        )
    }
}
