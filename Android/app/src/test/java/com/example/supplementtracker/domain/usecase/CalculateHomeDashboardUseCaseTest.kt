package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.util.DoseEventKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class CalculateCycleUseCaseTest {
    private val useCase = CalculateCycleUseCase()

    @Test
    fun continuous_isAlwaysOn_whenNotExpired() {
        val start = LocalDate.of(2026, 1, 1)
        val status = useCase(start, CycleConfig.Continuous, LocalDate.of(2026, 6, 1))
        assertEquals(CycleStatus.ON, status)
    }

    @Test
    fun onOffCycle_returnsOff_duringOffWindow() {
        val start = LocalDate.of(2026, 1, 1)
        val config = CycleConfig(daysOn = 5, daysOff = 2, isContinuous = false)
        // day 5 (0-based dayInCycle=5) is first off day when daysOn=5
        val status = useCase(start, config, start.plusDays(5))
        assertEquals(CycleStatus.OFF, status)
    }

    @Test
    fun onOffCycle_returnsOn_duringOnWindow() {
        val start = LocalDate.of(2026, 1, 1)
        val config = CycleConfig(daysOn = 5, daysOff = 2, isContinuous = false)
        val status = useCase(start, config, start.plusDays(2))
        assertEquals(CycleStatus.ON, status)
    }

    @Test
    fun isExpired_true_whenPastDurationDays() {
        val start = LocalDate.of(2026, 1, 1)
        val config = CycleConfig(daysOn = 1, daysOff = 0, isContinuous = true, durationMonths = 10)
        assertTrue(useCase.isExpired(start, config, start.plusDays(10)))
        assertEquals(false, useCase.isExpired(start, config, start.plusDays(9)))
    }
}

class CalculateHomeDashboardUseCaseTest {
    private val zone = ZoneId.of("UTC")
    private val useCase = CalculateHomeDashboardUseCase()
    private val clientId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun supplement(
        name: String = "Vit C",
        start: LocalDate = LocalDate.of(2026, 1, 1),
        config: CycleConfig = CycleConfig.Continuous,
        intakeTime: String = "08:00",
        id: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        lastTaken: LocalDate? = null,
        deletedAt: Long? = null
    ) = UserSupplement(
        id = id,
        clientId = clientId,
        name = name,
        startDate = start,
        cycleConfig = config,
        dailyDose = "1 tablet",
        intakeTime = intakeTime,
        lastTakenLocalDate = lastTaken,
        deletedAtEpochMs = deletedAt
    )

    private fun epochAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun emptySupplements_returnsEmptyResult() {
        val result = useCase(emptyList(), emptyList(), LocalDate.of(2026, 3, 1), zoneId = zone)
        assertEquals(emptyMap<String, List<*>>(), result.activeDoses)
        assertTrue(result.restingSupplements.isEmpty())
        assertEquals(0, result.streakDays)
    }

    @Test
    fun expiredSupplement_isExcludedFromActiveDoses() {
        val start = LocalDate.of(2026, 1, 1)
        val config = CycleConfig(daysOn = 1, daysOff = 0, isContinuous = true, durationMonths = 5)
        val supp = supplement(start = start, config = config)
        val today = start.plusDays(5)
        val result = useCase(listOf(supp), emptyList(), today, zoneId = zone)
        assertTrue(result.activeDoses.isEmpty())
    }

    @Test
    fun continuousDose_withoutRecord_isPlannedOrMissed() {
        val today = LocalDate.of(2026, 3, 10)
        val supp = supplement(start = LocalDate.of(2026, 1, 1), intakeTime = "08:00")
        val beforeWindow = epochAt(today, 8, 30)
        val planned = useCase(listOf(supp), emptyList(), today, nowEpochMs = beforeWindow, zoneId = zone)
        val doses = planned.activeDoses["08:00"] ?: emptyList()
        assertEquals(1, doses.size)
        assertEquals(CalculateHomeDashboardUseCase.DoseStatus.PLANNED, doses[0].doseStatus)

        val afterMissWindow = epochAt(today, 11, 0)
        val missed = useCase(listOf(supp), emptyList(), today, nowEpochMs = afterMissWindow, zoneId = zone)
        assertEquals(
            CalculateHomeDashboardUseCase.DoseStatus.MISSED,
            missed.activeDoses["08:00"]!![0].doseStatus
        )
    }

    @Test
    fun recordedTaken_mapsToTakenStatus() {
        val today = LocalDate.of(2026, 3, 10)
        val supp = supplement(start = LocalDate.of(2026, 1, 1), intakeTime = "08:00")
        val scheduledAt = epochAt(today, 8, 0)
        val record = IntakeRecord(
            id = DoseEventKey.make(supp.id.toString(), scheduledAt),
            supplementId = supp.id.toString(),
            date = scheduledAt,
            status = "Taken"
        )
        val result = useCase(
            listOf(supp),
            listOf(record),
            today,
            nowEpochMs = epochAt(today, 12, 0),
            zoneId = zone
        )
        assertEquals(
            CalculateHomeDashboardUseCase.DoseStatus.TAKEN,
            result.activeDoses["08:00"]!![0].doseStatus
        )
    }

    @Test
    fun recordedSkipped_mapsToSkippedStatus() {
        val today = LocalDate.of(2026, 3, 10)
        val supp = supplement(start = LocalDate.of(2026, 1, 1), intakeTime = "08:00")
        val scheduledAt = epochAt(today, 8, 0)
        val record = IntakeRecord(
            id = DoseEventKey.make(supp.id.toString(), scheduledAt),
            supplementId = supp.id.toString(),
            date = scheduledAt,
            status = "Skipped"
        )
        val result = useCase(
            listOf(supp),
            listOf(record),
            today,
            nowEpochMs = epochAt(today, 12, 0),
            zoneId = zone
        )
        assertEquals(
            CalculateHomeDashboardUseCase.DoseStatus.SKIPPED,
            result.activeDoses["08:00"]!![0].doseStatus
        )
    }

    @Test
    fun onOffOffDay_appearsInRestingList() {
        val start = LocalDate.of(2026, 1, 1)
        // daysOn=5 daysOff=2 → day index 5 is OFF
        val config = CycleConfig(daysOn = 5, daysOff = 2, isContinuous = false, durationMonths = null)
        val supp = supplement(start = start, config = config, intakeTime = "09:00")
        val offDay = start.plusDays(5)
        val result = useCase(listOf(supp), emptyList(), offDay, zoneId = zone)
        assertTrue(result.activeDoses.isEmpty())
        assertEquals(1, result.restingSupplements.size)
        assertEquals(supp.id, result.restingSupplements[0].supplement.id)
        assertTrue(result.restingSupplements[0].daysRemaining > 0)
    }

    @Test
    fun weeklyRecurrence_excludesNonMatchingWeekday() {
        val start = LocalDate.of(2026, 1, 5) // Monday
        // mask bit 0 = Monday only
        val weekly = WeeklyRecurrenceConfig(weekdaysMask = 1, intervalWeeks = 1, anchorDate = start)
        val config = CycleConfig(
            daysOn = 1,
            daysOff = 0,
            isContinuous = true,
            weeklyRecurrence = weekly
        )
        val supp = supplement(start = start, config = config, intakeTime = "10:00")
        val tuesday = LocalDate.of(2026, 1, 6)
        val result = useCase(listOf(supp), emptyList(), tuesday, zoneId = zone)
        assertTrue(result.activeDoses.isEmpty())

        val monday = LocalDate.of(2026, 1, 12)
        val mondayResult = useCase(
            listOf(supp),
            emptyList(),
            monday,
            nowEpochMs = epochAt(monday, 9, 0),
            zoneId = zone
        )
        assertEquals(1, mondayResult.activeDoses["10:00"]?.size ?: 0)
    }

    @Test
    fun deletedSupplement_isExcluded() {
        val today = LocalDate.of(2026, 3, 10)
        val supp = supplement(
            start = LocalDate.of(2026, 1, 1),
            deletedAt = epochAt(today, 0, 0)
        )
        val result = useCase(listOf(supp), emptyList(), today, zoneId = zone)
        assertTrue(result.activeDoses.isEmpty())
        assertTrue(result.restingSupplements.isEmpty())
    }
}
