package com.example.supplementtracker.presentation.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.HomeViewModel
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE) }
    val hostedBinId by homeViewModel.hostedBinId.collectAsStateWithLifecycle()
    val cloudSyncLoading by homeViewModel.cloudSyncLoading.collectAsStateWithLifecycle()
    val uiStatus by homeViewModel.cloudSyncUiStatus.collectAsStateWithLifecycle()
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var linkedBinId by remember { mutableStateOf(prefs.getString("cloudSyncLinkedBinId", "").orEmpty()) }
    var isAutoSyncEnabled by remember { mutableStateOf(prefs.getBoolean("isAutoSyncEnabled", false)) }
    var isEncryptionEnabled by remember { mutableStateOf(prefs.getBoolean("cloudSyncEncryptionEnabled", false)) }
    var encryptionKeyInput by remember { mutableStateOf("") }
    var isBinIdVisible by remember { mutableStateOf(false) }
    var isRevokeConfirmVisible by remember { mutableStateOf(false) }
    var isRotateConfirmVisible by remember { mutableStateOf(false) }
    var isDisableEncryptionConfirmVisible by remember { mutableStateOf(false) }
    var isRehostConfirmVisible by remember { mutableStateOf(false) }
    var isImportKeyConfirmVisible by remember { mutableStateOf(false) }
    var isClearLogConfirmVisible by remember { mutableStateOf(false) }
    var logsVersion by remember { mutableIntStateOf(0) }
    var logQuery by remember { mutableStateOf("") }
    var logPhaseFilter by remember { mutableStateOf("ALL") }
    var isPhaseMenuExpanded by remember { mutableStateOf(false) }
    var isExportLogConfirmVisible by remember { mutableStateOf(false) }

    val activeBinId = remember(hostedBinId, linkedBinId) {
        val hosted = (hostedBinId ?: "").trim()
        val linked = linkedBinId.trim()
        if (hosted.isNotEmpty()) hosted else linked
    }

    LaunchedEffect(isAutoSyncEnabled) {
        prefs.edit().putBoolean("isAutoSyncEnabled", isAutoSyncEnabled).apply()
        if (isAutoSyncEnabled) {
            homeViewModel.startAutoSync()
            return@LaunchedEffect
        }
        homeViewModel.stopAutoSync()
    }

    LaunchedEffect(activeBinId) {
        if (activeBinId.isNotEmpty()) homeViewModel.refreshCloudSyncUi(activeBinId)
    }

    LaunchedEffect(linkedBinId) {
        prefs.edit().putString("cloudSyncLinkedBinId", linkedBinId.trim()).apply()
    }

    val exportedKey = homeViewModel.exportCloudEncryptionKey().orEmpty()
    val logsRaw = remember(activeBinId, uiStatus?.lastAttemptEpochMs, uiStatus?.lastError, logsVersion) {
        if (activeBinId.isBlank()) return@remember emptyList<CloudSyncLogUiItem>()
        val raw = prefs.getString("cloudSyncLog_${activeBinId.trim()}", "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val out = ArrayList<CloudSyncLogUiItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                CloudSyncLogUiItem(
                    ts = obj.optLong("ts", 0L),
                    phase = obj.optString("phase", "").orEmpty(),
                    msg = obj.optString("msg", "").orEmpty()
                )
            )
        }
        out.asReversed()
    }
    val logs = remember(logsRaw, logPhaseFilter, logQuery) {
        val q = logQuery.trim()
        logsRaw.filter { item ->
            val phaseOk = logPhaseFilter == "ALL" || item.phase.equals(logPhaseFilter, ignoreCase = true)
            if (!phaseOk) return@filter false
            if (q.isEmpty()) return@filter true
            item.phase.contains(q, ignoreCase = true) || item.msg.contains(q, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isRevokeConfirmVisible) {
            AlertDialog(
                onDismissRequest = { isRevokeConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_revoke_title)) },
                text = { Text(stringResource(R.string.sync_center_revoke_message)) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            isRevokeConfirmVisible = false
                            homeViewModel.revokeHostedBin()
                        },
                        enabled = !cloudSyncLoading
                    ) { Text(stringResource(R.string.sync_center_action_revoke)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isRevokeConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_cancel)) }
                }
            )
        }
        if (isRotateConfirmVisible) {
            AlertDialog(
                onDismissRequest = { isRotateConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_rotate_title)) },
                text = { Text(stringResource(R.string.sync_center_rotate_message)) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            isRotateConfirmVisible = false
                            homeViewModel.rotateCloudEncryptionKey()
                        }
                    ) { Text(stringResource(R.string.sync_center_action_rotate)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isRotateConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_cancel)) }
                }
            )
        }
        if (isDisableEncryptionConfirmVisible) {
            AlertDialog(
                onDismissRequest = { isDisableEncryptionConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_disable_encryption_title)) },
                text = { Text(stringResource(R.string.sync_center_disable_encryption_message)) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            isDisableEncryptionConfirmVisible = false
                            isEncryptionEnabled = false
                            prefs.edit().putBoolean("cloudSyncEncryptionEnabled", false).apply()
                            homeViewModel.enableCloudEncryption(false)
                        }
                    ) { Text(stringResource(R.string.sync_center_action_disable)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isDisableEncryptionConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_keep_encryption)) }
                }
            )
        }
        if (isRehostConfirmVisible) {
            AlertDialog(
                onDismissRequest = { isRehostConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_rehost_title)) },
                text = { Text(stringResource(R.string.sync_center_rehost_message)) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            isRehostConfirmVisible = false
                            homeViewModel.hostData()
                        },
                        enabled = !cloudSyncLoading
                    ) { Text(stringResource(R.string.sync_center_action_create_new_code)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isRehostConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_cancel)) }
                }
            )
        }
        if (isImportKeyConfirmVisible) {
            val raw = encryptionKeyInput.trim()
            val keyId = raw.substringBefore(":", "")
            val currentKeyId = prefs.getString("cloudSyncEncCurrentKeyId", "").orEmpty().trim()
            val previousKeyId = prefs.getString("cloudSyncEncPreviousKeyId", "").orEmpty().trim()
            val note = when {
                keyId.isBlank() -> stringResource(R.string.sync_center_import_key_note_invalid_format)
                keyId == currentKeyId -> stringResource(R.string.sync_center_import_key_note_same_current)
                keyId == previousKeyId -> stringResource(R.string.sync_center_import_key_note_same_previous)
                else -> stringResource(R.string.sync_center_import_key_note_keyid_format, keyId)
            }
            AlertDialog(
                onDismissRequest = { isImportKeyConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_import_key_title)) },
                text = { Text(note) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            isImportKeyConfirmVisible = false
                            homeViewModel.importCloudEncryptionKey(encryptionKeyInput)
                            encryptionKeyInput = ""
                            Toast.makeText(context, context.getString(R.string.sync_center_toast_key_imported), Toast.LENGTH_SHORT).show()
                        },
                        enabled = keyId.isNotBlank()
                    ) { Text(stringResource(R.string.sync_center_action_import)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isImportKeyConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_cancel)) }
                }
            )
        }
        if (isClearLogConfirmVisible) {
            AlertDialog(
                onDismissRequest = { isClearLogConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_clear_log_title)) },
                text = { Text(stringResource(R.string.sync_center_clear_log_message)) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            val id = activeBinId.trim()
                            if (id.isNotEmpty()) {
                                prefs.edit().remove("cloudSyncLog_$id").apply()
                                logsVersion += 1
                                Toast.makeText(context, context.getString(R.string.sync_center_toast_log_cleared), Toast.LENGTH_SHORT).show()
                            }
                            isClearLogConfirmVisible = false
                        }
                    ) { Text(stringResource(R.string.sync_center_action_delete)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isClearLogConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_cancel)) }
                }
            )
        }
        if (isExportLogConfirmVisible) {
            AlertDialog(
                onDismissRequest = { isExportLogConfirmVisible = false },
                title = { Text(stringResource(R.string.sync_center_export_log_title)) },
                text = { Text(stringResource(R.string.sync_center_export_log_message)) },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            val id = activeBinId.trim()
                            if (id.isNotEmpty()) {
                                val raw = prefs.getString("cloudSyncLog_$id", "[]").orEmpty()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("cloudSyncLog", raw))
                                Toast.makeText(context, context.getString(R.string.sync_center_toast_log_copied), Toast.LENGTH_SHORT).show()
                            }
                            isExportLogConfirmVisible = false
                        }
                    ) { Text(stringResource(R.string.sync_center_action_copy)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { isExportLogConfirmVisible = false }) { Text(stringResource(R.string.sync_center_action_cancel)) }
                }
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(stringResource(R.string.sync_center_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "tabs") {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.sync_center_tab_host)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.sync_center_tab_link)) }
                        )
                    }
                }

                item(key = "setup") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (selectedTab == 0) {
                                Text(stringResource(R.string.sync_center_device_a_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.sync_center_device_a_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Divider()
                                StepRow(number = 1, text = stringResource(R.string.sync_center_step_host_1))
                                StepRow(number = 2, text = stringResource(R.string.sync_center_step_host_2))
                                StepRow(number = 3, text = stringResource(R.string.sync_center_step_host_3))
                                OutlinedButton(
                                    onClick = {
                                        val existing = (hostedBinId ?: "").trim()
                                        if (existing.isNotEmpty()) {
                                            isRehostConfirmVisible = true
                                            return@OutlinedButton
                                        }
                                        homeViewModel.hostData()
                                    },
                                    enabled = !cloudSyncLoading
                                ) {
                                    Text(stringResource(R.string.sync_center_action_host_data))
                                }

                                val hosted = (hostedBinId ?: "").trim()
                                if (hosted.isNotEmpty()) {
                                    Divider()
                                    Text(stringResource(R.string.sync_center_link_code_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (isBinIdVisible) hosted else "•".repeat(24),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(
                                            onClick = { isBinIdVisible = !isBinIdVisible }
                                        ) { Text(if (isBinIdVisible) stringResource(R.string.sync_center_action_hide) else stringResource(R.string.sync_center_action_show)) }
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("binId", hosted))
                                                Toast.makeText(context, context.getString(R.string.sync_center_toast_code_copied), Toast.LENGTH_SHORT).show()
                                            }
                                        ) { Text(stringResource(R.string.sync_center_action_copy)) }
                                        OutlinedButton(
                                            onClick = { isRevokeConfirmVisible = true },
                                            enabled = !cloudSyncLoading
                                        ) { Text(stringResource(R.string.sync_center_action_revoke)) }
                                    }
                                }
                            } else {
                                Text(stringResource(R.string.sync_center_device_b_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.sync_center_device_b_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Divider()
                                StepRow(number = 1, text = stringResource(R.string.sync_center_step_link_1))
                                StepRow(number = 2, text = stringResource(R.string.sync_center_step_link_2))
                                StepRow(number = 3, text = stringResource(R.string.sync_center_step_link_3))
                                OutlinedTextField(
                                    value = linkedBinId,
                                    onValueChange = { linkedBinId = it },
                                    label = { Text(stringResource(R.string.sync_center_link_code_input_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedButton(
                                    onClick = { homeViewModel.receiveData(linkedBinId) },
                                    enabled = linkedBinId.trim().isNotEmpty() && !cloudSyncLoading
                                ) { Text(stringResource(R.string.sync_center_action_download)) }
                            }

                            Divider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.sync_center_auto_sync_label), style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = isAutoSyncEnabled, onCheckedChange = { isAutoSyncEnabled = it })
                            }
                        }
                    }
                }

                item(key = "status") {
                    if (activeBinId.isNotBlank() && uiStatus?.binId == activeBinId.trim()) {
                        val status = uiStatus!!
                        val notYet = stringResource(R.string.sync_center_not_yet)
                        val lastSyncText = if (status.lastSyncEpochMs > 0L) formatter.format(Instant.ofEpochMilli(status.lastSyncEpochMs)) else notYet
                        val lastAttemptText = if (status.lastAttemptEpochMs > 0L) formatter.format(Instant.ofEpochMilli(status.lastAttemptEpochMs)) else notYet
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.sync_center_status_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.sync_center_status_code_format, status.binId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.sync_center_status_last_sync_format, lastSyncText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.sync_center_status_last_attempt_format, lastAttemptText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (status.hasPendingChanges) stringResource(R.string.sync_center_status_pending_changes) else stringResource(R.string.sync_center_status_no_pending_changes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(
                                        R.string.sync_center_status_bytes_format,
                                        formatBytes(status.bytesDownloaded),
                                        formatBytes(status.bytesUploaded)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(
                                        R.string.sync_center_status_timings_format,
                                        status.pullMs,
                                        status.mergeMs,
                                        status.pushMs,
                                        status.totalMs
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!status.lastError.isNullOrBlank()) {
                                    Text(
                                        stringResource(R.string.sync_center_status_last_error_format, status.lastError.orEmpty()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD32F2F),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val err = status.lastError.orEmpty()
                                    val hint = isEncryptionEnabled &&
                                        (err.contains("Missing cloud sync key", ignoreCase = true) ||
                                            err.contains("Decrypt failed", ignoreCase = true))
                                    if (hint) {
                                        Text(
                                            stringResource(R.string.sync_center_status_hint_missing_key),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { homeViewModel.syncNow(status.binId) },
                                    enabled = !cloudSyncLoading
                                ) { Text(stringResource(R.string.sync_center_action_sync_now)) }
                            }
                        }
                    }
                }

                item(key = "encryption") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.sync_center_encryption_title), style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = isEncryptionEnabled,
                                    onCheckedChange = {
                                        if (!it) {
                                            isDisableEncryptionConfirmVisible = true
                                            return@Switch
                                        }
                                        isEncryptionEnabled = true
                                        prefs.edit().putBoolean("cloudSyncEncryptionEnabled", true).apply()
                                        homeViewModel.enableCloudEncryption(true)
                                    }
                                )
                            }

                            if (isEncryptionEnabled) {
                                OutlinedTextField(
                                    value = exportedKey,
                                    onValueChange = { _: String -> },
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.sync_center_export_key_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("cloudSyncKey", exportedKey))
                                            Toast.makeText(context, context.getString(R.string.sync_center_toast_key_copied), Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = exportedKey.isNotEmpty()
                                    ) { Text(stringResource(R.string.sync_center_action_copy_key)) }
                                    OutlinedButton(onClick = { isRotateConfirmVisible = true }) { Text(stringResource(R.string.sync_center_action_rotate)) }
                                }
                                OutlinedTextField(
                                    value = encryptionKeyInput,
                                    onValueChange = { encryptionKeyInput = it },
                                    label = { Text(stringResource(R.string.sync_center_import_key_input_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )
                                OutlinedButton(
                                    onClick = {
                                        isImportKeyConfirmVisible = true
                                    },
                                    enabled = encryptionKeyInput.trim().isNotEmpty()
                                ) { Text(stringResource(R.string.sync_center_action_import_key)) }
                            }
                        }
                    }
                }

                item(key = "logs_title") {
                    if (activeBinId.isNotBlank()) {
                        Text(stringResource(R.string.sync_center_logs_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = logQuery,
                            onValueChange = { logQuery = it },
                            label = { Text(stringResource(R.string.sync_center_logs_search_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val phaseLabel = when (logPhaseFilter.uppercase()) {
                            "ERROR" -> stringResource(R.string.sync_center_filter_error)
                            "HOST" -> stringResource(R.string.sync_center_filter_host)
                            "DONE" -> stringResource(R.string.sync_center_filter_done)
                            else -> stringResource(R.string.sync_center_filter_all)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Phase",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box {
                                OutlinedButton(onClick = { isPhaseMenuExpanded = true }) { Text(phaseLabel) }
                                DropdownMenu(
                                    expanded = isPhaseMenuExpanded,
                                    onDismissRequest = { isPhaseMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sync_center_filter_all)) },
                                        onClick = {
                                            logPhaseFilter = "ALL"
                                            isPhaseMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sync_center_filter_error)) },
                                        onClick = {
                                            logPhaseFilter = "ERROR"
                                            isPhaseMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sync_center_filter_host)) },
                                        onClick = {
                                            logPhaseFilter = "HOST"
                                            isPhaseMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sync_center_filter_done)) },
                                        onClick = {
                                            logPhaseFilter = "DONE"
                                            isPhaseMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { isExportLogConfirmVisible = true }) { Text(stringResource(R.string.sync_center_action_export)) }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { isClearLogConfirmVisible = true }) {
                                Text(stringResource(R.string.sync_center_action_clear), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                items(items = logs, key = { it.ts }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val time = if (item.ts > 0L) formatter.format(Instant.ofEpochMilli(item.ts)) else ""
                            Text("$time • ${item.phase}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.msg, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private data class CloudSyncLogUiItem(
    val ts: Long,
    val phase: String,
    val msg: String
)

@Composable
private fun StepRow(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.2fMB", mb)
}
