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

    @Query("SELECT * FROM supplements WHERE clientId = :clientId AND deletedAtEpochMs IS NULL ORDER BY name ASC")
    fun getSupplementsByClient(clientId: String): Flow<List<SupplementEntity>>
    
    @Query("SELECT * FROM supplements WHERE clientId = :clientId ORDER BY name ASC")
    suspend fun getSupplementsByClientForSync(clientId: String): List<SupplementEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM supplements
            WHERE clientId = :clientId
            AND (updatedAtEpochMs > :sinceEpochMs OR COALESCE(deletedAtEpochMs, 0) > :sinceEpochMs)
            LIMIT 1
        )
        """
    )
    suspend fun hasSupplementChangesSince(clientId: String, sinceEpochMs: Long): Boolean

    // --- Intake Records ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntakeRecord(record: IntakeRecordEntity)
    
    @Query("SELECT * FROM intake_records WHERE id = :id LIMIT 1")
    suspend fun getIntakeRecordById(id: String): IntakeRecordEntity?

    @Query(
        """
        SELECT
            r.id AS id,
            r.supplementId AS supplementId,
            r.date AS date,
            r.status AS status,
            r.updatedAtEpochMs AS updatedAtEpochMs,
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

    @Query("DELETE FROM intake_records WHERE supplementId = :supplementId AND date = :date AND id != :keepId")
    suspend fun deleteDuplicateIntakeRecords(supplementId: String, date: Long, keepId: String)

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
            s.weeklyWeekdaysMask AS weeklyWeekdaysMask,
            s.weeklyIntervalWeeks AS weeklyIntervalWeeks,
            s.weeklyAnchorDate AS weeklyAnchorDate,
            s.intervalDays AS intervalDays,
            s.lastTakenLocalDate AS lastTakenLocalDate,
            (
                SELECT r.status
                FROM intake_records r
                WHERE r.supplementId = s.id
                AND r.date >= :startOfDay
                AND r.date <= :endOfDay
                ORDER BY r.updatedAtEpochMs DESC
                LIMIT 1
            ) AS todayStatus,
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
        AND s.deletedAtEpochMs IS NULL
        ORDER BY s.name ASC
        """
    )
    fun getSupplementsWithTakenToday(
        clientId: String,
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<SupplementWithTakenTodayEntity>>

    @Query(
        """
        SELECT
            r.id AS id,
            r.supplementId AS supplementId,
            r.date AS date,
            r.status AS status,
            r.updatedAtEpochMs AS updatedAtEpochMs,
            s.name AS supplementName,
            s.dailyDose AS dailyDose,
            s.intakeTime AS intakeTime
        FROM intake_records r
        INNER JOIN supplements s ON s.id = r.supplementId
        WHERE s.clientId = :clientId
        ORDER BY r.date DESC
        """
    )
    suspend fun getAllRecordsByClient(clientId: String): List<IntakeRecordWithSupplementEntity>
    
    @Query(
        """
        SELECT *
        FROM intake_records
        WHERE supplementId IN (SELECT id FROM supplements WHERE clientId = :clientId)
        ORDER BY date DESC
        LIMIT 5000
        """
    )
    suspend fun getAllRecordsByClientForSync(clientId: String): List<IntakeRecordEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM intake_records r
            INNER JOIN supplements s ON s.id = r.supplementId
            WHERE s.clientId = :clientId
            AND r.updatedAtEpochMs > :sinceEpochMs
            LIMIT 1
        )
        """
    )
    suspend fun hasHistoryChangesSince(clientId: String, sinceEpochMs: Long): Boolean

    @Query(
        """
        SELECT
            r.id AS id,
            r.supplementId AS supplementId,
            r.date AS date,
            r.status AS status,
            r.updatedAtEpochMs AS updatedAtEpochMs,
            s.name AS supplementName,
            s.dailyDose AS dailyDose,
            s.intakeTime AS intakeTime
        FROM intake_records r
        INNER JOIN supplements s ON s.id = r.supplementId
        WHERE s.clientId = :clientId
        ORDER BY r.date DESC
        """
    )
    fun observeAllRecordsByClient(clientId: String): Flow<List<IntakeRecordWithSupplementEntity>>

    @Query(
        """
        DELETE FROM intake_records
        WHERE supplementId IN (
            SELECT id FROM supplements WHERE clientId = :clientId
        )
        """
    )
    suspend fun deleteAllIntakeRecordsByClient(clientId: String)

    @Query("DELETE FROM supplements WHERE clientId = :clientId")
    suspend fun deleteAllSupplementsByClient(clientId: String)

    @Transaction
    suspend fun importBackupAtomic(
        clientId: String,
        supplements: List<SupplementEntity>,
        records: List<IntakeRecordEntity>
    ) {
        deleteAllIntakeRecordsByClient(clientId)
        deleteAllSupplementsByClient(clientId)
        supplements.forEach { insertSupplement(it) }
        records.forEach { insertIntakeRecord(it) }
    }
}

data class IntakeRecordWithSupplementEntity(
    val id: String,
    val supplementId: String,
    val date: Long,
    val status: String,
    val updatedAtEpochMs: Long,
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
    val weeklyWeekdaysMask: Int?,
    val weeklyIntervalWeeks: Int?,
    val weeklyAnchorDate: String?,
    val intervalDays: Int?,
    val lastTakenLocalDate: String?,
    val todayStatus: String?,
    val isTakenToday: Boolean
)
