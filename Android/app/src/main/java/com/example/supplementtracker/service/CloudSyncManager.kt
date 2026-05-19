package com.example.supplementtracker.service

import com.example.supplementtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.math.min
import kotlin.random.Random

data class CloudDownload(val json: String?, val etag: String?)

class CloudSyncManager {
    private class HttpStatusError(val code: Int, val body: String) : Exception("Server error ($code): $body")
    
    private suspend fun <T> executeWithRetry(block: () -> T): T {
        var attempt = 0
        var last: Throwable? = null
        while (attempt < 3) {
            attempt += 1
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                val retryable = t is IOException ||
                    t is SocketTimeoutException ||
                    (t is HttpStatusError && (t.code == 408 || t.code == 429 || t.code in 500..599))
                if (!retryable || attempt >= 3) throw t
                val base = 400L * (1L shl (attempt - 1))
                val jitter = Random.nextLong(0, 200)
                delay(min(4_000L, base + jitter))
            }
        }
        throw last ?: IllegalStateException("Retry failed")
    }
    
    suspend fun uploadBackup(jsonString: String): Result<String> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(
                    executeWithRetry {
                        val connection = (URL(BASE_URL).openConnection() as HttpURLConnection)
                        try {
                            connection.requestMethod = "POST"
                            connection.doOutput = true
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.setRequestProperty("X-Master-Key", key)
                            connection.outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                            val code = connection.responseCode
                            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            if (code !in 200..299) throw HttpStatusError(code, body)
                            val root = JSONObject(body)
                            root.getJSONObject("metadata").getString("id")
                        } finally {
                            connection.disconnect()
                        }
                    }
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun upsertBackup(binId: String, jsonString: String): Result<Unit> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            try {
                executeWithRetry {
                    val url = URL("$BASE_URL/$id")
                    val connection = (url.openConnection() as HttpURLConnection)
                    try {
                        connection.requestMethod = "PUT"
                        connection.doOutput = true
                        connection.connectTimeout = 8_000
                        connection.readTimeout = 12_000
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("X-Master-Key", key)
                        connection.outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (code !in 200..299) throw HttpStatusError(code, body)
                    } finally {
                        connection.disconnect()
                    }
                }
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun upsertBackup(binId: String, jsonString: String, ifMatchEtag: String?): Result<String?> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        val tag = ifMatchEtag.orEmpty().trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(
                    executeWithRetry {
                        val url = URL("$BASE_URL/$id")
                        val connection = (url.openConnection() as HttpURLConnection)
                        try {
                            connection.requestMethod = "PUT"
                            connection.doOutput = true
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.setRequestProperty("X-Master-Key", key)
                            if (tag.isNotEmpty()) connection.setRequestProperty("If-Match", tag)
                            connection.outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                            val code = connection.responseCode
                            val newEtag = connection.getHeaderField("ETag")?.trim()
                            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            if (code !in 200..299) throw HttpStatusError(code, body)
                            newEtag
                        } finally {
                            connection.disconnect()
                        }
                    }
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    suspend fun downloadBackup(binId: String): Result<String> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(
                    executeWithRetry {
                        val url = URL("$BASE_URL/$id/latest")
                        val connection = (url.openConnection() as HttpURLConnection)
                        try {
                            connection.requestMethod = "GET"
                            connection.doInput = true
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty("X-Master-Key", key)
                            val code = connection.responseCode
                            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            if (code !in 200..299) throw HttpStatusError(code, body)
                            val record = JSONObject(body).get("record")
                            record.toString()
                        } finally {
                            connection.disconnect()
                        }
                    }
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    suspend fun downloadBackupIfChanged(binId: String, etag: String?): Result<CloudDownload> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        val tag = etag.orEmpty().trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(
                    executeWithRetry {
                        val url = URL("$BASE_URL/$id/latest")
                        val connection = (url.openConnection() as HttpURLConnection)
                        try {
                            connection.requestMethod = "GET"
                            connection.doInput = true
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty("X-Master-Key", key)
                            if (tag.isNotEmpty()) connection.setRequestProperty("If-None-Match", tag)
                            val code = connection.responseCode
                            val newEtag = connection.getHeaderField("ETag")?.trim()
                            if (code == 304) return@executeWithRetry CloudDownload(null, newEtag ?: tag)
                            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            if (code !in 200..299) throw HttpStatusError(code, body)
                            val record = JSONObject(body).get("record")
                            val json = record.toString()
                            CloudDownload(json, newEtag ?: tag)
                        } finally {
                            connection.disconnect()
                        }
                    }
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun downloadBackupAlways(binId: String): Result<CloudDownload> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(
                    executeWithRetry {
                        val url = URL("$BASE_URL/$id/latest")
                        val connection = (url.openConnection() as HttpURLConnection)
                        try {
                            connection.requestMethod = "GET"
                            connection.doInput = true
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty("X-Master-Key", key)
                            val code = connection.responseCode
                            val newEtag = connection.getHeaderField("ETag")?.trim()
                            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            if (code !in 200..299) throw HttpStatusError(code, body)
                            val record = JSONObject(body).get("record")
                            val json = record.toString()
                            CloudDownload(json, newEtag)
                        } finally {
                            connection.disconnect()
                        }
                    }
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun deleteBackup(binId: String): Result<Unit> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            try {
                executeWithRetry {
                    val url = URL("$BASE_URL/$id")
                    val connection = (url.openConnection() as HttpURLConnection)
                    try {
                        connection.requestMethod = "DELETE"
                        connection.doInput = true
                        connection.connectTimeout = 8_000
                        connection.readTimeout = 12_000
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("X-Master-Key", key)
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (code == 404) return@executeWithRetry
                        if (code !in 200..299) throw HttpStatusError(code, body)
                    } finally {
                        connection.disconnect()
                    }
                }
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    companion object {
        const val BASE_URL = "https://api.jsonbin.io/v3/b"
    }
}
