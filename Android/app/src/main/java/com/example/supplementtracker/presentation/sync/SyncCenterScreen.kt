package com.example.supplementtracker.presentation.sync

import com.example.supplementtracker.presentation.designsystem.OakColors
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.example.supplementtracker.service.OakPrefs
import com.example.supplementtracker.service.FirebaseRevision
import com.example.supplementtracker.service.CloudSyncLogEntry
import com.example.supplementtracker.service.CloudSyncLogStore
import com.example.supplementtracker.service.SyncHealthEvaluator
import com.example.supplementtracker.service.SyncHealthInput
import com.example.supplementtracker.service.SyncHealthLevel
import com.example.supplementtracker.service.SyncHealthReport
import com.example.supplementtracker.service.SyncRecoveryAction
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakCardVariant
import com.example.supplementtracker.presentation.home.HomeViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { OakPrefs.get(context) }
    val hostedBinId by homeViewModel.hostedBinId.collectAsStateWithLifecycle()
    val linkedBinId by homeViewModel.linkedBinId.collectAsStateWithLifecycle()
    val cloudSyncLoading by homeViewModel.cloudSyncLoading.collectAsStateWithLifecycle()
    val uiStatus by homeViewModel.cloudSyncUiStatus.collectAsStateWithLifecycle()
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    }
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    var selectedTab by remember { mutableIntStateOf(0) }
    var linkedBinInput by remember { mutableStateOf(linkedBinId.orEmpty()) }
    var isAutoSyncEnabled by remember { mutableStateOf(prefs.getBoolean("isAutoSyncEnabled", false)) }
    var isEncryptionEnabled by remember { mutableStateOf(prefs.getBoolean("cloudSyncEncryptionEnabled", false)) }
    var encryptionKeyInput by remember { mutableStateOf("") }
    var isExportedKeyVisible by remember { mutableStateOf(false) }
    var isImportKeyVisible by remember { mutableStateOf(false) }
    var isBinIdVisible by remember { mutableStateOf(false) }
    var isLinkedBinIdVisible by remember { mutableStateOf(false) }
    var isRevokeConfirmVisible by remember { mutableStateOf(false) }
    var isDisableEncryptionConfirmVisible by remember { mutableStateOf(false) }
    var isRehostConfirmVisible by remember { mutableStateOf(false) }
    var isImportKeyConfirmVisible by remember { mutableStateOf(false) }
    var isClearLogConfirmVisible by remember { mutableStateOf(false) }
    var logsVersion by remember { mutableIntStateOf(0) }
    var logQuery by remember { mutableStateOf("") }
    var logPhaseFilter by remember { mutableStateOf("ALL") }
    var isPhaseMenuExpanded by remember { mutableStateOf(false) }
    var isExportLogConfirmVisible by remember { mutableStateOf(false) }
    var isStatusBinIdVisible by remember { mutableStateOf(false) }
    var isManifestPartsVisible by remember { mutableStateOf(false) }
    var isStatusDiagnosticsVisible by remember { mutableStateOf(false) }

    val activeBinId = remember(hostedBinId, linkedBinId) {
        val hosted = hostedBinId.orEmpty().trim()
        val linked = linkedBinId.orEmpty().trim()
        if (hosted.isNotEmpty()) hosted else linked
    }
    val hasActiveCloudLink = activeBinId.isNotEmpty()
    
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "isAutoSyncEnabled" -> isAutoSyncEnabled = prefs.getBoolean("isAutoSyncEnabled", false)
                "cloudSyncEncryptionEnabled" -> isEncryptionEnabled = prefs.getBoolean("cloudSyncEncryptionEnabled", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(linkedBinId) {
        linkedBinInput = linkedBinId.orEmpty()
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
        isStatusBinIdVisible = false
        isManifestPartsVisible = false
        if (activeBinId.isNotEmpty()) {
            homeViewModel.refreshCloudSyncUi(activeBinId)
            if (isAutoSyncEnabled) homeViewModel.startAutoSync()
        }
    }

    val exportedKey = homeViewModel.exportCloudEncryptionKey().orEmpty()
    val logsRaw = remember(activeBinId, uiStatus?.lastAttemptEpochMs, uiStatus?.lastError, logsVersion) {
        CloudSyncLogStore.read(prefs, activeBinId)
    }
    val logs = remember(logsRaw, logPhaseFilter, logQuery) {
        val q = logQuery.trim()
        logsRaw.filter { item ->
            val phaseOk = logPhaseFilter == "ALL" || item.phase.equals(logPhaseFilter, ignoreCase = true)
            if (!phaseOk) return@filter false
            if (q.isEmpty()) return@filter true
            item.phase.contains(q, ignoreCase = true) || item.message.contains(q, ignoreCase = true)
        }
    }

    OakBackground {
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
                                CloudSyncLogStore.clear(prefs, id)
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
                                val pretty = formatLogPretty(CloudSyncLogStore.read(prefs, id), formatter)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("cloudSyncLog", pretty))
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.sync_center_title), color = primaryTextColor) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_navigate_back), tint = primaryTextColor)
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
                    OakCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = OakCardVariant.Paper,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp),
                        elevation = 0.dp
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(R.string.sync_center_tab_host), color = primaryTextColor) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(R.string.sync_center_tab_link), color = primaryTextColor) }
                            )
                        }
                    }
                }

                item(key = "setup") {
                    OakCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = OakCardVariant.Paper,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(16.dp),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (selectedTab == 0) {
                                Text(stringResource(R.string.sync_center_device_a_title), style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                                Text(
                                    stringResource(R.string.sync_center_device_a_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                                HorizontalDivider()
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
                                    HorizontalDivider()
                                    Text(stringResource(R.string.sync_center_link_code_label), style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                    Text(
                                        text = if (isBinIdVisible) hosted else "•".repeat(24),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryTextColor,
                                        modifier = Modifier.clickable(enabled = hosted.isNotEmpty()) {
                                            copySensitiveText(context, "binId", hosted)
                                            Toast.makeText(context, context.getString(R.string.sync_center_toast_code_copied), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(
                                            onClick = { isBinIdVisible = !isBinIdVisible }
                                        ) { Text(if (isBinIdVisible) stringResource(R.string.sync_center_action_hide) else stringResource(R.string.sync_center_action_show)) }
                                        OutlinedButton(
                                            onClick = {
                                                copySensitiveText(context, "binId", hosted)
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
                                Text(stringResource(R.string.sync_center_device_b_title), style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                                Text(
                                    stringResource(R.string.sync_center_device_b_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                                HorizontalDivider()
                                StepRow(number = 1, text = stringResource(R.string.sync_center_step_link_1))
                                StepRow(number = 2, text = stringResource(R.string.sync_center_step_link_2))
                                StepRow(number = 3, text = stringResource(R.string.sync_center_step_link_3))
                                OutlinedTextField(
                                    value = linkedBinInput,
                                    onValueChange = { linkedBinInput = it },
                                    label = { Text(stringResource(R.string.sync_center_link_code_input_label), color = secondaryTextColor) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (isLinkedBinIdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isLinkedBinIdVisible = !isLinkedBinIdVisible }) {
                                            Icon(
                                                imageVector = if (isLinkedBinIdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = stringResource(if (isLinkedBinIdVisible) R.string.a11y_hide else R.string.a11y_show),
                                                tint = secondaryTextColor
                                            )
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryTextColor),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = primaryTextColor,
                                        unfocusedTextColor = primaryTextColor,
                                        focusedLabelColor = secondaryTextColor,
                                        unfocusedLabelColor = secondaryTextColor,
                                        focusedTrailingIconColor = secondaryTextColor,
                                        unfocusedTrailingIconColor = secondaryTextColor
                                    )
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val pasted = clipboard.primaryClip
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                            .orEmpty()
                                            .trim()
                                        if (pasted.isNotEmpty()) linkedBinInput = pasted
                                    }) { Text(stringResource(R.string.sync_center_action_paste)) }
                                    OutlinedButton(
                                        onClick = {
                                            val linkCode = linkedBinInput.trim()
                                            if (!FirebaseRevision.isValidBinId(linkCode)) {
                                                Toast.makeText(context, context.getString(R.string.sync_center_invalid_link_code), Toast.LENGTH_SHORT).show()
                                                return@OutlinedButton
                                            }
                                            homeViewModel.linkData(linkCode)
                                        },
                                        enabled = linkedBinInput.trim().isNotEmpty() && !cloudSyncLoading
                                    ) { Text(stringResource(R.string.sync_center_action_download)) }
                                }
                                if (!linkedBinId.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            homeViewModel.unlinkData()
                                            linkedBinInput = ""
                                            if (hostedBinId.isNullOrBlank()) isAutoSyncEnabled = false
                                        },
                                        enabled = !cloudSyncLoading
                                    ) { Text(stringResource(R.string.sync_center_action_unlink)) }
                                }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.sync_center_auto_sync_label), style = MaterialTheme.typography.bodyLarge, color = primaryTextColor)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = isAutoSyncEnabled, onCheckedChange = { isAutoSyncEnabled = it })
                            }
                        }
                    }
                }

                item(key = "status") {
                    if (activeBinId.isNotBlank() && uiStatus?.binId == activeBinId.trim()) {
                        val status = uiStatus!!
                        val health = remember(status, isAutoSyncEnabled, isEncryptionEnabled) {
                            SyncHealthEvaluator.evaluate(
                                SyncHealthInput(
                                    hasLink = true,
                                    autoSyncEnabled = isAutoSyncEnabled,
                                    hasPendingChanges = status.hasPendingChanges,
                                    lastSyncEpochMs = status.lastSyncEpochMs,
                                    lastAttemptEpochMs = status.lastAttemptEpochMs,
                                    lastError = status.lastError,
                                    encryptionEnabled = isEncryptionEnabled
                                )
                            )
                        }
                        val notYet = stringResource(R.string.sync_center_not_yet)
                        val lastSyncText = if (status.lastSyncEpochMs > 0L) formatter.format(Instant.ofEpochMilli(status.lastSyncEpochMs)) else notYet
                        val lastAttemptText = if (status.lastAttemptEpochMs > 0L) formatter.format(Instant.ofEpochMilli(status.lastAttemptEpochMs)) else notYet
                        OakCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = OakCardVariant.Paper,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(16.dp),
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.sync_center_status_title), style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                                SyncHealthSummary(
                                    report = health,
                                    onSyncNow = { homeViewModel.syncNow(status.binId) }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.sync_center_status_code_format,
                                            if (isStatusBinIdVisible) status.binId else "•".repeat(24)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { isStatusBinIdVisible = !isStatusBinIdVisible }) {
                                        Icon(
                                            imageVector = if (isStatusBinIdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = stringResource(if (isLinkedBinIdVisible) R.string.a11y_hide else R.string.a11y_show),
                                                tint = secondaryTextColor
                                        )
                                    }
                                }
                                Text(stringResource(R.string.sync_center_status_last_sync_format, lastSyncText), style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                Text(stringResource(R.string.sync_center_status_last_attempt_format, lastAttemptText), style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                Text(
                                    if (status.hasPendingChanges) stringResource(R.string.sync_center_status_pending_changes) else stringResource(R.string.sync_center_status_no_pending_changes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                                if (!status.lastError.isNullOrBlank()) {
                                    Text(
                                        stringResource(R.string.sync_center_failure_safe_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) OakColors.ErrorDark else OakColors.Error
                                    )
                                }
                                TextButton(onClick = { isStatusDiagnosticsVisible = !isStatusDiagnosticsVisible }) {
                                    Text(
                                        stringResource(
                                            if (isStatusDiagnosticsVisible) R.string.sync_center_diagnostics_hide
                                            else R.string.sync_center_diagnostics_show
                                        )
                                    )
                                }
                                if (isStatusDiagnosticsVisible) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.sync_center_queue_format, status.queuedMutationCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                                if (status.nextRetryEpochMs > System.currentTimeMillis()) {
                                    Text(
                                        stringResource(
                                            R.string.sync_center_retry_after_format,
                                            formatter.format(Instant.ofEpochMilli(status.nextRetryEpochMs))
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor
                                    )
                                }
                                if (status.conflictRemoteWins + status.conflictLocalWins + status.conflictTieLocalWins > 0) {
                                    Text(
                                        stringResource(
                                            R.string.sync_center_conflict_preview_format,
                                            status.conflictRemoteWins,
                                            status.conflictLocalWins,
                                            status.conflictTieLocalWins
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor
                                    )
                                }
                                Text(
                                    stringResource(R.string.sync_center_journal_count_format, status.journalCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                                Text(
                                    stringResource(
                                        R.string.sync_center_status_bytes_format,
                                        formatBytes(status.bytesDownloaded),
                                        formatBytes(status.bytesUploaded)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
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
                                    color = secondaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val stackId = prefs.getString("cloudSyncStackBinId_${status.binId}", "").orEmpty().trim()
                                val historyId = prefs.getString("cloudSyncHistoryBinId_${status.binId}", "").orEmpty().trim()
                                if (stackId.isNotEmpty() || historyId.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (stackId.isNotEmpty()) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.sync_center_stack_id_label_format,
                                                        if (isManifestPartsVisible) stackId else "•".repeat(16)
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = secondaryTextColor
                                                )
                                            }
                                            if (historyId.isNotEmpty()) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.sync_center_history_id_label_format,
                                                        if (isManifestPartsVisible) historyId else "•".repeat(16)
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = secondaryTextColor
                                                )
                                            }
                                        }
                                        IconButton(onClick = { isManifestPartsVisible = !isManifestPartsVisible }) {
                                            Icon(
                                                imageVector = if (isManifestPartsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = stringResource(if (isManifestPartsVisible) R.string.a11y_hide else R.string.a11y_show),
                                                tint = secondaryTextColor
                                            )
                                        }
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val text = buildString {
                                                if (stackId.isNotEmpty()) append("stack: ").append(stackId).append('\n')
                                                if (historyId.isNotEmpty()) append("history: ").append(historyId)
                                            }.trim()
                                            if (text.isNotEmpty()) {
                                                clipboard.setPrimaryClip(ClipData.newPlainText("cloudSyncParts", text))
                                                Toast.makeText(context, context.getString(R.string.sync_center_toast_code_copied), Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = stringResource(R.string.a11y_copy), tint = secondaryTextColor)
                                        }
                                    }
                                }
                                if (!status.lastError.isNullOrBlank()) {
                                    Text(
                                        stringResource(R.string.sync_center_status_last_error_format, status.lastError.orEmpty()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) OakColors.ErrorDark else OakColors.Error,
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
                                            color = secondaryTextColor,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
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
                    OakCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = OakCardVariant.Paper,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(16.dp),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.sync_center_encryption_title), style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = isEncryptionEnabled,
                                    enabled = !hasActiveCloudLink,
                                    onCheckedChange = {
                                        if (!it) {
                                            isDisableEncryptionConfirmVisible = true
                                            return@Switch
                                        }
                                        isEncryptionEnabled = true
                                        homeViewModel.enableCloudEncryption(true)
                                    }
                                )
                            }

                            if (hasActiveCloudLink) {
                                Text(
                                    stringResource(R.string.sync_center_encryption_locked_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }

                            if (isEncryptionEnabled || hasActiveCloudLink) {
                                if (exportedKey.isNotEmpty()) {
                                    OutlinedTextField(
                                        value = exportedKey,
                                        onValueChange = { _: String -> },
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.sync_center_export_key_label), color = secondaryTextColor) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2,
                                        visualTransformation = if (isExportedKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { isExportedKeyVisible = !isExportedKeyVisible }) {
                                                Icon(
                                                    imageVector = if (isExportedKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = stringResource(if (isExportedKeyVisible) R.string.a11y_hide else R.string.a11y_show)
                                                )
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = primaryTextColor),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = primaryTextColor,
                                            unfocusedTextColor = primaryTextColor,
                                            focusedLabelColor = secondaryTextColor,
                                            unfocusedLabelColor = secondaryTextColor
                                        )
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            copySensitiveText(context, "cloudSyncKey", exportedKey)
                                            Toast.makeText(context, context.getString(R.string.sync_center_toast_key_copied), Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = exportedKey.isNotEmpty()
                                    ) { Text(stringResource(R.string.sync_center_action_copy_key)) }
                                }
                                OutlinedTextField(
                                    value = encryptionKeyInput,
                                    onValueChange = { encryptionKeyInput = it },
                                    label = { Text(stringResource(R.string.sync_center_import_key_input_label), color = secondaryTextColor) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2,
                                    visualTransformation = if (isImportKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isImportKeyVisible = !isImportKeyVisible }) {
                                            Icon(
                                                imageVector = if (isImportKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = stringResource(if (isImportKeyVisible) R.string.a11y_hide else R.string.a11y_show)
                                            )
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = primaryTextColor),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = primaryTextColor,
                                        unfocusedTextColor = primaryTextColor,
                                        focusedLabelColor = secondaryTextColor,
                                        unfocusedLabelColor = secondaryTextColor
                                    )
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val pasted = clipboard.primaryClip
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                            .orEmpty()
                                            .trim()
                                        if (pasted.isNotEmpty()) encryptionKeyInput = pasted
                                    }) { Text(stringResource(R.string.sync_center_action_paste)) }
                                    OutlinedButton(
                                        onClick = { isImportKeyConfirmVisible = true },
                                        enabled = encryptionKeyInput.trim().isNotEmpty()
                                    ) { Text(stringResource(R.string.sync_center_action_import_key)) }
                                }
                            } else {
                                Text(
                                    stringResource(R.string.sync_center_encryption_off_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }
                }

                item(key = "logs_title") {
                    if (activeBinId.isNotBlank()) {
                        Text(stringResource(R.string.sync_center_logs_title), style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = logQuery,
                            onValueChange = { logQuery = it },
                            label = { Text(stringResource(R.string.sync_center_logs_search_label), color = secondaryTextColor) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryTextColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = primaryTextColor,
                                unfocusedTextColor = primaryTextColor,
                                focusedLabelColor = secondaryTextColor,
                                unfocusedLabelColor = secondaryTextColor
                            )
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
                                text = stringResource(R.string.sync_center_phase_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
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

                items(items = logs, key = { "${it.epochMs}-${it.phase}" }) { item ->
                    OakCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = OakCardVariant.Paper,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(12.dp),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val time = if (item.epochMs > 0L) formatter.format(Instant.ofEpochMilli(item.epochMs)) else ""
                            val phaseText = when (item.phase.uppercase()) {
                                "ERROR" -> stringResource(R.string.sync_center_filter_error)
                                "HOST" -> stringResource(R.string.sync_center_filter_host)
                                "DONE" -> stringResource(R.string.sync_center_filter_done)
                                else -> item.phase
                            }
                            Text("$time • $phaseText", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                            Text(item.message, style = MaterialTheme.typography.bodyMedium, color = primaryTextColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncHealthSummary(report: SyncHealthReport, onSyncNow: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(syncHealthTitle(report.level), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(syncRecoveryHint(report.action), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (report.action == SyncRecoveryAction.SYNC_NOW) {
            OutlinedButton(onClick = onSyncNow) { Text(stringResource(R.string.sync_center_action_sync_now)) }
        }
    }
}

@Composable
private fun syncHealthTitle(level: SyncHealthLevel): String = when (level) {
    SyncHealthLevel.UNLINKED -> stringResource(R.string.sync_health_unlinked)
    SyncHealthLevel.IDLE -> stringResource(R.string.sync_health_idle)
    SyncHealthLevel.HEALTHY -> stringResource(R.string.sync_health_healthy)
    SyncHealthLevel.PENDING -> stringResource(R.string.sync_health_pending)
    SyncHealthLevel.NEEDS_KEY -> stringResource(R.string.sync_health_needs_key)
    SyncHealthLevel.RETRYABLE_ERROR -> stringResource(R.string.sync_health_retryable)
    SyncHealthLevel.ACTION_REQUIRED -> stringResource(R.string.sync_health_action_required)
}

@Composable
private fun syncRecoveryHint(action: SyncRecoveryAction): String = when (action) {
    SyncRecoveryAction.NONE -> stringResource(R.string.sync_health_hint_none)
    SyncRecoveryAction.SYNC_NOW -> stringResource(R.string.sync_health_hint_sync_now)
    SyncRecoveryAction.IMPORT_KEY -> stringResource(R.string.sync_health_hint_import_key)
    SyncRecoveryAction.CHECK_LINK -> stringResource(R.string.sync_health_hint_check_link)
}

private fun copySensitiveText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
}

private fun formatLogPretty(entries: List<CloudSyncLogEntry>, formatter: DateTimeFormatter): String {
    if (entries.isEmpty()) return "[]"
    return entries.asReversed().joinToString("\n") { item ->
        val whenText = if (item.epochMs > 0L) formatter.format(Instant.ofEpochMilli(item.epochMs)) else ""
        listOf(whenText, item.phase, item.message)
            .filter(String::isNotBlank)
            .joinToString(" • ")
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
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
