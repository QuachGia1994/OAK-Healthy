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
            advice = "Supports stress reduction and sleep quality.",
            preferredTime = "21:00",
            preferredDose = null,
            defaultCycle = CycleConfig(28, 7)
        ),
        SupplementReference(
            name = "Boron",
            advice = "Cycle it to support hormone optimization.",
            preferredTime = "08:00",
            preferredDose = null,
            defaultCycle = CycleConfig(14, 7)
        ),
        SupplementReference(
            name = "Caffeine",
            advice = "Boosts focus and energy for workouts and trading sessions. Take ~30 minutes pre-workout.",
            preferredTime = "07:30",
            preferredDose = "200 mg",
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Vitamin D3",
            advice = "Take with a fat-containing meal for better absorption.",
            preferredTime = "08:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Omega-3",
            advice = "Take after a fatty meal for best absorption.",
            preferredTime = "12:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "CoQ10",
            advice = "Supports cellular energy. Take after a meal.",
            preferredTime = "12:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Creatine",
            advice = "Supports strength and performance. Take daily.",
            preferredTime = "12:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Magnesium",
            advice = "Supports muscles and nervous system. Take before sleep.",
            preferredTime = "21:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Zinc",
            advice = "Avoid taking on an empty stomach.",
            preferredTime = "21:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        )
    )
}
