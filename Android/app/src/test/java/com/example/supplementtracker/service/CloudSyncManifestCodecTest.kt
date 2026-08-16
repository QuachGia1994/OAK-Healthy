package com.example.supplementtracker.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncManifestCodecTest {
    @Test
    fun roundTrip_encodesAndDecodes() {
        val json = CloudSyncManifestCodec.encode(stackBinId = "stack", historyBinId = "history")
        val decoded = CloudSyncManifestCodec.decode(json)
        assertEquals(1, decoded.v)
        assertEquals("stack", decoded.stackBinId)
        assertEquals("history", decoded.historyBinId)
    }

    @Test
    fun decode_defaultsMissingVersionToOneAndTrimsIds() {
        val json = JSONObject()
            .put("stackBinId", " stack ")
            .put("historyBinId", " history ")
            .toString()

        val decoded = CloudSyncManifestCodec.decode(json)

        assertEquals(1, decoded.v)
        assertEquals("stack", decoded.stackBinId)
        assertEquals("history", decoded.historyBinId)
    }

    @Test
    fun decode_preservesExplicitVersion() {
        val json = JSONObject()
            .put("v", 2)
            .put("stackBinId", "stack")
            .put("historyBinId", "history")
            .toString()

        assertEquals(2, CloudSyncManifestCodec.decode(json).v)
    }

    @Test
    fun decode_rejectsMissingStackId() {
        assertMissingId(JSONObject().put("historyBinId", "history").toString())
    }

    @Test
    fun decode_rejectsMissingHistoryId() {
        assertMissingId(JSONObject().put("stackBinId", "stack").toString())
    }

    private fun assertMissingId(json: String) {
        try {
            CloudSyncManifestCodec.decode(json)
            throw AssertionError("Expected exception")
        } catch (e: CloudSyncCryptoError.InvalidPayload) {
            assertTrue(e.reason.contains("stackBinId/historyBinId"))
        }
    }
}

