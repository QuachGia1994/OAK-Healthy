package com.example.supplementtracker.data.mock

import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.SupplementReference

/**
 * Từ điển cục bộ cung cấp dữ liệu mặc định.
 */
object SupplementDictionary {
    val references = listOf(
        SupplementReference(
            name = "Ashwagandha",
            preferredTime = IntakeTime.NIGHT,
            defaultCycle = CycleConfig(56, 14)
        ),
        SupplementReference(
            name = "Boron",
            preferredTime = IntakeTime.MORNING,
            defaultCycle = CycleConfig(14, 7)
        ),
        SupplementReference(
            name = "Vitamin D3",
            preferredTime = IntakeTime.MORNING,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Magnesium",
            preferredTime = IntakeTime.NIGHT,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Zinc",
            preferredTime = IntakeTime.NIGHT,
            defaultCycle = CycleConfig(30, 7)
        )
    )
}
