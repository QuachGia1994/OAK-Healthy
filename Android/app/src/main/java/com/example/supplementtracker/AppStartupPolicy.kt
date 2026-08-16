package com.example.supplementtracker

import com.example.supplementtracker.presentation.navigation.AppTheme

internal object AppStartupPolicy {
    const val MIN_SPLASH_MILLIS = 1_350L

    fun storedTheme(raw: String?): AppTheme {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return AppTheme.SYSTEM
        return runCatching { AppTheme.valueOf(value) }.getOrDefault(AppTheme.SYSTEM)
    }

    fun remainingSplashDelay(elapsedMillis: Long): Long {
        return (MIN_SPLASH_MILLIS - elapsedMillis).coerceAtLeast(0L)
    }

    fun notificationPreferenceAfterPermissionCheck(
        storedEnabled: Boolean,
        permissionGranted: Boolean
    ): Boolean = storedEnabled && permissionGranted
}
