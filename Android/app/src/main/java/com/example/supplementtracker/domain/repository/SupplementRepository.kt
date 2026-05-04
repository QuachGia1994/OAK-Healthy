package com.example.supplementtracker.domain.repository

import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository định nghĩa các thao tác dữ liệu ở lớp Domain.
 */
interface SupplementRepository {
    suspend fun saveSupplement(supplement: UserSupplement)
    suspend fun deleteSupplement(supplement: UserSupplement)
    suspend fun getSupplementById(id: String): UserSupplement?
    fun getAllSupplements(): Flow<List<UserSupplement>>
    fun getSupplementsWithTakenToday(startOfDay: Long, endOfDay: Long): Flow<List<UserSupplementTakenToday>>

    // Intake Records
    suspend fun logIntake(supplementId: String, date: Long)
    suspend fun removeIntake(supplementId: String, date: Long)
    fun getRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<IntakeRecord>>
}

data class IntakeRecord(
    val id: String,
    val supplementId: String,
    val date: Long,
    val status: String,
    val supplementName: String? = null
)
