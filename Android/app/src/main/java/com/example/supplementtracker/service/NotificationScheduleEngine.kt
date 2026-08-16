package com.example.supplementtracker.service

import android.content.Context
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
}
