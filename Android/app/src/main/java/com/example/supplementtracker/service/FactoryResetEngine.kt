package com.example.supplementtracker.service

import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.first

class FactoryResetEngine(
    private val repository: SupplementRepository,
    private val clearNotifications: suspend () -> Unit,
    private val disableAutoSync: () -> Unit,
    private val clearPreferences: () -> Unit,
    private val clearCryptoMaterial: () -> Unit,
    private val clearActiveClient: () -> Unit
) {
    suspend fun reset(): Result<Unit> = runCatching {
        clearNotifications()
        disableAutoSync()
        deleteAllClients()
        clearPreferences()
        clearCryptoMaterial()
        clearActiveClient()
    }

    private suspend fun deleteAllClients() {
        repository.observeClients().first().forEach { client ->
            repository.deleteClient(client)
        }
    }
}
