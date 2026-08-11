package com.example.supplementtracker.service

import android.content.Context
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.first

class NotificationScheduleEngine(
    private val context: Context,
    private val repository: SupplementRepository
) {
    suspend fun rescheduleAll() {
        val supplements = loadAllSupplements()
        NotificationSchedulerImpl(context).rescheduleAll(supplements)
    }

    suspend fun clearAll() {
        val supplements = loadAllSupplements()
        NotificationSchedulerImpl(context).clearAll(supplements)
    }

    private suspend fun loadAllSupplements() = repository.observeClients()
        .first()
        .flatMap { client -> repository.getAllSupplements(client.id.toString()).first() }
}
