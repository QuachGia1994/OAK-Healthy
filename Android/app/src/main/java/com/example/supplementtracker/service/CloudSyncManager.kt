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

    private data class HttpResponse(val code: Int, val body: String, val etag: String?)

    private fun performRequest(
        url: URL,
        method: String,
        key: String,
        body: String? = null,
        ifMatchEtag: String? = null,
        ifNoneMatchEtag: String? = null
    ): HttpResponse {
        val connection = (url.openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Master-Key", key)
            val match = ifMatchEtag.orEmpty().trim()
            if (match.isNotEmpty()) connection.setRequestProperty("If-Match", match)
            val noneMatch = ifNoneMatchEtag.orEmpty().trim()
            if (noneMatch.isNotEmpty()) connection.setRequestProperty("If-None-Match", noneMatch)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            } else {
                connection.doInput = true
            }
            val code = connection.responseCode
            val etag = connection.getHeaderField("ETag")?.trim()
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(code = code, body = responseBody, etag = etag)
        } finally {
            connection.disconnect()
        }
    }
    
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
                        val response = performRequest(url = URL(BASE_URL), method = "POST", key = key, body = jsonString)
                        if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
                        JSONObject(response.body).getJSONObject("metadata").getString("id")
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
                    val response = performRequest(url = URL("$BASE_URL/$id"), method = "PUT", key = key, body = jsonString)
                    if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
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
                        val response = performRequest(
                            url = URL("$BASE_URL/$id"),
                            method = "PUT",
                            key = key,
                            body = jsonString,
                            ifMatchEtag = tag
                        )
                        if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
                        response.etag
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
                        val response = performRequest(url = URL("$BASE_URL/$id/latest"), method = "GET", key = key)
                        if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
                        JSONObject(response.body).get("record").toString()
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
                        val response = performRequest(
                            url = URL("$BASE_URL/$id/latest"),
                            method = "GET",
                            key = key,
                            ifNoneMatchEtag = tag
                        )
                        val newTag = response.etag ?: tag
                        if (response.code == 304) return@executeWithRetry CloudDownload(null, newTag)
                        if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
                        val json = JSONObject(response.body).get("record").toString()
                        CloudDownload(json, newTag)
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
                        val response = performRequest(url = URL("$BASE_URL/$id/latest"), method = "GET", key = key)
                        if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
                        val json = JSONObject(response.body).get("record").toString()
                        CloudDownload(json, response.etag)
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
                    val response = performRequest(url = URL("$BASE_URL/$id"), method = "DELETE", key = key)
                    if (response.code == 404) return@executeWithRetry
                    if (response.code !in 200..299) throw HttpStatusError(response.code, response.body)
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
