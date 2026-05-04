package com.example.supplementtracker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object cho thực phẩm bổ sung.
 */
@Dao
interface SupplementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplement(supplement: SupplementEntity)

    @Delete
    suspend fun deleteSupplement(supplement: SupplementEntity)

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getSupplementById(id: String): SupplementEntity?

    @Query("SELECT * FROM supplements ORDER BY name ASC")
    fun getAllSupplements(): Flow<List<SupplementEntity>>

    // --- Intake Records ---

    @Insert
    suspend fun insertIntakeRecord(record: IntakeRecordEntity)

    @Query("SELECT * FROM intake_records WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<IntakeRecordEntity>>

    @Query("SELECT * FROM intake_records WHERE supplementId = :supplementId ORDER BY date DESC")
    fun getRecordsBySupplement(supplementId: String): Flow<List<IntakeRecordEntity>>

    @Query("DELETE FROM intake_records WHERE supplementId = :supplementId AND date >= :startOfDay AND date <= :endOfDay")
    suspend fun deleteRecordByDate(supplementId: String, startOfDay: Long, endOfDay: Long)
}
