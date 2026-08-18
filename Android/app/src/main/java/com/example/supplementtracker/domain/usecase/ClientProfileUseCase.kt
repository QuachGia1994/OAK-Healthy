package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.domain.util.ClientNamePolicy
import kotlinx.coroutines.flow.first

class ClientProfileUseCase(
    private val repository: SupplementRepository
) {
    suspend fun create(profile: ClientProfile): Boolean {
        val name = ClientNamePolicy.cleaned(profile.name)
        if (!ClientNamePolicy.isValid(name)) return false
        val canonical = ClientNamePolicy.canonical(name)
        val exists = repository.observeClients().first().any { ClientNamePolicy.canonical(it.name) == canonical }
        if (exists) return false
        repository.saveClient(profile.copy(name = name))
        return true
    }

    suspend fun update(profile: ClientProfile): Boolean {
        val name = ClientNamePolicy.cleaned(profile.name)
        if (!ClientNamePolicy.isValid(name)) return false
        val canonical = ClientNamePolicy.canonical(name)
        val exists = repository.observeClients().first().any {
            it.id != profile.id && ClientNamePolicy.canonical(it.name) == canonical
        }
        if (exists) return false
        repository.updateClient(profile.copy(name = name))
        return true
    }

    suspend fun delete(profile: ClientProfile) {
        repository.deleteClient(profile)
    }
}
