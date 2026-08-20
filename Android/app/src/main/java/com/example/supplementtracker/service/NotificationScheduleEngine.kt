package com.example.supplementtracker.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

class NotificationScheduleEngine(
    private val context: Context,
    private val repository: SupplementRepository,
    private val currentClientId: () -> UUID?
) {
    suspend fun rescheduleAll() {
        val supplements = loadActiveSupplements()
        NotificationSchedulerImpl(context).rescheduleAll(supplements)
    }

    suspend fun reconcileIfNeeded(environmentChanged: Boolean = false): NotificationRecoveryDecision {
        val supplements = loadActiveSupplements()
        val scheduler = NotificationSchedulerImpl(context)
        val audit = scheduler.auditDebugEntries()
        val decision = NotificationRecoveryPolicy.decide(
            NotificationRecoveryInput(
                enabledByUser = OakPrefs.get(context).getBoolean("isNotificationEnabledByUser", false),
                permissionGranted = hasNotificationPermission(),
                activeSupplementCount = supplements.size,
                scheduledCount = audit.scheduledCount,
                missingPendingIntentCount = audit.missingPendingIntentCount,
                staleEntryCount = audit.staleEntryCount,
                environmentChanged = environmentChanged
            )
        )
        if (decision.action == NotificationRecoveryAction.REBUILD) {
            scheduler.rescheduleAll(supplements)
        }
        return decision
    }

    suspend fun clearAll() {
        val supplements = loadAllSupplements()
        NotificationSchedulerImpl(context).clearAll(supplements)
    }

    private suspend fun loadActiveSupplements() = currentClientId()
        ?.let { repository.getAllSupplements(it.toString()).first() }
        .orEmpty()

    private suspend fun loadAllSupplements() = repository.observeClients()
        .first()
        .flatMap { client -> repository.getAllSupplements(client.id.toString()).first() }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
