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
import com.example.supplementtracker.service.NotificationScheduleEngine
import com.example.supplementtracker.worker.CloudAutoSyncWork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val context: Context,
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val activeClientManager: ActiveClientManager,
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
    private val _dataTransferMessage = MutableStateFlow<String?>(null)
    val dataTransferMessage: StateFlow<String?> = _dataTransferMessage
    private val _cloudSyncLoading = MutableStateFlow(false)
    val cloudSyncLoading: StateFlow<Boolean> = _cloudSyncLoading
    private val _hostedBinId = MutableStateFlow<String?>(null)
    val hostedBinId: StateFlow<String?> = _hostedBinId
    private val _cloudSyncUiStatus = MutableStateFlow<CloudSyncUiStatus?>(null)
    val cloudSyncUiStatus: StateFlow<CloudSyncUiStatus?> = _cloudSyncUiStatus
    private var pendingAutoSyncJob: Job? = null
    private val cloudSyncEngine = CloudSyncEngine(
        context = context,
        repository = repository,
        currentClientId = { activeClientManager.currentClientId.value },
        buildFullBackupJson = { buildFullBackupJson() },
        buildStackBackupJson = { buildStackBackupJson() },
        buildHistoryBackupJson = { buildHistoryBackupJson() },
        updateUi = { updateCloudSyncUiStatus(it) },
        setLoading = { _cloudSyncLoading.value = it },
        rescheduleNotifications = { rescheduleNotificationsNow() },
        disableAutoSync = { stopAutoSync() },
        appendLog = { prefs, binId, phase, message -> appendCloudSyncLog(prefs, binId, phase, message) }
    )
    private val cloudBackupEngine = CloudBackupEngine(
        context = context,
        repository = repository,
        currentClientId = { activeClientManager.currentClientId.value }
    )
    private val calculateHomeDashboardUseCase = CalculateHomeDashboardUseCase(calculateCycleUseCase)
    private val importBackupUseCase = ImportBackupUseCase(repository)
    private val recordDoseUseCase = RecordDoseUseCase(repository)
    private val notificationScheduleEngine = NotificationScheduleEngine(context, repository)
    private val clientProfileUseCase = ClientProfileUseCase(repository)
    private val cloudHostEngine = CloudHostEngine(
        context = context,
        getHostedBinId = { _hostedBinId.value },
        setHostedBinId = { _hostedBinId.value = it },
        buildStackBackupJson = { cloudBackupEngine.buildStackBackupJson() },
        buildHistoryBackupJson = { cloudBackupEngine.buildHistoryBackupJson() },
        buildFullBackupJson = { cloudBackupEngine.buildFullBackupJson() },
        updateUi = { updateCloudSyncUiStatus(it) },
        setLoading = { _cloudSyncLoading.value = it },
        appendLog = { prefs, binId, phase, message -> appendCloudSyncLog(prefs, binId, phase, message) },
        setMessage = { _dataTransferMessage.value = it }
    )
    private var realtimeListener: com.example.supplementtracker.service.FirebaseRealtimeSyncListener? = null

    fun startAutoSync() {
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
        val prefs = OakPrefs.get(context)
        val id = binId.trim()
        if (id.isEmpty()) {
            _cloudSyncUiStatus.value = null
            return
        }
        val lastSyncKey = "cloudSyncLastSyncEpochMs_$id"
        val lastAttemptKey = "cloudSyncLastAttemptEpochMs_$id"
        val lastErrorKey = "cloudSyncLastError_$id"
        val phaseKey = "cloudSyncPhase_$id"
        val retryKey = "cloudSyncConflictRetryCount_$id"
        val bytesDownloadedKey = "cloudSyncBytesDownloaded_$id"
        val bytesUploadedKey = "cloudSyncBytesUploaded_$id"
        val pullMsKey = "cloudSyncPullMs_$id"
        val mergeMsKey = "cloudSyncMergeMs_$id"
        val pushMsKey = "cloudSyncPushMs_$id"
        val totalMsKey = "cloudSyncTotalMs_$id"
        val lastSyncEpochMs = prefs.getLong(lastSyncKey, 0L)
        val lastAttemptEpochMs = prefs.getLong(lastAttemptKey, 0L)
        val lastError = prefs.getString(lastErrorKey, null)?.trim()?.takeIf { it.isNotEmpty() }
        val phase = runCatching { CloudSyncPhase.valueOf(prefs.getString(phaseKey, "") ?: "") }.getOrNull()
            ?: CloudSyncPhase.IDLE
        val retryCount = prefs.getInt(retryKey, 0)
        val bytesDownloaded = prefs.getLong(bytesDownloadedKey, 0L)
        val bytesUploaded = prefs.getLong(bytesUploadedKey, 0L)
        val pullMs = prefs.getLong(pullMsKey, 0L)
        val mergeMs = prefs.getLong(mergeMsKey, 0L)
        val pushMs = prefs.getLong(pushMsKey, 0L)
        val totalMs = prefs.getLong(totalMsKey, 0L)
        val pending = cloudSyncEngine.hasLocalChangesSince(clientId, lastSyncEpochMs)
        _cloudSyncUiStatus.value = CloudSyncUiStatus(
            binId = id,
            lastSyncEpochMs = lastSyncEpochMs,
            lastAttemptEpochMs = lastAttemptEpochMs,
            hasPendingChanges = pending,
            lastError = lastError
            ,
            phase = phase,
            conflictRetryCount = retryCount,
            bytesDownloaded = bytesDownloaded,
            bytesUploaded = bytesUploaded,
            pullMs = pullMs,
            mergeMs = mergeMs,
            pushMs = pushMs,
            totalMs = totalMs
        )
    }
    
    private suspend fun buildStackBackupJson(): Result<String> = cloudBackupEngine.buildStackBackupJson()

    private suspend fun buildHistoryBackupJson(): Result<String> = cloudBackupEngine.buildHistoryBackupJson()

    private suspend fun buildFullBackupJson(): Result<String> = cloudBackupEngine.buildFullBackupJson()

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
        val prefs = OakPrefs.get(context)
        val enabled = prefs.getBoolean("isAutoSyncEnabled", false)
        if (!enabled) return null
        val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
        val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
        val id = if (hosted.isNotEmpty()) hosted else linked
        return id.takeIf { it.isNotEmpty() }
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
    
    private fun appendCloudSyncLog(prefs: android.content.SharedPreferences, binId: String, phase: String, message: String) {
        val id = binId.trim()
        if (id.isEmpty()) return
        val key = "cloudSyncLog_$id"
        val existing = prefs.getString(key, null)
        val array = runCatching { if (existing.isNullOrBlank()) JSONArray() else JSONArray(existing) }.getOrElse { JSONArray() }
        val now = System.currentTimeMillis()
        if (array.length() > 0) {
            val last = runCatching { array.getJSONObject(array.length() - 1) }.getOrNull()
            val lastPhase = last?.optString("phase").orEmpty()
            val lastMsg = last?.optString("msg").orEmpty()
            val lastTs = last?.optLong("ts") ?: 0L
            if (lastPhase == phase && lastMsg == message && (now - lastTs) < 15_000L) return
        }
        val entry = JSONObject()
            .put("ts", now)
            .put("phase", phase)
            .put("msg", message)
        array.put(entry)
        val keep = 30
        val trimmed = JSONArray()
        val start = (array.length() - keep).coerceAtLeast(0)
        for (i in start until array.length()) trimmed.put(array.getJSONObject(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }










    



    
    fun refreshNotificationSchedules() {
        viewModelScope.launch {
            rescheduleNotificationsNow()
        }
    }
    
    fun clearPendingNotifications() {
        viewModelScope.launch {
            notificationScheduleEngine.clearAll()
        }
    }

    private suspend fun rescheduleNotificationsNow() {
        notificationScheduleEngine.rescheduleAll()
    }

    fun receiveData(binId: String) {
        viewModelScope.launch {
            syncTwoWay(binId)
        }
    }

    fun silentDownloadAndMerge(binId: String) {
        viewModelScope.launch { syncTwoWay(binId) }
    }

    suspend fun runSyncTwoWayNow(binId: String) {
        syncTwoWay(binId)
    }
    

    

    








    fun createClient(profile: ClientProfile) {
        viewModelScope.launch {
            clientProfileUseCase.create(profile)
        }
    }

    fun deleteClient(profile: ClientProfile) {
        viewModelScope.launch {
            clientProfileUseCase.delete(profile)
        }
    }

    fun updateClient(profile: ClientProfile) {
        viewModelScope.launch {
            clientProfileUseCase.update(profile)
        }
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
