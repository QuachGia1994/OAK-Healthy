package com.example.supplementtracker.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncPayloadCodecTest {
    @Test
    fun decompressIfNeeded_returnsInputWhenNotObject() {
        val input = "[1,2,3]"
        val out = CloudSyncPayloadCodec.decompressIfNeeded(input)
        assertEquals(input, out)
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

    @Test
    fun compressIfUseful_returnsInputWhenSmall() {
        val input = """{"k":"v"}"""
        val out = CloudSyncPayloadCodec.compressIfUseful(input)
        assertEquals(input, out)
    }

    @Test
    fun compressIfUseful_roundTripWhenLarge() {
        val big = "a".repeat(50_000)
        val input = JSONObject().put("k", big).toString()
        val compressed = CloudSyncPayloadCodec.compressIfUseful(input)
        val roundTrip = CloudSyncPayloadCodec.decompressIfNeeded(compressed)
        assertEquals(input, roundTrip)
    }
}
