package com.example.supplementtracker.service

import android.content.Context
import com.example.supplementtracker.R
import java.util.UUID

/** Orchestrates creation/update of the hosted manifest and its stack/history bins. */
class CloudHostEngine(
    private val context: Context,
    private val currentClientId: () -> UUID?,
    private val getHostedBinId: (UUID) -> String?,
    private val setHostedBinId: (UUID, String?) -> Unit,
    private val buildStackBackupJson: suspend (UUID) -> Result<String>,
    private val buildHistoryBackupJson: suspend (UUID) -> Result<String>,
    private val buildFullBackupJson: suspend (UUID) -> Result<String>,
    private val updateUi: suspend (String) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val appendLog: (android.content.SharedPreferences, String, String, String) -> Unit,
    private val setMessage: (String) -> Unit
) {
    suspend fun hostData() {
        setLoading(true)
        val clientId = currentClientId() ?: return abortMissingClient()
        val manifestId = getHostedBinId(clientId)?.trim().orEmpty()
        val prefs = OakPrefs.get(context)
        val stackEncrypted = encryptExport(
            buildStackBackupJson(clientId),
            R.string.cloud_host_export_stack_failed,
            R.string.cloud_host_encrypt_stack_failed
        ) ?: return
        val historyEncrypted = encryptExport(
            buildHistoryBackupJson(clientId),
            R.string.cloud_host_export_history_failed,
            R.string.cloud_host_encrypt_history_failed
        ) ?: return

        if (manifestId.isNotEmpty()) {
            updateExisting(prefs, manifestId, stackEncrypted, historyEncrypted, clientId)
            return
        }
        createNewHost(prefs, stackEncrypted, historyEncrypted, clientId)
    }

    private fun abortMissingClient() {
        setLoading(false)
        setMessage(context.getString(R.string.missing_active_client))
    }

    private suspend fun encryptExport(
        plainResult: Result<String>,
        exportFailRes: Int,
        encryptFailRes: Int
    ): String? {
        val plain = plainResult.getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(exportFailRes))
            return null
        }
        return runCatching { CloudSyncCrypto.wrapForUploadIfEnabled(context, plain) }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(encryptFailRes))
            null
        }
    }

    private suspend fun createNewHost(
        prefs: android.content.SharedPreferences,
        stackEncrypted: String,
        historyEncrypted: String,
        clientId: UUID
    ) {
        val stackId = uploadOrAbort(stackEncrypted, R.string.cloud_host_upload_stack_failed) ?: return
        val historyId = uploadOrAbort(historyEncrypted, R.string.cloud_host_upload_history_failed)
        if (historyId == null) {
            deleteUploadedBins(stackId)
            return
        }
        val manifestEncrypted = encryptManifest(stackId, historyId)
        if (manifestEncrypted == null) {
            deleteUploadedBins(stackId, historyId)
            return
        }
        val newManifestId = uploadOrAbort(manifestEncrypted, R.string.cloud_host_upload_manifest_failed)
        if (newManifestId == null) {
            deleteUploadedBins(stackId, historyId)
            return
        }
        persistNewHost(prefs, newManifestId, stackId, historyId, clientId)
    }

    private suspend fun deleteUploadedBins(vararg binIds: String) {
        val manager = CloudSyncManager()
        binIds.forEach { binId -> runCatching { manager.deleteBackup(binId).getOrThrow() } }
    }

    private suspend fun uploadOrAbort(payload: String, failRes: Int): String? {
        return CloudSyncManager().uploadBackup(payload).getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(failRes))
            return null
        }
    }

    private fun encryptManifest(stackId: String, historyId: String): String? {
        return runCatching {
            CloudSyncCrypto.wrapForUploadIfEnabled(
                context,
                CloudSyncManifestCodec.encode(stackId, historyId)
            )
        }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_manifest_failed))
            null
        }
    }

    private suspend fun persistNewHost(
        prefs: android.content.SharedPreferences,
        newManifestId: String,
        stackId: String,
        historyId: String,
        clientId: UUID
    ) {
        setLoading(false)
        val oldManifestId = getHostedBinId(clientId)?.trim().orEmpty()
        setHostedBinId(clientId, newManifestId)
        val editor = prefs.edit()
            .putString("cloudSyncStackBinId_$newManifestId", stackId)
            .putString("cloudSyncHistoryBinId_$newManifestId", historyId)
            .putLong("cloudSyncLastSyncEpochMs_$newManifestId", System.currentTimeMillis())
            .remove("cloudSyncLastError_$newManifestId")
        if (oldManifestId.isNotEmpty() && oldManifestId != newManifestId) {
            editor.remove("cloudSyncStackBinId_$oldManifestId")
                .remove("cloudSyncHistoryBinId_$oldManifestId")
                .remove("cloudSyncEtagV2_$oldManifestId")
                .remove("cloudSyncEtagStackV2_$oldManifestId")
                .remove("cloudSyncEtagHistoryV2_$oldManifestId")
                .remove("cloudSyncLastSeenRevV2_$oldManifestId")
                .remove("cloudSyncLastSyncEpochMs_$oldManifestId")
        }
        editor.apply()
        updateUi(newManifestId)
        appendLog(prefs, newManifestId, "HOST", "Host created OK")
        setMessage(context.getString(R.string.cloud_host_success))
    }

    suspend fun revokeHostedBin(): Result<Unit> {
        val clientId = currentClientId()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.missing_active_client)))
        val manifestId = getHostedBinId(clientId)?.trim().orEmpty()
        if (manifestId.isEmpty()) {
            return Result.failure(IllegalStateException(context.getString(R.string.cloud_revoke_missing_code)))
        }
        val prefs = OakPrefs.get(context)
        val stackId = prefs.getString("cloudSyncStackBinId_$manifestId", "").orEmpty().trim()
        val historyId = prefs.getString("cloudSyncHistoryBinId_$manifestId", "").orEmpty().trim()
        val manager = CloudSyncManager()
        return runCatching {
            manager.deleteBackup(manifestId).getOrThrow()
            if (stackId.isNotEmpty()) manager.deleteBackup(stackId).getOrThrow()
            if (historyId.isNotEmpty()) manager.deleteBackup(historyId).getOrThrow()
            setHostedBinId(clientId, null)
            prefs.edit()
                .remove("cloudSyncStackBinId_$manifestId")
                .remove("cloudSyncHistoryBinId_$manifestId")
                .remove("cloudSyncEtagV2_$manifestId")
                .remove("cloudSyncEtagStackV2_$manifestId")
                .remove("cloudSyncEtagHistoryV2_$manifestId")
                .apply()
            updateUi(manifestId)
            appendLog(prefs, manifestId, "HOST", "Host revoke OK")
        }
    }

    private suspend fun updateExisting(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        stackEncrypted: String,
        historyEncrypted: String,
        clientId: UUID
    ) {
        val resolved = resolveStackHistoryIds(prefs, manifestId, clientId) ?: return
        val (stackId, historyId) = resolved
        appendLog(prefs, manifestId, "HOST", "Host update start")
        val upsertStack = CloudSyncManager().upsertBackup(stackId, stackEncrypted)
        val upsertHistory = CloudSyncManager().upsertBackup(historyId, historyEncrypted)
        val manifestEncrypted = runCatching {
            CloudSyncCrypto.wrapForUploadIfEnabled(
                context,
                CloudSyncManifestCodec.encode(stackId, historyId)
            )
        }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_manifest_failed))
            return
        }
        val upsertManifest = CloudSyncManager().upsertBackup(manifestId, manifestEncrypted)
        finishUpdateExisting(prefs, manifestId, upsertStack, upsertHistory, upsertManifest)
    }

    private suspend fun resolveStackHistoryIds(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        clientId: UUID
    ): Pair<String, String>? {
        val stackKey = "cloudSyncStackBinId_$manifestId"
        val historyKey = "cloudSyncHistoryBinId_$manifestId"
        var stackId = prefs.getString(stackKey, "").orEmpty().trim()
        var historyId = prefs.getString(historyKey, "").orEmpty().trim()
        if (stackId.isNotEmpty() && historyId.isNotEmpty()) return stackId to historyId

        val manifestDownload = CloudSyncManager().downloadBackupAlways(manifestId).getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_load_manifest_failed))
            return null
        }
        val prepared = prepareManifestPayload(manifestDownload.json ?: "") ?: return null
        val decoded = runCatching { CloudSyncManifestCodec.decode(prepared) }.getOrNull()
        if (decoded == null) {
            updateLegacyFullPayload(prefs, manifestId, clientId)
            return null
        }
        stackId = decoded.stackBinId
        historyId = decoded.historyBinId
        prefs.edit().putString(stackKey, stackId).putString(historyKey, historyId).apply()
        return stackId to historyId
    }

    private fun prepareManifestPayload(rawJson: String): String? {
        val decrypted = runCatching {
            CloudSyncCrypto.unwrapDownloadedIfNeeded(context, rawJson)
        }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_decrypt_manifest_failed))
            return null
        }
        return runCatching { CloudSyncPayloadCodec.decompressIfNeeded(decrypted) }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_decode_manifest_failed))
            null
        }
    }

    private suspend fun updateLegacyFullPayload(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        clientId: UUID
    ) {
        val legacyPlain = buildFullBackupJson(clientId).getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_export_failed))
            return
        }
        val legacyEncrypted = runCatching {
            CloudSyncCrypto.wrapForUploadIfEnabled(context, legacyPlain)
        }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_failed))
            return
        }
        val result = CloudSyncManager().upsertBackup(manifestId, legacyEncrypted)
        setLoading(false)
        result.onSuccess { publishHostSuccess(prefs, manifestId, "Host legacy update OK") }
            .onFailure { publishHostFailure(prefs, manifestId, it, "Host legacy update failed") }
    }

    private suspend fun publishHostSuccess(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        logMsg: String
    ) {
        prefs.edit()
            .putLong("cloudSyncLastSyncEpochMs_$manifestId", System.currentTimeMillis())
            .remove("cloudSyncLastError_$manifestId")
            .apply()
        updateUi(manifestId)
        appendLog(prefs, manifestId, "HOST", logMsg)
        setMessage(context.getString(R.string.cloud_host_update_existing_success))
    }

    private suspend fun publishHostFailure(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        error: Throwable,
        logMsg: String
    ) {
        val msg = error.message ?: context.getString(R.string.error_unknown)
        prefs.edit().putString("cloudSyncLastError_$manifestId", msg).apply()
        updateUi(manifestId)
        appendLog(prefs, manifestId, "ERROR", logMsg)
        setMessage(context.getString(R.string.cloud_host_failed_format, msg))
    }

    private suspend fun finishUpdateExisting(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        upsertStack: Result<*>,
        upsertHistory: Result<*>,
        upsertManifest: Result<*>
    ) {
        setLoading(false)
        val error = upsertStack.exceptionOrNull()
            ?: upsertHistory.exceptionOrNull()
            ?: upsertManifest.exceptionOrNull()
        if (error != null) {
            publishHostFailure(prefs, manifestId, error, "Host update failed")
            return
        }
        publishHostSuccess(prefs, manifestId, "Host update OK")
    }
}
