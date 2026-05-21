package com.example.supplementtracker.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeStringsPerfGuardTest {
    @Test
    fun normalizeList_isIdempotent() {
        val once = TimeStrings.normalizeList("7:05, 21:30")
        val twice = TimeStrings.normalizeList(once.joinToString(", "))
        assertEquals(once, twice)
    }
}

