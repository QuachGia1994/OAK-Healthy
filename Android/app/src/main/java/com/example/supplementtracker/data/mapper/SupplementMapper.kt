package com.example.supplementtracker.data.mapper

import com.example.supplementtracker.data.local.SupplementEntity
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.IntakeTime
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
        intakeTime = intakeTime
    )
}

fun SupplementEntity.toDomain(): UserSupplement {
    return UserSupplement(
        id = UUID.fromString(id),
        clientId = UUID.fromString(clientId),
        name = name,
        startDate = LocalDate.parse(startDate),
        cycleConfig = CycleConfig(
            daysOn = daysOn,
            daysOff = daysOff,
            isContinuous = isContinuous,
            durationMonths = durationMonths
        ),
        dailyDose = dailyDose,
        intakeTime = intakeTime
    )
}
