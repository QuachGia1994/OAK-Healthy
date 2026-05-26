package com.example.supplementtracker.presentation.home

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.util.DoseEventKey
import com.example.supplementtracker.domain.util.TimeStrings
import com.example.supplementtracker.data.mock.SupplementDictionary
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.export.SupplementExportJson
import com.example.supplementtracker.domain.export.SupplementExportSchema
import com.example.supplementtracker.domain.export.OAKBackupDataDTO
import com.example.supplementtracker.domain.export.OAKBackupHistoryDTO
import com.example.supplementtracker.domain.export.OAKBackupJson
import com.example.supplementtracker.domain.export.OAKBackupMetaDTO
import com.example.supplementtracker.domain.export.OAKBackupSchema
import com.example.supplementtracker.domain.export.OAKBackupSupplementDTO
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.R
import java.util.Locale
import com.example.supplementtracker.service.CloudSyncManager
import com.example.supplementtracker.service.CloudSyncCrypto
import com.example.supplementtracker.service.CloudSyncCryptoError
import com.example.supplementtracker.service.CloudSyncPayloadCodec
import com.example.supplementtracker.service.CloudSyncManifestCodec
import com.example.supplementtracker.service.NotificationSchedulerImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log
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

    private enum class RecordStatus(val raw: String) {
        TAKEN("Taken"),
        SKIPPED("Skipped")
    }

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
    private var autoSyncJob: Job? = null
    private val adviceByName: Map<String, String?> =
        SupplementDictionary.localizedReferences(context).associate { it.name to it.advice }
    private val intakeTimesCache = ConcurrentHashMap<String, List<String>>()

    val uiState: StateFlow<HomeUiState> = combine(
        activeClientManager.currentClientId,
        _refreshTrigger
    ) { clientId, _ -> clientId }
        .flatMapLatest { clientId ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(HomeUiState.NoClient)
            combine(
                repository.getAllSupplements(id),
                repository.getRecordsByDateRange(id, getStartOfDay(daysAgo = 119), getEndOfTomorrow())
            ) { supplements, records -> supplements to records }
                .mapLatest { (supplements, records) ->
                    withContext(Dispatchers.Default) { processSupplements(supplements, records) }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    val allClientSupplements: StateFlow<List<UserSupplement>> = activeClientManager.currentClientId
        .flatMapLatest { clientId ->
            val id = clientId?.toString() ?: return@flatMapLatest flowOf(emptyList())
            repository.getAllSupplements(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refresh() {
        _refreshTrigger.value += 1
    }
    
    fun clearDataTransferMessage() {
        _dataTransferMessage.value = null
    }
    
    fun syncNow(binId: String) {
        viewModelScope.launch { syncTwoWay(binId) }
    }
    
    fun refreshCloudSyncUi(binId: String) {
        viewModelScope.launch { updateCloudSyncUiStatus(binId) }
    }
    
    private suspend fun updateCloudSyncUiStatus(binId: String) {
        val clientId = activeClientManager.currentClientId.value ?: return
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
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
        val pending = hasLocalChangesSince(clientId, lastSyncEpochMs)
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
    
    private suspend fun buildBackupJson(includeStack: Boolean, includeHistory: Boolean): Result<String> {
        return runCatching {
            val clientId = activeClientManager.currentClientId.value
                ?: error(context.getString(R.string.missing_active_client))
            val clientIdString = clientId.toString()

            val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
            val deviceId = prefs.getString("cloudSyncDeviceId", null) ?: run {
                val created = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("cloudSyncDeviceId", created).apply()
                created
            }
            val now = System.currentTimeMillis()

            val stack = if (includeStack) {
                repository.getAllSupplementsForSync(clientIdString).map { supplement ->
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
                            weeklyAnchorDate = supplement.cycleConfig.weeklyRecurrence?.anchorDate?.toString(),
                            intervalDays = supplement.cycleConfig.intervalDays
                        ),
                        lastTakenLocalDate = supplement.lastTakenLocalDate?.toString(),
                        updatedAtEpochMs = supplement.updatedAtEpochMs,
                        deletedAtEpochMs = supplement.deletedAtEpochMs
                    )
                }
            } else {
                emptyList()
            }

            val history = if (includeHistory) {
                val cutoffEpochMs = getStartOfDay(90)
                repository.getAllRecordsForSync(clientIdString)
                    .filter { it.date >= cutoffEpochMs }
                    .groupBy { DoseEventKey.make(it.supplementId, it.date) }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAtEpochMs } }
                    .map { record ->
                        val key = DoseEventKey.make(record.supplementId, record.date)
                        OAKBackupHistoryDTO(
                            id = key,
                            supplementId = record.supplementId.lowercase(Locale.ROOT),
                            dateEpochMs = record.date,
                            status = record.status,
                            updatedAtEpochMs = record.updatedAtEpochMs
                        )
                    }
            } else emptyList()

            OAKBackupJson.encode(
                OAKBackupDataDTO(
                    version = OAKBackupSchema.VERSION,
                    meta = OAKBackupMetaDTO(schemaVersion = 2, updatedAtEpochMs = now, deviceId = deviceId),
                    stack = stack,
                    history = history,
                    historyZlibBase64 = null
                )
            )
        }
    }

    private suspend fun buildStackBackupJson(): Result<String> = buildBackupJson(includeStack = true, includeHistory = false)

    private suspend fun buildHistoryBackupJson(): Result<String> = buildBackupJson(includeStack = false, includeHistory = true)

    private suspend fun buildFullBackupJson(): Result<String> = buildBackupJson(includeStack = true, includeHistory = true)

    private fun getStartOfDay(daysAgo: Long): Long {
        return LocalDate.now().minusDays(daysAgo).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    
    private fun getEndOfTomorrow(): Long {
        return LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun processSupplements(
        supplements: List<UserSupplement>,
        records: List<IntakeRecord>
    ): HomeUiState {
        val today = LocalDate.now()
        val nowEpochMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val recordIndex = buildRecordIndex(records)
        val liveSupplements = supplements.filter { it.deletedAtEpochMs == null }
        if (liveSupplements.isEmpty()) return HomeUiState.Success(emptyMap(), emptyList(), 0)
        val streakDays = computeStreakDays(today, liveSupplements, recordIndex.hasRecordByDose, zoneId)
        val activeItems = buildActiveItems(liveSupplements, today, nowEpochMs, recordIndex.statusByDose, zoneId)
        val restingList = buildRestingList(liveSupplements, today)
        return HomeUiState.Success(activeItems, restingList, streakDays)
    }

    private data class RecordIndex(
        val statusByDose: Map<String, String>,
        val hasRecordByDose: Set<String>
    )

    private fun buildRecordIndex(records: List<IntakeRecord>): RecordIndex {
        val statusByDose = HashMap<String, String>(records.size)
        val hasRecordByDose = HashSet<String>(records.size)
        records.forEach { record ->
            val key = DoseEventKey.make(record.supplementId, record.date)
            statusByDose[key] = record.status
            hasRecordByDose.add(key)
        }
        return RecordIndex(statusByDose = statusByDose, hasRecordByDose = hasRecordByDose)
    }

    private fun computeStreakDays(
        today: LocalDate,
        supplements: List<UserSupplement>,
        hasRecordByDose: Set<String>,
        zoneId: ZoneId
    ): Int {
        val seedDay = if (isDayComplete(today, supplements, hasRecordByDose, zoneId)) today else today.minusDays(1)
        var streakDays = 0
        var cursor = seedDay
        var remaining = 120
        while (remaining > 0 && isDayComplete(cursor, supplements, hasRecordByDose, zoneId)) {
            streakDays += 1
            cursor = cursor.minusDays(1)
            remaining -= 1
        }
        return streakDays
    }

    private fun buildActiveItems(
        supplements: List<UserSupplement>,
        today: LocalDate,
        nowEpochMs: Long,
        statusByDose: Map<String, String>,
        zoneId: ZoneId
    ): Map<String, List<SupplementUiItem>> {
        val items = supplements
            .filter {
                calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.ON &&
                    matchesWeeklyRecurrenceIfNeeded(it, today) &&
                    matchesIntervalRecurrenceIfNeeded(it, today)
            }
            .flatMap { supplement ->
                buildUiItemsForSupplement(supplement, today, nowEpochMs, statusByDose, zoneId)
            }
        return items.groupBy { it.timeString }.toSortedMap()
    }

    private fun buildUiItemsForSupplement(
        supplement: UserSupplement,
        today: LocalDate,
        nowEpochMs: Long,
        statusByDose: Map<String, String>,
        zoneId: ZoneId
    ): List<SupplementUiItem> {
        val advice = adviceByName[supplement.name]
        return effectiveTimes(supplement).map { time ->
            val scheduledAt = scheduledAtEpochMs(today, time, zoneId) ?: 0L
            val status = statusByDose[DoseEventKey.make(supplement.id.toString(), scheduledAt)]
            val doseStatus = doseStatus(scheduledAtEpochMs = scheduledAt, recordedStatus = status, nowEpochMs = nowEpochMs)
            val dueSoonMs = 20 * 60 * 1000L
            val missedAfter = scheduledAt + (2 * 60 * 60 * 1000L)
            val isDueSoon = doseStatus == DoseStatus.PLANNED &&
                scheduledAt > nowEpochMs &&
                (scheduledAt - nowEpochMs) <= dueSoonMs
            val isMissedSoon = doseStatus == DoseStatus.PLANNED &&
                scheduledAt > 0L &&
                nowEpochMs in (missedAfter - dueSoonMs) until missedAfter
            SupplementUiItem(
                supplement = supplement,
                timeString = time,
                scheduledAtEpochMs = scheduledAt,
                doseStatus = doseStatus,
                advice = advice,
                isDueSoon = isDueSoon,
                isMissedSoon = isMissedSoon
            )
        }
    }

    private fun buildRestingList(
        supplements: List<UserSupplement>,
        today: LocalDate
    ): List<RestingSupplementInfo> {
        return supplements
            .filter { calculateCycleUseCase(it.startDate, it.cycleConfig, today) == CycleStatus.OFF }
            .map { RestingSupplementInfo(it, calculateDaysRemaining(it, today)) }
    }

    private fun isDayComplete(
        day: LocalDate,
        supplements: List<UserSupplement>,
        hasRecordByDose: Set<String>,
        zoneId: ZoneId
    ): Boolean {
        for (supplement in supplements) {
            if (supplement.deletedAtEpochMs != null) continue
            if (calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, day) != CycleStatus.ON) continue
            if (!matchesWeeklyRecurrenceIfNeeded(supplement, day)) continue
            if (!matchesIntervalRecurrenceIfNeeded(supplement, day)) continue
            for (time in effectiveTimes(supplement)) {
                val scheduledAt = scheduledAtEpochMs(day, time, zoneId) ?: continue
                if (!hasRecordByDose.contains(DoseEventKey.make(supplement.id.toString(), scheduledAt))) return false
            }
        }
        return true
    }

    private fun doseStatus(scheduledAtEpochMs: Long, recordedStatus: String?, nowEpochMs: Long): DoseStatus {
        if (recordedStatus == RecordStatus.SKIPPED.raw) return DoseStatus.SKIPPED
        if (recordedStatus == RecordStatus.TAKEN.raw) return DoseStatus.TAKEN
        if (scheduledAtEpochMs <= 0L) return DoseStatus.PLANNED

        val missedAfter = scheduledAtEpochMs + (2 * 60 * 60 * 1000L)
        if (nowEpochMs > missedAfter) return DoseStatus.MISSED
        return DoseStatus.PLANNED
    }

    private fun parseTimes(raw: String): List<String> {
        val key = raw.trim()
        if (key.isEmpty()) return emptyList()
        return intakeTimesCache.computeIfAbsent(key) { TimeStrings.normalizeList(it) }
    }

    private fun effectiveTimes(supplement: UserSupplement): List<String> {
        val times = parseTimes(supplement.intakeTime)
        val interval = supplement.cycleConfig.intervalDays ?: return times
        if (interval <= 1) return times
        return times.take(1)
    }

    private fun scheduledAtEpochMs(date: LocalDate, timeString: String, zoneId: ZoneId): Long? {
        val parsed = TimeStrings.parseLenient(timeString) ?: return null
        return date.atTime(parsed).atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun scheduledAtEpochMs(timeString: String): Long? {
        return scheduledAtEpochMs(LocalDate.now(), timeString, ZoneId.systemDefault())
    }
    
    private fun matchesWeeklyRecurrenceIfNeeded(supplement: UserSupplement, date: LocalDate): Boolean {
        val weekly = supplement.cycleConfig.weeklyRecurrence ?: return true
        val bitIndex = when (date.dayOfWeek) {
            null -> return false
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

    private fun matchesIntervalRecurrenceIfNeeded(supplement: UserSupplement, date: LocalDate): Boolean {
        val interval = supplement.cycleConfig.intervalDays ?: return true
        if (interval <= 1) return true
        val lastTaken = supplement.lastTakenLocalDate
        if (lastTaken != null) {
            val days = ChronoUnit.DAYS.between(lastTaken, date).toInt()
            return days >= 0 && days % interval == 0
        }
        if (date.isBefore(supplement.startDate)) return false
        val days = ChronoUnit.DAYS.between(supplement.startDate, date).toInt()
        return days % interval == 0
    }

    fun toggleIntake(supplementId: String, timeString: String, action: DoseAction) {
        viewModelScope.launch {
            val scheduledAt = scheduledAtEpochMs(timeString) ?: return@launch
            recordDoseInternal(supplementId = supplementId, scheduledAtEpochMs = scheduledAt, action = action)
        }
    }

    fun recordDoseFromNotification(
        supplementId: String,
        scheduledAtEpochMs: Long,
        action: DoseAction
    ) {
        viewModelScope.launch {
            recordDoseInternal(supplementId = supplementId, scheduledAtEpochMs = scheduledAtEpochMs, action = action)
        }
    }

    private suspend fun recordDoseInternal(supplementId: String, scheduledAtEpochMs: Long, action: DoseAction) {
        if (scheduledAtEpochMs <= 0L) return
        val now = System.currentTimeMillis()
        val normalizedSupplementId = supplementId.lowercase(Locale.ROOT)
        val status = when (action) {
            DoseAction.TAKEN -> RecordStatus.TAKEN.raw
            DoseAction.SKIPPED -> RecordStatus.SKIPPED.raw
        }
        repository.insertIntakeRecord(
            IntakeRecord(
                id = DoseEventKey.make(normalizedSupplementId, scheduledAtEpochMs),
                supplementId = normalizedSupplementId,
                date = scheduledAtEpochMs,
                status = status,
                updatedAtEpochMs = now
            )
        )
        if (action == DoseAction.TAKEN) {
            val supplement = repository.getSupplementById(normalizedSupplementId)
            if (supplement != null) {
                val day = java.time.Instant.ofEpochMilli(scheduledAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
                repository.updateSupplement(
                    supplement.copy(lastTakenLocalDate = day, updatedAtEpochMs = now)
                )
            }
        }
        rescheduleNotificationsNow()

        val binId = activeAutoSyncBinId()
        if (binId != null) {
            Log.d("AutoSync", "☁️ Auto-Sync: Starting upload...")
            syncTwoWay(binId)
        }
    }

    fun deleteItem(supplement: UserSupplement) {
        viewModelScope.launch {
            repository.deleteSupplement(supplement)
            rescheduleNotificationsNow()
        }
    }

    fun deleteItem(supplementId: String) {
        viewModelScope.launch {
            val supplement = repository.getSupplementById(supplementId) ?: return@launch
            repository.deleteSupplement(supplement)
            rescheduleNotificationsNow()
        }
    }
    
    fun importBackupFromJson(json: String) {
        viewModelScope.launch {
            val clientId = activeClientManager.currentClientId.value
            if (clientId == null) {
                _dataTransferMessage.value = context.getString(R.string.missing_active_client)
                return@launch
            }
            
            val prepared = runCatching { CloudSyncPayloadCodec.decompressIfNeeded(json) }.getOrElse {
                _dataTransferMessage.value = context.getString(R.string.invalid_json)
                return@launch
            }
            val decoded = OAKBackupJson.decodeCompat(prepared).getOrElse {
                _dataTransferMessage.value = context.getString(R.string.invalid_json)
                return@launch
            }

            val clientIdString = clientId.toString()
            val importedSupplementIds = HashSet<String>(decoded.stack.size)
            val supplementsToImport = decoded.stack.mapNotNull { dto ->
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
                    id = runCatching { java.util.UUID.fromString(dto.id) }.getOrElse {
                        com.example.supplementtracker.domain.util.StableId.uuidFromString(
                            dto.id.trim().lowercase(Locale.ROOT)
                        )
                    },
                    clientId = clientId,
                    name = dto.name,
                    startDate = startDate,
                    cycleConfig = cycle,
                    dailyDose = dto.dailyDose,
                    intakeTime = dto.intakeTime,
                    updatedAtEpochMs = dto.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    deletedAtEpochMs = dto.deletedAtEpochMs
                )
                
                importedSupplementIds.add(imported.id.toString().lowercase(Locale.ROOT))
                imported
            }

            val recordsToImport = decoded.history.mapNotNull { record ->
                val normalizedSupplementId = record.supplementId.lowercase(Locale.ROOT)
                if (!importedSupplementIds.contains(normalizedSupplementId)) return@mapNotNull null
                    com.example.supplementtracker.domain.repository.IntakeRecord(
                        id = record.id,
                        supplementId = normalizedSupplementId,
                        date = record.dateEpochMs,
                        status = record.status,
                        updatedAtEpochMs = record.updatedAtEpochMs.takeIf { it > 0L } ?: record.dateEpochMs
                    )
            }

            repository.importBackupAtomic(clientIdString, supplementsToImport, recordsToImport)
            
            refresh()
            rescheduleNotificationsNow()
            context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
                .edit()
                .putLong("oakLastBackupImportEpochMs", System.currentTimeMillis())
                .apply()
            _dataTransferMessage.value = context.getString(R.string.import_success)
        }
    }

    fun exportFullBackupJson(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(buildFullBackupJson())
        }
    }

    fun hostData() {
        viewModelScope.launch {
            _cloudSyncLoading.value = true
            val manifestId = _hostedBinId.value?.trim().orEmpty()
            val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
            val stackPlain = buildStackBackupJson().getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_export_stack_failed)
                return@launch
            }
            val historyPlain = buildHistoryBackupJson().getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_export_history_failed)
                return@launch
            }

            fun encryptPrepared(plaintext: String): String {
                val prepared = CloudSyncPayloadCodec.compressIfUseful(plaintext)
                return CloudSyncCrypto.wrapForUploadIfEnabled(context, prepared)
            }

            val stackEncrypted = runCatching { encryptPrepared(stackPlain) }.getOrElse {
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_encrypt_stack_failed)
                return@launch
            }
            val historyEncrypted = runCatching { encryptPrepared(historyPlain) }.getOrElse {
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_encrypt_history_failed)
                return@launch
            }

            if (manifestId.isNotEmpty()) {
                val stackKey = "cloudSyncStackBinId_$manifestId"
                val historyKey = "cloudSyncHistoryBinId_$manifestId"
                var stackId = prefs.getString(stackKey, "").orEmpty().trim()
                var historyId = prefs.getString(historyKey, "").orEmpty().trim()
                if (stackId.isEmpty() || historyId.isEmpty()) {
                    val manifestDownload = CloudSyncManager().downloadBackupAlways(manifestId).getOrElse { error ->
                        _cloudSyncLoading.value = false
                        _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_load_manifest_failed)
                        return@launch
                    }
                    val decrypted = runCatching { CloudSyncCrypto.unwrapDownloadedIfNeeded(context, manifestDownload.json ?: "") }.getOrElse {
                        _cloudSyncLoading.value = false
                        _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_decrypt_manifest_failed)
                        return@launch
                    }
                    val prepared = runCatching { CloudSyncPayloadCodec.decompressIfNeeded(decrypted) }.getOrElse {
                        _cloudSyncLoading.value = false
                        _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_decode_manifest_failed)
                        return@launch
                    }
                    val decoded = runCatching { CloudSyncManifestCodec.decode(prepared) }.getOrNull()
                    if (decoded == null) {
                        val legacyPlain = buildFullBackupJson().getOrElse { error ->
                            _cloudSyncLoading.value = false
                            _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_export_failed)
                            return@launch
                        }
                        val legacyEncrypted = runCatching { encryptPrepared(legacyPlain) }.getOrElse {
                            _cloudSyncLoading.value = false
                            _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_encrypt_failed)
                            return@launch
                        }
                        val upsertLegacy = CloudSyncManager().upsertBackup(manifestId, legacyEncrypted)
                        _cloudSyncLoading.value = false
                        upsertLegacy.onSuccess {
                            val lastSyncKey = "cloudSyncLastSyncEpochMs_$manifestId"
                            val lastErrorKey = "cloudSyncLastError_$manifestId"
                            prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).remove(lastErrorKey).apply()
                            updateCloudSyncUiStatus(manifestId)
                            appendCloudSyncLog(prefs, manifestId, "HOST", "Host legacy update OK")
                            _dataTransferMessage.value = context.getString(R.string.cloud_host_update_existing_success)
                        }.onFailure {
                            val lastErrorKey = "cloudSyncLastError_$manifestId"
                            prefs.edit().putString(lastErrorKey, it.message ?: context.getString(R.string.error_unknown)).apply()
                            updateCloudSyncUiStatus(manifestId)
                            appendCloudSyncLog(prefs, manifestId, "ERROR", "Host legacy update failed")
                            _dataTransferMessage.value = context.getString(
                                R.string.cloud_host_failed_format,
                                it.message ?: context.getString(R.string.error_unknown)
                            )
                        }
                        return@launch
                    }
                    stackId = decoded.stackBinId
                    historyId = decoded.historyBinId
                    prefs.edit().putString(stackKey, stackId).putString(historyKey, historyId).apply()
                }

                appendCloudSyncLog(prefs, manifestId, "HOST", "Host update start")
                val upsertStack = CloudSyncManager().upsertBackup(stackId, stackEncrypted)
                val upsertHistory = CloudSyncManager().upsertBackup(historyId, historyEncrypted)
                val manifestPlain = CloudSyncManifestCodec.encode(stackId, historyId)
                val manifestEncrypted = runCatching { encryptPrepared(manifestPlain) }.getOrElse {
                    _cloudSyncLoading.value = false
                    _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_encrypt_manifest_failed)
                    return@launch
                }
                val upsertManifest = CloudSyncManager().upsertBackup(manifestId, manifestEncrypted)
                _cloudSyncLoading.value = false

                val error = upsertStack.exceptionOrNull()
                    ?: upsertHistory.exceptionOrNull()
                    ?: upsertManifest.exceptionOrNull()
                if (error != null) {
                    val lastErrorKey = "cloudSyncLastError_$manifestId"
                    prefs.edit().putString(lastErrorKey, error.message ?: context.getString(R.string.error_unknown)).apply()
                    updateCloudSyncUiStatus(manifestId)
                    appendCloudSyncLog(prefs, manifestId, "ERROR", "Host update failed")
                    _dataTransferMessage.value = context.getString(
                        R.string.cloud_host_failed_format,
                        error.message ?: context.getString(R.string.error_unknown)
                    )
                    return@launch
                }

                val lastSyncKey = "cloudSyncLastSyncEpochMs_$manifestId"
                val lastErrorKey = "cloudSyncLastError_$manifestId"
                prefs.edit()
                    .putLong(lastSyncKey, System.currentTimeMillis())
                    .remove(lastErrorKey)
                    .apply()
                updateCloudSyncUiStatus(manifestId)
                appendCloudSyncLog(prefs, manifestId, "HOST", "Host update OK")
                _dataTransferMessage.value = context.getString(R.string.cloud_host_update_existing_success)
                return@launch
            }

            val stackId = CloudSyncManager().uploadBackup(stackEncrypted).getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_upload_stack_failed)
                return@launch
            }
            val historyId = CloudSyncManager().uploadBackup(historyEncrypted).getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_upload_history_failed)
                return@launch
            }
            val manifestPlain = CloudSyncManifestCodec.encode(stackId, historyId)
            val manifestEncrypted = runCatching { encryptPrepared(manifestPlain) }.getOrElse {
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_host_encrypt_manifest_failed)
                return@launch
            }
            val newManifestId = CloudSyncManager().uploadBackup(manifestEncrypted).getOrElse { error ->
                _cloudSyncLoading.value = false
                _dataTransferMessage.value = error.message ?: context.getString(R.string.cloud_host_upload_manifest_failed)
                return@launch
            }
            _cloudSyncLoading.value = false
            _hostedBinId.value = newManifestId
            prefs.edit()
                .putString("cloudSyncHostedBinId", newManifestId)
                .putString("cloudSyncStackBinId_$newManifestId", stackId)
                .putString("cloudSyncHistoryBinId_$newManifestId", historyId)
                .putLong("cloudSyncLastSyncEpochMs_$newManifestId", System.currentTimeMillis())
                .remove("cloudSyncLastError_$newManifestId")
                .apply()
            updateCloudSyncUiStatus(newManifestId)
            appendCloudSyncLog(prefs, newManifestId, "HOST", "Host created OK")
            _dataTransferMessage.value = context.getString(R.string.cloud_host_success)
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
    
    fun rotateCloudEncryptionKey() {
        runCatching { CloudSyncCrypto.rotateKey(context) }
            .onSuccess { _dataTransferMessage.value = context.getString(R.string.cloud_rotate_success) }
            .onFailure { _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_rotate_failed) }
    }
    
    fun importCloudEncryptionKey(exported: String) {
        runCatching { CloudSyncCrypto.importKey(context, exported) }
            .onSuccess { _dataTransferMessage.value = context.getString(R.string.cloud_import_key_success_format, it) }
            .onFailure { _dataTransferMessage.value = it.message ?: context.getString(R.string.cloud_import_key_failed) }
    }
    
    fun revokeHostedBin() {
        viewModelScope.launch {
            val manifestId = _hostedBinId.value ?: run {
                _dataTransferMessage.value = context.getString(R.string.cloud_revoke_missing_code)
                return@launch
            }
            val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
            val stackId = prefs.getString("cloudSyncStackBinId_$manifestId", "").orEmpty().trim()
            val historyId = prefs.getString("cloudSyncHistoryBinId_$manifestId", "").orEmpty().trim()
            _cloudSyncLoading.value = true
            val manager = CloudSyncManager()
            val deleteManifest = manager.deleteBackup(manifestId)
            val deleteStack = if (stackId.isNotEmpty()) manager.deleteBackup(stackId) else Result.success(Unit)
            val deleteHistory = if (historyId.isNotEmpty()) manager.deleteBackup(historyId) else Result.success(Unit)
            _cloudSyncLoading.value = false
            val error = deleteManifest.exceptionOrNull()
                ?: deleteStack.exceptionOrNull()
                ?: deleteHistory.exceptionOrNull()
            if (error == null) {
                _hostedBinId.value = null
                prefs.edit()
                    .remove("cloudSyncHostedBinId")
                    .remove("cloudSyncStackBinId_$manifestId")
                    .remove("cloudSyncHistoryBinId_$manifestId")
                    .remove("cloudSyncEtag_$manifestId")
                    .remove("cloudSyncEtagStack_$manifestId")
                    .remove("cloudSyncEtagHistory_$manifestId")
                    .apply()
                _dataTransferMessage.value = context.getString(R.string.cloud_revoke_success)
                return@launch
            }
            _dataTransferMessage.value = context.getString(
                R.string.cloud_revoke_failed_format,
                error.message ?: context.getString(R.string.error_unknown)
            )
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
                    if (!_cloudSyncLoading.value) syncTwoWay(hosted)
                    delay(delayMs)
                    continue
                }

                if (linked.isNotEmpty()) {
                    if (!_cloudSyncLoading.value) syncTwoWay(linked)
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
    
    private fun appendCloudSyncLog(prefs: android.content.SharedPreferences, binId: String, phase: String, message: String) {
        val id = binId.trim()
        if (id.isEmpty()) return
        val key = "cloudSyncLog_$id"
        val existing = prefs.getString(key, null)
        val array = runCatching { if (existing.isNullOrBlank()) JSONArray() else JSONArray(existing) }.getOrElse { JSONArray() }
        val entry = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("phase", phase)
            .put("msg", message)
        array.put(entry)
        val keep = 30
        val trimmed = JSONArray()
        val start = (array.length() - keep).coerceAtLeast(0)
        for (i in start until array.length()) trimmed.put(array.getJSONObject(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    private fun decryptAndPrepare(recordJson: String): String {
        val decrypted = CloudSyncCrypto.unwrapDownloadedIfNeeded(context, recordJson)
        return CloudSyncPayloadCodec.decompressIfNeeded(decrypted)
    }

    private fun encryptAndPrepare(plaintextJson: String): String {
        val prepared = CloudSyncPayloadCodec.compressIfUseful(plaintextJson)
        return CloudSyncCrypto.wrapForUploadIfEnabled(context, prepared)
    }

    private fun abortCloudSync(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        lastErrorKey: String,
        phaseKey: String,
        stageMsKey: String,
        stageStartedAt: Long,
        startedAt: Long,
        errorMessage: String,
        logMessage: String
    ) {
        prefs.edit().putString(lastErrorKey, errorMessage).apply()
        prefs.edit().putString(phaseKey, CloudSyncPhase.ERROR.name).apply()
        prefs.edit().putLong(stageMsKey, SystemClock.elapsedRealtime() - stageStartedAt).apply()
        prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        appendCloudSyncLog(prefs, manifestId, "ERROR", logMessage)
        _cloudSyncLoading.value = false
        viewModelScope.launch { updateCloudSyncUiStatus(manifestId) }
    }

    private fun totalMsKeyFor(manifestId: String): String {
        val id = manifestId.trim()
        return "cloudSyncTotalMs_$id"
    }

    private suspend fun finishCloudSyncNoChanges(
        prefs: android.content.SharedPreferences,
        manifestId: String,
        lastSyncKey: String,
        lastErrorKey: String,
        phaseKey: String,
        startedAt: Long
    ) {
        prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
        prefs.edit().remove(lastErrorKey).apply()
        prefs.edit().putString(phaseKey, CloudSyncPhase.DONE.name).apply()
        prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        markSyncActivity()
        _cloudSyncLoading.value = false
        appendCloudSyncLog(prefs, manifestId, "DONE", "No changes")
        updateCloudSyncUiStatus(manifestId)
        rescheduleNotificationsNow()
    }
    
    private suspend fun syncTwoWay(binId: String) {
        val clientId = activeClientManager.currentClientId.value ?: return
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
        val manifestId = binId.trim()
        if (manifestId.isEmpty()) return

        _cloudSyncLoading.value = true
        val etagManifestKey = "cloudSyncEtag_$manifestId"
        val etagStackKey = "cloudSyncEtagStack_$manifestId"
        val etagHistoryKey = "cloudSyncEtagHistory_$manifestId"
        val stackIdKey = "cloudSyncStackBinId_$manifestId"
        val historyIdKey = "cloudSyncHistoryBinId_$manifestId"
        val lastSyncKey = "cloudSyncLastSyncEpochMs_$manifestId"
        val lastAttemptKey = "cloudSyncLastAttemptEpochMs_$manifestId"
        val lastErrorKey = "cloudSyncLastError_$manifestId"
        val phaseKey = "cloudSyncPhase_$manifestId"
        val retryKey = "cloudSyncConflictRetryCount_$manifestId"
        val bytesDownloadedKey = "cloudSyncBytesDownloaded_$manifestId"
        val bytesUploadedKey = "cloudSyncBytesUploaded_$manifestId"
        val pullMsKey = "cloudSyncPullMs_$manifestId"
        val mergeMsKey = "cloudSyncMergeMs_$manifestId"
        val pushMsKey = "cloudSyncPushMs_$manifestId"
        val totalMsKey = "cloudSyncTotalMs_$manifestId"

        val lastSyncEpochMs = prefs.getLong(lastSyncKey, 0L)
        val localStackChanged = hasLocalStackChangesSince(clientId, lastSyncEpochMs)
        val localHistoryChanged = hasLocalHistoryChangesSince(clientId, lastSyncEpochMs)
        val startedAt = SystemClock.elapsedRealtime()

        prefs.edit()
            .putLong(lastAttemptKey, System.currentTimeMillis())
            .putString(phaseKey, CloudSyncPhase.PULLING.name)
            .putInt(retryKey, 0)
            .putLong(bytesDownloadedKey, 0L)
            .putLong(bytesUploadedKey, 0L)
            .putLong(pullMsKey, 0L)
            .putLong(mergeMsKey, 0L)
            .putLong(pushMsKey, 0L)
            .putLong(totalMsKey, 0L)
            .apply()
        appendCloudSyncLog(prefs, manifestId, "START", "Sync start")
        updateCloudSyncUiStatus(manifestId)

        var stackId = prefs.getString(stackIdKey, "").orEmpty().trim()
        var historyId = prefs.getString(historyIdKey, "").orEmpty().trim()
        val pullStartedAt = SystemClock.elapsedRealtime()
        val previousManifestEtag = prefs.getString(etagManifestKey, "").orEmpty().trim()
        val manifestDownload = if (stackId.isEmpty() || historyId.isEmpty()) {
            CloudSyncManager().downloadBackupAlways(manifestId).getOrElse { error ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = error.message ?: "Manifest load failed",
                    logMessage = "Manifest load failed"
                )
                return
            }
        } else {
            CloudSyncManager().downloadBackupIfChanged(manifestId, previousManifestEtag).getOrElse { error ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = error.message ?: "Manifest pull failed",
                    logMessage = "Manifest pull failed"
                )
                return
            }
        }
        val manifestEtag = manifestDownload.etag.orEmpty().trim().ifEmpty { previousManifestEtag }
        if (manifestEtag.isNotEmpty()) prefs.edit().putString(etagManifestKey, manifestEtag).apply()
        if (!manifestDownload.json.isNullOrBlank()) {
            val manifestJson = manifestDownload.json.orEmpty()
            val prepared = runCatching { decryptAndPrepare(manifestJson) }.getOrElse { t ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = pullMsKey,
                    stageStartedAt = pullStartedAt,
                    startedAt = startedAt,
                    errorMessage = t.message ?: "Manifest decrypt failed",
                    logMessage = "Manifest decrypt failed"
                )
                return
            }
            val decoded = runCatching { CloudSyncManifestCodec.decode(prepared) }.getOrNull()
            if (decoded == null) {
                val bytesDown = manifestJson.toByteArray(Charsets.UTF_8).size
                prefs.edit().putLong(bytesDownloadedKey, bytesDown.toLong()).apply()
                prefs.edit().putString(phaseKey, CloudSyncPhase.MERGING.name).apply()
                updateCloudSyncUiStatus(manifestId)
                val mergeStartedAt = SystemClock.elapsedRealtime()
                val legacyPlain = runCatching { decryptAndPrepare(manifestJson) }.getOrElse { t ->
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = mergeMsKey,
                        stageStartedAt = mergeStartedAt,
                        startedAt = startedAt,
                        errorMessage = t.message ?: "Decrypt failed",
                        logMessage = "Decrypt failed"
                    )
                    return
                }
                mergeRemoteIntoLocal(legacyPlain, clientId)
                prefs.edit().putLong(mergeMsKey, SystemClock.elapsedRealtime() - mergeStartedAt).apply()
                val remoteChanged = manifestJson.isNotBlank()
                if (!remoteChanged && !localStackChanged && !localHistoryChanged) {
                    finishCloudSyncNoChanges(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastSyncKey = lastSyncKey,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        startedAt = startedAt
                    )
                    return
                }
                prefs.edit().putString(phaseKey, CloudSyncPhase.PUSHING.name).apply()
                updateCloudSyncUiStatus(manifestId)
                val pushStartedAt = SystemClock.elapsedRealtime()
                val fullPlain = buildFullBackupJson().getOrElse {
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = pushMsKey,
                        stageStartedAt = pushStartedAt,
                        startedAt = startedAt,
                        errorMessage = it.message ?: "Export failed",
                        logMessage = "Export failed"
                    )
                    return
                }
                val fullEnc = runCatching { encryptAndPrepare(fullPlain) }.getOrElse { t ->
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = pushMsKey,
                        stageStartedAt = pushStartedAt,
                        startedAt = startedAt,
                        errorMessage = t.message ?: "Encrypt failed",
                        logMessage = "Encrypt failed"
                    )
                    return
                }
                val bytesUp = fullEnc.toByteArray(Charsets.UTF_8).size.toLong()
                prefs.edit().putLong(bytesUploadedKey, bytesUp).apply()
                val upsert = CloudSyncManager().upsertBackup(manifestId, fullEnc, manifestEtag.takeIf { it.isNotEmpty() })
                upsert.getOrElse { error ->
                    abortCloudSync(
                        prefs = prefs,
                        manifestId = manifestId,
                        lastErrorKey = lastErrorKey,
                        phaseKey = phaseKey,
                        stageMsKey = pushMsKey,
                        stageStartedAt = pushStartedAt,
                        startedAt = startedAt,
                        errorMessage = error.message ?: "Upload failed",
                        logMessage = "Upload failed"
                    )
                    return
                }?.orEmpty()?.trim()?.let { if (it.isNotEmpty()) prefs.edit().putString(etagManifestKey, it).apply() }
                prefs.edit().putLong(pushMsKey, SystemClock.elapsedRealtime() - pushStartedAt).apply()
                prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
                prefs.edit().remove(lastErrorKey).apply()
                prefs.edit().putString(phaseKey, CloudSyncPhase.DONE.name).apply()
                prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
                markSyncActivity()
                _cloudSyncLoading.value = false
                appendCloudSyncLog(prefs, manifestId, "DONE", "OK • up ${bytesUp}B • down ${bytesDown}B • total ${prefs.getLong(totalMsKeyFor(manifestId), 0L)}ms")
                updateCloudSyncUiStatus(manifestId)
                rescheduleNotificationsNow()
                return
            }
            stackId = decoded.stackBinId
            historyId = decoded.historyBinId
            prefs.edit().putString(stackIdKey, stackId).putString(historyIdKey, historyId).apply()
        }
        if (stackId.isEmpty() || historyId.isEmpty()) {
            abortCloudSync(
                prefs = prefs,
                manifestId = manifestId,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                stageMsKey = pullMsKey,
                stageStartedAt = pullStartedAt,
                startedAt = startedAt,
                errorMessage = "Missing stack/history id",
                logMessage = "Missing stack/history id"
            )
            return
        }

        val prevStackEtag = prefs.getString(etagStackKey, "").orEmpty().trim()
        val prevHistoryEtag = prefs.getString(etagHistoryKey, "").orEmpty().trim()
        val stackDownload = CloudSyncManager().downloadBackupIfChanged(stackId, prevStackEtag).getOrElse { error ->
            abortCloudSync(
                prefs = prefs,
                manifestId = manifestId,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                stageMsKey = pullMsKey,
                stageStartedAt = pullStartedAt,
                startedAt = startedAt,
                errorMessage = error.message ?: "Stack pull failed",
                logMessage = "Stack pull failed"
            )
            return
        }
        val historyDownload = CloudSyncManager().downloadBackupIfChanged(historyId, prevHistoryEtag).getOrElse { error ->
            abortCloudSync(
                prefs = prefs,
                manifestId = manifestId,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                stageMsKey = pullMsKey,
                stageStartedAt = pullStartedAt,
                startedAt = startedAt,
                errorMessage = error.message ?: "History pull failed",
                logMessage = "History pull failed"
            )
            return
        }

        val pullMs = SystemClock.elapsedRealtime() - pullStartedAt
        prefs.edit().putLong(pullMsKey, pullMs).apply()
        val stackEtag = stackDownload.etag.orEmpty().trim().ifEmpty { prevStackEtag }
        val historyEtag = historyDownload.etag.orEmpty().trim().ifEmpty { prevHistoryEtag }
        if (stackEtag.isNotEmpty()) prefs.edit().putString(etagStackKey, stackEtag).apply()
        if (historyEtag.isNotEmpty()) prefs.edit().putString(etagHistoryKey, historyEtag).apply()

        val bytesDown = (manifestDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
            (stackDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
            (historyDownload.json?.toByteArray(Charsets.UTF_8)?.size ?: 0)
        prefs.edit().putLong(bytesDownloadedKey, bytesDown.toLong()).apply()

        prefs.edit().putString(phaseKey, CloudSyncPhase.MERGING.name).apply()
        updateCloudSyncUiStatus(manifestId)
        val mergeStartedAt = SystemClock.elapsedRealtime()
        if (!stackDownload.json.isNullOrBlank()) {
            val prepared = runCatching { decryptAndPrepare(stackDownload.json.orEmpty()) }.getOrElse { t ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = mergeMsKey,
                    stageStartedAt = mergeStartedAt,
                    startedAt = startedAt,
                    errorMessage = t.message ?: "Decrypt failed",
                    logMessage = "Decrypt stack failed"
                )
                return
            }
            mergeRemoteIntoLocal(prepared, clientId)
        }
        if (!historyDownload.json.isNullOrBlank()) {
            val prepared = runCatching { decryptAndPrepare(historyDownload.json.orEmpty()) }.getOrElse { t ->
                abortCloudSync(
                    prefs = prefs,
                    manifestId = manifestId,
                    lastErrorKey = lastErrorKey,
                    phaseKey = phaseKey,
                    stageMsKey = mergeMsKey,
                    stageStartedAt = mergeStartedAt,
                    startedAt = startedAt,
                    errorMessage = t.message ?: "Decrypt failed",
                    logMessage = "Decrypt history failed"
                )
                return
            }
            mergeRemoteIntoLocal(prepared, clientId)
        }
        val mergeMs = SystemClock.elapsedRealtime() - mergeStartedAt
        prefs.edit().putLong(mergeMsKey, mergeMs).apply()

        val remoteChanged = !stackDownload.json.isNullOrBlank() || !historyDownload.json.isNullOrBlank()
        if (!remoteChanged && !localStackChanged && !localHistoryChanged) {
            finishCloudSyncNoChanges(
                prefs = prefs,
                manifestId = manifestId,
                lastSyncKey = lastSyncKey,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                startedAt = startedAt
            )
            return
        }

        prefs.edit().putString(phaseKey, CloudSyncPhase.PUSHING.name).apply()
        updateCloudSyncUiStatus(manifestId)
        val pushStartedAt = SystemClock.elapsedRealtime()
        var bytesUp = 0L

        suspend fun pushPart(
            partId: String,
            etagKey: String,
            build: suspend () -> Result<String>,
            label: String
        ): String? {
            val plaintext = build().getOrElse { throw it }
            val encrypted = encryptAndPrepare(plaintext)
            bytesUp += encrypted.toByteArray(Charsets.UTF_8).size.toLong()
            val etag = prefs.getString(etagKey, "").orEmpty().trim()
            val upsert = CloudSyncManager().upsertBackup(partId, encrypted, etag.takeIf { it.isNotEmpty() })
            return upsert.getOrElse { error ->
                val msg = error.message.orEmpty()
                if (!msg.contains("412") && !msg.contains("409")) throw error
                prefs.edit().putString(phaseKey, CloudSyncPhase.RETRYING_CONFLICT.name).apply()
                prefs.edit().putInt(retryKey, 1).apply()
                updateCloudSyncUiStatus(manifestId)
                appendCloudSyncLog(prefs, manifestId, "ERROR", "$label conflict, retry")
                val latest = CloudSyncManager().downloadBackupAlways(partId).getOrThrow()
                if (!latest.json.isNullOrBlank()) {
                    val prepared = decryptAndPrepare(latest.json.orEmpty())
                    mergeRemoteIntoLocal(prepared, clientId)
                }
                val retryPlain = build().getOrThrow()
                val retryEnc = encryptAndPrepare(retryPlain)
                bytesUp += retryEnc.toByteArray(Charsets.UTF_8).size.toLong()
                CloudSyncManager().upsertBackup(partId, retryEnc, latest.etag).getOrThrow()
                latest.etag
            }?.orEmpty()?.trim()
        }

        try {
            if (localStackChanged) {
                val newEtag = pushPart(stackId, etagStackKey, ::buildStackBackupJson, "STACK")
                if (!newEtag.isNullOrBlank()) prefs.edit().putString(etagStackKey, newEtag).apply()
            }
            if (localHistoryChanged) {
                val newEtag = pushPart(historyId, etagHistoryKey, ::buildHistoryBackupJson, "HISTORY")
                if (!newEtag.isNullOrBlank()) prefs.edit().putString(etagHistoryKey, newEtag).apply()
            }
        } catch (t: Throwable) {
            abortCloudSync(
                prefs = prefs,
                manifestId = manifestId,
                lastErrorKey = lastErrorKey,
                phaseKey = phaseKey,
                stageMsKey = pushMsKey,
                stageStartedAt = pushStartedAt,
                startedAt = startedAt,
                errorMessage = t.message ?: "Upload failed",
                logMessage = "Upload failed"
            )
            return
        }

        prefs.edit().putLong(bytesUploadedKey, bytesUp).apply()
        prefs.edit().putLong(pushMsKey, SystemClock.elapsedRealtime() - pushStartedAt).apply()
        prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
        prefs.edit().remove(lastErrorKey).apply()
        prefs.edit().putString(phaseKey, CloudSyncPhase.DONE.name).apply()
        prefs.edit().putLong(totalMsKeyFor(manifestId), SystemClock.elapsedRealtime() - startedAt).apply()
        markSyncActivity()
        _cloudSyncLoading.value = false
        appendCloudSyncLog(prefs, manifestId, "DONE", "OK • up ${bytesUp}B • down ${bytesDown}B • total ${prefs.getLong(totalMsKeyFor(manifestId), 0L)}ms")
        updateCloudSyncUiStatus(manifestId)
        rescheduleNotificationsNow()
    }
    
    fun refreshNotificationSchedules() {
        viewModelScope.launch {
            rescheduleNotificationsNow()
        }
    }
    
    fun clearPendingNotifications() {
        viewModelScope.launch {
            val clients = repository.observeClients().first()
            val supplements = clients.flatMap { client ->
                repository.getAllSupplements(client.id.toString()).first()
            }
            val scheduler = NotificationSchedulerImpl(context)
            scheduler.clearAll(supplements)
        }
    }

    private suspend fun rescheduleNotificationsNow() {
        val clients = repository.observeClients().first()
        val supplements = clients.flatMap { client ->
            repository.getAllSupplements(client.id.toString()).first()
        }
        val scheduler = NotificationSchedulerImpl(context)
        scheduler.rescheduleAll(supplements)
    }

    fun receiveData(binId: String) {
        viewModelScope.launch {
            syncTwoWay(binId)
        }
    }

    fun silentDownloadAndMerge(binId: String) {
        viewModelScope.launch { syncTwoWay(binId) }
    }
    
    private suspend fun mergeRemoteIntoLocal(json: String, clientId: java.util.UUID) {
        val decoded = OAKBackupJson.decodeCompat(json).getOrElse { return }
        val clientIdString = clientId.toString()
        val localSupplements = repository.getAllSupplementsForSync(clientIdString).associateBy { it.id.toString().lowercase(Locale.ROOT) }
        val localRecordList = repository.getAllRecordsForSync(clientIdString)
        val localRecords = localRecordList.associateBy { it.id.lowercase(Locale.ROOT) }
        val localRecordsByDoseKey = localRecordList
            .groupBy { DoseEventKey.make(it.supplementId, it.date) }
            .mapNotNull { (key, list) -> list.maxByOrNull { it.updatedAtEpochMs }?.let { key to it } }
            .toMap()
        
        decoded.stack.forEach { remote ->
            val remoteId = remote.id.lowercase(Locale.ROOT)
            val local = localSupplements[remoteId]
            val remoteUpdatedAt = remote.updatedAtEpochMs
            val remoteDeletedAt = remote.deletedAtEpochMs
            if (remoteDeletedAt != null) {
                val localDeletedAt = local?.deletedAtEpochMs ?: 0L
                if (remoteDeletedAt > localDeletedAt) {
                    val updated = (local ?: makeLocalFromRemote(remote, clientId)).copy(
                        updatedAtEpochMs = maxOf(remoteUpdatedAt, remoteDeletedAt),
                        deletedAtEpochMs = remoteDeletedAt
                    )
                    repository.saveSupplement(updated)
                }
                return@forEach
            }
            
            if (local == null) {
                repository.saveSupplement(makeLocalFromRemote(remote, clientId))
                return@forEach
            }
            
            val localTs = maxOf(local.updatedAtEpochMs, local.deletedAtEpochMs ?: 0L)
            if (remoteUpdatedAt > localTs) {
                repository.updateSupplement(
                    local.copy(
                        name = remote.name,
                        startDate = runCatching { LocalDate.parse(remote.startDate) }.getOrElse { local.startDate },
                        cycleConfig = local.cycleConfig.copy(
                            isContinuous = remote.cycle.isContinuous,
                            daysOn = remote.cycle.daysOn,
                            daysOff = remote.cycle.daysOff,
                            durationMonths = remote.cycle.durationMonths ?: local.cycleConfig.durationMonths,
                            weeklyRecurrence = run {
                                val mask = remote.cycle.weeklyWeekdaysMask ?: return@run null
                                val interval = remote.cycle.weeklyIntervalWeeks ?: return@run null
                                val anchor = remote.cycle.weeklyAnchorDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } ?: return@run null
                                WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
                            },
                            intervalDays = remote.cycle.intervalDays ?: local.cycleConfig.intervalDays
                        ),
                        dailyDose = remote.dailyDose,
                        intakeTime = remote.intakeTime,
                        lastTakenLocalDate = remote.lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: local.lastTakenLocalDate,
                        updatedAtEpochMs = remoteUpdatedAt,
                        deletedAtEpochMs = null
                    )
                )
            }
        }
        
        decoded.history.forEach { remote ->
            val remoteId = remote.id.lowercase(Locale.ROOT)
            val normalizedSupplementId = remote.supplementId.lowercase(Locale.ROOT)
            val remoteDoseKey = DoseEventKey.make(normalizedSupplementId, remote.dateEpochMs)
            val remoteUpdatedAt = remote.updatedAtEpochMs.takeIf { it > 0L } ?: remote.dateEpochMs
            val localByKey = localRecordsByDoseKey[remoteDoseKey]
            if (localByKey != null) {
                if (remoteUpdatedAt <= localByKey.updatedAtEpochMs) return@forEach
                val updated = localByKey.copy(
                    supplementId = normalizedSupplementId,
                    date = remote.dateEpochMs,
                    status = remote.status,
                    updatedAtEpochMs = remoteUpdatedAt
                )
                repository.insertIntakeRecord(
                    updated
                )
                repository.deleteDuplicateIntakeRecords(
                    supplementId = normalizedSupplementId,
                    date = remote.dateEpochMs,
                    keepId = updated.id
                )
                return@forEach
            }

            val local = localRecords[remoteId]
            val localUpdatedAt = local?.updatedAtEpochMs ?: 0L
            if (remoteUpdatedAt <= localUpdatedAt) return@forEach
            val inserted = com.example.supplementtracker.domain.repository.IntakeRecord(
                id = remoteId,
                supplementId = normalizedSupplementId,
                date = remote.dateEpochMs,
                status = remote.status,
                updatedAtEpochMs = remoteUpdatedAt
            )
            repository.insertIntakeRecord(inserted)
            repository.deleteDuplicateIntakeRecords(
                supplementId = normalizedSupplementId,
                date = remote.dateEpochMs,
                keepId = inserted.id
            )
        }
    }
    
    private fun makeLocalFromRemote(remote: OAKBackupSupplementDTO, clientId: java.util.UUID): UserSupplement {
        val weekly = run {
            val mask = remote.cycle.weeklyWeekdaysMask ?: return@run null
            val interval = remote.cycle.weeklyIntervalWeeks ?: return@run null
            val anchor = remote.cycle.weeklyAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@run null
            WeeklyRecurrenceConfig(weekdaysMask = mask, intervalWeeks = interval, anchorDate = anchor)
        }
        val cycle = CycleConfig(
            daysOn = remote.cycle.daysOn,
            daysOff = remote.cycle.daysOff,
            isContinuous = remote.cycle.isContinuous,
            durationMonths = remote.cycle.durationMonths,
            weeklyRecurrence = weekly,
            intervalDays = remote.cycle.intervalDays
        )
        return UserSupplement(
            id = runCatching { java.util.UUID.fromString(remote.id) }.getOrElse {
                com.example.supplementtracker.domain.util.StableId.uuidFromString(
                    remote.id.trim().lowercase(Locale.ROOT)
                )
            },
            clientId = clientId,
            name = remote.name,
            startDate = runCatching { LocalDate.parse(remote.startDate) }.getOrElse { LocalDate.now() },
            cycleConfig = cycle,
            dailyDose = remote.dailyDose,
            intakeTime = remote.intakeTime,
            lastTakenLocalDate = remote.lastTakenLocalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            updatedAtEpochMs = remote.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            deletedAtEpochMs = remote.deletedAtEpochMs
        )
    }
    
    private suspend fun hasLocalChangesSince(clientId: java.util.UUID, lastSyncEpochMs: Long): Boolean {
        if (lastSyncEpochMs <= 0L) return true
        if (hasLocalStackChangesSince(clientId, lastSyncEpochMs)) return true
        return hasLocalHistoryChangesSince(clientId, lastSyncEpochMs)
    }

    private suspend fun hasLocalStackChangesSince(clientId: java.util.UUID, lastSyncEpochMs: Long): Boolean {
        if (lastSyncEpochMs <= 0L) return true
        val clientIdString = clientId.toString()
        val supplements = repository.getAllSupplementsForSync(clientIdString)
        return supplements.any { maxOf(it.updatedAtEpochMs, it.deletedAtEpochMs ?: 0L) > lastSyncEpochMs }
    }

    private suspend fun hasLocalHistoryChangesSince(clientId: java.util.UUID, lastSyncEpochMs: Long): Boolean {
        if (lastSyncEpochMs <= 0L) return true
        val clientIdString = clientId.toString()
        val records = repository.getAllRecordsForSync(clientIdString)
        return records.any { it.updatedAtEpochMs > lastSyncEpochMs }
    }

    private fun markSyncActivity() {
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
        prefs.edit().putLong("cloudSyncLastActivityEpochMs", System.currentTimeMillis()).apply()
    }

    private fun autoSyncDelayMs(prefs: android.content.SharedPreferences): Long {
        return 1_000L
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
