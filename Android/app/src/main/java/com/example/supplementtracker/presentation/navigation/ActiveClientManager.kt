package com.example.supplementtracker.presentation.navigation

import android.content.Context
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.repository.SupplementRepository
import com.example.supplementtracker.service.ActiveClientStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ActiveClientManager(
    context: Context,
    repository: SupplementRepository
) {
    private val activeClientStore = ActiveClientStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val clients: StateFlow<List<ClientProfile>> = repository.observeClients()
        .map { list ->
            list
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name.trim().lowercase(Locale.ROOT) }
        }
        .distinctUntilChanged()
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentClientId = MutableStateFlow(activeClientStore.currentClientId())
    val currentClientId: StateFlow<UUID?> = _currentClientId.asStateFlow()

    init {
        scope.launch {
            clients.collect { list ->
                val current = _currentClientId.value
                val exists = current != null && list.any { it.id == current }
                if (exists) return@collect

                setCurrentClientId(list.firstOrNull()?.id)
            }
        }
    }

    fun setCurrentClientId(id: UUID?) {
        _currentClientId.value = id
        activeClientStore.setCurrentClientId(id)
    }
}
