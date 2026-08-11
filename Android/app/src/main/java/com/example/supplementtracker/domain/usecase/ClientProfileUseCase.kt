package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.first

class ClientProfileUseCase(
    private val repository: SupplementRepository
) {
    suspend fun create(profile: ClientProfile): Boolean {
        val name = profile.name.trim()
        val exists = repository.observeClients().first().any { it.name.trim().equals(name, ignoreCase = true) }
        if (exists) return false
        repository.saveClient(profile)
        return true
    }

    suspend fun update(profile: ClientProfile): Boolean {
        val name = profile.name.trim()
        val exists = repository.observeClients().first().any {
            it.id != profile.id && it.name.trim().equals(name, ignoreCase = true)
        }
        if (exists) return false
        repository.updateClient(profile)
        return true
    }

    suspend fun delete(profile: ClientProfile) {
        repository.deleteClient(profile)
    }
}
