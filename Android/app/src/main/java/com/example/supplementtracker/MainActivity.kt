package com.example.supplementtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.domain.usecase.SaveSupplementUseCase
import com.example.supplementtracker.presentation.add_supplement.AddSupplementViewModel
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.presentation.navigation.AppNavigation
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.presentation.navigation.ActiveClientManager

import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import com.example.supplementtracker.receiver.TimeZoneChangeReceiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.supplementtracker.presentation.splash.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private data class AppDeps(
        val homeViewModel: HomeViewModel,
        val historyViewModel: HistoryViewModel,
        val addSupplementViewModel: AddSupplementViewModel,
        val activeClientManager: ActiveClientManager
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        capturePendingIntakeAction(intent)
        restorePendingIntakeAction(savedInstanceState)

        // Đăng ký TimeZoneChangeReceiver
        timeZoneReceiver = TimeZoneChangeReceiver {
            homeViewModel?.refreshNotificationSchedules()
        }
        registerReceiver(timeZoneReceiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))

        setContent {
            var appTheme by rememberSaveable { mutableStateOf(AppTheme.SYSTEM) }
            val isDarkTheme = when (appTheme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            var deps by remember { mutableStateOf<AppDeps?>(null) }
            var initError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                try {
                    val (repository, activeClientManager) = withContext(Dispatchers.IO) {
                        val db = SupplementDatabase.getInstance(applicationContext)
                        val repository = SupplementRepositoryImpl(db.supplementDao)
                        val activeClientManager = ActiveClientManager(applicationContext, repository)
                        repository to activeClientManager
                    }

                    val homeViewModel = HomeViewModel(
                        context = applicationContext,
                        repository = repository,
                        activeClientManager = activeClientManager
                    )
                    val historyViewModel = HistoryViewModel(repository, activeClientManager)
                    val addSupplementViewModel = AddSupplementViewModel(
                        saveSupplementUseCase = SaveSupplementUseCase(repository),
                        repository = repository,
                        context = applicationContext,
                        activeClientManager = activeClientManager
                    )

                    this@MainActivity.homeViewModel = homeViewModel
                    homeViewModel.refreshNotificationSchedules()
                    consumePendingIntakeActionIfPossible(homeViewModel)

                    deps = AppDeps(
                        homeViewModel = homeViewModel,
                        historyViewModel = historyViewModel,
                        addSupplementViewModel = addSupplementViewModel,
                        activeClientManager = activeClientManager
                    )
                } catch (e: Exception) {
                    Log.e("Startup", "Init failed", e)
                    initError = e.message ?: "Unknown"
                }
            }

            MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val ready = deps
                    if (initError != null) {
                        Text(text = initError ?: "Unknown error")
                    } else if (ready == null) {
                        SplashScreen(autoFinish = false)
                    } else {
                        AppNavigation(
                            homeViewModel = ready.homeViewModel,
                            historyViewModel = ready.historyViewModel,
                            addSupplementViewModel = ready.addSupplementViewModel,
                            activeClientManager = ready.activeClientManager,
                            appTheme = appTheme,
                            onThemeChange = { appTheme = it }
                        )
                    }
                }
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
        homeViewModel?.refreshNotificationSchedules()
        val prefs = applicationContext.getSharedPreferences("oak_settings", MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val stored = prefs.getBoolean("isNotificationEnabledByUser", false)
            if (granted != stored) prefs.edit().putBoolean("isNotificationEnabledByUser", granted).apply()
        }

        val enabled = prefs.getBoolean("isAutoSyncEnabled", false)
        if (!enabled) return
        
        val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
        val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
        val binId = if (hosted.isNotEmpty()) hosted else linked
        if (binId.isEmpty()) return
        
        // We don't auto-download on resume to avoid annoying 404 errors if the bin is gone.
        // Auto-sync is handled via periodic upload in HomeViewModel.
        // Log.d("AutoSync", "☁️ Auto-Sync: Starting download...")
        // homeViewModel.receiveData(binId)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 101) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val prefs = applicationContext.getSharedPreferences("oak_settings", MODE_PRIVATE)
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
