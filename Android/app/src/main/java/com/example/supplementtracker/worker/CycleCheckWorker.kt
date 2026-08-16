package com.example.supplementtracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.service.NotificationSchedulerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CycleCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = SupplementDatabase.getInstance(applicationContext)
            val repository = SupplementRepositoryImpl(database.supplementDao)
            val scheduler = NotificationSchedulerImpl(applicationContext)
            val clients = repository.observeClients().first()
            val supplements = clients.flatMap { client ->
                repository.getAllSupplements(client.id.toString()).first()
            }
            supplements.forEach(scheduler::schedule)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
