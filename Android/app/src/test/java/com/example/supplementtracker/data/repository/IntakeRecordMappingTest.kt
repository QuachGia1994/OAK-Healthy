package com.example.supplementtracker.data.repository

import com.example.supplementtracker.data.local.IntakeRecordWithSupplementEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class IntakeRecordMappingTest {
    @Test
    fun toDomain_usesUpdatedAtEpochMs() {
        val entity = IntakeRecordWithSupplementEntity(
            id = "r1",
            supplementId = "s1",
            date = 100L,
            status = "Taken",
            updatedAtEpochMs = 200L,
            supplementName = "Name",
            dailyDose = "1",
            intakeTime = "07:00"
        )

        val domain = entity.toDomain()

        assertEquals(200L, domain.updatedAtEpochMs)
    }
}

