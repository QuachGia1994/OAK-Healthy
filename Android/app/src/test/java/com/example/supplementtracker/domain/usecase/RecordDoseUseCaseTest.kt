package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private class RecordingSupplementRepository : SupplementRepository {
    val insertedRecords = mutableListOf<IntakeRecord>()
    val updatedSupplements = mutableListOf<UserSupplement>()
    var supplement: UserSupplement? = null

    override suspend fun saveClient(profile: ClientProfile) = Unit
    override suspend fun updateClient(profile: ClientProfile) = Unit
    override suspend fun deleteClient(profile: ClientProfile) = Unit
    override fun observeClients(): Flow<List<ClientProfile>> = flowOf(emptyList())
    override suspend fun saveSupplement(supplement: UserSupplement) = Unit
    override suspend fun updateSupplement(supplement: UserSupplement) {
        updatedSupplements += supplement
        this.supplement = supplement
    }
    override suspend fun deleteSupplement(supplement: UserSupplement) = Unit
    override suspend fun getSupplementById(id: String): UserSupplement? =
        supplement?.takeIf { it.id.toString() == id }
    override fun getAllSupplements(clientId: String): Flow<List<UserSupplement>> = emptyFlow()
    override fun getSupplementsWithTakenToday(
        clientId: String,
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<UserSupplementTakenToday>> = emptyFlow()
    override suspend fun logIntake(supplementId: String, date: Long) = Unit
    override suspend fun insertIntakeRecord(record: IntakeRecord) {
        insertedRecords += record
    }
    override suspend fun getIntakeRecordById(id: String): IntakeRecord? = null
    override suspend fun deleteDuplicateIntakeRecords(
        supplementId: String,
        date: Long,
        keepId: String
    ) = Unit
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
    override suspend fun deleteAllSupplementsByClient(clientId: String) = Unit
    override suspend fun deleteAllIntakeRecordsByClient(clientId: String) = Unit
    override suspend fun importBackupAtomic(
        clientId: String,
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>
    ) = Unit
}

class RecordDoseUseCaseTest {
    private val repository = RecordingSupplementRepository()
    private val useCase = RecordDoseUseCase(repository)
    private val supplementId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val scheduledAt = 1_770_000_000_000L

    @Test
    fun invalidScheduledTime_doesNotPersistAnything() = kotlinx.coroutines.runBlocking {
        useCase(supplementId.toString(), 0L, RecordDoseUseCase.Action.TAKEN)

        assertTrue(repository.insertedRecords.isEmpty())
        assertTrue(repository.updatedSupplements.isEmpty())
    }

    @Test
    fun skipped_persistsNormalizedIdAndSkippedStatus() = kotlinx.coroutines.runBlocking {
        useCase(supplementId.toString().uppercase(), scheduledAt, RecordDoseUseCase.Action.SKIPPED)

        val record = repository.insertedRecords.single()
        assertEquals(supplementId.toString(), record.supplementId)
        assertEquals("Skipped", record.status)
        assertEquals(scheduledAt, record.date)
        assertTrue(record.updatedAtEpochMs > 0L)
        assertTrue(repository.updatedSupplements.isEmpty())
    }

    @Test
    fun taken_persistsRecordAndUpdatesLastTakenDate() = kotlinx.coroutines.runBlocking {
        val day = Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
        repository.supplement = UserSupplement(
            id = supplementId,
            clientId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Vitamin C",
            startDate = day,
            cycleConfig = CycleConfig.Continuous,
            dailyDose = "1",
            intakeTime = "08:00"
        )

        useCase(supplementId.toString().uppercase(), scheduledAt, RecordDoseUseCase.Action.TAKEN)

        assertEquals("Taken", repository.insertedRecords.single().status)
        val updated = repository.updatedSupplements.single()
        assertEquals(day, updated.lastTakenLocalDate)
        assertTrue(updated.updatedAtEpochMs > 0L)
    }

    @Test
    fun taken_forMissingSupplement_keepsPersistedIntakeRecord() = kotlinx.coroutines.runBlocking {
        useCase(supplementId.toString(), scheduledAt, RecordDoseUseCase.Action.TAKEN)

        assertEquals(1, repository.insertedRecords.size)
        assertEquals("Taken", repository.insertedRecords.single().status)
        assertTrue(repository.updatedSupplements.isEmpty())
    }

    @Test
    fun sameDoseIdentity_isStableAcrossNormalizedIdCase() = kotlinx.coroutines.runBlocking {
        useCase(supplementId.toString(), scheduledAt, RecordDoseUseCase.Action.TAKEN)
        val firstId = repository.insertedRecords.single().id

        repository.insertedRecords.clear()
        useCase(supplementId.toString().uppercase(), scheduledAt, RecordDoseUseCase.Action.SKIPPED)

        assertEquals(firstId, repository.insertedRecords.single().id)
    }
}
