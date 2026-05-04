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

import android.content.Intent
import android.content.IntentFilter
import com.example.supplementtracker.receiver.TimeZoneChangeReceiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var timeZoneReceiver: TimeZoneChangeReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()
        
        // Khởi tạo Database và Repository (Trong thực tế nên dùng Hilt)
        val db = Room.databaseBuilder(
            applicationContext,
            SupplementDatabase::class.java,
            SupplementDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
        
        val repository = SupplementRepositoryImpl(db.supplementDao)
        
        // Khởi tạo ViewModels
        val homeViewModel = HomeViewModel(
            repository = repository
        )
        val historyViewModel = HistoryViewModel(repository)
        val addSupplementViewModel = AddSupplementViewModel(
            saveSupplementUseCase = SaveSupplementUseCase(repository),
            repository = repository,
            context = applicationContext
        )

        // Đăng ký TimeZoneChangeReceiver
        timeZoneReceiver = TimeZoneChangeReceiver {
            homeViewModel.refresh()
        }
        registerReceiver(timeZoneReceiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))

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
                        appTheme = appTheme,
                        onThemeChange = { appTheme = it }
                    )
                }
            }
        }
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
