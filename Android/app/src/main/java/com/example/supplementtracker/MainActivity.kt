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
import androidx.room.Room
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
import com.example.supplementtracker.receiver.TimeZoneChangeReceiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.supplementtracker.presentation.splash.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var timeZoneReceiver: TimeZoneChangeReceiver? = null
    private var homeViewModel: HomeViewModel? = null

    private data class AppDeps(
        val homeViewModel: HomeViewModel,
        val historyViewModel: HistoryViewModel,
        val addSupplementViewModel: AddSupplementViewModel,
        val activeClientManager: ActiveClientManager
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()

        // Đăng ký TimeZoneChangeReceiver
        timeZoneReceiver = TimeZoneChangeReceiver {
            homeViewModel?.refresh()
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
                        val db = Room.databaseBuilder(
                            applicationContext,
                            SupplementDatabase::class.java,
                            SupplementDatabase.DATABASE_NAME
                        )
                            .addMigrations(
                                SupplementDatabase.MIGRATION_2_3,
                                SupplementDatabase.MIGRATION_3_4,
                                SupplementDatabase.MIGRATION_4_5
                            )
                            .fallbackToDestructiveMigration()
                            .build()

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
    
    override fun onResume() {
        super.onResume()
        homeViewModel?.refreshNotificationSchedules()
        
        val prefs = applicationContext.getSharedPreferences("oak_settings", MODE_PRIVATE)
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
