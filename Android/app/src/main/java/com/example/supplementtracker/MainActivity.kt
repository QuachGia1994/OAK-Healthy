package com.example.supplementtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

class MainActivity : ComponentActivity() {
    private var timeZoneReceiver: TimeZoneChangeReceiver? = null
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()
        
        // Khởi tạo Database và Repository (Trong thực tế nên dùng Hilt)
        val db = Room.databaseBuilder(
            applicationContext,
            SupplementDatabase::class.java,
            SupplementDatabase.DATABASE_NAME
        )
            .addMigrations(SupplementDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
        
        val repository = SupplementRepositoryImpl(db.supplementDao)
        val activeClientManager = ActiveClientManager(applicationContext, repository)
        
        // Khởi tạo ViewModels
        homeViewModel = HomeViewModel(
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

        // Đăng ký TimeZoneChangeReceiver
        timeZoneReceiver = TimeZoneChangeReceiver {
            homeViewModel.refresh()
        }
        registerReceiver(timeZoneReceiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
        
        homeViewModel.refreshNotificationSchedules()

        setContent {
            var appTheme by rememberSaveable { mutableStateOf(AppTheme.SYSTEM) }
            val isDarkTheme = when (appTheme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        homeViewModel = homeViewModel,
                        historyViewModel = historyViewModel,
                        addSupplementViewModel = addSupplementViewModel,
                        activeClientManager = activeClientManager,
                        appTheme = appTheme,
                        onThemeChange = { appTheme = it }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        homeViewModel.refreshNotificationSchedules()
        
        val prefs = applicationContext.getSharedPreferences("oak_settings", MODE_PRIVATE)
        val enabled = prefs.getBoolean("isAutoSyncEnabled", false)
        if (!enabled) return
        
        val hosted = prefs.getString("cloudSyncHostedBinId", "").orEmpty().trim()
        val linked = prefs.getString("cloudSyncLinkedBinId", "").orEmpty().trim()
        val binId = if (hosted.isNotEmpty()) hosted else linked
        if (binId.isEmpty()) return
        
        Log.d("AutoSync", "☁️ Auto-Sync: Starting download...")
        homeViewModel.receiveData(binId)
    }

    override fun onDestroy() {
        super.onDestroy()
        timeZoneReceiver?.let { unregisterReceiver(it) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
