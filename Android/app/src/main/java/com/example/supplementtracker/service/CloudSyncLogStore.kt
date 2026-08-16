package com.example.supplementtracker.service

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object CloudSyncLogStore {
    fun append(prefs: SharedPreferences, binId: String, phase: String, message: String) {
        val id = binId.trim()
        if (id.isEmpty()) return
        val key = "cloudSyncLog_$id"
        val existing = prefs.getString(key, null)
        val array = runCatching {
            if (existing.isNullOrBlank()) JSONArray() else JSONArray(existing)
        }.getOrElse { JSONArray() }
        val now = System.currentTimeMillis()
        if (isDuplicateRecent(array, phase, message, now)) return
        array.put(JSONObject().put("ts", now).put("phase", phase).put("msg", message))
        prefs.edit().putString(key, trimmed(array).toString()).apply()
    }

    private fun isDuplicateRecent(array: JSONArray, phase: String, message: String, now: Long): Boolean {
        if (array.length() == 0) return false
        val last = runCatching { array.getJSONObject(array.length() - 1) }.getOrNull() ?: return false
        return last.optString("phase") == phase &&
            last.optString("msg") == message &&
            now - last.optLong("ts") < 15_000L
    }

    private fun trimmed(array: JSONArray): JSONArray {
        val result = JSONArray()
        val start = (array.length() - 30).coerceAtLeast(0)
        for (index in start until array.length()) {
            result.put(array.getJSONObject(index))
        }
        return result
    }
}
