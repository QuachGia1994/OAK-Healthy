package com.example.supplementtracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.service.NotificationScheduler
import com.example.supplementtracker.service.NotificationSchedulerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Worker chạy hàng ngày để kiểm tra chu kỳ On/Off và lên lịch nhắc nhở.
 */
class CycleCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: SupplementRepository, // Giả định DI cung cấp repository
    private val scheduler: NotificationScheduler = NotificationSchedulerImpl(context)
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val clients = repository.observeClients().first()
            val supplements = clients.flatMap { client ->
                repository.getAllSupplements(client.id.toString()).first()
            }

            // 2. Kiểm tra chu kỳ cho từng chất
            for (supplement in supplements) {
                scheduler.schedule(supplement)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
