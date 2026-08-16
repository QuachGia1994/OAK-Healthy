package com.example.supplementtracker.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class CloudSyncPayloadCodecTest {
    @Test
    fun decompressIfNeeded_returnsInputWhenNotObject() {
        val input = "[1,2,3]"
        val out = CloudSyncPayloadCodec.decompressIfNeeded(input)
        assertEquals(input, out)
    }

    @Test
    fun decompressIfNeeded_returnsObjectWithoutCompressionEnvelope() {
        val input = JSONObject().put("schemaVersion", 1).put("value", "plain").toString()

        assertEquals(input, CloudSyncPayloadCodec.decompressIfNeeded(input))
    }

    @Test
    fun decompressIfNeeded_inflatesValidPayload() {
        val original = "{\"schemaVersion\":1,\"value\":\"compressed\"}"
        val input = JSONObject().put(
            "z",
            JSONObject().put("ct", Base64.getEncoder().encodeToString(deflate(original)))
        ).toString()

        assertEquals(original, CloudSyncPayloadCodec.decompressIfNeeded(input))
    }

    @Test
    fun decompressIfNeeded_rejectsOversizedInflatedPayload() {
        val oversized = "a".repeat(10 * 1024 * 1024 + 1)
        val input = JSONObject().put(
            "z",
            JSONObject().put("ct", Base64.getEncoder().encodeToString(deflate(oversized)))
        ).toString()

        try {
            CloudSyncPayloadCodec.decompressIfNeeded(input)
            throw AssertionError("Expected exception")
        } catch (e: CloudSyncCryptoError.InvalidPayload) {
            assertTrue(e.reason.contains("10MB"))
        }
    }

    @Test
    fun decompressIfNeeded_throwsWhenMissingCT() {
        val input = JSONObject().put("z", JSONObject().put("v", 1).put("alg", "ZLIB")).toString()
        try {
            CloudSyncPayloadCodec.decompressIfNeeded(input)
            throw AssertionError("Expected exception")
        } catch (e: CloudSyncCryptoError.InvalidPayload) {
            assertEquals("Missing z.ct", e.reason)
        }
    }

    @Test
    fun decompressIfNeeded_throwsWhenInvalidBase64() {
        val input = JSONObject().put("z", JSONObject().put("ct", "%%%")).toString()
        try {
            CloudSyncPayloadCodec.decompressIfNeeded(input)
            throw AssertionError("Expected exception")
        } catch (e: CloudSyncCryptoError.InvalidPayload) {
            assertTrue(e.reason.contains("base64"))
        }
    }

    private fun deflate(value: String): ByteArray {
        return ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { it.write(value.toByteArray()) }
            output.toByteArray()
        }
    }
}
