package com.example.supplementtracker

import com.example.supplementtracker.presentation.navigation.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupPolicyTest {
    @Test
    fun storedThemeFallsBackToSystemForMissingOrInvalidValue() {
        assertEquals(AppTheme.SYSTEM, AppStartupPolicy.storedTheme(null))
        assertEquals(AppTheme.SYSTEM, AppStartupPolicy.storedTheme(""))
        assertEquals(AppTheme.SYSTEM, AppStartupPolicy.storedTheme("unknown"))
        assertEquals(AppTheme.DARK, AppStartupPolicy.storedTheme("DARK"))
    }

    @Test
    fun splashDelayNeverDropsBelowZero() {
        assertEquals(1_350L, AppStartupPolicy.remainingSplashDelay(0L))
        assertEquals(350L, AppStartupPolicy.remainingSplashDelay(1_000L))
        assertEquals(0L, AppStartupPolicy.remainingSplashDelay(1_350L))
        assertEquals(0L, AppStartupPolicy.remainingSplashDelay(2_000L))
    }

    @Test
    fun permissionRevocationOnlyDisablesExistingOptIn() {
        assertFalse(AppStartupPolicy.notificationPreferenceAfterPermissionCheck(true, false))
        assertFalse(AppStartupPolicy.notificationPreferenceAfterPermissionCheck(false, true))
        assertTrue(AppStartupPolicy.notificationPreferenceAfterPermissionCheck(true, true))
    }
}
