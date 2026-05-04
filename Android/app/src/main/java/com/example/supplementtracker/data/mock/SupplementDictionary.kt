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
            advice = "Giúp giảm căng thẳng và cải thiện giấc ngủ.",
            preferredTime = "21:00",
            defaultCycle = CycleConfig(56, 14)
        ),
        SupplementReference(
            name = "Boron",
            advice = "Nên uống theo chu kỳ để tối ưu nội tiết tố.",
            preferredTime = "08:00",
            defaultCycle = CycleConfig(14, 7)
        ),
        SupplementReference(
            name = "Vitamin D3",
            advice = "Nên uống cùng bữa ăn có chất béo.",
            preferredTime = "08:00",
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Omega-3",
            advice = "Nên uống sau bữa ăn giàu chất béo để hấp thu tốt nhất.",
            preferredTime = "12:00",
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "CoQ10",
            advice = "Cải thiện năng lượng tế bào, uống sau bữa ăn.",
            preferredTime = "12:00",
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Magnesium",
            advice = "Hỗ trợ cơ bắp và thần kinh, uống trước khi ngủ.",
            preferredTime = "21:00",
            defaultCycle = CycleConfig.Continuous
        ),
        SupplementReference(
            name = "Zinc",
            advice = "Không nên uống khi bụng đói.",
            preferredTime = "21:00",
            defaultCycle = CycleConfig(30, 7)
        )
    )
}
