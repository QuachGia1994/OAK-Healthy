package com.example.supplementtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.domain.usecase.GetAllSupplementsUseCase
import com.example.supplementtracker.domain.usecase.SaveSupplementUseCase
import com.example.supplementtracker.presentation.add_supplement.AddSupplementViewModel
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.presentation.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Khởi tạo Database và Repository (Trong thực tế nên dùng Hilt)
        val db = Room.databaseBuilder(
            applicationContext,
            SupplementDatabase::class.java,
            SupplementDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
        
        val repository = SupplementRepositoryImpl(db.supplementDao)
        
        // Khởi tạo ViewModels
        val homeViewModel = HomeViewModel(
            getAllSupplementsUseCase = GetAllSupplementsUseCase(repository),
            repository = repository
        )
        val historyViewModel = HistoryViewModel(repository)
        val addSupplementViewModel = AddSupplementViewModel(
            saveSupplementUseCase = SaveSupplementUseCase(repository),
            context = applicationContext
        )

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        homeViewModel = homeViewModel,
                        historyViewModel = historyViewModel,
                        addSupplementViewModel = addSupplementViewModel
                    )
                }
            }
        }
    }
}
