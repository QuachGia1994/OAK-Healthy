package com.example.supplementtracker.service

import android.content.Context
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.export.OAKBackupDataDTO
import com.example.supplementtracker.domain.export.OAKBackupHistoryDTO
import com.example.supplementtracker.domain.export.OAKBackupJson
import com.example.supplementtracker.domain.export.OAKBackupMetaDTO
import com.example.supplementtracker.domain.export.OAKBackupSchema
import com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
import com.example.supplementtracker.domain.util.DoseEventKey
import com.example.supplementtracker.domain.util.StableId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import android.os.SystemClock
import java.time.LocalDate
import java.util.Locale

/**
 * Application-level coordinator for two-way cloud sync.
 * Transport remains in CloudSyncManager/FirebaseCloudStore; this class owns
 * sync orchestration, merge/conflict policy, and sync telemetry side effects.
 */
class CloudSyncEngine(
    private val context: Context,
    private val repository: SupplementRepository,
    private val currentClientId: () -> java.util.UUID?,
    private val buildFullBackupJson: suspend () -> Result<String>,
    private val buildStackBackupJson: suspend () -> Result<String>,
    private val buildHistoryBackupJson: suspend () -> Result<String>,
    private val updateUi: suspend (String) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val rescheduleNotifications: suspend () -> Unit,
    private val disableAutoSync: () -> Unit,
    private val appendLog: (android.content.SharedPreferences, String, String, String) -> Unit
) {
    private val syncMutex = Mutex()

    private enum class SyncPhase { IDLE, PULLING, MERGING, PUSHING, RETRYING_CONFLICT, DONE, ERROR }

    private fun decryptAndPrepare(recordJson: String): String {
        val decrypted = CloudSyncCrypto.unwrapDownloadedIfNeeded(context, recordJson)
        return CloudSyncPayloadCodec.decompressIfNeeded(decrypted)
    }

    private fun encryptAndPrepare(plaintextJson: String): String {
        return CloudSyncCrypto.wrapForUploadIfEnabled(context, plaintextJson)
    }

    private suspend fun abortCloudSync(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        lastErrorKey: String,
        phaseKey: String,
        stageMsKey: String,
        stageStartedAt: Long,
        startedAt: Long,
        errorMessage: String,
        logMessage: String
    ) {
        prefs.edit().putString(lastErrorKey, errorMessage).apply()
        prefs.edit().putString(phaseKey, SyncPhase.ERROR.name).apply()
        prefs.edit().putLong(stageMsKey, SystemClock.elapsedRealtime() - stageStartedAt).apply()
        prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        prefs.edit().putLong("cloudSyncLastFailureEpochMs", System.currentTimeMillis()).apply()
        appendLog(prefs, manifestId, "ERROR", logMessage)
        if (errorMessage.startsWith("Missing cloud sync key:", ignoreCase = true)) {
            prefs.edit().putBoolean("isAutoSyncEnabled", false).apply()
            disableAutoSync()
        }
        setLoading(false)
        updateUi(manifestId)
    }

    private fun totalMsKeyFor(manifestId: String): String {
        val id = manifestId.trim()
        return "cloudSyncTotalMs_$id"
    }

    private suspend fun finishCloudSyncNoChanges(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        lastSyncKey: String,
        lastErrorKey: String,
        phaseKey: String,
        startedAt: Long
    ) {
        prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
        prefs.edit().remove(lastErrorKey).apply()
        prefs.edit().putString(phaseKey, SyncPhase.DONE.name).apply()
        prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        markSyncActivity()
        setLoading(false)
        appendLog(prefs, manifestId, "DONE", "No changes")
        updateUi(manifestId)
        rescheduleNotifications()
    }

    suspend fun syncTwoWay(binId: String): Boolean {
        syncMutex.lock()
        try {
            val id = binId.trim()
            val prefs = OakPrefs.get(context)
            val attemptKey = "cloudSyncLastAttemptEpochMs_$id"
            val phaseKey = "cloudSyncPhase_$id"
            val lastErrorKey = "cloudSyncLastError_$id"
            val previousAttempt = prefs.getLong(attemptKey, 0L)
            performSyncTwoWay(binId)
            val currentAttempt = prefs.getLong(attemptKey, 0L)
            val phase = prefs.getString(phaseKey, "").orEmpty()
            val error = prefs.getString(lastErrorKey, null).orEmpty().trim()
            return currentAttempt != previousAttempt && phase == SyncPhase.DONE.name && error.isEmpty()
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun performSyncTwoWay(binId: String) {
        val clientId = currentClientId() ?: return
        val prefs = OakPrefs.get(context)
        val manifestId = binId.trim()
        if (manifestId.isEmpty()) return

        setLoading(true)
        val etagManifestKey = "cloudSyncEtagV2_$manifestId"
        val etagStackKey = "cloudSyncEtagStackV2_$manifestId"
        val etagHistoryKey = "cloudSyncEtagHistoryV2_$manifestId"
        val stackIdKey = "cloudSyncStackBinId_$manifestId"
        val historyIdKey = "cloudSyncHistoryBinId_$manifestId"
        val lastSyncKey = "cloudSyncLastSyncEpochMs_$manifestId"
        val lastAttemptKey = "cloudSyncLastAttemptEpochMs_$manifestId"
        val lastErrorKey = "cloudSyncLastError_$manifestId"
        val phaseKey = "cloudSyncPhase_$manifestId"
        val retryKey = "cloudSyncConflictRetryCount_$manifestId"
        val bytesDownloadedKey = "cloudSyncBytesDownloaded_$manifestId"
        val bytesUploadedKey = "cloudSyncBytesUploaded_$manifestId"
        val pullMsKey = "cloudSyncPullMs_$manifestId"
        val mergeMsKey = "cloudSyncMergeMs_$manifestId"
        val pushMsKey = "cloudSyncPushMs_$manifestId"
        val totalMsKey = "cloudSyncTotalMs_$manifestId"

        val lastSyncEpochMs = prefs.getLong(lastSyncKey, 0L)
        val localStackChanged = hasLocalStackChangesSince(clientId, lastSyncEpochMs)
        val localHistoryChanged = hasLocalHistoryChangesSince(clientId, lastSyncEpochMs)
        val startedAt = SystemClock.elapsedRealtime()

        prefs.edit()
            .putLong(lastAttemptKey, System.currentTimeMillis())
            .putString(phaseKey, SyncPhase.PULLING.name)
            .putInt(retryKey, 0)
            .putLong(bytesDownloadedKey, 0L)
            .putLong(bytesUploadedKey, 0L)
            .putLong(pullMsKey, 0L)
            .putLong(mergeMsKey, 0L)
            .putLong(pushMsKey, 0L)
            .putLong(totalMsKey, 0L)
            .apply()
        appendLog(prefs, manifestId, "START", "Sync start")
        updateUi(manifestId)

        var stackId = prefs.getString(stackIdKey, "").orEmpty().trim()
        var historyId = prefs.getString(historyIdKey, "").orEmpty().trim()
        val pullStartedAt = SystemClock.elapsedRealtime()
        val previousManifestEtag = prefs.getString(etagManifestKey, "").orEmpty().trim()
        val manifestDownload = if (stackId.isEmpty() || historyId.isEmpty()) {
            CloudSyncManager().downloadBackupAlways(manifestId).getOrElse { error ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = error.message ?: "Manifest load failed",
                    logMessage = "Manifest load failed"
                )
                return
            }
        } else {
            CloudSyncManager().downloadBackupIfChanged(manifestId, previousManifestEtag).getOrElse { error ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = error.message ?: "Manifest pull failed",
                    logMessage = "Manifest pull failed"
                )
                return
            }
        }
        val manifestEtag = manifestDownload.etag.orEmpty().trim().ifEmpty { previousManifestEtag }
        if (!manifestDownload.json.isNullOrBlank()) {
            val manifestJson = manifestDownload.json.orEmpty()
            val prepared = runCatching { decryptAndPrepare(manifestJson) }.getOrElse { t ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = t.message ?: "Manifest decrypt failed",
                    logMessage = "Manifest decrypt failed"
                )
                return
            }
            val decoded = runCatching { CloudSyncManifestCodec.decode(prepared) }.getOrNull()
            if (decoded == null) {
                val bytesDown = manifestJson.toByteArray(Charsets.UTF_8).size
                prefs.edit().putLong(bytesDownloadedKey, bytesDown.toLong()).apply()
                prefs.edit().putString(phaseKey, SyncPhase.MERGING.name).apply()
                updateUi(manifestId)
                val mergeStartedAt = SystemClock.elapsedRealtime()
                val legacyPlain = runCatching { decryptAndPrepare(manifestJson) }.getOrElse { t ->
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = mergeMsKey,
                        stageStartedAt = mergeStartedAt,
                        startedAt = startedAt,
                        errorMessage = t.message ?: "Decrypt failed",
                        logMessage = "Decrypt failed"
                    )
                    return
                }
                if (!mergeRemoteIntoLocal(legacyPlain, clientId)) {
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = mergeMsKey,
                        stageStartedAt = mergeStartedAt,
                        startedAt = startedAt,
                        errorMessage = "Legacy payload merge failed",
                        logMessage = "Legacy payload merge failed"
                    )
                    return
                }
                prefs.edit().putLong(mergeMsKey, SystemClock.elapsedRealtime() - mergeStartedAt).apply()
                if (manifestEtag.isNotEmpty()) prefs.edit().putString(etagManifestKey, manifestEtag).apply()
                val remoteChanged = manifestJson.isNotBlank()
                if (!remoteChanged && !localStackChanged && !localHistoryChanged) {
                    finishCloudSyncNoChanges(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastSyncKey = lastSyncKey,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        startedAt = startedAt
                    )
                    return
                }
                prefs.edit().putString(phaseKey, SyncPhase.PUSHING.name).apply()
                updateUi(manifestId)
                val pushStartedAt = SystemClock.elapsedRealtime()
                val fullPlain = buildFullBackupJson().getOrElse {
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = pushMsKey,
                        stageStartedAt = pushStartedAt,
                        startedAt = startedAt,
                        errorMessage = it.message ?: "Export failed",
                        logMessage = "Export failed"
                    )
                    return
                }
                val fullEnc = runCatching { encryptAndPrepare(fullPlain) }.getOrElse { t ->
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = pushMsKey,
                        stageStartedAt = pushStartedAt,
                        startedAt = startedAt,
                        errorMessage = t.message ?: "Encrypt failed",
                        logMessage = "Encrypt failed"
                    )
                    return
                }
                val bytesUp = fullEnc.toByteArray(Charsets.UTF_8).size.toLong()
                prefs.edit().putLong(bytesUploadedKey, bytesUp).apply()
                val upsert = CloudSyncManager().upsertBackup(manifestId, fullEnc, manifestEtag.takeIf { it.isNotEmpty() })
                upsert.getOrElse { error ->
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = pushMsKey,
                        stageStartedAt = pushStartedAt,
                        startedAt = startedAt,
                        errorMessage = error.message ?: "Upload failed",
                        logMessage = "Upload failed"
                    )
                    return
                }?.orEmpty()?.trim()?.let { if (it.isNotEmpty()) prefs.edit().putString(etagManifestKey, it).apply() }
                prefs.edit().putLong(pushMsKey, SystemClock.elapsedRealtime() - pushStartedAt).apply()
                prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
                prefs.edit().remove(lastErrorKey).apply()
                prefs.edit().putString(phaseKey, SyncPhase.DONE.name).apply()
                prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
                markSyncActivity()
                setLoading(false)
                appendLog(prefs, manifestId, "DONE", "OK • up ${bytesUp}B • down ${bytesDown}B • total ${prefs.getLong(totalMsKeyFor(manifestId), 0L)}ms")
                updateUi(manifestId)
                rescheduleNotifications()
                return
            }
            stackId = decoded.stackBinId
            historyId = decoded.historyBinId
            prefs.edit().putString(stackIdKey, stackId).putString(historyIdKey, historyId).apply()
            if (manifestEtag.isNotEmpty()) prefs.edit().putString(etagManifestKey, manifestEtag).apply()
        }
        if (stackId.isEmpty() || historyId.isEmpty()) {
            abortCloudSync(
                prefs = prefs,
                manifestId = manifestId,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                stageMsKey = pullMsKey,
                stageStartedAt = pullStartedAt,
                startedAt = startedAt,
                errorMessage = "Missing stack/history id",
                logMessage = "Missing stack/history id"
            )
            return
        }

        val prevStackEtag = prefs.getString(etagStackKey, "").orEmpty().trim()
        val prevHistoryEtag = prefs.getString(etagHistoryKey, "").orEmpty().trim()
        val cloud = CloudSyncManager()
        val (stackDownload, historyDownload) = coroutineScope {
            val stackDeferred = async(Dispatchers.IO) { cloud.downloadBackupIfChanged(stackId, prevStackEtag) }
            val historyDeferred = async(Dispatchers.IO) { cloud.downloadBackupIfChanged(historyId, prevHistoryEtag) }
            val stack = stackDeferred.await().getOrElse { error ->
                historyDeferred.cancel()
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = error.message ?: "Stack pull failed",
                    logMessage = "Stack pull failed"
                )
                return@coroutineScope null
            }
            val history = historyDeferred.await().getOrElse { error ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = error.message ?: "History pull failed",
                    logMessage = "History pull failed"
                )
                return@coroutineScope null
            }
            stack to history
        } ?: return

        val pullMs = SystemClock.elapsedRealtime() - pullStartedAt
        prefs.edit().putLong(pullMsKey, pullMs).apply()
        val stackEtag = stackDownload.etag.orEmpty().trim().ifEmpty { prevStackEtag }
        val historyEtag = historyDownload.etag.orEmpty().trim().ifEmpty { prevHistoryEtag }

        val bytesDown = (manifestDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
            (stackDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
            (historyDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0)
        prefs.edit().putLong(bytesDownloadedKey, bytesDown.toLong()).apply()

        prefs.edit().putString(phaseKey, SyncPhase.MERGING.name).apply()
        updateUi(manifestId)
        val mergeStartedAt = SystemClock.elapsedRealtime()
        if (!stackDownload.json.isNullOrBlank()) {
            val prepared = runCatching { decryptAndPrepare(stackDownload.json.orEmpty()) }.getOrElse { t ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = mergeMsKey,
                    stageStartedAt = mergeStartedAt,
                    startedAt = startedAt,
                    errorMessage = t.message ?: "Decrypt failed",
                    logMessage = "Decrypt stack failed"
                )
                return
            }
            if (!mergeRemoteIntoLocal(prepared, clientId)) {
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = mergeMsKey,
                    stageStartedAt = mergeStartedAt,
                    startedAt = startedAt,
                    errorMessage = "Stack payload merge failed",
                    logMessage = "Stack payload merge failed"
                )
                return
            }
        }
        if (!historyDownload.json.isNullOrBlank()) {
            val prepared = runCatching { decryptAndPrepare(historyDownload.json.orEmpty()) }.getOrElse { t ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = mergeMsKey,
                    stageStartedAt = mergeStartedAt,
                    startedAt = startedAt,
                    errorMessage = t.message ?: "Decrypt failed",
                    logMessage = "Decrypt history failed"
                )
                return
            }
            if (!mergeRemoteIntoLocal(prepared, clientId)) {
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = mergeMsKey,
                    stageStartedAt = mergeStartedAt,
                    startedAt = startedAt,
                    errorMessage = "History payload merge failed",
                    logMessage = "History payload merge failed"
                )
                return
            }
        }
        val mergeMs = SystemClock.elapsedRealtime() - mergeStartedAt
        prefs.edit().putLong(mergeMsKey, mergeMs).apply()
        if (!stackDownload.json.isNullOrBlank() && stackEtag.isNotEmpty()) {
            prefs.edit().putString(etagStackKey, stackEtag).apply()
        }
        if (!historyDownload.json.isNullOrBlank() && historyEtag.isNotEmpty()) {
            prefs.edit().putString(etagHistoryKey, historyEtag).apply()
        }

        val remoteChanged = !stackDownload.json.isNullOrBlank() || !historyDownload.json.isNullOrBlank()
        if (!remoteChanged && !localStackChanged && !localHistoryChanged) {
            finishCloudSyncNoChanges(
                prefs = prefs,
                manifestId = manifestId,
                lastSyncKey = lastSyncKey,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                startedAt = startedAt
            )
            return
        }

        prefs.edit().putString(phaseKey, SyncPhase.PUSHING.name).apply()
        updateUi(manifestId)
        val pushStartedAt = SystemClock.elapsedRealtime()
        val mergeMutex = kotlinx.coroutines.sync.Mutex()
        var bytesUp = 0L

        suspend fun pushPart(
            partId: String,
            etagKey: String,
            build: suspend () -> Result<String>,
            label: String
        ): Pair<String?, Long> {
            val plaintext = build().getOrElse { throw it }
            val encrypted = encryptAndPrepare(plaintext)
            var bytesUp = encrypted.toByteArray(Charsets.UTF_8).size.toLong()
            val etag = prefs.getString(etagKey, "").orEmpty().trim()
            val upsert = cloud.upsertBackup(partId, encrypted, etag.takeIf { it.isNotEmpty() })
            val newEtag = upsert.getOrElse { error ->
                val msg = error.message.orEmpty()
                if (!msg.contains("412") && !msg.contains("409")) throw error
                prefs.edit().putString(phaseKey, SyncPhase.RETRYING_CONFLICT.name).apply()
                prefs.edit().putInt(retryKey, 1).apply()
                updateUi(manifestId)
                appendLog(prefs, manifestId, "ERROR", "$label conflict, retry")
                val latest = cloud.downloadBackupAlways(partId).getOrThrow()
                if (!latest.json.isNullOrBlank()) {
                    val prepared = decryptAndPrepare(latest.json.orEmpty())
                    mergeMutex.lock()
                    try {
                        check(mergeRemoteIntoLocal(prepared, clientId)) { "$label payload merge failed" }
                    } finally {
                        mergeMutex.unlock()
                    }
                }
                val retryPlain = build().getOrThrow()
                val retryEnc = encryptAndPrepare(retryPlain)
                bytesUp += retryEnc.toByteArray(Charsets.UTF_8).size.toLong()
                cloud.upsertBackup(partId, retryEnc, latest.etag).getOrThrow()
                latest.etag
            }?.orEmpty()?.trim()
            return newEtag to bytesUp
        }

        try {
            val (stackResult, historyResult) = coroutineScope {
                val stackDeferred = async(Dispatchers.IO) {
                    if (localStackChanged) pushPart(stackId, etagStackKey, { buildStackBackupJson() }, "STACK") else null
                }
                val historyDeferred = async(Dispatchers.IO) {
                    if (localHistoryChanged) pushPart(historyId, etagHistoryKey, { buildHistoryBackupJson() }, "HISTORY") else null
                }
                stackDeferred.await() to historyDeferred.await()
            }
            stackResult?.first?.let { if (it.isNotBlank()) prefs.edit().putString(etagStackKey, it).apply() }
            historyResult?.first?.let { if (it.isNotBlank()) prefs.edit().putString(etagHistoryKey, it).apply() }
            bytesUp = (stackResult?.second ?: 0L) + (historyResult?.second ?: 0L)
            prefs.edit().putLong(bytesUploadedKey, bytesUp).apply()
        } catch (t: Throwable) {
            abortCloudSync(
                prefs = prefs,
                manifestId = manifestId,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                stageMsKey = pushMsKey,
                stageStartedAt = pushStartedAt,
                startedAt = startedAt,
                errorMessage = t.message ?: "Upload failed",
                logMessage = "Upload failed"
            )
            return
        }
        prefs.edit().putLong(pushMsKey, SystemClock.elapsedRealtime() - pushStartedAt).apply()
        prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
        prefs.edit().remove(lastErrorKey).apply()
        prefs.edit().putString(phaseKey, SyncPhase.DONE.name).apply()
        prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        markSyncActivity()
        setLoading(false)
        appendLog(prefs, manifestId, "DONE", "OK • up ${bytesUp}B • down ${bytesDown}B • total ${prefs.getLong(totalMsKeyFor(manifestId), 0L)}ms")
        updateUi(manifestId)
        rescheduleNotifications()
    }

    private suspend fun mergeRemoteIntoLocal(json: String, clientId: java.util.UUID): Boolean {
        val decoded = OAKBackupJson.decodeCompat(json).getOrElse { return false }
        val clientIdString = clientId.toString()
        val localSupplements = repository.getAllSupplementsForSync(clientIdString).associateBy { it.id.toString().lowercase(Locale.ROOT) }
        val localRecordList = repository.getAllRecordsForSync(clientIdString)
        val localRecords = localRecordList.associateBy { it.id.lowercase(Locale.ROOT) }
        val localRecordsByDoseKey = localRecordList
            .groupBy { DoseEventKey.make(it.supplementId, it.date) }
            .mapNotNull { (key, list) -> list.maxByOrNull { it.updatedAtEpochMs }?.let { key to it } }
            .toMap()
        
        decoded.stack.forEach { remote ->
            val remoteId = remote.id.lowercase(Locale.ROOT)
            val local = localSupplements[remoteId]
            val remoteUpdatedAt = remote.updatedAtEpochMs
            val remoteDeletedAt = remote.deletedAtEpochMs
            if (remoteDeletedAt != null) {
                val localDeletedAt = local?.deletedAtEpochMs ?: 0L
                if (remoteDeletedAt > localDeletedAt) {
                    val updated = (local ?: makeLocalFromRemote(remote, clientId)).copy(
                        updatedAtEpochMs = maxOf(remoteUpdatedAt, remoteDeletedAt),
                        deletedAtEpochMs = remoteDeletedAt
                    )
                    repository.saveSupplement(updated)
                }
                return@forEach
            }
            
            if (local == null) {
                repository.saveSupplement(makeLocalFromRemote(remote, clientId))
                return@forEach
            }
            
            val localTs = maxOf(local.updatedAtEpochMs, local.deletedAtEpochMs ?: 0L)
            val fields = remote.modifiedFields
            val shouldUpdate = if (fields != null) {
                remoteUpdatedAt > local.updatedAtEpochMs
            } else {
                remoteUpdatedAt > localTs
            }
            if (shouldUpdate) {
                val f = fields
                val hasField = { name: String -> f == null || f.contains(name) }
                repository.updateSupplement(
                    local.copy(
                        name = if (hasField("name")) remote.name else local.name,
                        startDate = if (hasField("startDate")) runCatching { LocalDate.parse(remote.startDate) }.getOrElse { local.startDate } else local.startDate,
                        cycleConfig = if (hasField("cycle")) local.cycleConfig.copy(
                            isContinuous = remote.cycle.isContinuous,
                            daysOn = remote.cycle.daysOn,
                            daysOff = remote.cycle.daysOff,
                            durationMonths = remote.cycle.durationMonths ?: local.cycleConfig.durationMonths,
                            weeklyRecurrence = run {
                                val mask = remote.cycle.weeklyWeekdaysMask ?: return@run null
                                val interval = remote.cycle.weeklyIntervalWeeks ?: return@run null
                                val anchor = remote.cycle.weeklyAnchorDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } ?: return@run null
                                WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
                            },
                            intervalDays = remote.cycle.intervalDays ?: local.cycleConfig.intervalDays
                        ) else local.cycleConfig,
                        dailyDose = if (hasField("dailyDose")) remote.dailyDose else local.dailyDose,
                        intakeTime = if (hasField("intakeTime")) remote.intakeTime else local.intakeTime,
                        lastTakenLocalDate = if (hasField("lastTakenLocalDate")) remote.lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: local.lastTakenLocalDate else local.lastTakenLocalDate,
                        updatedAtEpochMs = remoteUpdatedAt,
                        deletedAtEpochMs = null
                    )
                )
            }
        }
        
        decoded.history.forEach { remote ->
            val remoteId = remote.id.lowercase(Locale.ROOT)
            val normalizedSupplementId = remote.supplementId.lowercase(Locale.ROOT)
            val remoteDoseKey = DoseEventKey.make(normalizedSupplementId, remote.dateEpochMs)
            val remoteUpdatedAt = remote.updatedAtEpochMs.takeIf { it > 0L } ?: remote.dateEpochMs
            val localByKey = localRecordsByDoseKey[remoteDoseKey]
            if (localByKey != null) {
                if (remoteUpdatedAt <= localByKey.updatedAtEpochMs) return@forEach
                val updated = localByKey.copy(
                    supplementId = normalizedSupplementId,
                    date = remote.dateEpochMs,
                    status = remote.status,
                    updatedAtEpochMs = remoteUpdatedAt
                )
                repository.insertIntakeRecord(
                    updated
                )
                repository.deleteDuplicateIntakeRecords(
                    supplementId = normalizedSupplementId,
                    date = remote.dateEpochMs,
                    keepId = updated.id
                )
                return@forEach
            }

            val local = localRecords[remoteId]
            val localUpdatedAt = local?.updatedAtEpochMs ?: 0L
            if (remoteUpdatedAt <= localUpdatedAt) return@forEach
            val inserted = com.example.supplementtracker.domain.repository.IntakeRecord(
                id = remoteId,
                supplementId = normalizedSupplementId,
                date = remote.dateEpochMs,
                status = remote.status,
                updatedAtEpochMs = remoteUpdatedAt
            )
            repository.insertIntakeRecord(inserted)
            repository.deleteDuplicateIntakeRecords(
                supplementId = normalizedSupplementId,
                date = remote.dateEpochMs,
                keepId = inserted.id
            )
        }
        return true
    }

    private fun makeLocalFromRemote(remote: OAKBackupSupplementDTO, clientId: java.util.UUID): UserSupplement {
        val weekly = run {
            val mask = remote.cycle.weeklyWeekdaysMask ?: return@run null
            val interval = remote.cycle.weeklyIntervalWeeks ?: return@run null
            val anchor = remote.cycle.weeklyAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@run null
            WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
        }
        val cycle = CycleConfig(
            daysOn = remote.cycle.daysOn,
            daysOff = remote.cycle.daysOff,
            isContinuous = remote.cycle.isContinuous,
            durationMonths = remote.cycle.durationMonths,
            weeklyRecurrence = weekly,
            intervalDays = remote.cycle.intervalDays
        )
        return UserSupplement(
            id = runCatching { java.util.UUID.fromString(remote.id) }.getOrElse {
                com.example.supplementtracker.domain.util.StableId.uuidFromString(
                    remote.id.trim().lowercase(Locale.ROOT)
                )
            },
            clientId = clientId,
            name = remote.name,
            startDate = runCatching { LocalDate.parse(remote.startDate) }.getOrElse { LocalDate.now() },
            cycleConfig = cycle,
            dailyDose = remote.dailyDose,
            intakeTime = remote.intakeTime,
            lastTakenLocalDate = remote.lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            updatedAtEpochMs = remote.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            deletedAtEpochMs = remote.deletedAtEpochMs
        )
    }

    suspend fun hasLocalChangesSince(clientId: java.util.UUID, lastSyncEpochMs: Long): Boolean {
        if (lastSyncEpochMs <= 0L) return true
        if (hasLocalStackChangesSince(clientId, lastSyncEpochMs)) return true
        return hasLocalHistoryChangesSince(clientId, lastSyncEpochMs)
    }

    private suspend fun hasLocalStackChangesSince(clientId: java.util.UUID, lastSyncEpochMs: Long): Boolean {
        if (lastSyncEpochMs <= 0L) return true
        val clientIdString = clientId.toString()
        val supplements = repository.getAllSupplementsForSync(clientIdString)
        return supplements.any { maxOf(it.updatedAtEpochMs, it.deletedAtEpochMs ?: 0L) > lastSyncEpochMs }
    }

    private suspend fun hasLocalHistoryChangesSince(clientId: java.util.UUID, lastSyncEpochMs: Long): Boolean {
        if (lastSyncEpochMs <= 0L) return true
        val clientIdString = clientId.toString()
        val records = repository.getAllRecordsForSync(clientIdString)
        return records.any { it.updatedAtEpochMs > lastSyncEpochMs }
    }

    private fun markSyncActivity() {
        val prefs = OakPrefs.get(context)
        prefs.edit().putLong("cloudSyncLastActivityEpochMs", System.currentTimeMillis()).apply()
    }
}
