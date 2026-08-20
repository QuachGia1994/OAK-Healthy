package com.example.supplementtracker.service

import com.example.supplementtracker.domain.model.ClientProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ClientProfileMutationEngineTest {
    private val clientA = ClientProfile(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        "Alice",
        1
    )
    private val clientB = ClientProfile(
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "Bob",
        2
    )

    @Test
    fun create_activatesOnlyAfterSuccessfulPersistence() = runBlocking {
        val events = mutableListOf<String>()
        val engine = engine(
            createProfile = {
                events += "persist"
                true
            },
            setCurrentClientId = { events += "activate:$it" }
        )

        val result = engine.create(clientA)

        assertEquals(ClientProfileMutationResult.Success, result)
        assertEquals(listOf("persist", "activate:${clientA.id}"), events)
    }

    @Test
    fun create_duplicateDoesNotCreatePhantomActiveClient() = runBlocking {
        val activated = mutableListOf<UUID?>()
        val engine = engine(
            createProfile = { false },
            setCurrentClientId = { activated += it }
        )

        val result = engine.create(clientA)

        assertEquals(ClientProfileMutationResult.DuplicateName, result)
        assertTrue(activated.isEmpty())
    }

    @Test
    fun create_rejectsProfileBeyondPlanLimitBeforePersistence() = runBlocking {
        var persisted = false
        val engine = engine(
            createProfile = {
                persisted = true
                true
            },
            loadClients = { listOf(clientA) },
            maxClients = { 1 }
        )

        val result = engine.create(clientB)

        assertEquals(ClientProfileMutationResult.ClientLimitReached, result)
        assertFalse(persisted)
    }

    @Test
    fun create_failureKeepsCurrentClientUntouched() = runBlocking {
        val activated = mutableListOf<UUID?>()
        val engine = engine(
            createProfile = { error("disk failure") },
            setCurrentClientId = { activated += it }
        )

        val result = engine.create(clientA)

        assertTrue(result is ClientProfileMutationResult.Failure)
        assertTrue(activated.isEmpty())
    }

    @Test
    fun create_cancellationPropagatesWithoutActivation() = runBlocking {
        var activated = false
        val engine = engine(
            createProfile = { throw CancellationException("cancelled") },
            setCurrentClientId = { activated = true }
        )

        try {
            engine.create(clientA)
            throw AssertionError("Expected CancellationException")
        } catch (_: CancellationException) {
            assertFalse(activated)
        }
    }

    @Test
    fun update_duplicateDoesNotReportSuccess() = runBlocking {
        val engine = engine(updateProfile = { false })

        val result = engine.update(clientA)

        assertEquals(ClientProfileMutationResult.DuplicateName, result)
    }

    @Test
    fun delete_activeClientSwitchesOnlyAfterDeleteCommit() = runBlocking {
        val events = mutableListOf<String>()
        val engine = engine(
            deleteProfile = { events += "delete:${it.id}" },
            loadClients = { listOf(clientA, clientB) },
            currentClientId = { clientA.id },
            setCurrentClientId = { events += "activate:$it" },
            clearCloudLinks = { events += "clear:$it" }
        )

        val result = engine.delete(clientA)

        assertEquals(ClientProfileMutationResult.Success, result)
        assertEquals(
            listOf(
                "delete:${clientA.id}",
                "clear:${clientA.id}",
                "activate:${clientB.id}"
            ),
            events
        )
    }

    @Test
    fun delete_failurePreservesActiveClientAndCloudLinks() = runBlocking {
        val events = mutableListOf<String>()
        val engine = engine(
            deleteProfile = {
                events += "delete"
                error("db failure")
            },
            loadClients = { listOf(clientA, clientB) },
            currentClientId = { clientA.id },
            setCurrentClientId = { events += "activate" },
            clearCloudLinks = { events += "clear" }
        )

        val result = engine.delete(clientA)

        assertTrue(result is ClientProfileMutationResult.Failure)
        assertEquals(listOf("delete"), events)
    }

    private fun engine(
        createProfile: suspend (ClientProfile) -> Boolean = { true },
        updateProfile: suspend (ClientProfile) -> Boolean = { true },
        deleteProfile: suspend (ClientProfile) -> Unit = {},
        loadClients: suspend () -> List<ClientProfile> = { listOf(clientA, clientB) },
        currentClientId: () -> UUID? = { clientA.id },
        setCurrentClientId: (UUID?) -> Unit = {},
        clearCloudLinks: (UUID) -> Unit = {},
        maxClients: () -> Int? = { null }
    ) = ClientProfileMutationEngine(
        createProfile = createProfile,
        updateProfile = updateProfile,
        deleteProfile = deleteProfile,
        loadClients = loadClients,
        currentClientId = currentClientId,
        setCurrentClientId = setCurrentClientId,
        clearCloudLinks = clearCloudLinks,
        maxClients = maxClients
    )
}
