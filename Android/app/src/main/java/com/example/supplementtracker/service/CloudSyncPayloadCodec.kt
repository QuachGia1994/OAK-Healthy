package com.example.supplementtracker.service

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object CloudSyncPayloadCodec {
    private const val thresholdBytes = 40_000

    fun compressIfUseful(plaintextJson: String): String {
        val bytes = plaintextJson.toByteArray(Charsets.UTF_8)
        if (bytes.size < thresholdBytes) return plaintextJson
        val compressed = deflate(bytes)
        val b64 = Base64.getEncoder().encodeToString(compressed)
        val wrapper = JSONObject()
            .put("z", JSONObject().put("v", 1).put("alg", "ZLIB").put("ct", b64))
            .toString()
        val wrapperBytes = wrapper.toByteArray(Charsets.UTF_8)
        if (wrapperBytes.size >= bytes.size - 1024) return plaintextJson
        return wrapper
    }

    fun decompressIfNeeded(payloadJson: String): String {
        val obj = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return payloadJson
        val z = obj.optJSONObject("z") ?: return payloadJson
        val b64 = z.optString("ct").trim()
        if (b64.isEmpty()) throw CloudSyncCryptoError.InvalidPayload("Missing z.ct")
        val compressed = runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
            ?: throw CloudSyncCryptoError.InvalidPayload("Invalid z.ct base64")
        val inflated = inflate(compressed)
        return inflated.toString(Charsets.UTF_8)
    }

    private fun deflate(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION, false)).use { it.write(input) }
        return out.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray {
        InflaterInputStream(ByteArrayInputStream(input)).use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }
    }
}

