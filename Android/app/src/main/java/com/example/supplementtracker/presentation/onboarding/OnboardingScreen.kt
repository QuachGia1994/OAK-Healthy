package com.example.supplementtracker.presentation.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakCardVariant
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import java.util.UUID

@Composable
fun OnboardingScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE) }
    val clients by activeClientManager.clients.collectAsStateWithLifecycle()
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
        } else {
            Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
        }
    }
    val cardShape = remember { RoundedCornerShape(28.dp) }

    var step by remember { mutableStateOf(OnboardingStep.CLIENT) }
    var isAddClientDialogVisible by remember { mutableStateOf(false) }
    var newClientName by remember { mutableStateOf("") }

    var isNotificationsEnabledByUser by remember {
        mutableStateOf(prefs.getBoolean("isNotificationEnabledByUser", false))
    }
    var hasNotificationPermission by remember { mutableStateOf(hasNotificationPermission(context)) }

    fun refreshPermissionState() {
        hasNotificationPermission = hasNotificationPermission(context)
        val stored = prefs.getBoolean("isNotificationEnabledByUser", false)
        val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) stored && hasNotificationPermission else stored
        if (next != isNotificationsEnabledByUser) isNotificationsEnabledByUser = next
    }

    LaunchedEffect(Unit) {
        refreshPermissionState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (step != OnboardingStep.CLIENT) {
            step = step.previous()
        }
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OnboardingProgress(step = step)

            OakCard(
                modifier = Modifier.fillMaxWidth(),
                variant = OakCardVariant.Surface,
                shape = cardShape,
                contentPadding = PaddingValues(16.dp),
                elevation = 2.dp
            ) {
                when (step) {
                    OnboardingStep.CLIENT -> ClientStep(
                        clients = clients,
                        currentClientId = currentClientId,
                        onSelectClient = { activeClientManager.setCurrentClientId(it) },
                        onAddClient = { isAddClientDialogVisible = true }
                    )
                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        checked = isNotificationsEnabledByUser,
                        hasPermission = hasNotificationPermission,
                        onCheckedChange = { checked ->
                            prefs.edit().putBoolean("isNotificationEnabledByUser", checked).apply()
                            isNotificationsEnabledByUser = checked
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
                                val activity = context as? Activity
                                if (activity != null) {
                                    ActivityCompat.requestPermissions(
                                        activity,
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                        101
                                    )
                                }
                            }
                            homeViewModel.refreshNotificationSchedules()
                            refreshPermissionState()
                        },
                        onOpenAppSettings = { openAppSettings(context) }
                    )
                    OnboardingStep.EXACT_ALARM -> ExactAlarmStep(onOpenExactAlarm = { openExactAlarmSettings(context) })
                    OnboardingStep.BATTERY -> BatteryStep(
                        onRequestDisableOptimization = { requestIgnoreBatteryOptimizations(context) },
                        onOpenAppSettings = { openAppSettings(context) }
                    )
                    OnboardingStep.DONE -> DoneStep()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step != OnboardingStep.CLIENT) {
                    OutlinedButton(onClick = { step = step.previous() }) {
                        Text(stringResource(R.string.back))
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (step == OnboardingStep.DONE) {
                            onDone()
                        } else {
                            step = step.next()
                        }
                    },
                    enabled = step != OnboardingStep.CLIENT || currentClientId != null
                ) {
                    Text(stringResource(if (step == OnboardingStep.DONE) R.string.onboarding_done else R.string.onboarding_next))
                }
            }
        }
    }

    if (isAddClientDialogVisible) {
        AlertDialog(
            onDismissRequest = { isAddClientDialogVisible = false },
            title = { Text(stringResource(R.string.add_a_client)) },
            text = {
                OutlinedTextField(
                    value = newClientName,
                    onValueChange = { newClientName = it },
                    label = { Text(stringResource(R.string.client_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newClientName.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    val profile = ClientProfile(id = UUID.randomUUID(), name = trimmed, avatarColorArgb = 0)
                    homeViewModel.createClient(profile)
                    activeClientManager.setCurrentClientId(profile.id)
                    newClientName = ""
                    isAddClientDialogVisible = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddClientDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ClientStep(
    clients: List<ClientProfile>,
    currentClientId: UUID?,
    onSelectClient: (UUID) -> Unit,
    onAddClient: () -> Unit
) {
    val itemShape = remember { RoundedCornerShape(16.dp) }
    val rowBase = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f)
    Text(
        text = stringResource(R.string.onboarding_step_client_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    Text(text = stringResource(R.string.onboarding_step_client_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (clients.isEmpty()) {
        Button(onClick = onAddClient) {
            Text(stringResource(R.string.add_a_client))
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = clients, key = { it.id }) { client ->
            val selected = client.id == currentClientId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = rowBase,
                        shape = itemShape
                    )
                    .semantics {
                        role = Role.Button
                        this.selected = selected
                    }
                    .clickable { onSelectClient(client.id) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = client.name, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.select),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    TextButton(onClick = onAddClient) {
        Text(stringResource(R.string.add_a_client))
    }
}

@Composable
private fun NotificationsStep(
    checked: Boolean,
    hasPermission: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.onboarding_step_notifications_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text(text = stringResource(R.string.onboarding_step_notifications_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(R.string.notification_permission_toggle), modifier = Modifier.weight(1f))
        Switch(checked = checked && hasPermission, onCheckedChange = onCheckedChange)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checked && !hasPermission) {
        Text(text = stringResource(R.string.onboarding_notifications_denied), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenAppSettings) {
            Text(stringResource(R.string.onboarding_open_settings))
        }
    }
}

@Composable
private fun ExactAlarmStep(onOpenExactAlarm: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.onboarding_step_exact_alarm_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text(text = stringResource(R.string.onboarding_step_exact_alarm_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onOpenExactAlarm) {
        Text(stringResource(R.string.settings_exact_alarm_enable_title))
    }
}

@Composable
private fun BatteryStep(
    onRequestDisableOptimization: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.onboarding_step_battery_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text(text = stringResource(R.string.onboarding_step_battery_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onRequestDisableOptimization) {
        Text(stringResource(R.string.settings_battery_opt_disable_title))
    }
    TextButton(onClick = onOpenAppSettings) {
        Text(stringResource(R.string.settings_open_app_settings))
    }
}

@Composable
private fun DoneStep() {
    Text(
        text = stringResource(R.string.onboarding_step_done_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    Text(text = stringResource(R.string.onboarding_step_done_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private enum class OnboardingStep {
    CLIENT,
    NOTIFICATIONS,
    EXACT_ALARM,
    BATTERY,
    DONE;

    fun next(): OnboardingStep {
        return when (this) {
            CLIENT -> NOTIFICATIONS
            NOTIFICATIONS -> EXACT_ALARM
            EXACT_ALARM -> BATTERY
            BATTERY -> DONE
            DONE -> DONE
        }
    }

    fun previous(): OnboardingStep {
        return when (this) {
            CLIENT -> CLIENT
            NOTIFICATIONS -> CLIENT
            EXACT_ALARM -> NOTIFICATIONS
            BATTERY -> EXACT_ALARM
            DONE -> BATTERY
        }
    }
}

@Composable
private fun OnboardingProgress(step: OnboardingStep) {
    val total = 5
    val index = when (step) {
        OnboardingStep.CLIENT -> 0
        OnboardingStep.NOTIFICATIONS -> 1
        OnboardingStep.EXACT_ALARM -> 2
        OnboardingStep.BATTERY -> 3
        OnboardingStep.DONE -> 4
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                val active = i <= index
                val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                Surface(shape = RoundedCornerShape(99.dp), color = color, modifier = Modifier.size(width = 18.dp, height = 6.dp)) {}
            }
        }
        val title = when (step) {
            OnboardingStep.CLIENT -> stringResource(R.string.onboarding_step_client_title)
            OnboardingStep.NOTIFICATIONS -> stringResource(R.string.onboarding_step_notifications_title)
            OnboardingStep.EXACT_ALARM -> stringResource(R.string.onboarding_step_exact_alarm_title)
            OnboardingStep.BATTERY -> stringResource(R.string.onboarding_step_battery_title)
            OnboardingStep.DONE -> stringResource(R.string.onboarding_step_done_title)
        }
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val packageName = context.packageName
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    val packageName = context.packageName
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val packageName = context.packageName
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
