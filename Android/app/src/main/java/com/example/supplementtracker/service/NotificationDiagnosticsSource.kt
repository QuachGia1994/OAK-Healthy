package com.example.supplementtracker.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class NotificationDiagnosticsSnapshot(
    val upcoming: List<ScheduledAlarmInfo>,
    val alarmAudit: NotificationAlarmAudit,
    val enabledByUser: Boolean,
    val permissionGranted: Boolean,
    val platform: NotificationPlatformDiagnostics
)

interface NotificationDiagnosticsSource {
    fun read(): NotificationDiagnosticsSnapshot
}

class AndroidNotificationDiagnosticsSource(
    private val context: Context
) : NotificationDiagnosticsSource {
    override fun read(): NotificationDiagnosticsSnapshot {
        val platform = NotificationPlatformDiagnosticsProvider.read(context)
        return NotificationDiagnosticsSnapshot(
            upcoming = NotificationDebugStore.getUpcoming(context),
            alarmAudit = NotificationSchedulerImpl(context).auditDebugEntries(),
            enabledByUser = OakPrefs.get(context).getBoolean("isNotificationEnabledByUser", false),
            permissionGranted = permissionGranted(),
            platform = platform
        )
    }

    private fun permissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
