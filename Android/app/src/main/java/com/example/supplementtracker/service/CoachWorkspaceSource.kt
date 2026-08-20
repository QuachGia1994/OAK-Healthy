package com.example.supplementtracker.service

import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.HealthDayBoundary
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class CoachWorkspaceSource(
    val clients: List<CoachClientSnapshot>,
    val records: Map<UUID, List<CoachRecordSnapshot>>
)

interface CoachWorkspaceSourceProvider {
    suspend fun load(): CoachWorkspaceSource
}

class RepositoryCoachWorkspaceSourceProvider(
    private val repository: SupplementRepository
) : CoachWorkspaceSourceProvider {
    override suspend fun load(): CoachWorkspaceSource {
        val clients = repository.observeClients().first()
        val (startEpochMs, endEpochMs) = coachWindowBounds()
        val records = clients.associate { client ->
            client.id to repository.getRecordsByDateRange(
                client.id.toString(), startEpochMs, endEpochMs
            ).first().map { CoachRecordSnapshot(epochMs = it.date, status = it.status) }
        }
        return CoachWorkspaceSource(
            clients = clients.map { CoachClientSnapshot(it.id, it.name) },
            records = records
        )
    }

    private fun coachWindowBounds(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDay = today.minusDays(PerformanceBudgets.COACH_HISTORY_DAYS - 1)
        val start = HealthDayBoundary.range(startDay, zone).startInclusive
        val endExclusive = HealthDayBoundary.range(today, zone).endExclusive
        return start to endExclusive
    }
}
