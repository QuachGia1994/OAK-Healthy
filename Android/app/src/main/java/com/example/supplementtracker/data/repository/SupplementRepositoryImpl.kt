package com.example.supplementtracker.data.repository

import com.example.supplementtracker.data.local.IntakeRecordEntity
import com.example.supplementtracker.data.local.IntakeRecordWithSupplementEntity
import com.example.supplementtracker.data.local.SupplementDao
import com.example.supplementtracker.data.local.SupplementWithTakenTodayEntity
import com.example.supplementtracker.data.local.ClientProfileEntity
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.data.mapper.toEntity
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.example.supplementtracker.domain.util.HealthDayBoundary
import java.util.UUID

/**
 * Triển khai Repository xử lý logic lưu trữ thực tế.
 */
class SupplementRepositoryImpl(
    private val dao: SupplementDao
) : SupplementRepository {

    override suspend fun saveClient(profile: ClientProfile) = withContext(Dispatchers.IO) {
        dao.insertClientProfile(profile.toEntity())
    }

    override suspend fun updateClient(profile: ClientProfile) = withContext(Dispatchers.IO) {
        dao.updateClientProfile(profile.toEntity())
    }

    override suspend fun deleteClient(profile: ClientProfile) = withContext(Dispatchers.IO) {
        dao.deleteClientProfile(profile.toEntity())
    }

    override fun observeClients(): Flow<List<ClientProfile>> {
        return dao.observeClientProfiles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveSupplement(supplement: UserSupplement) = withContext(Dispatchers.IO) {
        dao.insertSupplement(supplement.toEntity())
    }

    override suspend fun updateSupplement(supplement: UserSupplement) = withContext(Dispatchers.IO) {
        val existing = dao.getSupplementById(supplement.id.toString())
        val preservedDeletedAt = existing?.deletedAtEpochMs
        dao.updateSupplement(
            supplement.copy(deletedAtEpochMs = preservedDeletedAt).toEntity()
        )
    }

    override suspend fun deleteSupplement(supplement: UserSupplement) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.getSupplementById(supplement.id.toString()) ?: return@withContext
        dao.updateSupplement(existing.copy(updatedAtEpochMs = now, deletedAtEpochMs = now))
    }

    override suspend fun getSupplementById(id: String): UserSupplement? = withContext(Dispatchers.IO) {
        dao.getSupplementById(id)?.toDomain()
    }

    override fun getAllSupplements(clientId: String): Flow<List<UserSupplement>> {
        return dao.getSupplementsByClient(clientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSupplementsWithTakenToday(
        clientId: String,
        startOfDay: Long,
        endExclusive: Long
    ): Flow<List<UserSupplementTakenToday>> {
        return dao.getSupplementsWithTakenToday(clientId, startOfDay, endExclusive).map { rows ->
            rows.map { row -> row.toTakenToday() }
        }
    }

    private fun SupplementWithTakenTodayEntity.toTakenToday(): UserSupplementTakenToday {
        return UserSupplementTakenToday(
            supplement = toSupplement(),
            todayStatus = todayStatus,
            isTakenToday = isTakenToday
        )
    }

    private fun SupplementWithTakenTodayEntity.toSupplement(): UserSupplement {
        val weekly = weeklyRecurrenceOrNull()
        return UserSupplement(
            id = UUID.fromString(id),
            clientId = UUID.fromString(clientId),
            name = name,
            startDate = LocalDate.parse(startDate),
            cycleConfig = CycleConfig(
                daysOn = daysOn,
                daysOff = daysOff,
                isContinuous = isContinuous,
                durationMonths = durationMonths,
                weeklyRecurrence = weekly,
                intervalDays = intervalDays
            ),
            dailyDose = dailyDose,
            intakeTime = intakeTime,
            lastTakenLocalDate = lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            updatedAtEpochMs = 0L,
            deletedAtEpochMs = null
        )
    }

    private fun SupplementWithTakenTodayEntity.weeklyRecurrenceOrNull(): WeeklyRecurrenceConfig? {
        val mask = weeklyWeekdaysMask ?: return null
        val interval = weeklyIntervalWeeks ?: return null
        val anchorRaw = weeklyAnchorDate ?: return null
        val anchor = runCatching { LocalDate.parse(anchorRaw) }.getOrNull() ?: return null
        return WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
    }

    override suspend fun logIntake(supplementId: String, date: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insertIntakeRecord(
            IntakeRecordEntity(
                id = "$supplementId-$date",
                supplementId = supplementId,
                date = date
                ,
                updatedAtEpochMs = now
            )
        )
    }

    override suspend fun insertIntakeRecord(record: IntakeRecord) = withContext(Dispatchers.IO) {
        dao.insertIntakeRecord(
            IntakeRecordEntity(
                id = record.id,
                supplementId = record.supplementId,
                date = record.date,
                status = record.status,
                updatedAtEpochMs = record.updatedAtEpochMs
            )
        )
    }
    
    override suspend fun getIntakeRecordById(id: String): IntakeRecord? = withContext(Dispatchers.IO) {
        val key = id.trim()
        if (key.isEmpty()) return@withContext null
        val entity = dao.getIntakeRecordById(key) ?: return@withContext null
        IntakeRecord(
            id = entity.id,
            supplementId = entity.supplementId,
            date = entity.date,
            status = entity.status,
            updatedAtEpochMs = entity.updatedAtEpochMs
        )
    }

    override suspend fun deleteDuplicateIntakeRecords(supplementId: String, date: Long, keepId: String) = withContext(Dispatchers.IO) {
        val normalizedSupplementId = supplementId.trim()
        val normalizedKeepId = keepId.trim()
        if (normalizedSupplementId.isEmpty()) return@withContext
        if (date <= 0L) return@withContext
        if (normalizedKeepId.isEmpty()) return@withContext
        dao.deleteDuplicateIntakeRecords(supplementId = normalizedSupplementId, date = date, keepId = normalizedKeepId)
    }

    override suspend fun removeIntake(supplementId: String, date: Long) = withContext(Dispatchers.IO) {
        val range = HealthDayBoundary.rangeFor(date)
        dao.deleteRecordByDate(supplementId, range.startInclusive, range.endExclusive)
    }

    override fun getRecordsByDateRange(clientId: String, startDate: Long, endExclusive: Long): Flow<List<IntakeRecord>> {
        return dao.getRecordsByDateRange(clientId, startDate, endExclusive).map { records ->
            records.map { it.toDomain() }
        }
    }

    override fun observeAllRecordsByClient(clientId: String): Flow<List<IntakeRecord>> {
        return dao.observeAllRecordsByClient(clientId).map { records ->
            records.map { it.toDomain() }
        }
    }

    override suspend fun getAllRecordsByClient(clientId: String): List<IntakeRecord> = withContext(Dispatchers.IO) {
        dao.getAllRecordsByClient(clientId).map { it.toDomain() }
    }
    
    override suspend fun getAllSupplementsForSync(clientId: String): List<UserSupplement> = withContext(Dispatchers.IO) {
        dao.getSupplementsByClientForSync(clientId).map { it.toDomain() }
    }
    
    override suspend fun getAllRecordsForSync(clientId: String): List<IntakeRecord> = withContext(Dispatchers.IO) {
        dao.getAllRecordsByClientForSync(clientId).map { entity ->
            IntakeRecord(
                id = entity.id,
                supplementId = entity.supplementId,
                date = entity.date,
                status = entity.status,
                updatedAtEpochMs = entity.updatedAtEpochMs
            )
        }
    }

    override suspend fun hasSupplementChangesSince(clientId: String, sinceEpochMs: Long): Boolean =
        withContext(Dispatchers.IO) { dao.hasSupplementChangesSince(clientId, sinceEpochMs) }

    override suspend fun hasHistoryChangesSince(clientId: String, sinceEpochMs: Long): Boolean =
        withContext(Dispatchers.IO) { dao.hasHistoryChangesSince(clientId, sinceEpochMs) }

    override suspend fun deleteAllSupplementsByClient(clientId: String) = withContext(Dispatchers.IO) {
        dao.deleteAllSupplementsByClient(clientId)
    }

    override suspend fun deleteAllIntakeRecordsByClient(clientId: String) = withContext(Dispatchers.IO) {
        dao.deleteAllIntakeRecordsByClient(clientId)
    }

    override suspend fun importBackupAtomic(
        clientId: String,
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>
    ) = withContext(Dispatchers.IO) {
        dao.importBackupAtomic(
            clientId = clientId,
            supplements = supplements.map { it.toEntity() },
            records = records.map { record ->
                IntakeRecordEntity(
                    id = record.id,
                    supplementId = record.supplementId,
                    date = record.date,
                    status = record.status,
                    updatedAtEpochMs = record.updatedAtEpochMs
                )
            }
        )
    }
}

private fun ClientProfile.toEntity(): ClientProfileEntity {
    return ClientProfileEntity(
        id = id.toString(),
        name = name,
        avatarColorArgb = avatarColorArgb,
        createdAt = createdAt
    )
}

private fun ClientProfileEntity.toDomain(): ClientProfile {
    return ClientProfile(
        id = UUID.fromString(id),
        name = name,
        avatarColorArgb = avatarColorArgb,
        createdAt = createdAt
    )
}

internal fun IntakeRecordWithSupplementEntity.toDomain(): IntakeRecord {
    return IntakeRecord(
        id = id,
        supplementId = supplementId,
        date = date,
        status = status,
        updatedAtEpochMs = updatedAtEpochMs,
        supplementName = supplementName,
        dailyDose = dailyDose,
        intakeTime = intakeTime
    )
}
