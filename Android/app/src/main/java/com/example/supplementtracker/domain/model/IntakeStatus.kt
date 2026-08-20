package com.example.supplementtracker.domain.model

/** Canonical persisted intake-history states. Storage values are schema-compatible. */
enum class IntakeStatus(val storageValue: String) {
    TAKEN("Taken"),
    SKIPPED("Skipped");

    companion object {
        fun fromStorage(raw: String?): IntakeStatus? = entries.firstOrNull { it.storageValue == raw }
    }
}
