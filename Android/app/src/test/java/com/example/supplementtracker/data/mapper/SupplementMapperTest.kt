package com.example.supplementtracker.data.mapper

import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.usecase.CalculateHomeDashboardUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Integration boundary: Room entity mapping ↔ domain model fields that drive dashboard.
 * Domain unit tests assume perfect UserSupplement inputs; these tests protect the
 * persistence mapping that actually feeds HomeViewModel / CalculateHomeDashboardUseCase.
 */
class SupplementMapperTest {

    private val zone = ZoneId.of("UTC")
    private val dashboard = CalculateHomeDashboardUseCase()
    private val clientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val supplementId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    private fun baseSupplement(
        config: CycleConfig = CycleConfig.Continuous,
        intakeTime: String = "08:00",
        lastTaken: LocalDate? = null,
        deletedAt: Long? = null
    ) = UserSupplement(
        id = supplementId,
        clientId = clientId,
        name = "Omega-3",
        startDate = LocalDate.of(2026, 1, 1),
        cycleConfig = config,
        dailyDose = "1 softgel",
        intakeTime = intakeTime,
        lastTakenLocalDate = lastTaken,
        updatedAtEpochMs = 1_700_000_000_000L,
        deletedAtEpochMs = deletedAt
    )

    private fun epochAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun roundTrip_preservesIntervalDaysAndLastTaken() {
        val original = baseSupplement(
            config = CycleConfig(
                daysOn = 1,
                daysOff = 0,
                isContinuous = true,
                intervalDays = 3
            ),
            lastTaken = LocalDate.of(2026, 3, 5)
        )
        val restored = original.toEntity().toDomain()

        assertEquals(3, restored.cycleConfig.intervalDays)
        assertEquals(LocalDate.of(2026, 3, 5), restored.lastTakenLocalDate)
        assertEquals(original.id, restored.id)
        assertEquals(original.startDate, restored.startDate)
    }

    @Test
    fun roundTrip_preservesWeeklyRecurrence() {
        val anchor = LocalDate.of(2026, 1, 5)
        val weekly = WeeklyRecurrenceConfig(
            weekdaysMask = 1, // Monday
            intervalWeeks = 2,
            anchorDate = anchor
        )
        val original = baseSupplement(
            config = CycleConfig(
                daysOn = 1,
                daysOff = 0,
                isContinuous = true,
                weeklyRecurrence = weekly
            )
        )
        val restored = original.toEntity().toDomain()

        assertNotNull(restored.cycleConfig.weeklyRecurrence)
        assertEquals(1, restored.cycleConfig.weeklyRecurrence!!.weekdaysMask)
        assertEquals(2, restored.cycleConfig.weeklyRecurrence!!.intervalWeeks)
        assertEquals(anchor, restored.cycleConfig.weeklyRecurrence!!.anchorDate)
    }

    @Test
    fun roundTrip_preservesSoftDeleteAndDuration() {
        val original = baseSupplement(
            config = CycleConfig(
                daysOn = 5,
                daysOff = 2,
                isContinuous = false,
                durationMonths = 30
            ),
            deletedAt = 1_700_100_000_000L
        )
        val restored = original.toEntity().toDomain()

        assertEquals(1_700_100_000_000L, restored.deletedAtEpochMs)
        assertEquals(30, restored.cycleConfig.durationMonths)
        assertEquals(5, restored.cycleConfig.daysOn)
        assertEquals(2, restored.cycleConfig.daysOff)
        assertEquals(false, restored.cycleConfig.isContinuous)
    }

    @Test
    fun roundTrip_preservesMultiTimeIntake() {
        val original = baseSupplement(intakeTime = "08:00, 20:00")
        val restored = original.toEntity().toDomain()
        assertEquals("08:00, 20:00", restored.intakeTime)
    }

    @Test
    fun roundTrip_nullWeeklyAndIntervalRemainNull() {
        val original = baseSupplement(config = CycleConfig.Continuous)
        val restored = original.toEntity().toDomain()
        assertNull(restored.cycleConfig.weeklyRecurrence)
        assertNull(restored.cycleConfig.intervalDays)
        assertNull(restored.cycleConfig.durationMonths)
        assertNull(restored.deletedAtEpochMs)
        assertNull(restored.lastTakenLocalDate)
    }

    @Test
    fun mappedIntervalDays_stillGatesDashboardActiveDoses() {
        // Boundary chain: domain → entity → domain → dashboard use case
        val original = baseSupplement(
            config = CycleConfig(
                daysOn = 1,
                daysOff = 0,
                isContinuous = true,
                intervalDays = 3
            )
        )
        val restored = original.toEntity().toDomain()
        val start = restored.startDate

        val onDay = start.plusDays(3)
        val onResult = dashboard(
            listOf(restored),
            emptyList(),
            onDay,
            nowEpochMs = epochAt(onDay, 7, 0),
            zoneId = zone
        )
        assertEquals(1, onResult.activeDoses["08:00"]?.size ?: 0)

        val offDay = start.plusDays(1)
        val offResult = dashboard(
            listOf(restored),
            emptyList(),
            offDay,
            nowEpochMs = epochAt(offDay, 7, 0),
            zoneId = zone
        )
        assertTrue(offResult.activeDoses.isEmpty())
    }

    @Test
    fun mappedSoftDelete_stillExcludedFromDashboard() {
        val original = baseSupplement(deletedAt = epochAt(LocalDate.of(2026, 3, 10), 0))
        val restored = original.toEntity().toDomain()
        val today = LocalDate.of(2026, 3, 10)

        val result = dashboard(
            listOf(restored),
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
    fun mappedWeeklyRecurrence_stillFiltersNonMatchingWeekday() {
        val anchor = LocalDate.of(2026, 1, 5) // Monday
        val original = baseSupplement(
            config = CycleConfig(
                daysOn = 1,
                daysOff = 0,
                isContinuous = true,
                weeklyRecurrence = WeeklyRecurrenceConfig(
                    weekdaysMask = 1,
                    intervalWeeks = 1,
                    anchorDate = anchor
                )
            ),
            intakeTime = "10:00"
        )
        val restored = original.toEntity().toDomain()

        val tuesday = LocalDate.of(2026, 1, 6)
        val tueResult = dashboard(
            listOf(restored),
            emptyList(),
            tuesday,
            zoneId = zone
        )
        assertTrue(tueResult.activeDoses.isEmpty())

        val monday = LocalDate.of(2026, 1, 12)
        val monResult = dashboard(
            listOf(restored),
            emptyList(),
            monday,
            nowEpochMs = epochAt(monday, 9, 0),
            zoneId = zone
        )
        assertEquals(1, monResult.activeDoses["10:00"]?.size ?: 0)
    }
}
