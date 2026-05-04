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

    @Update
    suspend fun updateSupplement(supplement: SupplementEntity)

    @Delete
    suspend fun deleteSupplement(supplement: SupplementEntity)

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getSupplementById(id: String): SupplementEntity?

    @Query("SELECT * FROM supplements ORDER BY name ASC")
    fun getAllSupplements(): Flow<List<SupplementEntity>>

    // --- Intake Records ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntakeRecord(record: IntakeRecordEntity)

    @Query("SELECT * FROM intake_records WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<IntakeRecordEntity>>

    @Query("SELECT * FROM intake_records WHERE supplementId = :supplementId ORDER BY date DESC")
    fun getRecordsBySupplement(supplementId: String): Flow<List<IntakeRecordEntity>>

    @Query("DELETE FROM intake_records WHERE supplementId = :supplementId AND date >= :startOfDay AND date <= :endOfDay")
    suspend fun deleteRecordByDate(supplementId: String, startOfDay: Long, endOfDay: Long)

    @Query(
        """
        SELECT
            s.id AS id,
            s.name AS name,
            s.startDate AS startDate,
            s.daysOn AS daysOn,
            s.daysOff AS daysOff,
            s.isContinuous AS isContinuous,
            s.durationMonths AS durationMonths,
            s.dailyDose AS dailyDose,
            s.intakeTime AS intakeTime,
            CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM intake_records r
                    WHERE r.supplementId = s.id
                    AND r.date >= :startOfDay
                    AND r.date <= :endOfDay
                ) THEN 1
                ELSE 0
            END AS isTakenToday
        FROM supplements s
        ORDER BY s.name ASC
        """
    )
    fun getSupplementsWithTakenToday(startOfDay: Long, endOfDay: Long): Flow<List<SupplementWithTakenTodayEntity>>
}

data class SupplementWithTakenTodayEntity(
    val id: String,
    val name: String,
    val startDate: String,
    val daysOn: Int,
    val daysOff: Int,
    val isContinuous: Boolean,
    val durationMonths: Int?,
    val dailyDose: String,
    val intakeTime: String,
    val isTakenToday: Boolean
)
