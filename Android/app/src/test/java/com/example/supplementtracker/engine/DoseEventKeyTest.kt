package com.example.supplementtracker.engine

import com.example.supplementtracker.domain.util.DoseEventKey
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseEventKeyTest {
    @Test
    fun make_isDeterministic() {
        val supplementId = "SUPP-1"
        val scheduledAt = 1_700_000_000_000L

        val key1 = DoseEventKey.make(supplementId, scheduledAt)
        val key2 = DoseEventKey.make(supplementId, scheduledAt)

        assertEquals(key1, key2)
    }
}

