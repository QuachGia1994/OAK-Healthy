package com.example.supplementtracker.service

import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.first
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
        val records = clients.associate { client ->
            client.id to repository.getAllRecordsByClient(client.id.toString()).map {
                CoachRecordSnapshot(epochMs = it.date, status = it.status)
            }
        }
        return CoachWorkspaceSource(
            clients = clients.map { CoachClientSnapshot(it.id, it.name) },
            records = records
        )
    }
}
