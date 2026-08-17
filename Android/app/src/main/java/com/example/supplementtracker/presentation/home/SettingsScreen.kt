package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.presentation.designsystem.OakColors
import android.app.Activity
import android.app.AlarmManager
import android.Manifest
import android.content.Context
import com.example.supplementtracker.service.OakPrefs
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.BuildConfig
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakLogoMark
import com.example.supplementtracker.presentation.components.ClientEditorDialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.service.ClientProfileMutationResult
import com.example.supplementtracker.service.CommercialFeature
import com.example.supplementtracker.service.CommercialPlan
import com.example.supplementtracker.service.EntitlementManager
import com.example.supplementtracker.service.EntitlementPolicy
import com.example.supplementtracker.service.DiagnosticsReporter
import com.example.supplementtracker.presentation.share.StackShareImageGenerator
import com.example.supplementtracker.presentation.share.StackShareItem
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
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

@Composable
private fun SettingsBrandHeader(secondaryTextColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OakLogoMark()
        Text(
            text = stringResource(R.string.settings_dedication),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = secondaryTextColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    entitlementManager: EntitlementManager,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onNavigateToNotificationCheck: () -> Unit,
    onNavigateToPlanAccess: () -> Unit,
    onNavigateToCoachOverview: () -> Unit,
    onNavigateToDemoPreview: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val prefs = remember { OakPrefs.get(context) }
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val allSupplements by homeViewModel.allClientSupplements.collectAsStateWithLifecycle()
    val lastNotificationRebuildEpochMs by homeViewModel.lastNotificationRebuildEpochMs.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val entitlementSnapshot by entitlementManager.snapshot.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw, entitlementSnapshot.plan) {
        val unique = clientsRaw.distinctBy { it.id }
        entitlementManager.maxClients()?.let(unique::take) ?: unique
    }
    val canExport = EntitlementPolicy.allows(entitlementSnapshot.plan, CommercialFeature.DATA_EXPORT)
    val currentPlanLabel = stringResource(
        when (entitlementSnapshot.plan) {
            CommercialPlan.FREE -> R.string.plan_free_title
            CommercialPlan.PRO -> R.string.plan_pro_title
            CommercialPlan.COACH -> R.string.plan_coach_title
        }
    )
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
    var isFactoryResetDialogVisible by remember { mutableStateOf(false) }
    var shareAnonymousDiagnostics by remember { mutableStateOf(DiagnosticsReporter.isEnabled(context)) }
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

    fun handleClientMutation(
        result: ClientProfileMutationResult,
        onSuccess: () -> Unit
    ) {
        when (result) {
            ClientProfileMutationResult.Success -> onSuccess()
            ClientProfileMutationResult.DuplicateName -> coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.client_name_duplicate))
            }
            ClientProfileMutationResult.ClientLimitReached -> coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.plan_client_limit_reached))
            }
            is ClientProfileMutationResult.Failure -> coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.client_mutation_failed_format,
                        result.error.message ?: context.getString(R.string.error_unknown)
                    )
                )
            }
        }
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
                            Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.a11y_close), tint = primaryTextColor)
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
                item { SettingsBrandHeader(secondaryTextColor) }

                item {
                    SettingsSection(title = stringResource(R.string.client_management)) {
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
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = stringResource(R.string.a11y_confirm), tint = OakColors.Taken)
                                        }

                                        IconButton(onClick = { isMenuExpanded = true }) {
                                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.a11y_more_options), tint = secondaryTextColor)
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
                                                    isEditClientDialogVisible = true
                                                    isMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete)) },
                                                onClick = {
                                                    homeViewModel.deleteClient(client) { result ->
                                                        handleClientMutation(result) {}
                                                    }
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
                    SettingsSection(title = stringResource(R.string.plan_access_title)) {
                        SettingsRow(
                            title = stringResource(R.string.plan_access_manage),
                            trailing = currentPlanLabel,
                            onClick = onNavigateToPlanAccess
                        )
                        SettingsRow(
                            title = stringResource(R.string.coach_overview_title),
                            trailing = if (entitlementSnapshot.plan == CommercialPlan.COACH) {
                                stringResource(R.string.coach_overview_open)
                            } else {
                                stringResource(R.string.plan_coach_title)
                            },
                            onClick = {
                                if (entitlementSnapshot.plan == CommercialPlan.COACH) {
                                    onNavigateToCoachOverview()
                                } else {
                                    onNavigateToPlanAccess()
                                }
                            }
                        )
                        if (BuildConfig.DEBUG) {
                            SettingsRow(
                                title = stringResource(R.string.demo_preview_title),
                                trailing = "DEBUG",
                                onClick = onNavigateToDemoPreview
                            )
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
                    SettingsSection(title = stringResource(R.string.privacy_diagnostics_title)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.diagnostics_opt_in_title), color = primaryTextColor)
                                Text(
                                    stringResource(R.string.diagnostics_opt_in_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                            Switch(
                                checked = shareAnonymousDiagnostics,
                                onCheckedChange = { enabled ->
                                    shareAnonymousDiagnostics = enabled
                                    DiagnosticsReporter.setConsent(context, enabled)
                                }
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            stringResource(R.string.health_disclaimer_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.data_tools)) {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        SettingsRow(
                            title = shareStackTitle,
                            onClick = {
                                if (!canExport) {
                                    onNavigateToPlanAccess()
                                } else coroutineScope.launch(Dispatchers.Main) {
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        SettingsRow(
                            title = stringResource(R.string.notification_check_open_diagnostics),
                            onClick = onNavigateToNotificationCheck
                        )
                        SettingsRow(
                            title = stringResource(R.string.reliability_last_rebuild),
                            trailing = if (lastNotificationRebuildEpochMs <= 0L) {
                                stringResource(R.string.reliability_never)
                            } else {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(lastNotificationRebuildEpochMs))
                            }
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
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
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                                color = OakColors.Error,
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
                    homeViewModel.factoryReset { result ->
                        result.onSuccess {
                            isNotificationEnabledByUser = false
                            onThemeChange(AppTheme.SYSTEM)
                            isFactoryResetDialogVisible = false
                        }.onFailure { error ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    error.message ?: context.getString(R.string.error_unknown)
                                )
                            }
                        }
                    }
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
        ClientEditorDialog(
            title = stringResource(R.string.add_a_client),
            initialName = "",
            confirmTitle = stringResource(R.string.client_create_action),
            onDismiss = { isAddClientDialogVisible = false },
            onConfirm = { name ->
                val created = ClientProfile(id = UUID.randomUUID(), name = name, avatarColorArgb = 0)
                homeViewModel.createClient(created) { result ->
                    handleClientMutation(result) { isAddClientDialogVisible = false }
                }
            }
        )
    }

    if (isEditClientDialogVisible) {
        val target = editingClient
        if (target != null) {
            ClientEditorDialog(
                title = stringResource(R.string.edit),
                initialName = target.name,
                confirmTitle = stringResource(R.string.save),
                onDismiss = { isEditClientDialogVisible = false },
                onConfirm = { name ->
                    homeViewModel.updateClient(target.copy(name = name)) { result ->
                        handleClientMutation(result) { isEditClientDialogVisible = false }
                    }
                }
            )
        }
    }

}
