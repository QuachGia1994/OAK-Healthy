package com.example.supplementtracker.domain.util

import java.util.Locale

object DoseEventKey {
    fun make(supplementId: String, scheduledAtEpochMs: Long): String {
        return "${supplementId.lowercase(Locale.ROOT)}-$scheduledAtEpochMs"
    }
}

