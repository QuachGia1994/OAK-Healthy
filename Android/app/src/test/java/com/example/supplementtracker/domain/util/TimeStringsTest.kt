package com.example.supplementtracker.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeStringsTest {
    @Test
    fun normalizeList_trimsAndSortsAndDedups() {
        val result = TimeStrings.normalizeList(" 7:05, 07:05 ; 21:30 | 21:30 ")
        assertEquals(listOf("07:05", "21:30"), result)
    }

    @Test
    fun parseLenient_rejectsOutOfRange() {
        assertEquals(null, TimeStrings.parseLenient("24:00"))
        assertEquals(null, TimeStrings.parseLenient("23:60"))
    }

    @Test
    fun removingTime_keepsOtherDoseTimes() {
        val result = TimeStrings.removingTime("7:00", from = "07:00, 14:30")
        assertEquals(listOf("14:30"), result)
    }

    @Test
    fun removingLastTime_leavesEmptySchedule() {
        val result = TimeStrings.removingTime("07:00", from = "7:00")
        assertEquals(emptyList<String>(), result)
    }
}
