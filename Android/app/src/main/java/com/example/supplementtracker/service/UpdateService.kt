package com.example.supplementtracker.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thông tin phiên bản ứng dụng từ xa.
 */
data class AppUpdateInfo(
    val version: String,
    val updateUrl: String,
    val forceUpdate: Boolean,
    val releaseNotes: String
)

data class UpdateConfig(
    val latestVersion: String,
    val isForceUpdate: Boolean,
    val releaseNotes: String,
    val updateUrl: String
)

/**
 * Dịch vụ kiểm tra cập nhật phiên bản (Android).
 */
class UpdateService(
    context: Context
) {
    private val prefs = OakPrefs.get(context)
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable = _isUpdateAvailable.asStateFlow()

    /**
     * Kiểm tra phiên bản mới bất đồng bộ.
     */
    suspend fun checkForUpdates(currentVersionName: String) {
        val config = fetchConfig() ?: return
        if (!isVersionNewer(config.latestVersion, currentVersionName)) return
        if (!config.isForceUpdate && getSkippedUpdateVersion() == config.latestVersion) return

        withContext(Dispatchers.Main) {
            _updateInfo.value = AppUpdateInfo(
                version = config.latestVersion,
                updateUrl = config.updateUrl,
                forceUpdate = config.isForceUpdate,
                releaseNotes = config.releaseNotes
            )
            _isUpdateAvailable.value = true
        }
    }

    fun dismissUpdate() {
        _isUpdateAvailable.value = false
    }

    fun skipUpdate(version: String) {
        if (version.isBlank()) {
            dismissUpdate()
            return
        }
        prefs.edit().putString(KEY_SKIPPED_VERSION, version).apply()
        dismissUpdate()
    }

    private fun getSkippedUpdateVersion(): String? {
        return prefs.getString(KEY_SKIPPED_VERSION, null)
    }

    // ponytail: only trust update URLs from known hosts.
    private val allowedHosts = listOf("github.com", "githubusercontent.com", "oakhealthy.com")

    private fun isUpdateUrlAllowed(urlString: String): Boolean {
        return runCatching {
            val host = URL(urlString).host?.lowercase() ?: return@runCatching false
            allowedHosts.any { host.endsWith(it) }
        }.getOrDefault(false)
    }

    private suspend fun fetchConfig(): UpdateConfig? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(CONFIG_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
            }

            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val updateUrl = json.getString("update_url")
                if (!isUpdateUrlAllowed(updateUrl)) return@withContext null
                UpdateConfig(
                    latestVersion = json.getString("latest_version"),
                    isForceUpdate = json.getBoolean("is_force_update"),
                    releaseNotes = json.getString("release_notes"),
                    updateUrl = updateUrl
                )
            }
        }.getOrNull()
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        return compareVersions(remote, current) > 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val count = maxOf(aParts.size, bParts.size)

        for (i in 0 until count) {
            val left = aParts.getOrElse(i) { 0 }
            val right = bParts.getOrElse(i) { 0 }
            if (left != right) return left - right
        }
        return 0
    }

    private companion object {
        private const val PREFS_NAME = "oak_update_prefs"
        private const val KEY_SKIPPED_VERSION = "SkippedUpdateVersion"
        private const val CONFIG_URL =
            "https://gist.githubusercontent.com/QuachGia1994/901e36f6bab91729d5dd0e2ccce7202f/raw/oak_update.json"
    }
}
