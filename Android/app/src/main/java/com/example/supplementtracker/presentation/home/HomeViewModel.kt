package com.example.supplementtracker.presentation.home

import android.content.Context
import com.example.supplementtracker.service.OakPrefs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.domain.usecase.CalculateHomeDashboardUseCase
import com.example.supplementtracker.domain.usecase.ClientProfileUseCase
import com.example.supplementtracker.domain.usecase.ImportBackupUseCase
import com.example.supplementtracker.domain.usecase.RecordDoseUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.util.DoseEventKey
import com.example.supplementtracker.domain.util.TimeStrings
import com.example.supplementtracker.data.mock.SupplementDictionary
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.R
import java.util.Locale
import com.example.supplementtracker.service.CloudSyncEngine
import com.example.supplementtracker.service.CloudHostEngine
import com.example.supplementtracker.service.CloudBackupEngine
import com.example.supplementtracker.service.CloudSyncCrypto
import com.example.supplementtracker.service.CloudSyncPayloadCodec
import com.example.supplementtracker.service.CloudSyncLogStore
import com.example.supplementtracker.service.CloudSyncProfileStore
import com.example.supplementtracker.service.ActiveProfileNotificationPolicy
import com.example.supplementtracker.service.FactoryResetEngine
import com.example.supplementtracker.service.ClientProfileMutationEngine
import com.example.supplementtracker.service.ClientProfileMutationResult
import com.example.supplementtracker.service.CommercialFeature
import com.example.supplementtracker.service.EntitlementManager
import com.example.supplementtracker.service.NotificationScheduleEngine
import com.example.supplementtracker.worker.CloudAutoSyncWork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val context: Context,
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val activeClientManager: ActiveClientManager,
    private val entitlementManager: EntitlementManager,
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) : ViewModel() {
    private val _today = MutableStateFlow(LocalDate.now())
    val currentDay: StateFlow<LocalDate> = _today

    enum class CloudSyncPhase {
        IDLE,
        PULLING,
        MERGING,
        PUSHING,
        RETRYING_CONFLICT,
        DONE,
        ERROR
    }

    data class CloudSyncUiStatus(
        val binId: String,
        val lastSyncEpochMs: Long,
        val lastAttemptEpochMs: Long,
        val hasPendingChanges: Boolean,
        val lastError: String?,
        val phase: CloudSyncPhase,
        val conflictRetryCount: Int,
        val bytesDownloaded: Long,
        val bytesUploaded: Long,
        val pullMs: Long,
        val mergeMs: Long,
        val pushMs: Long,
        val totalMs: Long
    )

    private val _refreshTrigger = MutableStateFlow(0)
    private val _lastNotificationRebuildEpochMs = MutableStateFlow(
        OakPrefs.get(context).getLong("oakLastNotificationRebuildEpochMs", 0L)
    )
    val lastNotificationRebuildEpochMs: StateFlow<Long> = _lastNotificationRebuildEpochMs
    private val _dataTransferMessage = MutableStateFlow<String?>(null)
    val dataTransferMessage: StateFlow<String?> = _dataTransferMessage
    private val _cloudSyncLoading = MutableStateFlow(false)
    val cloudSyncLoading: StateFlow<Boolean> = _cloudSyncLoading
    private val cloudSyncProfileStore = CloudSyncProfileStore(context)
    private val initialCloudLinks = cloudSyncProfileStore.links(activeClientManager.currentClientId.value)
    private val _hostedBinId = MutableStateFlow(initialCloudLinks.hostedBinId)
    val hostedBinId: StateFlow<String?> = _hostedBinId
    private val _linkedBinId = MutableStateFlow(initialCloudLinks.linkedBinId)
    val linkedBinId: StateFlow<String?> = _linkedBinId
    private val _cloudSyncUiStatus = MutableStateFlow<CloudSyncUiStatus?>(null)
    val cloudSyncUiStatus: StateFlow<CloudSyncUiStatus?> = _cloudSyncUiStatus
    private var pendingAutoSyncJob: Job? = null
    private val cloudBackupEngine = CloudBackupEngine(
        context = context,
        repository = repository,
        currentClientId = { activeClientManager.currentClientId.value }
    )
    private val cloudSyncEngine = CloudSyncEngine(
        context = context,
        repository = repository,
        currentClientId = { activeClientManager.currentClientId.value },
        buildFullBackupJson = { clientId -> cloudBackupEngine.buildFullBackupJson(clientId) },
        buildStackBackupJson = { clientId -> cloudBackupEngine.buildStackBackupJson(clientId) },
        buildHistoryBackupJson = { clientId -> cloudBackupEngine.buildHistoryBackupJson(clientId) },
        updateUi = { updateCloudSyncUiStatus(it) },
        setLoading = { _cloudSyncLoading.value = it },
        rescheduleNotifications = { rescheduleNotificationsNow() },
        disableAutoSync = { stopAutoSync() },
        appendLog = CloudSyncLogStore::append
    )
    private val calculateHomeDashboardUseCase = CalculateHomeDashboardUseCase(calculateCycleUseCase)
    private val importBackupUseCase = ImportBackupUseCase(repository)
    private val recordDoseUseCase = RecordDoseUseCase(repository)
    private val notificationScheduleEngine = NotificationScheduleEngine(
        context,
        repository,
        { activeClientManager.currentClientId.value }
    )
    private val factoryResetEngine = FactoryResetEngine(
        repository = repository,
        clearNotifications = { notificationScheduleEngine.clearAll() },
        disableAutoSync = { stopAutoSync() },
        clearPreferences = { check(OakPrefs.get(context).edit().clear().commit()) },
        clearCryptoMaterial = { CloudSyncCrypto.clearLocalKeyMaterial().getOrThrow() },
        clearActiveClient = { activeClientManager.setCurrentClientId(null) }
    )
    private val clientProfileUseCase = ClientProfileUseCase(repository)
    private val clientProfileMutationEngine = ClientProfileMutationEngine(
        createProfile = clientProfileUseCase::create,
        updateProfile = clientProfileUseCase::update,
        deleteProfile = clientProfileUseCase::delete,
        loadClients = { repository.observeClients().first() },
        currentClientId = { activeClientManager.currentClientId.value },
        setCurrentClientId = activeClientManager::setCurrentClientId,
        clearCloudLinks = cloudSyncProfileStore::clearLinks,
        maxClients = entitlementManager::maxClients
    )
    private val cloudHostEngine = CloudHostEngine(
        context = context,
        currentClientId = { activeClientManager.currentClientId.value },
        getHostedBinId = { clientId -> cloudSyncProfileStore.links(clientId).hostedBinId },
        setHostedBinId = { clientId, binId -> setHostedBinId(clientId, binId) },
        buildStackBackupJson = { clientId -> cloudBackupEngine.buildStackBackupJson(clientId) },
        buildHistoryBackupJson = { clientId -> cloudBackupEngine.buildHistoryBackupJson(clientId) },
        buildFullBackupJson = { clientId -> cloudBackupEngine.buildFullBackupJson(clientId) },
        updateUi = { updateCloudSyncUiStatus(it) },
        setLoading = { _cloudSyncLoading.value = it },
        appendLog = CloudSyncLogStore::append,
        setMessage = { _dataTransferMessage.value = it }
    )
    private var realtimeListener: com.example.supplementtracker.service.FirebaseRealtimeSyncListener? = null

    fun startAutoSync() {
        if (!entitlementManager.canUse(CommercialFeature.ENCRYPTED_CLOUD_SYNC)) {
            OakPrefs.get(context).edit().putBoolean("isAutoSyncEnabled", false).apply()
            stopAutoSync()
            return
        }
        CloudAutoSyncWork.setEnabled(context, true)
        startRealtimeListener()
        activeAutoSyncBinId()?.let { requestAutoSyncDebounced(it, delayMillis = 0L) }
    }

    fun stopAutoSync() {
        CloudAutoSyncWork.setEnabled(context, false)
        pendingAutoSyncJob?.cancel()
        pendingAutoSyncJob = null
        pauseAutoSync()
    }

    fun pauseAutoSync() {
        stopRealtimeListener()
    }

    private fun startRealtimeListener() {
        stopRealtimeListener()
        val binId = activeAutoSyncBinId() ?: return
        val listener = com.example.supplementtracker.service.FirebaseRealtimeSyncListener(context) {
            syncTwoWay(binId)
        }
        realtimeListener = listener
        listener.start(binId)
    }

    private fun stopRealtimeListener() {
        realtimeListener?.close()
        realtimeListener = null
    }

    private val adviceByName: Map<String, String?> =
        SupplementDictionary.localizedReferences(context).associate { it.name to it.advice }
    private val expiredCleanupIds = ConcurrentHashMap.newKeySet<String>()

    init {
        observeDayChanges()
        observeActiveClientChanges()
    }

    private fun observeActiveClientChanges() {
        viewModelScope.launch {
            activeClientManager.currentClientId
                .drop(1)
                .collectLatest { handleActiveClientChanged(it) }
        }
    }

    private suspend fun handleActiveClientChanged(clientId: java.util.UUID?) {
        pendingAutoSyncJob?.cancel()
        pendingAutoSyncJob = null
        stopRealtimeListener()
        val links = cloudSyncProfileStore.links(clientId)
        _hostedBinId.value = links.hostedBinId
        _linkedBinId.value = links.linkedBinId
        _cloudSyncUiStatus.value = null
        rescheduleNotificationsNow()
        if (!OakPrefs.get(context).getBoolean("isAutoSyncEnabled", false)) return
        val manifestId = links.activeManifestId ?: return
        startRealtimeListener()
        requestAutoSyncDebounced(manifestId, delayMillis = 0L)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        activeClientManager.currentClientId,
        _refreshTrigger,
        _today
    ) { clientId, _, today -> clientId to today }
        .flatMapLatest { (clientId, today) ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(HomeUiState.NoClient)
            combine(
                repository.getAllSupplements(id),
                repository.getRecordsByDateRange(id, getStartOfDay(daysAgo = 119), getEndOfTomorrow())
            ) { supplements, records -> supplements to records }
                .mapLatest { (supplements, records) ->
                    cleanupExpiredSupplements(supplements, today)
                    processSupplements(supplements, records, today)
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    val allClientSupplements: StateFlow<List<UserSupplement>> = combine(
        activeClientManager.currentClientId,
        _today
    ) { clientId, today -> clientId to today }
        .flatMapLatest { (clientId, today) ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(emptyList())
            repository.getAllSupplements(id)
                .map { supplements ->
                    cleanupExpiredSupplements(supplements, today)
                    supplements.filter { it.deletedAtEpochMs == null && !isExpired(it, today) }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refresh() {
        _today.value = LocalDate.now()
        _refreshTrigger.value += 1
    }
    
    fun clearDataTransferMessage() {
        _dataTransferMessage.value = null
    }
    
    private suspend fun syncTwoWay(binId: String): Boolean = cloudSyncEngine.syncTwoWay(binId)

    fun syncNow(binId: String) {
        viewModelScope.launch { syncTwoWay(binId) }
    }
    
    fun refreshCloudSyncUi(binId: String) {
        viewModelScope.launch { updateCloudSyncUiStatus(binId) }
    }
    
    private suspend fun updateCloudSyncUiStatus(binId: String) {
        val clientId = activeClientManager.currentClientId.value ?: return
        val id = binId.trim()
        if (id.isEmpty()) {
            _cloudSyncUiStatus.value = null
            return
        }
        val prefs = OakPrefs.get(context)
        val lastSync = prefs.getLong("cloudSyncLastSyncEpochMs_$id", 0L)
        val pending = cloudSyncEngine.hasLocalChangesSince(clientId, lastSync)
        _cloudSyncUiStatus.value = buildCloudSyncUiStatus(prefs, id, lastSync, pending)
    }

    private fun buildCloudSyncUiStatus(
        prefs: android.content.SharedPreferences,
        id: String,
        lastSync: Long,
        pending: Boolean
    ): CloudSyncUiStatus {
        val phase = runCatching {
            CloudSyncPhase.valueOf(prefs.getString("cloudSyncPhase_$id", "").orEmpty())
        }.getOrDefault(CloudSyncPhase.IDLE)
        return CloudSyncUiStatus(
            binId = id,
            lastSyncEpochMs = lastSync,
            lastAttemptEpochMs = prefs.getLong("cloudSyncLastAttemptEpochMs_$id", 0L),
            hasPendingChanges = pending,
            lastError = prefs.getString("cloudSyncLastError_$id", null)?.trim()?.takeIf(String::isNotEmpty),
            phase = phase,
            conflictRetryCount = prefs.getInt("cloudSyncConflictRetryCount_$id", 0),
            bytesDownloaded = prefs.getLong("cloudSyncBytesDownloaded_$id", 0L),
            bytesUploaded = prefs.getLong("cloudSyncBytesUploaded_$id", 0L),
            pullMs = prefs.getLong("cloudSyncPullMs_$id", 0L),
            mergeMs = prefs.getLong("cloudSyncMergeMs_$id", 0L),
            pushMs = prefs.getLong("cloudSyncPushMs_$id", 0L),
            totalMs = prefs.getLong("cloudSyncTotalMs_$id", 0L)
        )
    }
    
    private fun getStartOfDay(daysAgo: Long): Long {
        return LocalDate.now().minusDays(daysAgo).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    
    private fun getEndOfTomorrow(): Long {
        return LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun processSupplements(
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>,
        today: LocalDate
    ): HomeUiState {
        val result = calculateHomeDashboardUseCase(
            supplements = supplements,
            records = records,
            today = today,
            nowEpochMs = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault()
        )
        val activeItems = result.activeDoses.mapValues { (_, doses) ->
            doses.map { dose ->
                SupplementUiItem(
                    supplement = dose.supplement,
                    timeString = dose.timeString,
                    scheduledAtEpochMs = dose.scheduledAtEpochMs,
                    doseStatus = DoseStatus.valueOf(dose.doseStatus.name),
                    advice = adviceByName[dose.supplement.name],
                    isDueSoon = dose.isDueSoon,
                    isMissedSoon = dose.isMissedSoon
                )
            }
        }
        val restingList = result.restingSupplements.map {
            RestingSupplementInfo(it.supplement, it.daysRemaining)
        }
        return HomeUiState.Success(activeItems, restingList, result.streakDays)
    }

    private fun isExpired(supplement: UserSupplement, today: LocalDate): Boolean =
        calculateHomeDashboardUseCase.isExpired(supplement, today)

    private fun cleanupExpiredSupplements(supplements: List<UserSupplement>, today: LocalDate) {
        val expired = supplements.filter { it.deletedAtEpochMs == null && isExpired(it, today) }
        if (expired.isEmpty()) return
        viewModelScope.launch {
            val removed = ArrayList<UserSupplement>(expired.size)
            for (supplement in expired) {
                val id = supplement.id.toString()
                if (!expiredCleanupIds.add(id)) continue
                try {
                    repository.deleteSupplement(supplement)
                    removed += supplement
                } finally {
                    expiredCleanupIds.remove(id)
                }
            }
            if (removed.isEmpty()) return@launch
            rescheduleNotificationsNow()
            activeAutoSyncBinId()?.let { requestAutoSyncDebounced(it) }
        }
    }

    fun toggleIntake(supplementId: String, timeString: String, action: DoseAction) {
        viewModelScope.launch {
            val scheduledAt = scheduledAtEpochMs(timeString) ?: return@launch
            recordDoseInternal(supplementId = supplementId, scheduledAtEpochMs = scheduledAt, action = action)
        }
    }

    private fun scheduledAtEpochMs(timeString: String): Long? {
        val parsed = TimeStrings.parseLenient(timeString) ?: return null
        return LocalDate.now().atTime(parsed).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun recordDoseFromNotification(
        supplementId: String,
        scheduledAtEpochMs: Long,
        action: DoseAction
    ) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
            val activeClientId = activeClientManager.currentClientId.value
            if (!ActiveProfileNotificationPolicy.allows(activeClientId, supplement.clientId)) return@launch
            val normalizedSupplementId = supplementId.lowercase(Locale.ROOT)
            val recordId = DoseEventKey.make(normalizedSupplementId, scheduledAtEpochMs)
            if (repository.getIntakeRecordById(recordId) != null) return@launch
            recordDoseInternal(supplementId = supplementId, scheduledAtEpochMs = scheduledAtEpochMs, action = action)
        }
    }

    private suspend fun recordDoseInternal(supplementId: String, scheduledAtEpochMs: Long, action: DoseAction) {
        recordDoseUseCase(
            supplementId = supplementId,
            scheduledAtEpochMs = scheduledAtEpochMs,
            action = when (action) {
                DoseAction.TAKEN -> RecordDoseUseCase.Action.TAKEN
                DoseAction.SKIPPED -> RecordDoseUseCase.Action.SKIPPED
            }
        )
        rescheduleNotificationsNow()
        activeAutoSyncBinId()?.let { requestAutoSyncDebounced(it) }
    }

    fun deleteItem(supplement: UserSupplement) {
        viewModelScope.launch {
            repository.deleteSupplement(supplement)
            rescheduleNotificationsNow()
            val binId = activeAutoSyncBinId()
            if (binId != null) {
                requestAutoSyncDebounced(binId)
            }
        }
    }

    fun deleteDoseTime(supplement: UserSupplement, timeString: String) {
        viewModelScope.launch {
            val remainingTimes = TimeStrings.removingTime(timeString, from = supplement.intakeTime)
            repository.updateSupplement(
                supplement.copy(
                    intakeTime = remainingTimes.joinToString(", "),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            rescheduleNotificationsNow()
            activeAutoSyncBinId()?.let { requestAutoSyncDebounced(it) }
        }
    }

    fun deleteItem(supplementId: String) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
            repository.deleteSupplement(supplement)
            rescheduleNotificationsNow()
            val binId = activeAutoSyncBinId()
            if (binId != null) {
                requestAutoSyncDebounced(binId)
            }
        }
    }
    
    fun importBackupFromJson(json: String) {
        viewModelScope.launch {
            val clientId = activeClientManager.currentClientId.value
            if (clientId == null) {
                _dataTransferMessage.value = context.getString(R.string.missing_active_client)
                return@launch
            }
            val prepared = runCatching { CloudSyncPayloadCodec.decompressIfNeeded(json) }
                .getOrElse {
                    _dataTransferMessage.value = context.getString(R.string.invalid_json)
                    return@launch
                }
            importBackupUseCase(prepared, clientId)
                .onSuccess {
                    refresh()
                    rescheduleNotificationsNow()
                    OakPrefs.get(context)
                        .edit()
                        .putLong("oakLastBackupImportEpochMs", System.currentTimeMillis())
                        .apply()
                    _dataTransferMessage.value = context.getString(R.string.import_success)
                }
                .onFailure {
                    _dataTransferMessage.value = context.getString(R.string.invalid_json)
                }
        }
    }

    fun exportFullBackupJson(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(cloudBackupEngine.buildFullBackupJson())
        }
    }

    fun hostData() {
        viewModelScope.launch {
            cloudHostEngine.hostData()
        }
    }
    fun enableCloudEncryption(enabled: Boolean) {
        CloudSyncCrypto.setEnabled(context, enabled)
            .onSuccess {
                _dataTransferMessage.value = if (enabled) {
                    context.getString(R.string.cloud_encryption_enabled)
                } else {
                    context.getString(R.string.cloud_encryption_disabled)
                }
            }
            .onFailure { _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_encryption_failed) }
    }
    
    fun exportCloudEncryptionKey(): String? {
        return CloudSyncCrypto.exportCurrentKey(context)
    }
    
    fun importCloudEncryptionKey(exported: String) {
        runCatching {
            val keyId = CloudSyncCrypto.importKey(context, exported)
            CloudSyncCrypto.setEnabled(context, true).getOrThrow()
            keyId
        }
            .onSuccess { _dataTransferMessage.value = context.getString(R.string.cloud_import_key_success_format, it) }
            .onFailure { _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_import_key_failed) }
    }
    
    fun revokeHostedBin() {
        viewModelScope.launch {
            _cloudSyncLoading.value = true
            val result = cloudHostEngine.revokeHostedBin()
            _cloudSyncLoading.value = false
            result
                .onSuccess { _dataTransferMessage.value = context.getString(R.string.cloud_revoke_success) }
                .onFailure { error ->
                    _dataTransferMessage.value = if (error.message == context.getString(R.string.cloud_revoke_missing_code)) {
                        context.getString(R.string.cloud_revoke_missing_code)
                    } else {
                        context.getString(
                            R.string.cloud_revoke_failed_format,
                            error.message ?: context.getString(R.string.error_unknown)
                        )
                    }
                }
        }
    }
    
    private fun activeAutoSyncBinId(): String? {
        if (!OakPrefs.get(context).getBoolean("isAutoSyncEnabled", false)) return null
        return cloudSyncProfileStore.activeManifestId(activeClientManager.currentClientId.value)
    }

    private fun setHostedBinId(clientId: java.util.UUID, binId: String?) {
        cloudSyncProfileStore.setHostedBinId(clientId, binId)
        if (activeClientManager.currentClientId.value != clientId) return
        _hostedBinId.value = binId?.trim()?.takeIf { it.isNotEmpty() }
    }
    
    private fun requestAutoSyncDebounced(binId: String, delayMillis: Long = 350L) {
        val id = binId.trim()
        if (id.isEmpty()) return
        pendingAutoSyncJob?.cancel()
        pendingAutoSyncJob = viewModelScope.launch {
            delay(delayMillis)
            if (activeAutoSyncBinId() != id) return@launch
            syncTwoWay(id)
        }
    }
    
    fun refreshNotificationSchedules() {
        viewModelScope.launch {
            rescheduleNotificationsNow()
        }
    }

    suspend fun rebuildNotificationSchedules(): Long {
        rescheduleNotificationsNow()
        return _lastNotificationRebuildEpochMs.value
    }
    
    fun clearPendingNotifications() {
        viewModelScope.launch {
            notificationScheduleEngine.clearAll()
        }
    }

    fun factoryReset(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = factoryResetEngine.reset()
            if (result.isSuccess) {
                _hostedBinId.value = null
                _linkedBinId.value = null
                _cloudSyncUiStatus.value = null
                _cloudSyncLoading.value = false
                refresh()
            }
            onResult(result)
        }
    }

    private suspend fun rescheduleNotificationsNow() {
        notificationScheduleEngine.rescheduleAll()
        val rebuiltAt = System.currentTimeMillis()
        OakPrefs.get(context).edit().putLong("oakLastNotificationRebuildEpochMs", rebuiltAt).apply()
        _lastNotificationRebuildEpochMs.value = rebuiltAt
    }

    fun linkData(binId: String) {
        viewModelScope.launch {
            val clientId = activeClientManager.currentClientId.value ?: return@launch
            val id = binId.trim()
            if (id.isEmpty() || !syncTwoWay(id)) return@launch
            if (activeClientManager.currentClientId.value != clientId) return@launch
            cloudSyncProfileStore.setLinkedBinId(clientId, id)
            _linkedBinId.value = id
            if (OakPrefs.get(context).getBoolean("isAutoSyncEnabled", false)) startRealtimeListener()
        }
    }

    fun unlinkData() {
        val clientId = activeClientManager.currentClientId.value ?: return
        cloudSyncProfileStore.setLinkedBinId(clientId, null)
        _linkedBinId.value = null
        if (_hostedBinId.value.isNullOrBlank()) stopRealtimeListener()
    }

    fun createClient(
        profile: ClientProfile,
        onResult: (ClientProfileMutationResult) -> Unit = {}
    ) {
        viewModelScope.launch { onResult(clientProfileMutationEngine.create(profile)) }
    }

    fun deleteClient(
        profile: ClientProfile,
        onResult: (ClientProfileMutationResult) -> Unit = {}
    ) {
        viewModelScope.launch { onResult(clientProfileMutationEngine.delete(profile)) }
    }

    fun updateClient(
        profile: ClientProfile,
        onResult: (ClientProfileMutationResult) -> Unit = {}
    ) {
        viewModelScope.launch { onResult(clientProfileMutationEngine.update(profile)) }
    }

    private fun observeDayChanges() {
        viewModelScope.launch {
            while (isActive) {
                val now = java.time.ZonedDateTime.now()
                val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(java.time.Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000L))
                _today.value = LocalDate.now()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeListener()
    }
}
