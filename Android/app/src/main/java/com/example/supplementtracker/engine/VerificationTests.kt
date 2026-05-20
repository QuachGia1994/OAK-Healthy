package com.example.supplementtracker.engine

import com.example.supplementtracker.domain.util.DoseEventKey

object DoseEventVerification {
    data class FakeRecord(
        val supplementId: String,
        val scheduledAtEpochMs: Long,
        val updatedAtEpochMs: Long,
        val status: String
    )

    fun verify(): List<String> {
        val failures = ArrayList<String>()

        val supplementId = "SUPP-1"
        val scheduledAt = 1_700_000_000_000L
        val key1 = DoseEventKey.make(supplementId, scheduledAt)
        val key2 = DoseEventKey.make(supplementId, scheduledAt)
        if (key1 != key2) failures.add("doseKey not deterministic")

        val older = FakeRecord(supplementId, scheduledAt, 100, "Taken")
        val newer = FakeRecord(supplementId, scheduledAt, 200, "Skipped")
        val selected = listOf(older, newer).maxByOrNull { it.updatedAtEpochMs }
        if (selected?.status != "Skipped") failures.add("LWW selection failed")

        return if (failures.isEmpty()) listOf("OK") else failures
    }
}

