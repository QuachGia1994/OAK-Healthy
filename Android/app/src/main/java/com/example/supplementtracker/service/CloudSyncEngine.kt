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

    private data class SyncKeys(val manifestId: String) {
        val etagManifest = "cloudSyncEtagV2_$manifestId"
        val etagStack = "cloudSyncEtagStackV2_$manifestId"
        val etagHistory = "cloudSyncEtagHistoryV2_$manifestId"
        val stackIdKey = "cloudSyncStackBinId_$manifestId"
        val historyIdKey = "cloudSyncHistoryBinId_$manifestId"
        val lastSync = "cloudSyncLastSyncEpochMs_$manifestId"
        val lastAttempt = "cloudSyncLastAttemptEpochMs_$manifestId"
        val lastError = "cloudSyncLastError_$manifestId"
        val phase = "cloudSyncPhase_$manifestId"
        val retry = "cloudSyncConflictRetryCount_$manifestId"
        val bytesDown = "cloudSyncBytesDownloaded_$manifestId"
        val bytesUp = "cloudSyncBytesUploaded_$manifestId"
        val pullMs = "cloudSyncPullMs_$manifestId"
        val mergeMs = "cloudSyncMergeMs_$manifestId"
        val pushMs = "cloudSyncPushMs_$manifestId"
        val totalMs = "cloudSyncTotalMs_$manifestId"
    }

    private data class ManifestResolveResult(
        val stackId: String,
        val historyId: String,
        val manifestDownload: CloudDownload,
        val bytesFromManifest: Int
    )

    private data class PullOutcome(
        val stackDownload: CloudDownload,
        val historyDownload: CloudDownload,
        val stackEtag: String,
        val historyEtag: String,
        val bytesDown: Int,
        val remoteChanged: Boolean
    )

    private suspend fun performSyncTwoWay(binId: String) {
        val clientId = currentClientId() ?: return
        val prefs = OakPrefs.get(context)
        val manifestId = binId.trim()
        if (manifestId.isEmpty()) return
        setLoading(true)
        val keys = SyncKeys(manifestId)
        val lastSyncEpochMs = prefs.getLong(keys.lastSync, 0L)
        val localStackChanged = hasLocalStackChangesSince(clientId, lastSyncEpochMs)
        val localHistoryChanged = hasLocalHistoryChangesSince(clientId, lastSyncEpochMs)
        val startedAt = SystemClock.elapsedRealtime()
        initSyncAttempt(prefs, keys)
        val resolved = resolveManifestIds(
            prefs, keys, clientId, localStackChanged, localHistoryChanged, startedAt
        ) ?: return
        val pullOutcome = pullStackAndHistory(prefs, keys, resolved, startedAt) ?: return
        if (!mergePulledPayloads(prefs, keys, pullOutcome, clientId, startedAt)) return
        if (!pullOutcome.remoteChanged && !localStackChanged && !localHistoryChanged) {
            finishCloudSyncNoChanges(
                prefs, keys.manifestId, keys.lastSync, keys.lastError, keys.phase, startedAt
            )
            return
        }
        pushChangedParts(
            prefs, keys, resolved.stackId, resolved.historyId,
            localStackChanged, localHistoryChanged,
            pullOutcome.bytesDown, clientId, startedAt
        )
    }

    private suspend fun initSyncAttempt(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys
    ) {
        prefs.edit()
            .putLong(keys.lastAttempt, System.currentTimeMillis())
            .putString(keys.phase, SyncPhase.PULLING.name)
            .putInt(keys.retry, 0)
            .putLong(keys.bytesDown, 0L)
            .putLong(keys.bytesUp, 0L)
            .putLong(keys.pullMs, 0L)
            .putLong(keys.mergeMs, 0L)
            .putLong(keys.pushMs, 0L)
            .putLong(keys.totalMs, 0L)
            .apply()
        appendLog(prefs, keys.manifestId, "START", "Sync start")
        updateUi(keys.manifestId)
    }

    private suspend fun resolveManifestIds(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        clientId: java.util.UUID,
        localStackChanged: Boolean,
        localHistoryChanged: Boolean,
        startedAt: Long
    ): ManifestResolveResult? {
        var stackId = prefs.getString(keys.stackIdKey, "").orEmpty().trim()
        var historyId = prefs.getString(keys.historyIdKey, "").orEmpty().trim()
        val pullStartedAt = SystemClock.elapsedRealtime()
        val previousManifestEtag = prefs.getString(keys.etagManifest, "").orEmpty().trim()
        val manifestDownload = downloadManifest(
            prefs, keys, stackId, historyId, previousManifestEtag, pullStartedAt, startedAt
        ) ?: return null
        val manifestEtag = manifestDownload.etag.orEmpty().trim().ifEmpty { previousManifestEtag }
        if (!manifestDownload.json.isNullOrBlank()) {
            val outcome = processManifestJson(
                prefs, keys, clientId, manifestDownload, manifestEtag,
                localStackChanged, localHistoryChanged, pullStartedAt, startedAt
            ) ?: return null
            if (outcome.finished) return null
            stackId = outcome.stackId
            historyId = outcome.historyId
        }
        return requireStackHistoryIds(
            prefs, keys, stackId, historyId, manifestDownload, pullStartedAt, startedAt
        )
    }

    private suspend fun requireStackHistoryIds(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        stackId: String,
        historyId: String,
        manifestDownload: CloudDownload,
        pullStartedAt: Long,
        startedAt: Long
    ): ManifestResolveResult? {
        if (stackId.isEmpty() || historyId.isEmpty()) {
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.pullMs,
                pullStartedAt, startedAt, "Missing stack/history id", "Missing stack/history id"
            )
            return null
        }
        val bytesFromManifest = manifestDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0
        return ManifestResolveResult(stackId, historyId, manifestDownload, bytesFromManifest)
    }

    private data class ManifestProcessOutcome(
        val finished: Boolean,
        val stackId: String = "",
        val historyId: String = ""
    )

    private suspend fun downloadManifest(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        stackId: String,
        historyId: String,
        previousManifestEtag: String,
        pullStartedAt: Long,
        startedAt: Long
    ): CloudDownload? {
        return if (stackId.isEmpty() || historyId.isEmpty()) {
            CloudSyncManager().downloadBackupAlways(keys.manifestId).getOrElse { error ->
                abortCloudSync(
                    prefs, keys.manifestId, keys.lastError, keys.phase, keys.pullMs,
                    pullStartedAt, startedAt,
                    error.message ?: "Manifest load failed", "Manifest load failed"
                )
                return null
            }
        } else {
            CloudSyncManager().downloadBackupIfChanged(keys.manifestId, previousManifestEtag).getOrElse { error ->
                abortCloudSync(
                    prefs, keys.manifestId, keys.lastError, keys.phase, keys.pullMs,
                    pullStartedAt, startedAt,
                    error.message ?: "Manifest pull failed", "Manifest pull failed"
                )
                return null
            }
        }
    }

    private suspend fun processManifestJson(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        clientId: java.util.UUID,
        manifestDownload: CloudDownload,
        manifestEtag: String,
        localStackChanged: Boolean,
        localHistoryChanged: Boolean,
        pullStartedAt: Long,
        startedAt: Long
    ): ManifestProcessOutcome? {
        val manifestJson = manifestDownload.json.orEmpty()
        val prepared = decryptManifestOrAbort(prefs, keys, manifestJson, pullStartedAt, startedAt)
            ?: return null
        val decoded = runCatching { CloudSyncManifestCodec.decode(prepared) }.getOrNull()
        if (decoded == null) {
            handleLegacyFullPayload(
                prefs, keys, clientId, manifestJson, manifestEtag,
                localStackChanged, localHistoryChanged, startedAt
            )
            return ManifestProcessOutcome(finished = true)
        }
        return persistDecodedIds(prefs, keys, decoded.stackBinId, decoded.historyBinId, manifestEtag)
    }

    private suspend fun decryptManifestOrAbort(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        manifestJson: String,
        pullStartedAt: Long,
        startedAt: Long
    ): String? {
        return runCatching { decryptAndPrepare(manifestJson) }.getOrElse { t ->
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.pullMs,
                pullStartedAt, startedAt,
                t.message ?: "Manifest decrypt failed", "Manifest decrypt failed"
            )
            null
        }
    }

    private fun persistDecodedIds(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        stackId: String,
        historyId: String,
        manifestEtag: String
    ): ManifestProcessOutcome {
        prefs.edit().putString(keys.stackIdKey, stackId).putString(keys.historyIdKey, historyId).apply()
        if (manifestEtag.isNotEmpty()) prefs.edit().putString(keys.etagManifest, manifestEtag).apply()
        return ManifestProcessOutcome(finished = false, stackId = stackId, historyId = historyId)
    }

    private suspend fun handleLegacyFullPayload(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        clientId: java.util.UUID,
        manifestJson: String,
        manifestEtag: String,
        localStackChanged: Boolean,
        localHistoryChanged: Boolean,
        startedAt: Long
    ) {
        val bytesDown = manifestJson.toByteArray(Charsets.UTF_8).size
        prefs.edit().putLong(keys.bytesDown, bytesDown.toLong()).apply()
        prefs.edit().putString(keys.phase, SyncPhase.MERGING.name).apply()
        updateUi(keys.manifestId)
        if (!mergeLegacyPayload(prefs, keys, clientId, manifestJson, startedAt)) return
        if (manifestEtag.isNotEmpty()) prefs.edit().putString(keys.etagManifest, manifestEtag).apply()
        val remoteChanged = manifestJson.isNotBlank()
        if (!remoteChanged && !localStackChanged && !localHistoryChanged) {
            finishCloudSyncNoChanges(
                prefs, keys.manifestId, keys.lastSync, keys.lastError, keys.phase, startedAt
            )
            return
        }
        pushLegacyFullPayload(prefs, keys, manifestEtag, bytesDown, startedAt)
    }

    private suspend fun mergeLegacyPayload(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        clientId: java.util.UUID,
        manifestJson: String,
        startedAt: Long
    ): Boolean {
        val mergeStartedAt = SystemClock.elapsedRealtime()
        val legacyPlain = runCatching { decryptAndPrepare(manifestJson) }.getOrElse { t ->
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.mergeMs,
                mergeStartedAt, startedAt, t.message ?: "Decrypt failed", "Decrypt failed"
            )
            return false
        }
        if (!mergeRemoteIntoLocal(legacyPlain, clientId)) {
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.mergeMs,
                mergeStartedAt, startedAt, "Legacy payload merge failed", "Legacy payload merge failed"
            )
            return false
        }
        prefs.edit().putLong(keys.mergeMs, SystemClock.elapsedRealtime() - mergeStartedAt).apply()
        return true
    }

    private suspend fun pushLegacyFullPayload(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        manifestEtag: String,
        bytesDown: Int,
        startedAt: Long
    ) {
        prefs.edit().putString(keys.phase, SyncPhase.PUSHING.name).apply()
        updateUi(keys.manifestId)
        val pushStartedAt = SystemClock.elapsedRealtime()
        val fullEnc = buildAndEncryptFullBackup(prefs, keys, pushStartedAt, startedAt) ?: return
        val bytesUp = fullEnc.toByteArray(Charsets.UTF_8).size.toLong()
        prefs.edit().putLong(keys.bytesUp, bytesUp).apply()
        if (!upsertLegacyManifest(prefs, keys, fullEnc, manifestEtag, pushStartedAt, startedAt)) return
        prefs.edit().putLong(keys.pushMs, SystemClock.elapsedRealtime() - pushStartedAt).apply()
        finishCloudSyncSuccess(prefs, keys, bytesUp, bytesDown, startedAt)
    }

    private suspend fun buildAndEncryptFullBackup(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        pushStartedAt: Long,
        startedAt: Long
    ): String? {
        val fullPlain = buildFullBackupJson().getOrElse {
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.pushMs,
                pushStartedAt, startedAt, it.message ?: "Export failed", "Export failed"
            )
            return null
        }
        return runCatching { encryptAndPrepare(fullPlain) }.getOrElse { t ->
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.pushMs,
                pushStartedAt, startedAt, t.message ?: "Encrypt failed", "Encrypt failed"
            )
            null
        }
    }

    private suspend fun upsertLegacyManifest(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        fullEnc: String,
        manifestEtag: String,
        pushStartedAt: Long,
        startedAt: Long
    ): Boolean {
        val upsert = CloudSyncManager().upsertBackup(
            keys.manifestId, fullEnc, manifestEtag.takeIf { it.isNotEmpty() }
        )
        upsert.getOrElse { error ->
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.pushMs,
                pushStartedAt, startedAt, error.message ?: "Upload failed", "Upload failed"
            )
            return false
        }?.orEmpty()?.trim()?.let {
            if (it.isNotEmpty()) prefs.edit().putString(keys.etagManifest, it).apply()
        }
        return true
    }

    private suspend fun pullStackAndHistory(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        resolved: ManifestResolveResult,
        startedAt: Long
    ): PullOutcome? {
        val pullStartedAt = SystemClock.elapsedRealtime()
        val prevStackEtag = prefs.getString(keys.etagStack, "").orEmpty().trim()
        val prevHistoryEtag = prefs.getString(keys.etagHistory, "").orEmpty().trim()
        val downloads = parallelPullParts(
            prefs, keys, resolved, prevStackEtag, prevHistoryEtag, pullStartedAt, startedAt
        ) ?: return null
        val (stackDownload, historyDownload) = downloads
        prefs.edit().putLong(keys.pullMs, SystemClock.elapsedRealtime() - pullStartedAt).apply()
        val stackEtag = stackDownload.etag.orEmpty().trim().ifEmpty { prevStackEtag }
        val historyEtag = historyDownload.etag.orEmpty().trim().ifEmpty { prevHistoryEtag }
        val bytesDown = resolved.bytesFromManifest +
            (stackDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
            (historyDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0)
        prefs.edit().putLong(keys.bytesDown, bytesDown.toLong()).apply()
        val remoteChanged = !stackDownload.json.isNullOrBlank() || !historyDownload.json.isNullOrBlank()
        return PullOutcome(stackDownload, historyDownload, stackEtag, historyEtag, bytesDown, remoteChanged)
    }

    private suspend fun parallelPullParts(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        resolved: ManifestResolveResult,
        prevStackEtag: String,
        prevHistoryEtag: String,
        pullStartedAt: Long,
        startedAt: Long
    ): Pair<CloudDownload, CloudDownload>? {
        val cloud = CloudSyncManager()
        return coroutineScope {
            val stackDeferred = async(Dispatchers.IO) {
                cloud.downloadBackupIfChanged(resolved.stackId, prevStackEtag)
            }
            val historyDeferred = async(Dispatchers.IO) {
                cloud.downloadBackupIfChanged(resolved.historyId, prevHistoryEtag)
            }
            val stack = awaitPullOrAbort(
                prefs, keys, stackDeferred, historyDeferred, pullStartedAt, startedAt, "Stack pull failed", cancelOther = true
            ) ?: return@coroutineScope null
            val history = awaitPullOrAbort(
                prefs, keys, historyDeferred, null, pullStartedAt, startedAt, "History pull failed", cancelOther = false
            ) ?: return@coroutineScope null
            stack to history
        }
    }

    private suspend fun awaitPullOrAbort(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        deferred: kotlinx.coroutines.Deferred<Result<CloudDownload>>,
        other: kotlinx.coroutines.Deferred<*>?,
        pullStartedAt: Long,
        startedAt: Long,
        logMsg: String,
        cancelOther: Boolean
    ): CloudDownload? {
        return deferred.await().getOrElse { error ->
            if (cancelOther) other?.cancel()
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.pullMs,
                pullStartedAt, startedAt, error.message ?: logMsg, logMsg
            )
            null
        }
    }

    private suspend fun mergePulledPayloads(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        outcome: PullOutcome,
        clientId: java.util.UUID,
        startedAt: Long
    ): Boolean {
        prefs.edit().putString(keys.phase, SyncPhase.MERGING.name).apply()
        updateUi(keys.manifestId)
        val mergeStartedAt = SystemClock.elapsedRealtime()
        if (!decryptAndMergePart(
                prefs, keys, outcome.stackDownload.json, clientId,
                mergeStartedAt, startedAt, "Decrypt stack failed", "Stack payload merge failed"
            )
        ) return false
        if (!decryptAndMergePart(
                prefs, keys, outcome.historyDownload.json, clientId,
                mergeStartedAt, startedAt, "Decrypt history failed", "History payload merge failed"
            )
        ) return false
        prefs.edit().putLong(keys.mergeMs, SystemClock.elapsedRealtime() - mergeStartedAt).apply()
        if (!outcome.stackDownload.json.isNullOrBlank() && outcome.stackEtag.isNotEmpty()) {
            prefs.edit().putString(keys.etagStack, outcome.stackEtag).apply()
        }
        if (!outcome.historyDownload.json.isNullOrBlank() && outcome.historyEtag.isNotEmpty()) {
            prefs.edit().putString(keys.etagHistory, outcome.historyEtag).apply()
        }
        return true
    }

    private suspend fun decryptAndMergePart(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        json: String?,
        clientId: java.util.UUID,
        mergeStartedAt: Long,
        startedAt: Long,
        decryptLog: String,
        mergeLog: String
    ): Boolean {
        if (json.isNullOrBlank()) return true
        val prepared = runCatching { decryptAndPrepare(json) }.getOrElse { t ->
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.mergeMs,
                mergeStartedAt, startedAt, t.message ?: "Decrypt failed", decryptLog
            )
            return false
        }
        if (!mergeRemoteIntoLocal(prepared, clientId)) {
            abortCloudSync(
                prefs, keys.manifestId, keys.lastError, keys.phase, keys.mergeMs,
                mergeStartedAt, startedAt, mergeLog, mergeLog
            )
            return false
        }
        return true
    }

    private suspend fun pushChangedParts(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        stackId: String,
        historyId: String,
        localStackChanged: Boolean,
        localHistoryChanged: Boolean,
        bytesDown: Int,
        clientId: java.util.UUID,
        startedAt: Long
    ) {
        prefs.edit().putString(keys.phase, SyncPhase.PUSHING.name).apply()
        updateUi(keys.manifestId)
        val pushStartedAt = SystemClock.elapsedRealtime()
        val cloud = CloudSyncManager()
        val mergeMutex = kotlinx.coroutines.sync.Mutex()
        try {
            val bytesUp = executeParallelPush(
                cloud, prefs, keys, stackId, historyId,
                localStackChanged, localHistoryChanged, clientId, mergeMutex
            )
            prefs.edit().putLong(keys.bytesUp, bytesUp)
                .putLong(keys.pushMs, SystemClock.elapsedRealtime() - pushStartedAt).apply()
            finishCloudSyncSuccess(prefs, keys, bytesUp, bytesDown, startedAt)
        } catch (t: Throwable) {
            abortCloudSync(prefs, keys.manifestId, keys.lastError, keys.phase, keys.pushMs,
                pushStartedAt, startedAt, t.message ?: "Upload failed", "Upload failed")
        }
    }

    private suspend fun executeParallelPush(
        cloud: CloudSyncManager,
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        stackId: String,
        historyId: String,
        localStackChanged: Boolean,
        localHistoryChanged: Boolean,
        clientId: java.util.UUID,
        mergeMutex: kotlinx.coroutines.sync.Mutex
    ): Long {
        val (stackResult, historyResult) = coroutineScope {
            val stackDeferred = async(Dispatchers.IO) {
                if (localStackChanged) {
                    pushPart(cloud, prefs, keys, stackId, keys.etagStack, { buildStackBackupJson() }, "STACK", clientId, mergeMutex)
                } else null
            }
            val historyDeferred = async(Dispatchers.IO) {
                if (localHistoryChanged) {
                    pushPart(cloud, prefs, keys, historyId, keys.etagHistory, { buildHistoryBackupJson() }, "HISTORY", clientId, mergeMutex)
                } else null
            }
            stackDeferred.await() to historyDeferred.await()
        }
        stackResult?.first?.let { if (it.isNotBlank()) prefs.edit().putString(keys.etagStack, it).apply() }
        historyResult?.first?.let { if (it.isNotBlank()) prefs.edit().putString(keys.etagHistory, it).apply() }
        return (stackResult?.second ?: 0L) + (historyResult?.second ?: 0L)
    }

    private suspend fun pushPart(
        cloud: CloudSyncManager,
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        partId: String,
        etagKey: String,
        build: suspend () -> Result<String>,
        label: String,
        clientId: java.util.UUID,
        mergeMutex: kotlinx.coroutines.sync.Mutex
    ): Pair<String?, Long> {
        val plaintext = build().getOrElse { throw it }
        val encrypted = encryptAndPrepare(plaintext)
        var bytesUp = encrypted.toByteArray(Charsets.UTF_8).size.toLong()
        val etag = prefs.getString(etagKey, "").orEmpty().trim()
        val upsert = cloud.upsertBackup(partId, encrypted, etag.takeIf { it.isNotEmpty() })
        val newEtag = upsert.getOrElse { error ->
            val msg = error.message.orEmpty()
            if (!msg.contains("412") && !msg.contains("409")) throw error
            val (retryEtag, extra) = handlePushConflict(
                cloud, prefs, keys, partId, label, clientId, mergeMutex, build
            )
            bytesUp += extra
            return retryEtag to bytesUp
        }?.orEmpty()?.trim()
        return newEtag to bytesUp
    }

    private suspend fun handlePushConflict(
        cloud: CloudSyncManager,
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        partId: String,
        label: String,
        clientId: java.util.UUID,
        mergeMutex: kotlinx.coroutines.sync.Mutex,
        build: suspend () -> Result<String>
    ): Pair<String?, Long> {
        prefs.edit().putString(keys.phase, SyncPhase.RETRYING_CONFLICT.name).apply()
        prefs.edit().putInt(keys.retry, 1).apply()
        updateUi(keys.manifestId)
        appendLog(prefs, keys.manifestId, "ERROR", "$label conflict, retry")
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
        val extraBytes = retryEnc.toByteArray(Charsets.UTF_8).size.toLong()
        val retryEtag = cloud.upsertBackup(partId, retryEnc, latest.etag).getOrThrow()
        return retryEtag to extraBytes
    }

    private suspend fun finishCloudSyncSuccess(
        prefs: android.content.SharedPreferences,
        keys: SyncKeys,
        bytesUp: Long,
        bytesDown: Int,
        startedAt: Long
    ) {
        prefs.edit().putLong(keys.lastSync, System.currentTimeMillis()).apply()
        prefs.edit().remove(keys.lastError).apply()
        prefs.edit().putString(keys.phase, SyncPhase.DONE.name).apply()
        prefs.edit().putLong(totalMsKeyFor(keys.manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        markSyncActivity()
        setLoading(false)
        val total = prefs.getLong(totalMsKeyFor(keys.manifestId), 0L)
        appendLog(prefs, keys.manifestId, "DONE", "OK • up ${bytesUp}B • down ${bytesDown}B • total ${total}ms")
        updateUi(keys.manifestId)
        rescheduleNotifications()
    }

    private suspend fun mergeRemoteIntoLocal(json: String, clientId: java.util.UUID): Boolean {
        val decoded = OAKBackupJson.decodeCompat(json).getOrElse { return false }
        val clientIdString = clientId.toString()
        val localSupplements = repository.getAllSupplementsForSync(clientIdString)
            .associateBy { it.id.toString().lowercase(Locale.ROOT) }
        val localRecordList = repository.getAllRecordsForSync(clientIdString)
        val localRecords = localRecordList.associateBy { it.id.lowercase(Locale.ROOT) }
        val localRecordsByDoseKey = localRecordList
            .groupBy { DoseEventKey.make(it.supplementId, it.date) }
            .mapNotNull { (key, list) -> list.maxByOrNull { it.updatedAtEpochMs }?.let { key to it } }
            .toMap()
        mergeStackItems(decoded.stack, localSupplements, clientId)
        mergeHistoryItems(decoded.history, localRecords, localRecordsByDoseKey)
        return true
    }

    private suspend fun mergeStackItems(
        stack: List<OAKBackupSupplementDTO>,
        localSupplements: Map<String, UserSupplement>,
        clientId: java.util.UUID
    ) {
        stack.forEach { remote -> applyRemoteSupplement(remote, localSupplements, clientId) }
    }

    private suspend fun applyRemoteSupplement(
        remote: OAKBackupSupplementDTO,
        localSupplements: Map<String, UserSupplement>,
        clientId: java.util.UUID
    ) {
        val remoteId = remote.id.lowercase(Locale.ROOT)
        val local = localSupplements[remoteId]
        val remoteUpdatedAt = remote.updatedAtEpochMs
        val remoteDeletedAt = remote.deletedAtEpochMs
        if (remoteDeletedAt != null) {
            applyRemoteDeletion(remote, local, clientId, remoteUpdatedAt, remoteDeletedAt)
            return
        }
        if (local == null) {
            repository.saveSupplement(makeLocalFromRemote(remote, clientId))
            return
        }
        if (shouldUpdateSupplement(remote, local)) {
            repository.updateSupplement(buildUpdatedSupplement(remote, local))
        }
    }

    private suspend fun applyRemoteDeletion(
        remote: OAKBackupSupplementDTO,
        local: UserSupplement?,
        clientId: java.util.UUID,
        remoteUpdatedAt: Long,
        remoteDeletedAt: Long
    ) {
        val localDeletedAt = local?.deletedAtEpochMs ?: 0L
        if (remoteDeletedAt > localDeletedAt) {
            val updated = (local ?: makeLocalFromRemote(remote, clientId)).copy(
                updatedAtEpochMs = maxOf(remoteUpdatedAt, remoteDeletedAt),
                deletedAtEpochMs = remoteDeletedAt
            )
            repository.saveSupplement(updated)
        }
    }

    private fun shouldUpdateSupplement(remote: OAKBackupSupplementDTO, local: UserSupplement): Boolean {
        val remoteUpdatedAt = remote.updatedAtEpochMs
        val localTs = maxOf(local.updatedAtEpochMs, local.deletedAtEpochMs ?: 0L)
        return remoteUpdatedAt > localTs
    }

    private fun buildUpdatedSupplement(
        remote: OAKBackupSupplementDTO,
        local: UserSupplement
    ): UserSupplement {
        val f = remote.modifiedFields
        val hasField = { name: String -> f == null || f.contains(name) }
        return local.copy(
            name = if (hasField("name")) remote.name else local.name,
            startDate = if (hasField("startDate"))
                runCatching { LocalDate.parse(remote.startDate) }.getOrElse { local.startDate }
            else local.startDate,
            cycleConfig = if (hasField("cycle")) mergeCycleConfig(remote, local) else local.cycleConfig,
            dailyDose = if (hasField("dailyDose")) remote.dailyDose else local.dailyDose,
            intakeTime = if (hasField("intakeTime")) remote.intakeTime else local.intakeTime,
            lastTakenLocalDate = if (hasField("lastTakenLocalDate"))
                remote.lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: local.lastTakenLocalDate
            else local.lastTakenLocalDate,
            updatedAtEpochMs = remote.updatedAtEpochMs,
            deletedAtEpochMs = null
        )
    }

    private fun mergeCycleConfig(
        remote: OAKBackupSupplementDTO,
        local: UserSupplement
    ): CycleConfig {
        return local.cycleConfig.copy(
            isContinuous = remote.cycle.isContinuous,
            daysOn = remote.cycle.daysOn,
            daysOff = remote.cycle.daysOff,
            durationMonths = remote.cycle.durationMonths ?: local.cycleConfig.durationMonths,
            weeklyRecurrence = parseWeeklyRecurrence(remote),
            intervalDays = remote.cycle.intervalDays ?: local.cycleConfig.intervalDays
        )
    }

    private suspend fun mergeHistoryItems(
        history: List<OAKBackupHistoryDTO>,
        localRecords: Map<String, IntakeRecord>,
        localRecordsByDoseKey: Map<String, IntakeRecord>
    ) {
        history.forEach { remote ->
            applyRemoteHistory(remote, localRecords, localRecordsByDoseKey)
        }
    }

    private suspend fun applyRemoteHistory(
        remote: OAKBackupHistoryDTO,
        localRecords: Map<String, IntakeRecord>,
        localRecordsByDoseKey: Map<String, IntakeRecord>
    ) {
        val remoteId = remote.id.lowercase(Locale.ROOT)
        val normalizedSupplementId = remote.supplementId.lowercase(Locale.ROOT)
        val remoteDoseKey = DoseEventKey.make(normalizedSupplementId, remote.dateEpochMs)
        val remoteUpdatedAt = remote.updatedAtEpochMs.takeIf { it > 0L } ?: remote.dateEpochMs
        val localByKey = localRecordsByDoseKey[remoteDoseKey]
        if (localByKey != null) {
            upsertHistoryByKey(localByKey, normalizedSupplementId, remote, remoteUpdatedAt)
            return
        }
        upsertHistoryById(remoteId, normalizedSupplementId, remote, remoteUpdatedAt, localRecords)
    }

    private suspend fun upsertHistoryByKey(
        localByKey: IntakeRecord,
        normalizedSupplementId: String,
        remote: OAKBackupHistoryDTO,
        remoteUpdatedAt: Long
    ) {
        if (remoteUpdatedAt <= localByKey.updatedAtEpochMs) return
        val updated = localByKey.copy(
            supplementId = normalizedSupplementId,
            date = remote.dateEpochMs,
            status = remote.status,
            updatedAtEpochMs = remoteUpdatedAt
        )
        repository.insertIntakeRecord(updated)
        repository.deleteDuplicateIntakeRecords(
            supplementId = normalizedSupplementId,
            date = remote.dateEpochMs,
            keepId = updated.id
        )
    }

    private suspend fun upsertHistoryById(
        remoteId: String,
        normalizedSupplementId: String,
        remote: OAKBackupHistoryDTO,
        remoteUpdatedAt: Long,
        localRecords: Map<String, IntakeRecord>
    ) {
        val local = localRecords[remoteId]
        val localUpdatedAt = local?.updatedAtEpochMs ?: 0L
        if (remoteUpdatedAt <= localUpdatedAt) return
        val inserted = IntakeRecord(
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

    private fun makeLocalFromRemote(remote: OAKBackupSupplementDTO, clientId: java.util.UUID): UserSupplement {
        return UserSupplement(
            id = parseRemoteId(remote.id),
            clientId = clientId,
            name = remote.name,
            startDate = runCatching { LocalDate.parse(remote.startDate) }.getOrElse { LocalDate.now() },
            cycleConfig = cycleFromRemote(remote),
            dailyDose = remote.dailyDose,
            intakeTime = remote.intakeTime,
            lastTakenLocalDate = remote.lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            updatedAtEpochMs = remote.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            deletedAtEpochMs = remote.deletedAtEpochMs
        )
    }

    private fun parseRemoteId(rawId: String): java.util.UUID {
        return runCatching { java.util.UUID.fromString(rawId) }.getOrElse {
            StableId.uuidFromString(rawId.trim().lowercase(Locale.ROOT))
        }
    }

    private fun cycleFromRemote(remote: OAKBackupSupplementDTO): CycleConfig {
        val weekly = parseWeeklyRecurrence(remote)
        return CycleConfig(
            daysOn = remote.cycle.daysOn,
            daysOff = remote.cycle.daysOff,
            isContinuous = remote.cycle.isContinuous,
            durationMonths = remote.cycle.durationMonths,
            weeklyRecurrence = weekly,
            intervalDays = remote.cycle.intervalDays
        )
    }

    private fun parseWeeklyRecurrence(remote: OAKBackupSupplementDTO): WeeklyRecurrenceConfig? {
        val mask = remote.cycle.weeklyWeekdaysMask ?: return null
        val interval = remote.cycle.weeklyIntervalWeeks ?: return null
        val anchor = remote.cycle.weeklyAnchorDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return null
        return WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
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
