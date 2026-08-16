package com.example.supplementtracker

import android.content.Context
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.domain.usecase.SaveSupplementUseCase
import com.example.supplementtracker.presentation.add_supplement.AddSupplementViewModel
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppDependencies(
    val homeViewModel: HomeViewModel,
    val historyViewModel: HistoryViewModel,
    val addSupplementViewModel: AddSupplementViewModel,
    val activeClientManager: ActiveClientManager
)

internal class AppDependenciesFactory(private val context: Context) {
    suspend fun create(): AppDependencies {
        val repository = withContext(Dispatchers.IO) {
            val database = SupplementDatabase.getInstance(context)
            SupplementRepositoryImpl(database.supplementDao)
        }
        val activeClientManager = ActiveClientManager(context, repository)
        val homeViewModel = HomeViewModel(context, repository, activeClientManager)
        val historyViewModel = HistoryViewModel(repository, activeClientManager)
        val addViewModel = AddSupplementViewModel(
            saveSupplementUseCase = SaveSupplementUseCase(repository),
            repository = repository,
            context = context,
            activeClientManager = activeClientManager
        )
        return AppDependencies(homeViewModel, historyViewModel, addViewModel, activeClientManager)
    }
}
