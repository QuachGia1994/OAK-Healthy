package com.example.supplementtracker.persistence

import androidx.room.Room
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.usecase.CalculateHomeDashboardUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Real persistence boundary:
 * Repository → DAO → in-memory Room SQLite → Entity → Mapper → Domain
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupplementPersistenceTest {

    private val zone = ZoneId.of("UTC")
    private val dashboard = CalculateHomeDashboardUseCase()
    private val clientId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val supplementId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

    private lateinit var database: SupplementDatabase
    private lateinit var repository: SupplementRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SupplementDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SupplementRepositoryImpl(database.supplementDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun epochAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private suspend fun seedClient() {
        repository.saveClient(
            ClientProfile(
                id = clientId,
                name = "Test Client",
                avatarColorArgb = 0xFF336699.toInt(),
                createdAt = 1_700_000_000_000L
            )
        )
    }

    @Test
    fun saveAndReload_preservesRecurrenceSoftDeleteAndMultiTime() = runBlocking {
        seedClient()

        val anchor = LocalDate.of(2026, 1, 5)
        val original = UserSupplement(
            id = supplementId,
            clientId = clientId,
            name = "Magnesium",
            startDate = LocalDate.of(2026, 1, 1),
            cycleConfig = CycleConfig(
                daysOn = 5,
                daysOff = 2,
                isContinuous = false,
                durationMonths = 30,
                weeklyRecurrence = WeeklyRecurrenceConfig(
                    weekdaysMask = 1,
                    intervalWeeks = 2,
                    anchorDate = anchor
                ),
                intervalDays = 3
            ),
            dailyDose = "200mg",
            intakeTime = "08:00, 20:00",
            lastTakenLocalDate = LocalDate.of(2026, 3, 5),
            updatedAtEpochMs = 1_700_000_000_000L,
            deletedAtEpochMs = null
        )

        repository.saveSupplement(original)
        val reloaded = repository.getSupplementById(supplementId.toString())

        assertNotNull(reloaded)
        assertEquals(original.id, reloaded!!.id)
        assertEquals(original.name, reloaded.name)
        assertEquals(original.startDate, reloaded.startDate)
        assertEquals(5, reloaded.cycleConfig.daysOn)
        assertEquals(2, reloaded.cycleConfig.daysOff)
        assertEquals(false, reloaded.cycleConfig.isContinuous)
        assertEquals(30, reloaded.cycleConfig.durationMonths)
        assertEquals(3, reloaded.cycleConfig.intervalDays)
        assertNotNull(reloaded.cycleConfig.weeklyRecurrence)
        assertEquals(1, reloaded.cycleConfig.weeklyRecurrence!!.weekdaysMask)
        assertEquals(2, reloaded.cycleConfig.weeklyRecurrence!!.intervalWeeks)
        assertEquals(anchor, reloaded.cycleConfig.weeklyRecurrence!!.anchorDate)
        assertEquals(LocalDate.of(2026, 3, 5), reloaded.lastTakenLocalDate)
        assertEquals("08:00, 20:00", reloaded.intakeTime)
    }

    @Test
    fun reloadedIntervalDays_gatesDashboardActiveDoses() = runBlocking {
        seedClient()

        val original = UserSupplement(
            id = supplementId,
            clientId = clientId,
            name = "Interval Supp",
            startDate = LocalDate.of(2026, 1, 1),
            cycleConfig = CycleConfig(
                daysOn = 1,
                daysOff = 0,
                isContinuous = true,
                intervalDays = 3
            ),
            dailyDose = "1",
            intakeTime = "08:00",
            updatedAtEpochMs = 1_700_000_000_000L
        )
        repository.saveSupplement(original)
        val reloaded = repository.getSupplementById(supplementId.toString())!!

        val onDay = reloaded.startDate.plusDays(3)
        val onResult = dashboard(
            listOf(reloaded),
            emptyList(),
            onDay,
            nowEpochMs = epochAt(onDay, 7, 0),
            zoneId = zone
        )
        assertEquals(1, onResult.activeDoses["08:00"]?.size ?: 0)

        val offDay = reloaded.startDate.plusDays(1)
        val offResult = dashboard(
            listOf(reloaded),
            emptyList(),
            offDay,
            nowEpochMs = epochAt(offDay, 7, 0),
            zoneId = zone
        )
        assertTrue(offResult.activeDoses.isEmpty())
    }

    @Test
    fun reloadedSoftDelete_isExcludedFromDashboard() = runBlocking {
        seedClient()

        val deletedAt = epochAt(LocalDate.of(2026, 3, 10), 0)
        val original = UserSupplement(
            id = supplementId,
            clientId = clientId,
            name = "Deleted Supp",
            startDate = LocalDate.of(2026, 1, 1),
            cycleConfig = CycleConfig.Continuous,
            dailyDose = "1",
            intakeTime = "08:00",
            updatedAtEpochMs = 1_700_000_000_000L,
            deletedAtEpochMs = deletedAt
        )
        repository.saveSupplement(original)
        val reloaded = repository.getSupplementById(supplementId.toString())!!

        assertEquals(deletedAt, reloaded.deletedAtEpochMs)

        val today = LocalDate.of(2026, 3, 10)
        val result = dashboard(
            listOf(reloaded),
            emptyList(),
            today,
            nowEpochMs = epochAt(today, 12, 0),
            zoneId = zone
        )
        assertTrue(result.activeDoses.isEmpty())
        assertTrue(result.restingSupplements.isEmpty())
        assertEquals(0, result.streakDays)
    }

    @Test
    fun reloadedWeeklyRecurrence_filtersNonMatchingWeekday() = runBlocking {
        seedClient()

        val anchor = LocalDate.of(2026, 3, 9) // Monday
        val original = UserSupplement(
            id = supplementId,
            clientId = clientId,
            name = "Weekly Supp",
            startDate = anchor,
            cycleConfig = CycleConfig(
                daysOn = 1,
                daysOff = 0,
                isContinuous = true,
                weeklyRecurrence = WeeklyRecurrenceConfig(
                    weekdaysMask = 1,
                    intervalWeeks = 1,
                    anchorDate = anchor
                )
            ),
            dailyDose = "1",
            intakeTime = "08:00",
            updatedAtEpochMs = 1_700_000_000_000L
        )
        repository.saveSupplement(original)
        val reloaded = repository.getSupplementById(supplementId.toString())!!

        val monday = dashboard(
            listOf(reloaded),
            emptyList(),
            anchor,
            nowEpochMs = epochAt(anchor, 7, 0),
            zoneId = zone
        )
        assertEquals(1, monday.activeDoses["08:00"]?.size ?: 0)

        val tuesday = anchor.plusDays(1)
        val tuesdayResult = dashboard(
            listOf(reloaded),
            emptyList(),
            tuesday,
            nowEpochMs = epochAt(tuesday, 7, 0),
            zoneId = zone
        )
        assertTrue(tuesdayResult.activeDoses.isEmpty())
    }

    @Test
    fun reloadedMultiTimeIntake_createsSeparateDashboardSlots() = runBlocking {
        seedClient()

        val original = UserSupplement(
            id = supplementId,
            clientId = clientId,
            name = "Multi Time",
            startDate = LocalDate.of(2026, 1, 1),
            cycleConfig = CycleConfig.Continuous,
            dailyDose = "1",
            intakeTime = "08:00, 20:00",
            updatedAtEpochMs = 1_700_000_000_000L
        )
        repository.saveSupplement(original)
        val reloaded = repository.getSupplementById(supplementId.toString())!!

        assertEquals("08:00, 20:00", reloaded.intakeTime)

        val today = LocalDate.of(2026, 3, 10)
        val result = dashboard(
            listOf(reloaded),
            emptyList(),
            today,
            nowEpochMs = epochAt(today, 7, 0),
            zoneId = zone
        )
        assertEquals(1, result.activeDoses["08:00"]?.size ?: 0)
        assertEquals(1, result.activeDoses["20:00"]?.size ?: 0)
    }
}
