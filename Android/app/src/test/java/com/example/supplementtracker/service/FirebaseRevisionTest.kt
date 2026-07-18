package com.example.supplementtracker.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseRevisionTest {
    @Test
    fun linkCodeValidationRejectsFirebasePathInjection() {
        assertTrue(FirebaseRevision.isValidBinId("-Oabc_123"))
        assertFalse(FirebaseRevision.isValidBinId("oakBins/other"))
        assertFalse(FirebaseRevision.isValidBinId("bin.with.dot"))
        assertFalse(FirebaseRevision.isValidBinId(" -Oabc_123 "))
        assertFalse(FirebaseRevision.isValidBinId("x".repeat(65)))
    }

    @Test
    fun revisionAlwaysIncreasesWhenClockDoesNot() {
        assertEquals(101L, FirebaseRevision.next(current = 100L, now = 99L))
        assertEquals(150L, FirebaseRevision.next(current = 100L, now = 150L))
    }

    @Test
    fun expectedRevisionMustMatchCurrentValue() {
        assertTrue(FirebaseRevision.matchesExpected(current = 7L, expected = ""))
        assertTrue(FirebaseRevision.matchesExpected(current = 7L, expected = "7"))
        assertFalse(FirebaseRevision.matchesExpected(current = 8L, expected = "7"))
        assertFalse(FirebaseRevision.matchesExpected(current = null, expected = "7"))
    }
}
