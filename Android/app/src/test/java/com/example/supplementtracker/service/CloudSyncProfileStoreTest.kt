package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class CloudSyncProfileStoreTest {
    private val clientA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val clientB = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun links_areIsolatedByClientAndHostedWins() {
        val store = CloudSyncProfileStore(FakeProfileStorage())
        store.setLinkedBinId(clientA, "linked-a")
        store.setHostedBinId(clientA, "hosted-a")
        store.setLinkedBinId(clientB, "linked-b")

        assertEquals("hosted-a", store.activeManifestId(clientA))
        assertEquals("linked-b", store.activeManifestId(clientB))
    }

    @Test
    fun nullClient_hasNoCloudLink() {
        val store = CloudSyncProfileStore(FakeProfileStorage())
        assertNull(store.activeManifestId(null))
    }

    @Test
    fun clearLinks_removesOnlyTargetClient() {
        val store = CloudSyncProfileStore(FakeProfileStorage())
        store.setHostedBinId(clientA, "hosted-a")
        store.setHostedBinId(clientB, "hosted-b")
        store.clearLinks(clientA)

        assertNull(store.activeManifestId(clientA))
        assertEquals("hosted-b", store.activeManifestId(clientB))
    }

    @Test
    fun legacyLinks_migrateOnlyToRequestedClient() {
        val storage = FakeProfileStorage(
            mutableMapOf(
                "cloudSyncHostedBinId" to "legacy-host",
                "cloudSyncLinkedBinId" to "legacy-link"
            )
        )
        val store = CloudSyncProfileStore(storage)

        assertEquals("legacy-host", store.links(clientA).hostedBinId)
        assertNull(store.links(clientB).hostedBinId)
        assertNull(storage.values["cloudSyncHostedBinId"])
        assertNull(storage.values["cloudSyncLinkedBinId"])
    }

    @Test
    fun legacyLinks_doNotOverwriteExistingScopedLinks() {
        val storage = FakeProfileStorage(
            mutableMapOf(
                "cloudSyncHostedBinId" to "legacy-host",
                "cloudSyncHostedBinId_client_${clientA}" to "scoped-host"
            )
        )
        val store = CloudSyncProfileStore(storage)
        assertEquals("scoped-host", store.links(clientA).hostedBinId)
    }

    private class FakeProfileStorage(
        val values: MutableMap<String, String> = mutableMapOf()
    ) : CloudSyncProfileStorage {
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }
}
