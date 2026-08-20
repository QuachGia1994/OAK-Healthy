package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.model.IntakeRecord
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
    var currentSupplements: List<UserSupplement> = emptyList()
    var currentRecords: List<IntakeRecord> = emptyList()
    var importAttempts: Int = 0
    var failNextImport: Boolean = false
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
    override suspend fun getAllRecordsByClient(clientId: String): List<IntakeRecord> = currentRecords
    override suspend fun getAllSupplementsForSync(clientId: String): List<UserSupplement> = currentSupplements
    override suspend fun getAllRecordsForSync(clientId: String): List<IntakeRecord> = currentRecords
    override suspend fun hasSupplementChangesSince(clientId: String, sinceEpochMs: Long): Boolean = false
    override suspend fun hasHistoryChangesSince(clientId: String, sinceEpochMs: Long): Boolean = false
    override suspend fun deleteAllSupplementsByClient(clientId: String) = Unit
    override suspend fun deleteAllIntakeRecordsByClient(clientId: String) = Unit
    override suspend fun importBackupAtomic(
        clientId: String,
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>
    ) {
        importAttempts += 1
        importedClientId = clientId
        importedSupplements = supplements
        importedRecords = records
        currentSupplements = supplements
        currentRecords = records
        if (failNextImport) {
            failNextImport = false
            error("simulated persistence failure")
        }
    }
}

class ImportBackupUseCaseTest {
    private val repository = RecordingImportRepository()
    private val useCase = ImportBackupUseCase(repository)
    private val clientId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val supplementId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun import_preservesRecurrenceDeletionAndLastTaken() = kotlinx.coroutines.runBlocking {
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
                {"id":"keep","supplementId":"$supplementId","dateEpochMs":1000,"status":"Taken","updatedAtEpochMs":2000}
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
    fun legacyV1Export_derivesCanonicalCrossPlatformSupplementId() = kotlinx.coroutines.runBlocking {
        val json = """
            {
              "schemaVersion":1,
              "exportedAtEpochMs":1700000000000,
              "supplements":[{
                "name":"Vitamin C","dailyDose":"500 mg","intakeTime":"08:00","startDate":"2026-01-02","category":null,
                "cycle":{"isContinuous":false,"daysOn":5,"daysOff":2,"durationMonths":3,"weeklyWeekdaysMask":2,
                  "weeklyIntervalWeeks":2,"weeklyAnchorDate":"2026-01-05","intervalDays":3}
              }]
            }
        """.trimIndent()

        val result = useCase(json, clientId)

        assertTrue(result.isSuccess)
        assertEquals(
            UUID.fromString("0c13f015-3ab0-47b7-af85-713a1c628ff0"),
            repository.importedSupplements.single().id
        )
    }

    @Test
    fun legacyArrayWithoutId_derivesCanonicalCrossPlatformSupplementId() = kotlinx.coroutines.runBlocking {
        val json = """
            [{
              "name":"Vitamin C","dailyDose":"500 mg","intakeTime":"08:00","startDate":"2026-01-02",
              "cycle":{"isContinuous":false,"daysOn":5,"daysOff":2,"durationMonths":3,"weeklyWeekdaysMask":2,
                "weeklyIntervalWeeks":2,"weeklyAnchorDate":"2026-01-05","intervalDays":3}
            }]
        """.trimIndent()

        val result = useCase(json, clientId)

        assertTrue(result.isSuccess)
        assertEquals(
            UUID.fromString("0c13f015-3ab0-47b7-af85-713a1c628ff0"),
            repository.importedSupplements.single().id
        )
    }

    @Test
    fun preview_blocksOrphanHistoryWithoutWriting() = kotlinx.coroutines.runBlocking {
        val orphanId = "33333333-3333-3333-3333-333333333333"
        val json = """
            {"version":"2.0","supplements":[{"id":"$supplementId","name":"Imported","dailyDose":"1",
            "intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}}],
            "historyLogs":[{"id":"orphan","supplementId":"$orphanId","dateEpochMs":1000,"status":"Taken","updatedAtEpochMs":2000}]}
        """.trimIndent()

        val plan = useCase.preview(json, clientId).getOrThrow()
        val result = useCase(json, clientId)

        assertFalse(plan.preview.canRestore)
        assertEquals(1, plan.preview.orphanHistoryCount)
        assertTrue(result.isFailure)
        assertEquals(0, repository.importAttempts)
    }

    @Test
    fun preview_reportsDuplicateIdsAndForeignProfileCollision() = kotlinx.coroutines.runBlocking {
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
            {"version":"2.0","supplements":[
              {"id":"$supplementId","name":"One","dailyDose":"1","intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}},
              {"id":"$supplementId","name":"Duplicate","dailyDose":"1","intakeTime":"09:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}}
            ],"historyLogs":[]}
        """.trimIndent()

        val preview = useCase.preview(json, clientId).getOrThrow().preview

        assertEquals("oak-2.0", preview.sourceSchema)
        assertEquals(1, preview.duplicateSupplementIdCount)
        assertEquals(1, preview.remappedSupplementIdCount)
        assertFalse(preview.canRestore)
        assertEquals(0, repository.importAttempts)
    }

    @Test
    fun restore_rollsBackPreRestoreSnapshotWhenPersistenceFails() = kotlinx.coroutines.runBlocking {
        val snapshotSupplement = UserSupplement(
            id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
            clientId = clientId,
            name = "Snapshot",
            startDate = LocalDate.parse("2026-01-01"),
            cycleConfig = CycleConfig.Continuous,
            dailyDose = "1",
            intakeTime = "07:00"
        )
        val snapshotRecord = IntakeRecord(
            id = DoseEventKey.make(snapshotSupplement.id.toString(), 500L),
            supplementId = snapshotSupplement.id.toString(),
            date = 500L,
            status = "Taken",
            updatedAtEpochMs = 600L
        )
        repository.currentSupplements = listOf(snapshotSupplement)
        repository.currentRecords = listOf(snapshotRecord)
        val json = """
            {"version":"2.0","supplements":[{"id":"$supplementId","name":"Imported","dailyDose":"1",
            "intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}}],
            "historyLogs":[]}
        """.trimIndent()
        val plan = useCase.preview(json, clientId).getOrThrow()
        repository.failNextImport = true

        val result = useCase.restore(plan)

        assertTrue(result.isFailure)
        assertEquals(2, repository.importAttempts)
        assertEquals(listOf(snapshotSupplement), repository.currentSupplements)
        assertEquals(listOf(snapshotRecord), repository.currentRecords)
    }

    @Test
    fun unknownIntakeStatus_returnsFailureWithoutImporting() = kotlinx.coroutines.runBlocking {
        val json = """
            {"version":"2.0","supplements":[{"id":"$supplementId","name":"Imported","dailyDose":"1",
            "intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}}],
            "historyLogs":[{"id":"bad-status","supplementId":"$supplementId","dateEpochMs":1000,"status":"Maybe","updatedAtEpochMs":2000}]}
        """.trimIndent()

        val result = useCase(json, clientId)

        assertTrue(result.isFailure)
        assertEquals(0, repository.importAttempts)
    }

    @Test
    fun invalidRecurrence_returnsFailureWithoutImporting() = kotlinx.coroutines.runBlocking {
        val json = """
            {"version":"2.0","supplements":[{"id":"$supplementId","name":"Imported","dailyDose":"1",
            "intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":false,"daysOn":0,"daysOff":2}}],
            "historyLogs":[]}
        """.trimIndent()

        val result = useCase(json, clientId)

        assertTrue(result.isFailure)
        assertEquals(0, repository.importAttempts)
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
