package com.example.supplementtracker.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.IntakeTime
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.UserSupplementTakenToday
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import com.example.supplementtracker.domain.model.WeeklyRecurrenceConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import com.example.supplementtracker.data.mock.SupplementDictionary
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.export.SupplementExportJson
import com.example.supplementtracker.domain.export.SupplementExportSchema
import com.example.supplementtracker.domain.export.OAKBackupDataDTO
import com.example.supplementtracker.domain.export.OAKBackupHistoryDTO
import com.example.supplementtracker.domain.export.OAKBackupJson
import com.example.supplementtracker.domain.export.OAKBackupSchema
import com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.R
import java.util.Locale
import com.example.supplementtracker.service.CloudSyncManager
import com.example.supplementtracker.service.NotificationSchedulerImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log

/**
 * ViewModel xử lý logic cho màn hình chính Dashboard.
 */
class HomeViewModel(
    private val context: Context,
    private val repository: com.example.supplementtracker.domain.repository.SupplementRepository,
    private val activeClientManager: ActiveClientManager,
    private val calculateCycleUseCase: CalculateCycleUseCase = CalculateCycleUseCase()
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)
    private val _dataTransferMessage = MutableStateFlow<String?>(null)
    val dataTransferMessage: StateFlow<String?> = _dataTransferMessage
    private val _cloudSyncLoading = MutableStateFlow(false)
    val cloudSyncLoading: StateFlow<Boolean> = _cloudSyncLoading
    private val _hostedBinId = MutableStateFlow<String?>(null)
    val hostedBinId: StateFlow<String?> = _hostedBinId
    private var autoSyncJob: Job? = null
    private val adviceByName: Map<String, String?> =
        SupplementDictionary.localizedReferences(context).associate { it.name to it.advice }

    val uiState: StateFlow<HomeUiState> = combine(
        activeClientManager.currentClientId,
        _refreshTrigger
    ) { clientId, _ -> clientId }
        .flatMapLatest { clientId ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(HomeUiState.NoClient)
            repository.getSupplementsWithTakenToday(id, getStartOfDay(), getEndOfDay())
                .map { supplements -> processSupplements(supplements) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun refresh() {
        _refreshTrigger.value += 1
    }
    
    fun clearDataTransferMessage() {
        _dataTransferMessage.value = null
    }
    
    suspend fun buildBackupJson(): Result<String> {
        return runCatching {
            val clientId = activeClientManager.currentClientId.value
                ?: error(context.getString(R.string.missing_active_client))
            val clientIdString = clientId.toString()

            val supplements = repository.getAllSupplements(clientIdString).first()
            val history = repository.getAllRecordsByClient(clientIdString)

            val stack = supplements.map { supplement ->
                OAKBackupSupplementDTO(
                    id = supplement.id.toString(),
                    name = supplement.name,
                    dailyDose = supplement.dailyDose,
                    intakeTime = supplement.intakeTime,
                    startDate = supplement.startDate.toString(),
                    cycle = com.example.supplementtracker.domain.export.SupplementExportCycleDTO(
                        isContinuous = supplement.cycleConfig.isContinuous,
                        daysOn = supplement.cycleConfig.daysOn,
                        daysOff = supplement.cycleConfig.daysOff,
                        durationMonths = supplement.cycleConfig.durationMonths,
                        weeklyWeekdaysMask = supplement.cycleConfig.weeklyRecurrence?.weekdaysMask,
                        weeklyIntervalWeeks = supplement.cycleConfig.weeklyRecurrence?.intervalWeeks,
                        weeklyAnchorDate = supplement.cycleConfig.weeklyRecurrence?.anchorDate?.toString()
                    )
                )
            }

            val records = history.map { record ->
                OAKBackupHistoryDTO(
                    id = record.id,
                    supplementId = record.supplementId,
                    dateEpochMs = record.date,
                    status = record.status
                )
            }

            OAKBackupJson.encode(
                OAKBackupDataDTO(
                    version = OAKBackupSchema.VERSION,
                    stack = stack,
                    history = records
                )
            )
        }
    }

    private fun getStartOfDay() = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun getEndOfDay() = LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun processSupplements(
        supplements: List<UserSupplementTakenToday>
    ): HomeUiState {
        val today = LocalDate.now()
        val activeItems = supplements
            .filter {
                calculateCycleUseCase(it.supplement.startDate, it.supplement.cycleConfig, today) == CycleStatus.ON &&
                    matchesWeeklyRecurrenceIfNeeded(it.supplement, today)
            }
            .map { taken ->
                val advice = adviceByName[taken.supplement.name]
                SupplementUiItem(taken.supplement, taken.isTakenToday, advice)
            }
            .groupBy { it.supplement.intakeTime }
            .toSortedMap()

        val restingList = supplements
            .filter { calculateCycleUseCase(it.supplement.startDate, it.supplement.cycleConfig, today) == CycleStatus.OFF }
            .map { RestingSupplementInfo(it.supplement, calculateDaysRemaining(it.supplement, today)) }

        return HomeUiState.Success(activeItems, restingList)
    }
    
    private fun matchesWeeklyRecurrenceIfNeeded(supplement: UserSupplement, date: LocalDate): Boolean {
        val weekly = supplement.cycleConfig.weeklyRecurrence ?: return true
        val bitIndex = when (date.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> 0
            java.time.DayOfWeek.TUESDAY -> 1
            java.time.DayOfWeek.WEDNESDAY -> 2
            java.time.DayOfWeek.THURSDAY -> 3
            java.time.DayOfWeek.FRIDAY -> 4
            java.time.DayOfWeek.SATURDAY -> 5
            java.time.DayOfWeek.SUNDAY -> 6
        }
        if ((weekly.weekdaysMask and (1 shl bitIndex)) == 0) return false
        val interval = weekly.intervalWeeks.coerceAtLeast(1)
        val fields = WeekFields.ISO
        val anchorStart = weekly.anchorDate.with(fields.dayOfWeek(), 1)
        val dateStart = date.with(fields.dayOfWeek(), 1)
        val weeks = ChronoUnit.WEEKS.between(anchorStart, dateStart).toInt()
        val mod = ((weeks % interval) + interval) % interval
        return mod == 0
    }

    fun toggleIntake(supplementId: String, isChecked: Boolean) {
        viewModelScope.launch {
            if (isChecked) {
                repository.logIntake(supplementId, System.currentTimeMillis())
                val binId = activeAutoSyncBinId()
                if (binId != null) {
                    Log.d("AutoSync", "☁️ Auto-Sync: Starting upload...")
                    uploadToBin(binId)
                }
                return@launch
            }
        }
    }

    fun deleteItem(supplement: UserSupplement) {
        viewModelScope.launch {
            repository.deleteSupplement(supplement)
        }
    }

    fun deleteItem(supplementId: String) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
            repository.deleteSupplement(supplement)
        }
    }
    
    fun importBackupFromJson(json: String) {
        viewModelScope.launch {
            val clientId = activeClientManager.currentClientId.value
            if (clientId == null) {
                _dataTransferMessage.value = context.getString(R.string.missing_active_client)
                return@launch
            }
            
            val decoded = OAKBackupJson.decodeCompat(json).getOrElse {
                _dataTransferMessage.value = context.getString(R.string.invalid_json)
                return@launch
            }

            val clientIdString = clientId.toString()
            repository.deleteAllIntakeRecordsByClient(clientIdString)
            repository.deleteAllSupplementsByClient(clientIdString)

            val importedSupplementIds = HashSet<String>(decoded.stack.size)
            decoded.stack.forEach { dto ->
                val weekly = run {
                    val mask = dto.cycle.weeklyWeekdaysMask ?: return@run null
                    val interval = dto.cycle.weeklyIntervalWeeks ?: return@run null
                    val anchor = dto.cycle.weeklyAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@run null
                    WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
                }
                val cycle = CycleConfig(
                    daysOn = dto.cycle.daysOn,
                    daysOff = dto.cycle.daysOff,
                    isContinuous = dto.cycle.isContinuous,
                    durationMonths = dto.cycle.durationMonths,
                    weeklyRecurrence = weekly
                )
                val startDate = runCatching { LocalDate.parse(dto.startDate) }.getOrElse { LocalDate.now() }
                
                val imported = UserSupplement(
                    id = runCatching { java.util.UUID.fromString(dto.id) }.getOrElse { java.util.UUID.randomUUID() },
                    clientId = clientId,
                    name = dto.name,
                    startDate = startDate,
                    cycleConfig = cycle,
                    dailyDose = dto.dailyDose,
                    intakeTime = dto.intakeTime
                )
                
                repository.saveSupplement(imported)
                importedSupplementIds.add(imported.id.toString().lowercase(Locale.ROOT))
            }

            decoded.history.forEach { record ->
                val normalizedSupplementId = record.supplementId.lowercase(Locale.ROOT)
                if (!importedSupplementIds.contains(normalizedSupplementId)) return@forEach
                repository.insertIntakeRecord(
                    com.example.supplementtracker.domain.repository.IntakeRecord(
                        id = record.id,
                        supplementId = normalizedSupplementId,
                        date = record.dateEpochMs,
                        status = record.status
                    )
                )
            }
            
            refresh()
            _dataTransferMessage.value = context.getString(R.string.import_success)
        }
    }

    fun hostData() {
        viewModelScope.launch {
            _cloudSyncLoading.value = true
            val oldBinId = _hostedBinId.value?.trim().orEmpty()
            val json = buildBackupJson().getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = error.message ?: "Export failed"
                return@launch
            }

            if (oldBinId.isNotEmpty()) {
                val upsert = CloudSyncManager().upsertBackup(oldBinId, json)
                _cloudSyncLoading.value = false
                upsert.onSuccess {
                    _hostedBinId.value = oldBinId
                    _dataTransferMessage.value = "Đã cập nhật dữ liệu lên mã hiện tại!"
                }.onFailure {
                    _dataTransferMessage.value = "Phát dữ liệu thất bại: ${it.message ?: "Unknown"}"
                }
                return@launch
            }

            val created = CloudSyncManager().uploadBackup(json)
            _cloudSyncLoading.value = false
            created.onSuccess {
                _hostedBinId.value = it
                _dataTransferMessage.value = "Phát dữ liệu thành công!"
            }.onFailure {
                _dataTransferMessage.value = "Phát dữ liệu thất bại: ${it.message ?: "Unknown"}"
            }
        }
    }
    
    fun revokeHostedBin() {
        viewModelScope.launch {
            val binId = _hostedBinId.value ?: run {
                _dataTransferMessage.value = "Không có mã để thu hồi."
                return@launch
            }
            _cloudSyncLoading.value = true
            val deleteResult = CloudSyncManager().deleteBackup(binId)
            _cloudSyncLoading.value = false
            deleteResult.onSuccess {
                _hostedBinId.value = null
                context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
                    .edit().remove("cloudSyncHostedBinId").apply()
                _dataTransferMessage.value = "Đã vô hiệu hóa mã."
            }.onFailure {
                _dataTransferMessage.value = "Thu hồi mã thất bại: ${it.message ?: "Unknown"}"
            }
        }
    }
    
    fun startAutoSync() {
        if (autoSyncJob != null) return
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
                val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
                val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
                val delayMs = autoSyncDelayMs(prefs)

                if (hosted.isNotEmpty()) {
                    if (!_cloudSyncLoading.value) uploadToBin(hosted)
                    delay(delayMs)
                    continue
                }

                if (linked.isNotEmpty()) {
                    silentDownloadAndMerge(linked)
                    delay(delayMs)
                    continue
                }

                delay(delayMs)
            }
        }
    }
    
    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }
    
    private fun activeAutoSyncBinId(): String? {
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("isAutoSyncEnabled", false)
        if (!enabled) return null
        val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
        val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
        val id = if (hosted.isNotEmpty()) hosted else linked
        return id.takeIf { it.isNotEmpty() }
    }
    
    private suspend fun uploadToBin(binId: String) {
        val json = buildBackupJson().getOrElse { return }
        CloudSyncManager().upsertBackup(binId, json).onSuccess {
            markSyncActivity()
        }.onFailure { error ->
            Log.d("AutoSync", "☁️ Auto-Sync: Upload failed: ${error.message ?: "Unknown"}")
        }
    }
    
    fun refreshNotificationSchedules() {
        viewModelScope.launch {
            val clients = repository.observeClients().first()
            val supplements = clients.flatMap { client ->
                repository.getAllSupplements(client.id.toString()).first()
            }
            val scheduler = NotificationSchedulerImpl(context)
            supplements.forEach { scheduler.schedule(it) }
        }
    }

    fun receiveData(binId: String) {
        viewModelScope.launch {
            _cloudSyncLoading.value = true
            val json = CloudSyncManager().downloadBackup(binId).getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = "Mã không hợp lệ / lỗi tải: ${error.message ?: "Unknown"}"
                return@launch
            }
            _cloudSyncLoading.value = false
            importBackupFromJson(json)
        }
    }

    fun silentDownloadAndMerge(binId: String) {
        viewModelScope.launch {
            Log.d("AutoSync", "☁️ Auto-Sync: Silent download from $binId...")
            val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
            val etagKey = "cloudSyncEtag_${binId.trim()}"
            val etag = prefs.getString(etagKey, "").orEmpty().trim()
            val result = CloudSyncManager().downloadBackupIfChanged(binId, etag).getOrElse { error ->
                val msg = error.message.orEmpty()
                Log.d("AutoSync", "☁️ Auto-Sync: Silent download failed: $msg")
                if (msg.contains("404") || msg.contains("not found", ignoreCase = true)) {
                    clearStaleBinId(binId)
                }
                return@launch
            }
            val newEtag = result.etag.orEmpty().trim()
            if (newEtag.isNotEmpty()) prefs.edit().putString(etagKey, newEtag).apply()
            val json = result.json ?: return@launch
            importBackupFromJson(json)
            markSyncActivity()
            Log.d("AutoSync", "☁️ Auto-Sync: Silent download & merge completed")
        }
    }

    private fun markSyncActivity() {
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
        prefs.edit().putLong("cloudSyncLastActivityEpochMs", System.currentTimeMillis()).apply()
    }

    private fun autoSyncDelayMs(prefs: android.content.SharedPreferences): Long {
        val last = prefs.getLong("cloudSyncLastActivityEpochMs", 0L)
        if (last <= 0L) return 60_000L
        val elapsed = System.currentTimeMillis() - last
        return if (elapsed < 60_000L) 15_000L else 60_000L
    }

    private fun clearStaleBinId(binId: String) {
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
        val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
        val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
        val editor = prefs.edit()
        if (hosted == binId) editor.putString("cloudSyncHostedBinId", "")
        if (linked == binId) editor.putString("cloudSyncLinkedBinId", "")
        editor.apply()
        Log.d("AutoSync", "☁️ Auto-Sync: Cleared stale binId: $binId")
    }

    fun createClient(profile: ClientProfile) {
        viewModelScope.launch {
            val newName = profile.name.trim()
            val alreadyExists = activeClientManager.clients.value.any { existing ->
                existing.name.trim().equals(newName, ignoreCase = true)
            }
            if (alreadyExists) return@launch
            repository.saveClient(profile)
        }
    }

    fun deleteClient(profile: ClientProfile) {
        viewModelScope.launch {
            repository.deleteClient(profile)
        }
    }

    fun updateClient(profile: ClientProfile) {
        viewModelScope.launch {
            val newName = profile.name.trim()
            val alreadyExists = activeClientManager.clients.value.any { existing ->
                existing.id != profile.id && existing.name.trim().equals(newName, ignoreCase = true)
            }
            if (alreadyExists) return@launch
            repository.updateClient(profile)
        }
    }

    private fun calculateDaysRemaining(supplement: UserSupplement, today: LocalDate): Int {
        val config = supplement.cycleConfig
        val totalCycleDays = config.daysOn + config.daysOff
        val daysElapsed = ChronoUnit.DAYS.between(supplement.startDate, today).toInt()
        val dayInCycle = daysElapsed % totalCycleDays
        
        return totalCycleDays - dayInCycle
    }
}
