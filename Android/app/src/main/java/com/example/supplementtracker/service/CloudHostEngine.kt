package com.example.supplementtracker.service

import android.content.Context
import com.example.supplementtracker.R
import java.util.UUID

/** Orchestrates creation/update of the hosted manifest and its stack/history bins. */
class CloudHostEngine(
    private val context: Context,
    private val getHostedBinId: () -> String?,
    private val setHostedBinId: (String?) -> Unit,
    private val buildStackBackupJson: suspend () -> Result<String>,
    private val buildHistoryBackupJson: suspend () -> Result<String>,
    private val buildFullBackupJson: suspend () -> Result<String>,
    private val updateUi: suspend (String) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val appendLog: (android.content.SharedPreferences, String, String, String) -> Unit,
    private val setMessage: (String) -> Unit
) {
    suspend fun hostData() {
        setLoading(true)
        val manifestId = getHostedBinId()?.trim().orEmpty()
        val prefs = OakPrefs.get(context)
        val stackPlain = buildStackBackupJson().getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_export_stack_failed))
            return
        }
        val historyPlain = buildHistoryBackupJson().getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_export_history_failed))
            return
        }

        fun encryptPrepared(plaintext: String): String = CloudSyncCrypto.wrapForUploadIfEnabled(context, plaintext)

        val stackEncrypted = runCatching { encryptPrepared(stackPlain) }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_stack_failed))
            return
        }
        val historyEncrypted = runCatching { encryptPrepared(historyPlain) }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_history_failed))
            return
        }

        if (manifestId.isNotEmpty()) {
            updateExisting(prefs, manifestId, stackEncrypted, historyEncrypted)
            return
        }

        val stackId = CloudSyncManager().uploadBackup(stackEncrypted).getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_upload_stack_failed))
            return
        }
        val historyId = CloudSyncManager().uploadBackup(historyEncrypted).getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_upload_history_failed))
            return
        }
        val manifestPlain = CloudSyncManifestCodec.encode(stackId, historyId)
        val manifestEncrypted = runCatching { encryptPrepared(manifestPlain) }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_manifest_failed))
            return
        }
        val newManifestId = CloudSyncManager().uploadBackup(manifestEncrypted).getOrElse { error ->
            setLoading(false)
            setMessage(error.message ?: context.getString(R.string.cloud_host_upload_manifest_failed))
            return
        }

        setLoading(false)
        val oldManifestId = getHostedBinId()?.trim().orEmpty()
        setHostedBinId(newManifestId)
        val editor = prefs.edit()
            .putString("cloudSyncHostedBinId", newManifestId)
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
        val manifestId = getHostedBinId()?.trim().orEmpty()
        if (manifestId.isEmpty()) return Result.failure(IllegalStateException(context.getString(R.string.cloud_revoke_missing_code)))
        val prefs = OakPrefs.get(context)
        val stackId = prefs.getString("cloudSyncStackBinId_$manifestId", "").orEmpty().trim()
        val historyId = prefs.getString("cloudSyncHistoryBinId_$manifestId", "").orEmpty().trim()
        val manager = CloudSyncManager()
        return runCatching {
            manager.deleteBackup(manifestId).getOrThrow()
            if (stackId.isNotEmpty()) manager.deleteBackup(stackId).getOrThrow()
            if (historyId.isNotEmpty()) manager.deleteBackup(historyId).getOrThrow()
            setHostedBinId(null)
            prefs.edit()
                .remove("cloudSyncHostedBinId")
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
        historyEncrypted: String
    ) {
        val stackKey = "cloudSyncStackBinId_$manifestId"
        val historyKey = "cloudSyncHistoryBinId_$manifestId"
        var stackId = prefs.getString(stackKey, "").orEmpty().trim()
        var historyId = prefs.getString(historyKey, "").orEmpty().trim()
        if (stackId.isEmpty() || historyId.isEmpty()) {
            val manifestDownload = CloudSyncManager().downloadBackupAlways(manifestId).getOrElse { error ->
                setLoading(false)
                setMessage(error.message ?: context.getString(R.string.cloud_host_load_manifest_failed))
                return
            }
            val decrypted = runCatching { CloudSyncCrypto.unwrapDownloadedIfNeeded(context, manifestDownload.json ?: "") }.getOrElse {
                setLoading(false)
                setMessage(it.message ?: context.getString(R.string.cloud_host_decrypt_manifest_failed))
                return
            }
            val prepared = runCatching { CloudSyncPayloadCodec.decompressIfNeeded(decrypted) }.getOrElse {
                setLoading(false)
                setMessage(it.message ?: context.getString(R.string.cloud_host_decode_manifest_failed))
                return
            }
            val decoded = runCatching { CloudSyncManifestCodec.decode(prepared) }.getOrNull()
            if (decoded == null) {
                val legacyPlain = buildFullBackupJson().getOrElse { error ->
                    setLoading(false)
                    setMessage(error.message ?: context.getString(R.string.cloud_host_export_failed))
                    return
                }
                val legacyEncrypted = runCatching { CloudSyncCrypto.wrapForUploadIfEnabled(context, legacyPlain) }.getOrElse {
                    setLoading(false)
                    setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_failed))
                    return
                }
                val result = CloudSyncManager().upsertBackup(manifestId, legacyEncrypted)
                setLoading(false)
                result.onSuccess {
                    prefs.edit().putLong("cloudSyncLastSyncEpochMs_$manifestId", System.currentTimeMillis()).remove("cloudSyncLastError_$manifestId").apply()
                    updateUi(manifestId)
                    appendLog(prefs, manifestId, "HOST", "Host legacy update OK")
                    setMessage(context.getString(R.string.cloud_host_update_existing_success))
                }.onFailure {
                    prefs.edit().putString("cloudSyncLastError_$manifestId", it.message ?: context.getString(R.string.error_unknown)).apply()
                    updateUi(manifestId)
                    appendLog(prefs, manifestId, "ERROR", "Host legacy update failed")
                    setMessage(context.getString(R.string.cloud_host_failed_format, it.message ?: context.getString(R.string.error_unknown)))
                }
                return
            }
            stackId = decoded.stackBinId
            historyId = decoded.historyBinId
            prefs.edit().putString(stackKey, stackId).putString(historyKey, historyId).apply()
        }

        appendLog(prefs, manifestId, "HOST", "Host update start")
        val upsertStack = CloudSyncManager().upsertBackup(stackId, stackEncrypted)
        val upsertHistory = CloudSyncManager().upsertBackup(historyId, historyEncrypted)
        val manifestPlain = CloudSyncManifestCodec.encode(stackId, historyId)
        val manifestEncrypted = runCatching { CloudSyncCrypto.wrapForUploadIfEnabled(context, manifestPlain) }.getOrElse {
            setLoading(false)
            setMessage(it.message ?: context.getString(R.string.cloud_host_encrypt_manifest_failed))
            return
        }
        val upsertManifest = CloudSyncManager().upsertBackup(manifestId, manifestEncrypted)
        setLoading(false)

        val error = upsertStack.exceptionOrNull() ?: upsertHistory.exceptionOrNull() ?: upsertManifest.exceptionOrNull()
        if (error != null) {
            prefs.edit().putString("cloudSyncLastError_$manifestId", error.message ?: context.getString(R.string.error_unknown)).apply()
            updateUi(manifestId)
            appendLog(prefs, manifestId, "ERROR", "Host update failed")
            setMessage(context.getString(R.string.cloud_host_failed_format, error.message ?: context.getString(R.string.error_unknown)))
            return
        }

        prefs.edit().putLong("cloudSyncLastSyncEpochMs_$manifestId", System.currentTimeMillis()).remove("cloudSyncLastError_$manifestId").apply()
        updateUi(manifestId)
        appendLog(prefs, manifestId, "HOST", "Host update OK")
        setMessage(context.getString(R.string.cloud_host_update_existing_success))
    }
}
