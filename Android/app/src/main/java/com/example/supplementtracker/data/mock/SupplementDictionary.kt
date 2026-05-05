package com.example.supplementtracker.data.mock

import android.content.Context
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.SupplementReference

/**
 * Từ điển cục bộ cung cấp dữ liệu mặc định.
 */
object SupplementDictionary {
    private data class Entry(
        val name: String,
        val adviceResId: Int?,
        val preferredTime: String,
        val preferredDose: String?,
        val defaultCycle: CycleConfig
    )

    private val entries = listOf(
        Entry(
            name = "Ashwagandha",
            adviceResId = R.string.supplement_note_ashwagandha,
            preferredTime = "21:00",
            preferredDose = null,
            defaultCycle = CycleConfig(28, 7)
        ),
        Entry(
            name = "Boron",
            adviceResId = R.string.supplement_note_boron,
            preferredTime = "08:00",
            preferredDose = null,
            defaultCycle = CycleConfig(14, 7)
        ),
        Entry(
            name = "Caffeine",
            adviceResId = R.string.supplement_note_caffeine,
            preferredTime = "07:30",
            preferredDose = "200 mg",
            defaultCycle = CycleConfig.Continuous
        ),
        Entry(
            name = "Vitamin D3",
            adviceResId = R.string.supplement_note_vitamin_d3,
            preferredTime = "08:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        Entry(
            name = "Omega-3",
            adviceResId = R.string.supplement_note_omega_3,
            preferredTime = "12:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        Entry(
            name = "CoQ10",
            adviceResId = R.string.supplement_note_coq10,
            preferredTime = "12:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        Entry(
            name = "Creatine",
            adviceResId = R.string.supplement_note_creatine,
            preferredTime = "12:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        Entry(
            name = "Magnesium",
            adviceResId = R.string.supplement_note_magnesium,
            preferredTime = "21:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        ),
        Entry(
            name = "Zinc",
            adviceResId = R.string.supplement_note_zinc,
            preferredTime = "21:00",
            preferredDose = null,
            defaultCycle = CycleConfig.Continuous
        )
    )

    fun localizedReferences(context: Context): List<SupplementReference> {
        return entries.map { entry ->
            SupplementReference(
                name = entry.name,
                advice = entry.adviceResId?.let(context::getString),
                preferredTime = entry.preferredTime,
                preferredDose = entry.preferredDose,
                defaultCycle = entry.defaultCycle
            )
        }
    }
}
