package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.model.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private class RecordingClientRepository(
    initialClients: List<ClientProfile> = emptyList()
) : SupplementRepository {
    var clients = initialClients
    val saved = mutableListOf<ClientProfile>()
    val updated = mutableListOf<ClientProfile>()
    val deleted = mutableListOf<ClientProfile>()

    override suspend fun saveClient(profile: ClientProfile) {
        saved += profile
        clients = clients + profile
    }

    override suspend fun updateClient(profile: ClientProfile) {
        updated += profile
        clients = clients.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun deleteClient(profile: ClientProfile) {
        deleted += profile
        clients = clients.filterNot { it.id == profile.id }
    }

    override fun observeClients(): Flow<List<ClientProfile>> = flowOf(clients)
    override suspend fun saveSupplement(supplement: UserSupplement) = Unit
    override suspend fun updateSupplement(supplement: UserSupplement) = Unit
    override suspend fun deleteSupplement(supplement: UserSupplement) = Unit
    override suspend fun getSupplementById(id: String): UserSupplement? = null
    override fun getAllSupplements(clientId: String): Flow<List<UserSupplement>> = emptyFlow()
    override fun getSupplementsWithTakenToday(
        clientId: String,
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<UserSupplementTakenToday>> = emptyFlow()
    override suspend fun logIntake(supplementId: String, date: Long) = Unit
    override suspend fun insertIntakeRecord(record: IntakeRecord) = Unit
    override suspend fun getIntakeRecordById(id: String): IntakeRecord? = null
    override suspend fun deleteDuplicateIntakeRecords(supplementId: String, date: Long, keepId: String) = Unit
    override suspend fun removeIntake(supplementId: String, date: Long) = Unit
    override fun getRecordsByDateRange(
        clientId: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<IntakeRecord>> = emptyFlow()
    override fun observeAllRecordsByClient(clientId: String): Flow<List<IntakeRecord>> = emptyFlow()
    override suspend fun getAllRecordsByClient(clientId: String): List<IntakeRecord> = emptyList()
    override suspend fun getAllSupplementsForSync(clientId: String): List<UserSupplement> = emptyList()
    override suspend fun getAllRecordsForSync(clientId: String): List<IntakeRecord> = emptyList()
    override suspend fun hasSupplementChangesSince(clientId: String, sinceEpochMs: Long): Boolean = false
    override suspend fun hasHistoryChangesSince(clientId: String, sinceEpochMs: Long): Boolean = false
    override suspend fun deleteAllSupplementsByClient(clientId: String) = Unit
    override suspend fun deleteAllIntakeRecordsByClient(clientId: String) = Unit
    override suspend fun importBackupAtomic(
        clientId: String,
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>
    ) = Unit
}

class ClientProfileUseCaseTest {
    private val clientA = ClientProfile(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Alice", 1)
    private val clientB = ClientProfile(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Bob", 2)
    @Test
    fun create_checksDuplicateUsingTrimmedName() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository(listOf(clientA.copy(name = "  Alice  ")))
        val useCase = ClientProfileUseCase(repository)

        assertFalse(useCase.create(clientB.copy(name = "Alice")))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun create_rejectsCaseInsensitiveDuplicateAfterTrim() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository(listOf(clientA))
        val useCase = ClientProfileUseCase(repository)

        assertFalse(useCase.create(clientB.copy(name = "  aLiCe  ")))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun update_allowsCurrentProfileToKeepItsOwnName() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository(listOf(clientA, clientB))
        val useCase = ClientProfileUseCase(repository)
        val renamed = clientA.copy(name = " Alice ")

        assertTrue(useCase.update(renamed))
        assertEquals(renamed.copy(name = "Alice"), repository.updated.single())
    }

    @Test
    fun create_rejectsDiacriticEquivalentDuplicate() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository(listOf(clientA.copy(name = "Ánh")))
        val useCase = ClientProfileUseCase(repository)

        assertFalse(useCase.create(clientB.copy(name = "anh")))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun create_persistsTrimmedName() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository()
        val useCase = ClientProfileUseCase(repository)

        assertTrue(useCase.create(clientA.copy(name = "  Alice  ")))
        assertEquals("Alice", repository.saved.single().name)
    }

    @Test
    fun update_rejectsNameOwnedByAnotherProfile() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository(listOf(clientA, clientB))
        val useCase = ClientProfileUseCase(repository)

        assertFalse(useCase.update(clientA.copy(name = "  bOB  ")))
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun delete_delegatesToRepository() = kotlinx.coroutines.runBlocking {
        val repository = RecordingClientRepository(listOf(clientA))
        val useCase = ClientProfileUseCase(repository)

        useCase.delete(clientA)

        assertEquals(listOf(clientA), repository.deleted)
        assertTrue(repository.clients.isEmpty())
    }
}
