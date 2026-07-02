package com.example.supplementtracker.service

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

object CloudSyncPayloadCodec {
    // ponytail: 10MB cap prevents zip bomb decompression.
    private const val MAX_DECOMPRESSED_SIZE = 10 * 1024 * 1024

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

    private fun inflate(input: ByteArray): ByteArray {
        InflaterInputStream(ByteArrayInputStream(input)).use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var totalRead = 0
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                totalRead += read
                if (totalRead > MAX_DECOMPRESSED_SIZE) {
                    throw CloudSyncCryptoError.InvalidPayload("Decompressed output exceeds 10MB limit")
                }
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }
    }
}
