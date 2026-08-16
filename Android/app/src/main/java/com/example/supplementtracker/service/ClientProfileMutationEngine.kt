package com.example.supplementtracker.service

import com.example.supplementtracker.domain.model.ClientProfile
import kotlinx.coroutines.CancellationException
import java.util.UUID

sealed interface ClientProfileMutationResult {
    data object Success : ClientProfileMutationResult
    data object DuplicateName : ClientProfileMutationResult
    data class Failure(val error: Throwable) : ClientProfileMutationResult
}

class ClientProfileMutationEngine(
    private val createProfile: suspend (ClientProfile) -> Boolean,
    private val updateProfile: suspend (ClientProfile) -> Boolean,
    private val deleteProfile: suspend (ClientProfile) -> Unit,
    private val loadClients: suspend () -> List<ClientProfile>,
    private val currentClientId: () -> UUID?,
    private val setCurrentClientId: (UUID?) -> Unit,
    private val clearCloudLinks: (UUID) -> Unit
) {
    suspend fun create(profile: ClientProfile): ClientProfileMutationResult = try {
        if (!createProfile(profile)) {
            ClientProfileMutationResult.DuplicateName
        } else {
            setCurrentClientId(profile.id)
            ClientProfileMutationResult.Success
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ClientProfileMutationResult.Failure(error)
    }

    suspend fun update(profile: ClientProfile): ClientProfileMutationResult = try {
        if (updateProfile(profile)) {
            ClientProfileMutationResult.Success
        } else {
            ClientProfileMutationResult.DuplicateName
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ClientProfileMutationResult.Failure(error)
    }

    suspend fun delete(profile: ClientProfile): ClientProfileMutationResult = try {
        val deletingActive = currentClientId() == profile.id
        val fallbackId = if (deletingActive) fallbackClientId(profile.id) else null
        deleteProfile(profile)
        clearCloudLinks(profile.id)
        if (deletingActive) setCurrentClientId(fallbackId)
        ClientProfileMutationResult.Success
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ClientProfileMutationResult.Failure(error)
    }

    private suspend fun fallbackClientId(deletingId: UUID): UUID? {
        return loadClients().firstOrNull { it.id != deletingId }?.id
    }
}
