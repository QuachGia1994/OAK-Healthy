package com.example.supplementtracker.service

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

data class NotificationPlatformDiagnostics(
    val manufacturer: String,
    val powerSaveMode: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val exactAlarmAvailable: Boolean
)

object NotificationPlatformDiagnosticsProvider {
    fun read(context: Context): NotificationPlatformDiagnostics {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return NotificationPlatformDiagnostics(
            manufacturer = Build.MANUFACTURER.trim().ifEmpty { "Unknown" },
            powerSaveMode = powerManager.isPowerSaveMode,
            batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            },
            exactAlarmAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        )
    }
}
