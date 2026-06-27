package com.example.supplementtracker.presentation.home

import android.app.Activity
import android.app.AlarmManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.ScrollableDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.presentation.share.StackShareImageGenerator
import com.example.supplementtracker.presentation.share.StackShareItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onNavigateToNotificationCheck: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val prefs = remember { context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE) }
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val allSupplements by homeViewModel.allClientSupplements.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw) { clientsRaw.distinctBy { it.id } }
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hostView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val localViewModelStoreOwner = LocalViewModelStoreOwner.current
    val compositionContext = rememberCompositionContext()
    val coroutineScope = rememberCoroutineScope()
    val shareStackTitle = stringResource(R.string.share_stack)
    val listState = rememberLazyListState()
    var isAddClientDialogVisible by remember { mutableStateOf(false) }
    var isEditClientDialogVisible by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientProfile?>(null) }
    var clientNameInput by remember { mutableStateOf("") }
    var isFactoryResetDialogVisible by remember { mutableStateOf(false) }
    var isNotificationEnabledByUser by remember { mutableStateOf(prefs.getBoolean("isNotificationEnabledByUser", false)) }
    var hasNotificationPermission by remember { mutableStateOf(hasNotificationPermission(context)) }

    fun refreshNotificationPermissionUi() {
        val granted = hasNotificationPermission(context)
        hasNotificationPermission = granted
        val stored = prefs.getBoolean("isNotificationEnabledByUser", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val next = stored && granted
            if (next != isNotificationEnabledByUser) {
                isNotificationEnabledByUser = next
                prefs.edit().putBoolean("isNotificationEnabledByUser", next).apply()
            }
            return
        }
        if (stored != isNotificationEnabledByUser) isNotificationEnabledByUser = stored
    }

    LaunchedEffect(Unit) {
        refreshNotificationPermissionUi()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNotificationPermissionUi()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OakBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.settings_title), color = primaryTextColor) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = primaryTextColor)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsSection(title = stringResource(R.string.client_management)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OakLogoMark()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.settings_dedication),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = secondaryTextColor
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        if (clients.isEmpty()) {
                            Text(
                                text = stringResource(R.string.add_client_to_start),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        } else {
                            val clientListState = rememberLazyListState()
                            LazyColumn(
                                state = clientListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    items = clients,
                                    key = { it.id },
                                    contentType = { "client" }
                                ) { client ->
                                    var isMenuExpanded by remember(client.id) { mutableStateOf(false) }
                                    val isActive = client.id == currentClientId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = client.name, color = primaryTextColor, modifier = Modifier.weight(1f))
                                        if (isActive) {
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                        }

                                        IconButton(onClick = { isMenuExpanded = true }) {
                                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = null, tint = secondaryTextColor)
                                        }

                                        DropdownMenu(
                                            expanded = isMenuExpanded,
                                            onDismissRequest = { isMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.select)) },
                                                onClick = {
                                                    activeClientManager.setCurrentClientId(client.id)
                                                    isMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.edit)) },
                                                onClick = {
                                                    editingClient = client
                                                    clientNameInput = client.name
                                                    isEditClientDialogVisible = true
                                                    isMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete)) },
                                                onClick = {
                                                    val deletingActive = client.id == currentClientId
                                                    homeViewModel.deleteClient(client)
                                                    if (deletingActive) activeClientManager.setCurrentClientId(null)
                                                    isMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(
                            onClick = { isAddClientDialogVisible = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.add_a_client))
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.appearance_title)) {
                        AppThemeSegmentedControl(
                            appTheme = appTheme,
                            onThemeChange = onThemeChange
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.data_tools)) {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        SettingsRow(
                            title = shareStackTitle,
                            onClick = {
                                coroutineScope.launch(Dispatchers.Main) {
                                    try {
                                        val activity = context as? Activity
                                        if (activity == null) {
                                            Toast.makeText(context, context.getString(R.string.settings_share_error_missing_activity), Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val shareItems = allSupplements
                                            .sortedBy { it.intakeTime }
                                            .map { StackShareItem(name = it.name, dose = it.dailyDose, time = it.intakeTime) }

                                        val savedStateRegistryOwner = hostView.findViewTreeSavedStateRegistryOwner()
                                        if (savedStateRegistryOwner == null) {
                                            Toast.makeText(context, context.getString(R.string.settings_share_error_missing_saved_state_registry_owner), Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val viewModelStoreOwner = localViewModelStoreOwner
                                        if (viewModelStoreOwner == null) {
                                            Toast.makeText(context, context.getString(R.string.settings_share_error_missing_view_model_store_owner), Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val bitmap = StackShareImageGenerator.generate(
                                            activity = activity,
                                            context = context,
                                            lifecycleOwner = lifecycleOwner,
                                            savedStateRegistryOwner = savedStateRegistryOwner,
                                            viewModelStoreOwner = viewModelStoreOwner,
                                            compositionContext = compositionContext,
                                            items = shareItems,
                                            isDark = isDark
                                        )

                                        val imageFile = withContext(Dispatchers.IO) {
                                            val cachePath = File(context.cacheDir, "shared_images")
                                            cachePath.mkdirs()
                                            val target = File(cachePath, "oak_stack_${System.currentTimeMillis()}.png")
                                            FileOutputStream(target).use { stream ->
                                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                                stream.flush()
                                            }
                                            target
                                        }

                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            imageFile
                                        )

                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, shareStackTitle))
                                    } catch (e: Exception) {
                                        Log.e("ShareStack", "Error sharing stack", e)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_share_error_format, e.message ?: context.getString(R.string.error_unknown)),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.settings_section_about_and_copyright)) {
                        Text(
                            text = stringResource(R.string.settings_intro_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = stringResource(R.string.settings_app_version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                        Text(
                            text = stringResource(R.string.settings_author),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                        Text(
                            text = stringResource(R.string.settings_copyright),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                    }
                }
                
                item {
                    val packageName = context.packageName
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching { powerManager.isIgnoringBatteryOptimizations(packageName) }.getOrElse { true }
                    } else {
                        true
                    }
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val canScheduleExactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        alarmManager.canScheduleExactAlarms()
                    } else {
                        true
                    }
                    
                    SettingsSection(title = stringResource(R.string.settings_notifications_title)) {
                        val permissionStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (hasNotificationPermission) {
                                stringResource(R.string.notification_check_permission_granted)
                            } else {
                                stringResource(R.string.notification_check_permission_denied)
                            }
                        } else {
                            stringResource(R.string.notification_check_permission_not_required)
                        }
                        SettingsRow(
                            title = stringResource(R.string.notification_check_permission_label),
                            trailing = permissionStatus,
                            onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(Uri.parse("package:$packageName"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            } else {
                                null
                            }
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        SettingsRow(
                            title = stringResource(R.string.notification_check_open_diagnostics),
                            onClick = onNavigateToNotificationCheck
                        )

                        SettingsRow(
                            title = stringResource(R.string.notification_check_reschedule_now),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
                                    val message = context.getString(R.string.notification_check_snackbar_permission_required)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                    return@SettingsRow
                                }
                                homeViewModel.refreshNotificationSchedules()
                                val message = context.getString(R.string.notification_check_snackbar_rescheduled)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )

                        SettingsRow(
                            title = stringResource(R.string.notification_check_clear_pending),
                            onClick = {
                                homeViewModel.clearPendingNotifications()
                                val message = context.getString(R.string.notification_check_snackbar_cleared)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        val notificationChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            isNotificationEnabledByUser && hasNotificationPermission
                        } else {
                            isNotificationEnabledByUser
                        }
                        SettingsSwitchRow(
                            title = stringResource(R.string.notification_permission_toggle),
                            checked = notificationChecked,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (!hasNotificationPermission(context)) {
                                        val activity = context as? Activity
                                        if (activity != null) {
                                            ActivityCompat.requestPermissions(
                                                activity,
                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                                101
                                            )
                                        }
                                    }
                                }
                                prefs.edit().putBoolean("isNotificationEnabledByUser", checked).apply()
                                isNotificationEnabledByUser = checked
                                homeViewModel.refreshNotificationSchedules()
                                refreshNotificationPermissionUi()
                            }
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        if (!canScheduleExactAlarms) {
                            Text(
                                text = stringResource(R.string.settings_exact_alarm_tip),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsRow(
                                title = stringResource(R.string.settings_exact_alarm_enable_title),
                                trailing = stringResource(R.string.notification_check_exact_alarms_disabled),
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        runCatching {
                                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(Uri.parse("package:$packageName"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            )
                        } else {
                            SettingsRow(
                                title = stringResource(R.string.settings_exact_alarm_status_title),
                                trailing = stringResource(R.string.notification_check_exact_alarms_enabled),
                                onClick = null
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        if (!isIgnoringBatteryOptimizations) {
                            Text(
                                text = stringResource(R.string.settings_battery_opt_tip),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsRow(
                                title = stringResource(R.string.settings_battery_opt_disable_title),
                                trailing = stringResource(R.string.settings_battery_opt_on),
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val uri = Uri.parse("package:$packageName")
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                    .setData(uri)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.onFailure {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            SettingsRow(
                                title = stringResource(R.string.settings_open_app_settings),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(Uri.parse("package:$packageName"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            )
                        } else {
                            SettingsRow(
                                title = stringResource(R.string.settings_battery_opt_status_title),
                                trailing = stringResource(R.string.settings_battery_opt_off),
                                onClick = null
                            )
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { isFactoryResetDialogVisible = true }) {
                            Text(
                                text = stringResource(R.string.factory_reset),
                                color = Color(0xFFD32F2F),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (isFactoryResetDialogVisible) {
        AlertDialog(
            onDismissRequest = { isFactoryResetDialogVisible = false },
            title = { Text(stringResource(R.string.factory_reset)) },
            text = { Text(stringResource(R.string.wipe_data_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    clients.forEach { homeViewModel.deleteClient(it) }
                    activeClientManager.setCurrentClientId(null)
                    isFactoryResetDialogVisible = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { isFactoryResetDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isAddClientDialogVisible) {
        AlertDialog(
            onDismissRequest = { isAddClientDialogVisible = false },
            title = { Text(stringResource(R.string.add_a_client)) },
            text = {
                OutlinedTextField(
                    value = clientNameInput,
                    onValueChange = { clientNameInput = it },
                    label = { Text(stringResource(R.string.client_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = clientNameInput.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    val created = ClientProfile(id = UUID.randomUUID(), name = trimmed, avatarColorArgb = 0)
                    homeViewModel.createClient(created)
                    activeClientManager.setCurrentClientId(created.id)
                    clientNameInput = ""
                    isAddClientDialogVisible = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    clientNameInput = ""
                    isAddClientDialogVisible = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (isEditClientDialogVisible) {
        val target = editingClient
        if (target != null) {
            AlertDialog(
                onDismissRequest = { isEditClientDialogVisible = false },
                title = { Text(stringResource(R.string.edit)) },
                text = {
                    OutlinedTextField(
                        value = clientNameInput,
                        onValueChange = { clientNameInput = it },
                        label = { Text(stringResource(R.string.client_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = clientNameInput.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        homeViewModel.updateClient(target.copy(name = trimmed))
                        clientNameInput = ""
                        isEditClientDialogVisible = false
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        clientNameInput = ""
                        isEditClientDialogVisible = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }

}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OakCard(
            modifier = Modifier.fillMaxWidth(),
            variant = OakCardVariant.Glass,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(16.dp),
            elevation = 2.dp,
            content = content
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

@Composable
private fun StepChip(label: String, done: Boolean) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = if (done) Color(0x3322C55E) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = if (done) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = secondaryTextColor
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = primaryTextColor)
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(text = trailing, color = secondaryTextColor)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = primaryTextColor)
        Spacer(modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AppearanceCard(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryTextColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppThemeSegmentedControl(
                appTheme = appTheme,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
private fun AppThemeSegmentedControl(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val outerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFF2F2F7)
    val selectedColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White

    val items = listOf(
        Triple(stringResource(R.string.appearance_light), AppTheme.LIGHT, appTheme == AppTheme.LIGHT),
        Triple(stringResource(R.string.appearance_dark), AppTheme.DARK, appTheme == AppTheme.DARK),
        Triple(stringResource(R.string.appearance_system), AppTheme.SYSTEM, appTheme == AppTheme.SYSTEM)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(outerColor, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (label, theme, selected) ->
            val pillShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) selectedColor else Color.Transparent, pillShape)
            ) {
                TextButton(
                    onClick = { onThemeChange(theme) },
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isDark) Color.White else Color(0xFF111111)
                    )
                }
            }
        }
    }
}

@Composable
private fun getCycleSummary(
    supplement: UserSupplement,
    calculateCycleUseCase: CalculateCycleUseCase,
    today: LocalDate
): String {
    val config = supplement.cycleConfig
    val status = calculateCycleUseCase(supplement.startDate, config, today)
    val statusText = if (status == CycleStatus.ON) {
        R.string.cycle_status_on
    } else {
        R.string.cycle_status_off
    }

    val cycleText = if (config.isContinuous) {
        "${stringResource(statusText)} • ${stringResource(R.string.cycle_continuous)}"
    } else {
        stringResource(R.string.cycle_summary_format, stringResource(statusText), config.daysOn, config.daysOff)
    }
    val intervalText = config.intervalDays?.let { interval ->
        stringResource(R.string.cycle_every_n_days_format, interval)
    }
    val durationText = config.durationMonths?.let { days ->
        val endDate = supplement.startDate.plusDays(days.toLong())
        val dateText = endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        stringResource(R.string.cycle_until_format, dateText)
    } ?: stringResource(R.string.unlimited)

    return listOfNotNull(cycleText, intervalText, durationText).joinToString(" • ")
}

@Composable
private fun InfoCard(title: String, content: String, isOffCycle: Boolean = false) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val alpha = if (isOffCycle) 0.55f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun ExpandableInfoCard(
    title: String,
    content: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCardNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = subtitleColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStackListScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    onNavigateToAdd: () -> Unit,
    onNavigateToSyncCenter: () -> Unit,
    onNavigateToUserGuide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val supplements by homeViewModel.allClientSupplements.collectAsStateWithLifecycle()
    val currentDay by homeViewModel.currentDay.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val backgroundBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
        } else {
            Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
        }
    }
    var searchText by rememberSaveable { mutableStateOf("") }
    val filteredSupplements by remember(supplements, searchText) {
        derivedStateOf {
            val q = searchText.trim().lowercase(Locale.ROOT)
            if (q.isEmpty()) return@derivedStateOf supplements
            supplements.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val listState = rememberLazyListState()
    val calculateCycleUseCase = remember { CalculateCycleUseCase() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.my_list_title), color = primaryTextColor) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = primaryTextColor)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                item(key = "quick_actions", contentType = "quick_actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCardNavigationRow(
                            title = stringResource(R.string.sync_center_title),
                            subtitle = "",
                            onClick = onNavigateToSyncCenter
                        )
                        InfoCardNavigationRow(
                            title = stringResource(R.string.settings_guide_title),
                            subtitle = "",
                            onClick = onNavigateToUserGuide
                        )
                    }
                }
                item(key = "search", contentType = "search") {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.history_search_placeholder), color = secondaryTextColor) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryTextColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                            focusedPlaceholderColor = secondaryTextColor,
                            unfocusedPlaceholderColor = secondaryTextColor
                        )
                    )
                }
                items(
                    items = filteredSupplements,
                    key = { it.id },
                    contentType = { "supplement" }
                ) { supplement ->
                    val time = supplement.intakeTime.trim()
                    val title = if (time.isEmpty()) supplement.name else "${supplement.name} ($time)"
                    val isOffCycle = calculateCycleUseCase(supplement.startDate, supplement.cycleConfig, currentDay) == CycleStatus.OFF
                    InfoCard(
                        title = title,
                        content = getCycleSummary(
                            supplement = supplement,
                            calculateCycleUseCase = calculateCycleUseCase,
                            today = currentDay
                        ),
                        isOffCycle = isOffCycle
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientManagementCard(
    clients: List<ClientProfile>,
    currentClientId: UUID?,
    onSelect: (UUID?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ClientProfile) -> Unit,
    onDelete: (ClientProfile) -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.client_management), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (clients.isEmpty()) {
                Text(text = stringResource(R.string.add_client_to_start), style = MaterialTheme.typography.bodyMedium)
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = clients,
                        key = { it.id },
                        contentType = { "client" }
                    ) { client ->
                        var isMenuExpanded by remember(client.id) { mutableStateOf(false) }
                        val isActive = client.id == currentClientId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = client.name, modifier = Modifier.weight(1f))
                            if (isActive) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }

                            IconButton(onClick = { isMenuExpanded = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select)) },
                                    onClick = {
                                        onSelect(client.id)
                                        isMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        onEdit(client)
                                        isMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        onDelete(client)
                                        isMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAdd) {
                Text(stringResource(R.string.add_a_client))
            }
        }
    }
}

@Composable
private fun LogoCard() {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OakLogoMark()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_dedication),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
