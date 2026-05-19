package com.example.supplementtracker.service

import org.json.JSONObject

data class CloudSyncManifest(
    val v: Int,
    val stackBinId: String,
    val historyBinId: String
)

object CloudSyncManifestCodec {
    fun encode(stackBinId: String, historyBinId: String): String {
        return JSONObject()
            .put("v", 1)
            .put("stackBinId", stackBinId)
            .put("historyBinId", historyBinId)
            .toString()
    }

    fun decode(json: String): CloudSyncManifest {
        val obj = JSONObject(json)
        val v = obj.optInt("v", 1)
        val stack = obj.optString("stackBinId").trim()
        val history = obj.optString("historyBinId").trim()
        if (stack.isEmpty() || history.isEmpty()) throw CloudSyncCryptoError.InvalidPayload("Missing stackBinId/historyBinId")
        return CloudSyncManifest(v = v, stackBinId = stack, historyBinId = history)
    }
}

