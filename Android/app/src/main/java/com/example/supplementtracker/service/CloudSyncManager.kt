package com.example.supplementtracker.service

import com.example.supplementtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CloudSyncManager {
    suspend fun uploadBackup(jsonString: String): Result<String> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Master-Key", key)
                }
                connection.outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("Server error ($code): $body")
                val root = JSONObject(body)
                root.getJSONObject("metadata").getString("id")
            }
        }
    }

    suspend fun downloadBackup(binId: String): Result<String> {
        val key = BuildConfig.JSONBIN_API_KEY.trim()
        val id = binId.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Missing access key"))
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Invalid binId"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("$BASE_URL/$id/latest")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    doInput = true
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Master-Key", key)
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("Server error ($code): $body")
                val record = JSONObject(body).get("record")
                when (record) {
                    is JSONObject -> record.toString()
                    else -> record.toString()
                }
            }
        }
    }

    companion object {
        const val BASE_URL = "https://api.jsonbin.io/v3/b"
    }
}
