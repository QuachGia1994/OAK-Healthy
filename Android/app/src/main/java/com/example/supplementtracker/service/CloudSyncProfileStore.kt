package com.example.supplementtracker.service

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

data class CloudSyncProfileLinks(
    val hostedBinId: String?,
    val linkedBinId: String?
) {
    val activeManifestId: String?
        get() = hostedBinId ?: linkedBinId
}

internal interface CloudSyncProfileStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class CloudSyncProfileStore internal constructor(
    private val storage: CloudSyncProfileStorage
) {
    constructor(context: Context) : this(
        SharedPreferencesProfileStorage(OakPrefs.get(context))
    )

    fun links(clientId: UUID?): CloudSyncProfileLinks {
        if (clientId == null) return CloudSyncProfileLinks(null, null)
        migrateLegacyLinks(clientId)
        return CloudSyncProfileLinks(
            hostedBinId = readScoped(HOSTED_PREFIX, clientId),
            linkedBinId = readScoped(LINKED_PREFIX, clientId)
        )
    }
    fun activeManifestId(clientId: UUID?): String? = links(clientId).activeManifestId

    fun setHostedBinId(clientId: UUID, binId: String?) {
        writeScoped(HOSTED_PREFIX, clientId, binId)
    }

    fun setLinkedBinId(clientId: UUID, binId: String?) {
        writeScoped(LINKED_PREFIX, clientId, binId)
    }

    fun clearLinks(clientId: UUID) {
        storage.remove(scopedKey(HOSTED_PREFIX, clientId))
        storage.remove(scopedKey(LINKED_PREFIX, clientId))
    }

    private fun migrateLegacyLinks(clientId: UUID) {
        migrateLegacyValue(LEGACY_HOSTED_KEY, HOSTED_PREFIX, clientId)
        migrateLegacyValue(LEGACY_LINKED_KEY, LINKED_PREFIX, clientId)
    }

    private fun migrateLegacyValue(legacyKey: String, prefix: String, clientId: UUID) {
        val scopedKey = scopedKey(prefix, clientId)
        val scopedValue = normalize(storage.getString(scopedKey))
        val legacyValue = normalize(storage.getString(legacyKey))
        if (scopedValue == null && legacyValue != null) {
            storage.putString(scopedKey, legacyValue)
        }
        storage.remove(legacyKey)
    }

    private fun readScoped(prefix: String, clientId: UUID): String? {
        return normalize(storage.getString(scopedKey(prefix, clientId)))
    }
    private fun writeScoped(prefix: String, clientId: UUID, binId: String?) {
        val key = scopedKey(prefix, clientId)
        val value = normalize(binId)
        if (value == null) storage.remove(key) else storage.putString(key, value)
    }

    private fun scopedKey(prefix: String, clientId: UUID): String {
        return "$prefix${clientId.toString().lowercase()}"
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val HOSTED_PREFIX = "cloudSyncHostedBinId_client_"
        const val LINKED_PREFIX = "cloudSyncLinkedBinId_client_"
        const val LEGACY_HOSTED_KEY = "cloudSyncHostedBinId"
        const val LEGACY_LINKED_KEY = "cloudSyncLinkedBinId"
    }
}

private class SharedPreferencesProfileStorage(
    private val prefs: SharedPreferences
) : CloudSyncProfileStorage {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
