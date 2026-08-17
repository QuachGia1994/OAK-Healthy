package com.example.supplementtracker.domain.repository

import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.model.ClientProfile
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository định nghĩa các thao tác dữ liệu ở lớp Domain.
 */
interface SupplementRepository {
    // Clients
    suspend fun saveClient(profile: ClientProfile)
    suspend fun updateClient(profile: ClientProfile)
    suspend fun deleteClient(profile: ClientProfile)
    fun observeClients(): Flow<List<ClientProfile>>

    suspend fun saveSupplement(supplement: UserSupplement)
    suspend fun updateSupplement(supplement: UserSupplement)
    suspend fun deleteSupplement(supplement: UserSupplement)
    suspend fun getSupplementById(id: String): UserSupplement?
    fun getAllSupplements(clientId: String): Flow<List<UserSupplement>>
    fun getSupplementsWithTakenToday(clientId: String, startOfDay: Long, endOfDay: Long): Flow<List<UserSupplementTakenToday>>

    // Intake Records
    suspend fun logIntake(supplementId: String, date: Long)
    suspend fun insertIntakeRecord(record: IntakeRecord)
    suspend fun getIntakeRecordById(id: String): IntakeRecord?
    suspend fun deleteDuplicateIntakeRecords(supplementId: String, date: Long, keepId: String)
    suspend fun removeIntake(supplementId: String, date: Long)
    fun getRecordsByDateRange(clientId: String, startDate: Long, endDate: Long): Flow<List<IntakeRecord>>
    fun observeAllRecordsByClient(clientId: String): Flow<List<IntakeRecord>>
    suspend fun getAllRecordsByClient(clientId: String): List<IntakeRecord>
    suspend fun getAllSupplementsForSync(clientId: String): List<UserSupplement>
    suspend fun getAllRecordsForSync(clientId: String): List<IntakeRecord>
    suspend fun hasSupplementChangesSince(clientId: String, sinceEpochMs: Long): Boolean =
        getAllSupplementsForSync(clientId).any {
            maxOf(it.updatedAtEpochMs, it.deletedAtEpochMs ?: 0L) > sinceEpochMs
        }
    suspend fun hasHistoryChangesSince(clientId: String, sinceEpochMs: Long): Boolean =
        getAllRecordsForSync(clientId).any { it.updatedAtEpochMs > sinceEpochMs }
    suspend fun deleteAllSupplementsByClient(clientId: String)
    suspend fun deleteAllIntakeRecordsByClient(clientId: String)
    suspend fun importBackupAtomic(
        clientId: String,
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>
    )
}

data class IntakeRecord(
    val id: String,
    val supplementId: String,
    val date: Long,
    val status: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val supplementName: String? = null,
    val dailyDose: String? = null,
    val intakeTime: String? = null
)
