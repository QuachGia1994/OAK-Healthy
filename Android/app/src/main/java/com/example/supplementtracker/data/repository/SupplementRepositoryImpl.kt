package com.example.supplementtracker.data.repository

import com.example.supplementtracker.data.local.IntakeRecordEntity
import com.example.supplementtracker.data.local.SupplementDao
import com.example.supplementtracker.data.local.ClientProfileEntity
import com.example.supplementtracker.data.mapper.toDomain
import com.example.supplementtracker.data.mapper.toEntity
import com.example.supplementtracker.domain.model.CycleConfig
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
        dao.updateSupplement(supplement.toEntity())
    }

    override suspend fun deleteSupplement(supplement: UserSupplement) = withContext(Dispatchers.IO) {
        dao.deleteSupplement(supplement.toEntity())
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
                val supplement = UserSupplement(
                    id = UUID.fromString(row.id),
                    clientId = UUID.fromString(row.clientId),
                    name = row.name,
                    startDate = LocalDate.parse(row.startDate),
                    cycleConfig = CycleConfig(
                        daysOn = row.daysOn,
                        daysOff = row.daysOff,
                        isContinuous = row.isContinuous,
                        durationMonths = row.durationMonths
                    ),
                    dailyDose = row.dailyDose,
                    intakeTime = row.intakeTime
                )
                UserSupplementTakenToday(supplement = supplement, isTakenToday = row.isTakenToday)
            }
        }
    }

    override suspend fun logIntake(supplementId: String, date: Long) = withContext(Dispatchers.IO) {
        dao.insertIntakeRecord(
            IntakeRecordEntity(
                id = "$supplementId-$date",
                supplementId = supplementId,
                date = date
            )
        )
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
                    supplementName = record.supplementName,
                    dailyDose = record.dailyDose,
                    intakeTime = record.intakeTime
                )
            }
        }
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
