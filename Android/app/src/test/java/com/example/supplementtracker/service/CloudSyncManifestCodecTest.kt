package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
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
}

