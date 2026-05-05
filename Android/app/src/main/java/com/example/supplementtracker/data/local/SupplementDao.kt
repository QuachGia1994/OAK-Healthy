package com.example.supplementtracker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object cho thực phẩm bổ sung.
 */
@Dao
interface SupplementDao {
    // --- Client Profiles ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClientProfile(profile: ClientProfileEntity)

    @Update
    suspend fun updateClientProfile(profile: ClientProfileEntity)

    @Delete
    suspend fun deleteClientProfile(profile: ClientProfileEntity)

    @Query("SELECT * FROM client_profiles ORDER BY createdAt ASC")
    fun observeClientProfiles(): Flow<List<ClientProfileEntity>>

    @Query("SELECT * FROM client_profiles WHERE id = :id")
    suspend fun getClientProfileById(id: String): ClientProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplement(supplement: SupplementEntity)

    @Update
    suspend fun updateSupplement(supplement: SupplementEntity)

    @Delete
    suspend fun deleteSupplement(supplement: SupplementEntity)

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getSupplementById(id: String): SupplementEntity?

    @Query("SELECT * FROM supplements WHERE clientId = :clientId ORDER BY name ASC")
    fun getSupplementsByClient(clientId: String): Flow<List<SupplementEntity>>

    // --- Intake Records ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntakeRecord(record: IntakeRecordEntity)

    @Query(
        """
        SELECT
            r.id AS id,
            r.supplementId AS supplementId,
            r.date AS date,
            r.status AS status,
            s.name AS supplementName,
            s.dailyDose AS dailyDose,
            s.intakeTime AS intakeTime
        FROM intake_records r
        INNER JOIN supplements s ON s.id = r.supplementId
        WHERE s.clientId = :clientId
        AND r.date >= :startDate
        AND r.date <= :endDate
        ORDER BY r.date DESC
        """
    )
    fun getRecordsByDateRange(
        clientId: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<IntakeRecordWithSupplementEntity>>

    @Query("SELECT * FROM intake_records WHERE supplementId = :supplementId ORDER BY date DESC")
    fun getRecordsBySupplement(supplementId: String): Flow<List<IntakeRecordEntity>>

    @Query("DELETE FROM intake_records WHERE supplementId = :supplementId AND date >= :startOfDay AND date <= :endOfDay")
    suspend fun deleteRecordByDate(supplementId: String, startOfDay: Long, endOfDay: Long)

    @Query(
        """
        SELECT
            s.id AS id,
            s.clientId AS clientId,
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
        WHERE s.clientId = :clientId
        ORDER BY s.name ASC
        """
    )
    fun getSupplementsWithTakenToday(
        clientId: String,
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<SupplementWithTakenTodayEntity>>
}

data class IntakeRecordWithSupplementEntity(
    val id: String,
    val supplementId: String,
    val date: Long,
    val status: String,
    val supplementName: String,
    val dailyDose: String,
    val intakeTime: String
)

data class SupplementWithTakenTodayEntity(
    val id: String,
    val clientId: String,
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
