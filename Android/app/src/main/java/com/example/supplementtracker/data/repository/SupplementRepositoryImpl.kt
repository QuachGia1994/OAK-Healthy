package com.example.supplementtracker.data.repository

import com.example.supplementtracker.data.local.IntakeRecordEntity
import com.example.supplementtracker.data.local.SupplementDao
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.data.mapper.toEntity
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Triển khai Repository xử lý logic lưu trữ thực tế.
 */
class SupplementRepositoryImpl(
    private val dao: SupplementDao
) : SupplementRepository {

    override suspend fun saveSupplement(supplement: UserSupplement) = withContext(Dispatchers.IO) {
        dao.insertSupplement(supplement.toEntity())
    }

    override suspend fun deleteSupplement(supplement: UserSupplement) = withContext(Dispatchers.IO) {
        dao.deleteSupplement(supplement.toEntity())
    }

    override suspend fun getSupplementById(id: String): UserSupplement? = withContext(Dispatchers.IO) {
        dao.getSupplementById(id)?.toDomain()
    }

    override fun getAllSupplements(): Flow<List<UserSupplement>> {
        return dao.getAllSupplements().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun logIntake(supplementId: String, date: Long) = withContext(Dispatchers.IO) {
        dao.insertIntakeRecord(IntakeRecordEntity(supplementId = supplementId, date = date))
    }

    override suspend fun removeIntake(supplementId: String, date: Long) = withContext(Dispatchers.IO) {
        val startOfDay = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.deleteRecordByDate(supplementId, startOfDay, endOfDay)
    }

    override fun getRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<IntakeRecord>> {
        return combine(
            dao.getRecordsByDateRange(startDate, endDate),
            dao.getAllSupplements()
        ) { records, supplements ->
            records.map { record ->
                IntakeRecord(
                    id = record.id,
                    supplementId = record.supplementId,
                    date = record.date,
                    status = record.status,
                    supplementName = supplements.find { it.id == record.supplementId }?.name
                )
            }
        }
    }
}
