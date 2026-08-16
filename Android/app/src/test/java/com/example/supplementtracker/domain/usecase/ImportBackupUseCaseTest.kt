package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.DoseEventKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

private class RecordingImportRepository : SupplementRepository {
    var importedClientId: String? = null
    var importedSupplements: List<UserSupplement> = emptyList()
    var importedRecords: List<IntakeRecord> = emptyList()
    val existingSupplements = mutableMapOf<String, UserSupplement>()

    override suspend fun saveClient(profile: ClientProfile) = Unit
    override suspend fun updateClient(profile: ClientProfile) = Unit
    override suspend fun deleteClient(profile: ClientProfile) = Unit
    override fun observeClients(): Flow<List<ClientProfile>> = flowOf(emptyList())
    override suspend fun saveSupplement(supplement: UserSupplement) = Unit
    override suspend fun updateSupplement(supplement: UserSupplement) = Unit
    override suspend fun deleteSupplement(supplement: UserSupplement) = Unit
    override suspend fun getSupplementById(id: String): UserSupplement? = existingSupplements[id.lowercase()]
    override fun getAllSupplements(clientId: String): Flow<List<UserSupplement>> = emptyFlow()
    override fun getSupplementsWithTakenToday(clientId: String, startOfDay: Long, endOfDay: Long): Flow<List<UserSupplementTakenToday>> = emptyFlow()
    override suspend fun logIntake(supplementId: String, date: Long) = Unit
    override suspend fun insertIntakeRecord(record: IntakeRecord) = Unit
    override suspend fun getIntakeRecordById(id: String): IntakeRecord? = null
    override suspend fun deleteDuplicateIntakeRecords(supplementId: String, date: Long, keepId: String) = Unit
    override suspend fun removeIntake(supplementId: String, date: Long) = Unit
    override fun getRecordsByDateRange(clientId: String, startDate: Long, endDate: Long): Flow<List<IntakeRecord>> = emptyFlow()
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
    ) {
        importedClientId = clientId
        importedSupplements = supplements
        importedRecords = records
    }
}

class ImportBackupUseCaseTest {
    private val repository = RecordingImportRepository()
    private val useCase = ImportBackupUseCase(repository)
    private val clientId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val supplementId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun import_preservesRecurrenceDeletionLastTakenAndFiltersUnknownHistory() = kotlinx.coroutines.runBlocking {
        val json = """
            {
              "version":"2.0",
              "supplements":[{
                "id":"$supplementId","name":"Vitamin C","dailyDose":"1","intakeTime":"08:00, 20:00",
                "startDate":"2026-08-10","lastTakenLocalDate":"2026-08-11","updatedAtEpochMs":1234,
                "deletedAtEpochMs":9876,
                "cycle":{"isContinuous":false,"daysOn":5,"daysOff":2,"durationMonths":3,"intervalDays":3,
                  "weeklyWeekdaysMask":2,"weeklyIntervalWeeks":2,"weeklyAnchorDate":"2026-08-10"}
              }],
              "historyLogs":[
                {"id":"keep","supplementId":"$supplementId","dateEpochMs":1000,"status":"Taken","updatedAtEpochMs":2000},
                {"id":"drop","supplementId":"33333333-3333-3333-3333-333333333333","dateEpochMs":1000,"status":"Taken","updatedAtEpochMs":2000}
              ]
            }
        """.trimIndent()

        val result = useCase(json, clientId)

        assertTrue(result.isSuccess)
        assertEquals(clientId.toString(), repository.importedClientId)
        val imported = repository.importedSupplements.single()
        assertEquals(3, imported.cycleConfig.intervalDays)
        assertEquals(5, imported.cycleConfig.daysOn)
        assertEquals(2, imported.cycleConfig.daysOff)
        assertEquals(3, imported.cycleConfig.durationMonths)
        assertEquals(2, imported.cycleConfig.weeklyRecurrence?.weekdaysMask)
        assertEquals(2, imported.cycleConfig.weeklyRecurrence?.intervalWeeks)
        assertEquals(9876L, imported.deletedAtEpochMs)
        assertEquals("2026-08-11", imported.lastTakenLocalDate.toString())
        assertEquals("08:00, 20:00", imported.intakeTime)
        assertEquals(1, repository.importedRecords.size)
        assertEquals(DoseEventKey.make(supplementId.toString(), 1000), repository.importedRecords.single().id)
    }

    @Test
    fun import_remapsIdOwnedByAnotherClientAndKeepsHistoryLinked() = kotlinx.coroutines.runBlocking {
        val otherClientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        repository.existingSupplements[supplementId.toString()] = UserSupplement(
            id = supplementId,
            clientId = otherClientId,
            name = "Other",
            startDate = LocalDate.parse("2026-01-01"),
            cycleConfig = CycleConfig.Continuous,
            dailyDose = "1",
            intakeTime = "08:00"
        )
        val json = """
            {"version":"2.0","supplements":[{"id":"$supplementId","name":"Imported","dailyDose":"1",
            "intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}}],
            "historyLogs":[{"id":"foreign-id","supplementId":"$supplementId","dateEpochMs":1000,"status":"Taken","updatedAtEpochMs":2000}]}
        """.trimIndent()

        val result = useCase(json, clientId)

        assertTrue(result.isSuccess)
        val imported = repository.importedSupplements.single()
        assertTrue(imported.id != supplementId)
        assertEquals(imported.id.toString(), repository.importedRecords.single().supplementId)
        assertEquals(DoseEventKey.make(imported.id.toString(), 1000), repository.importedRecords.single().id)
    }

    @Test
    fun invalidJson_returnsFailureWithoutImporting() = kotlinx.coroutines.runBlocking {
        val result = useCase("not-json", clientId)

        assertFalse(result.isSuccess)
        assertTrue(repository.importedClientId == null)
        assertTrue(repository.importedSupplements.isEmpty())
        assertTrue(repository.importedRecords.isEmpty())
    }
}
