package com.example.supplementtracker.data.repository

import com.example.supplementtracker.data.local.IntakeRecordEntity
import com.example.supplementtracker.data.local.SupplementDao
import com.example.supplementtracker.data.local.ClientProfileEntity
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.data.mapper.toEntity
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
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
        endOfDay: Long
    ): Flow<List<UserSupplementTakenToday>> {
        return dao.getSupplementsWithTakenToday(clientId, startOfDay, endOfDay).map { rows ->
            rows.map { row ->
                val weekly = run {
                    val mask = row.weeklyWeekdaysMask ?: return@run null
                    val interval = row.weeklyIntervalWeeks ?: return@run null
                    val anchor = row.weeklyAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@run null
                    WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
                }
                val supplement = UserSupplement(
                    id = UUID.fromString(row.id),
                    clientId = UUID.fromString(row.clientId),
                    name = row.name,
                    startDate = LocalDate.parse(row.startDate),
                    cycleConfig = CycleConfig(
                        daysOn = row.daysOn,
                        daysOff = row.daysOff,
                        isContinuous = row.isContinuous,
                        durationMonths = row.durationMonths,
                        weeklyRecurrence = weekly
                    ),
                    dailyDose = row.dailyDose,
                    intakeTime = row.intakeTime,
                    updatedAtEpochMs = 0L,
                    deletedAtEpochMs = null
                )
                UserSupplementTakenToday(
                    supplement = supplement,
                    todayStatus = row.todayStatus,
                    isTakenToday = row.isTakenToday
                )
            }
        }
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

    override suspend fun deleteDuplicateIntakeRecords(supplementId: String, date: Long, keepId: String) = withContext(Dispatchers.IO) {
        val normalizedSupplementId = supplementId.trim()
        val normalizedKeepId = keepId.trim()
        if (normalizedSupplementId.isEmpty()) return@withContext
        if (date <= 0L) return@withContext
        if (normalizedKeepId.isEmpty()) return@withContext
        dao.deleteDuplicateIntakeRecords(supplementId = normalizedSupplementId, date = date, keepId = normalizedKeepId)
    }

    override suspend fun removeIntake(supplementId: String, date: Long) = withContext(Dispatchers.IO) {
        val startOfDay = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.deleteRecordByDate(supplementId, startOfDay, endOfDay)
    }

    override fun getRecordsByDateRange(clientId: String, startDate: Long, endDate: Long): Flow<List<IntakeRecord>> {
        return dao.getRecordsByDateRange(clientId, startDate, endDate).map { records ->
            records.map { record ->
                IntakeRecord(
                    id = record.id,
                    supplementId = record.supplementId,
                    date = record.date,
                    status = record.status,
                    updatedAtEpochMs = record.date,
                    supplementName = record.supplementName,
                    dailyDose = record.dailyDose,
                    intakeTime = record.intakeTime
                )
            }
        }
    }

    override fun observeAllRecordsByClient(clientId: String): Flow<List<IntakeRecord>> {
        return dao.observeAllRecordsByClient(clientId).map { records ->
            records.map { record ->
                IntakeRecord(
                    id = record.id,
                    supplementId = record.supplementId,
                    date = record.date,
                    status = record.status,
                    updatedAtEpochMs = record.date,
                    supplementName = record.supplementName,
                    dailyDose = record.dailyDose,
                    intakeTime = record.intakeTime
                )
            }
        }
    }

    override suspend fun getAllRecordsByClient(clientId: String): List<IntakeRecord> = withContext(Dispatchers.IO) {
        dao.getAllRecordsByClient(clientId).map { record ->
            IntakeRecord(
                id = record.id,
                supplementId = record.supplementId,
                date = record.date,
                status = record.status,
                updatedAtEpochMs = record.date,
                supplementName = record.supplementName,
                dailyDose = record.dailyDose,
                intakeTime = record.intakeTime
            )
        }
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
