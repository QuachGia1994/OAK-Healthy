package com.example.supplementtracker

import android.app.UiModeManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.designsystem.OakBackground
import com.example.supplementtracker.presentation.designsystem.OakDarkColorScheme
import com.example.supplementtracker.presentation.designsystem.OakLightColorScheme
import com.example.supplementtracker.presentation.navigation.AppNavigation
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.service.OakPrefs

import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import com.example.supplementtracker.receiver.TimeZoneChangeReceiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.supplementtracker.presentation.splash.SplashScreen
import com.example.supplementtracker.security.AppIntegrity
import com.example.supplementtracker.worker.CloudAutoSyncWork
import kotlinx.coroutines.delay
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : ComponentActivity() {
    private var timeZoneReceiver: TimeZoneChangeReceiver? = null
    private var homeViewModel: HomeViewModel? = null
    private var pendingIntakeAction: PendingIntakeAction? = null

    enum class IntakeAction {
        TAKEN,
        SKIPPED
    }

    data class IntakeActionIntent(
        val intent: Intent,
        val requestCode: Int
    )

    private data class PendingIntakeAction(
        val supplementId: String,
        val intakeTime: String,
        val scheduledAtMillis: Long,
        val action: IntakeAction,
        val notificationId: Int
    )

    companion object {
        private const val EXTRA_INTAKE_ACTION = "oak_intake_action"
        private const val EXTRA_SUPPLEMENT_ID = "oak_supplement_id"
        private const val EXTRA_INTAKE_TIME = "oak_intake_time"
        private const val EXTRA_SCHEDULED_AT = "oak_scheduled_at"
        private const val EXTRA_NOTIFICATION_ID = "oak_notification_id"

        private const val STATE_HAS_PENDING_INTAKE = "state_has_pending_intake"
        private const val STATE_INTAKE_ACTION = "state_intake_action"
        private const val STATE_SUPPLEMENT_ID = "state_supplement_id"
        private const val STATE_INTAKE_TIME = "state_intake_time"
        private const val STATE_SCHEDULED_AT = "state_scheduled_at"
        private const val STATE_NOTIFICATION_ID = "state_notification_id"

        fun buildIntakeActionIntent(
            context: Context,
            supplementId: String,
            intakeTime: String,
            scheduledAtMillis: Long,
            action: IntakeAction,
            notificationId: Int
        ): IntakeActionIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_INTAKE_ACTION, action.name)
                putExtra(EXTRA_SUPPLEMENT_ID, supplementId)
                putExtra(EXTRA_INTAKE_TIME, intakeTime)
                putExtra(EXTRA_SCHEDULED_AT, scheduledAtMillis)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val requestCode = "$supplementId|$intakeTime|$scheduledAtMillis|${action.name}".hashCode()
            return IntakeActionIntent(intent = intent, requestCode = requestCode)
        }
    }

    private fun applySavedNightMode(theme: AppTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(UiModeManager::class.java)
            val mode = when (theme) {
                AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
                AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
                AppTheme.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
            uiModeManager.setApplicationNightMode(mode)
        }

        val compatMode = when (theme) {
            AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != compatMode) {
            AppCompatDelegate.setDefaultNightMode(compatMode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = OakPrefs.get(applicationContext)
        val initialTheme = AppStartupPolicy.storedTheme(prefs.getString("appTheme", null))
        applySavedNightMode(initialTheme)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        capturePendingIntakeAction(intent)
        restorePendingIntakeAction(savedInstanceState)
        registerTimeZoneReceiver()
        setContent { AppRoot(initialTheme, prefs) }
    }

    private fun registerTimeZoneReceiver() {
        timeZoneReceiver = TimeZoneChangeReceiver {
            homeViewModel?.refreshNotificationSchedules()
        }
        registerReceiver(timeZoneReceiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Composable
    private fun AppRoot(initialTheme: AppTheme, prefs: android.content.SharedPreferences) {
        val integrityVerdict = remember { AppIntegrity.evaluate(applicationContext) }
        if (!integrityVerdict.ok) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    IntegrityBlockedScreen(onExit = { finish() })
                }
            }
            return
        }
        HealthyApp(initialTheme, prefs)
    }

    @Composable
    private fun HealthyApp(initialTheme: AppTheme, prefs: android.content.SharedPreferences) {
        var appTheme by rememberSaveable { mutableStateOf(initialTheme) }
        var dependencies by remember { mutableStateOf<AppDependencies?>(null) }
        var initError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            initializeAppDependencies()
                .onSuccess { dependencies = it }
                .onFailure { error ->
                    Log.e("Startup", "Init failed", error)
                    initError = error.message ?: "Unknown"
                }
        }
        val darkTheme = resolveDarkTheme(appTheme)
        MaterialTheme(colorScheme = if (darkTheme) OakDarkColorScheme else OakLightColorScheme) {
            OakBackground {
                StartupContent(dependencies, initError, appTheme) { newTheme ->
                    appTheme = newTheme
                    prefs.edit().putString("appTheme", newTheme.name).apply()
                    applySavedNightMode(newTheme)
                }
            }
        }
    }

    @Composable
    private fun resolveDarkTheme(appTheme: AppTheme): Boolean = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    private suspend fun initializeAppDependencies(): Result<AppDependencies> = runCatching {
        val splashStartedAt = SystemClock.elapsedRealtime()
        val dependencies = AppDependenciesFactory(applicationContext).create()
        homeViewModel = dependencies.homeViewModel
        dependencies.homeViewModel.refreshNotificationSchedules()
        val prefs = OakPrefs.get(applicationContext)
        CloudAutoSyncWork.setEnabled(applicationContext, prefs.getBoolean("isAutoSyncEnabled", false))
        consumePendingIntakeActionIfPossible(dependencies.homeViewModel)
        val elapsed = SystemClock.elapsedRealtime() - splashStartedAt
        val remainingDelay = AppStartupPolicy.remainingSplashDelay(elapsed)
        if (remainingDelay > 0L) delay(remainingDelay)
        dependencies
    }

    @Composable
    private fun StartupContent(
        dependencies: AppDependencies?,
        initError: String?,
        appTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit
    ) {
        if (initError != null) {
            Text(text = initError)
            return
        }
        val ready = dependencies
        if (ready == null) {
            SplashScreen(autoFinish = false)
            return
        }
        AppNavigation(
            homeViewModel = ready.homeViewModel,
            historyViewModel = ready.historyViewModel,
            addSupplementViewModel = ready.addSupplementViewModel,
            activeClientManager = ready.activeClientManager,
            entitlementManager = ready.entitlementManager,
            appTheme = appTheme,
            onThemeChange = onThemeChange
        )
    }

    @Composable
    private fun IntegrityBlockedScreen(onExit: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.integrity_blocked_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = stringResource(R.string.integrity_blocked_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onExit) {
                Text(text = stringResource(R.string.integrity_blocked_exit))
            }
        }
    }
    
    override fun onDestroy() {
        timeZoneReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        timeZoneReceiver = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePendingIntakeAction(intent)
        homeViewModel?.let { consumePendingIntakeActionIfPossible(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val pending = pendingIntakeAction
        if (pending == null) {
            outState.putBoolean(STATE_HAS_PENDING_INTAKE, false)
            return
        }
        outState.putBoolean(STATE_HAS_PENDING_INTAKE, true)
        outState.putString(STATE_INTAKE_ACTION, pending.action.name)
        outState.putString(STATE_SUPPLEMENT_ID, pending.supplementId)
        outState.putString(STATE_INTAKE_TIME, pending.intakeTime)
        outState.putLong(STATE_SCHEDULED_AT, pending.scheduledAtMillis)
        outState.putInt(STATE_NOTIFICATION_ID, pending.notificationId)
    }
    
    override fun onResume() {
        super.onResume()
        reconcileNotificationPermissionPreference()
        homeViewModel?.refreshNotificationSchedules()
    }

    private fun reconcileNotificationPermissionPreference() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = OakPrefs.get(applicationContext)
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        val stored = prefs.getBoolean("isNotificationEnabledByUser", false)
        val normalized = AppStartupPolicy.notificationPreferenceAfterPermissionCheck(stored, granted)
        if (normalized != stored) {
            prefs.edit().putBoolean("isNotificationEnabledByUser", normalized).apply()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 101) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val prefs = OakPrefs.get(applicationContext)
        prefs.edit().putBoolean("isNotificationEnabledByUser", granted).apply()
        homeViewModel?.refreshNotificationSchedules()
    }

    private fun capturePendingIntakeAction(intent: Intent?) {
        val rawAction = intent?.getStringExtra(EXTRA_INTAKE_ACTION)?.trim().orEmpty()
        if (rawAction.isEmpty()) return
        val action = runCatching { IntakeAction.valueOf(rawAction) }.getOrNull() ?: return
        val supplementId = intent?.getStringExtra(EXTRA_SUPPLEMENT_ID)?.trim().orEmpty()
        val intakeTime = intent?.getStringExtra(EXTRA_INTAKE_TIME)?.trim().orEmpty()
        val scheduledAt = intent?.getLongExtra(EXTRA_SCHEDULED_AT, 0L) ?: 0L
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, 0) ?: 0
        if (supplementId.isEmpty() || scheduledAt <= 0L) return
        pendingIntakeAction = PendingIntakeAction(
            supplementId = supplementId,
            intakeTime = intakeTime,
            scheduledAtMillis = scheduledAt,
            action = action,
            notificationId = notificationId
        )
    }

    private fun restorePendingIntakeAction(savedInstanceState: Bundle?) {
        if (pendingIntakeAction != null) return
        val state = savedInstanceState ?: return
        val hasPending = state.getBoolean(STATE_HAS_PENDING_INTAKE, false)
        if (!hasPending) return
        val rawAction = state.getString(STATE_INTAKE_ACTION, "").orEmpty().trim()
        val action = runCatching { IntakeAction.valueOf(rawAction) }.getOrNull() ?: return
        val supplementId = state.getString(STATE_SUPPLEMENT_ID, "").orEmpty().trim()
        val intakeTime = state.getString(STATE_INTAKE_TIME, "").orEmpty().trim()
        val scheduledAt = state.getLong(STATE_SCHEDULED_AT, 0L)
        val notificationId = state.getInt(STATE_NOTIFICATION_ID, 0)
        if (supplementId.isEmpty() || scheduledAt <= 0L) return
        pendingIntakeAction = PendingIntakeAction(
            supplementId = supplementId,
            intakeTime = intakeTime,
            scheduledAtMillis = scheduledAt,
            action = action,
            notificationId = notificationId
        )
    }

    private fun clearPendingActionExtras() {
        intent?.removeExtra(EXTRA_INTAKE_ACTION)
        intent?.removeExtra(EXTRA_SUPPLEMENT_ID)
        intent?.removeExtra(EXTRA_INTAKE_TIME)
        intent?.removeExtra(EXTRA_SCHEDULED_AT)
        intent?.removeExtra(EXTRA_NOTIFICATION_ID)
    }

    private fun consumePendingIntakeActionIfPossible(homeViewModel: HomeViewModel) {
        val pending = pendingIntakeAction ?: return
        pendingIntakeAction = null
        val mapped = when (pending.action) {
            IntakeAction.TAKEN -> com.example.supplementtracker.presentation.home.DoseAction.TAKEN
            IntakeAction.SKIPPED -> com.example.supplementtracker.presentation.home.DoseAction.SKIPPED
        }
        homeViewModel.recordDoseFromNotification(
            supplementId = pending.supplementId,
            scheduledAtEpochMs = pending.scheduledAtMillis,
            action = mapped
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        manager.cancel(pending.notificationId)
        clearPendingActionExtras()
    }
}
