package com.example.supplementtracker.data.mapper

import com.example.supplementtracker.data.local.SupplementEntity
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.model.UserSupplement
import java.time.LocalDate
import java.util.*

/**
 * Chuyển đổi dữ liệu giữa Domain Model và Data Entity.
 */
fun UserSupplement.toEntity(): SupplementEntity {
    return SupplementEntity(
        id = id.toString(),
        clientId = clientId.toString(),
        name = name,
        startDate = startDate.toString(),
        daysOn = cycleConfig.daysOn,
        daysOff = cycleConfig.daysOff,
        isContinuous = cycleConfig.isContinuous,
        durationMonths = cycleConfig.durationMonths,
        dailyDose = dailyDose,
        intakeTime = intakeTime,
        weeklyWeekdaysMask = cycleConfig.weeklyRecurrence?.weekdaysMask,
        weeklyIntervalWeeks = cycleConfig.weeklyRecurrence?.intervalWeeks,
        weeklyAnchorDate = cycleConfig.weeklyRecurrence?.anchorDate?.toString()
    )
}

fun SupplementEntity.toDomain(): UserSupplement {
    val weekly = run {
        val mask = weeklyWeekdaysMask ?: return@run null
        val interval = weeklyIntervalWeeks ?: return@run null
        val anchor = weeklyAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@run null
        WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
    }
    return UserSupplement(
        id = UUID.fromString(id),
        clientId = UUID.fromString(clientId),
        name = name,
        startDate = LocalDate.parse(startDate),
        cycleConfig = CycleConfig(
            daysOn = daysOn,
            daysOff = daysOff,
            isContinuous = isContinuous,
            durationMonths = durationMonths,
            weeklyRecurrence = weekly
        ),
        dailyDose = dailyDose,
        intakeTime = intakeTime
    )
}
