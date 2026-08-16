package com.example.supplementtracker

import android.content.Context
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.domain.usecase.SaveSupplementUseCase
import com.example.supplementtracker.presentation.add_supplement.AddSupplementViewModel
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.presentation.home.HomeViewModel
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.service.EntitlementManager
import com.example.supplementtracker.service.GooglePlayBillingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppDependencies(
    val homeViewModel: HomeViewModel,
    val historyViewModel: HistoryViewModel,
    val addSupplementViewModel: AddSupplementViewModel,
    val activeClientManager: ActiveClientManager,
    val entitlementManager: EntitlementManager,
    val billingService: GooglePlayBillingService
)

internal class AppDependenciesFactory(private val context: Context) {
    suspend fun create(): AppDependencies {
        val repository = withContext(Dispatchers.IO) {
            val database = SupplementDatabase.getInstance(context)
            SupplementRepositoryImpl(database.supplementDao)
        }
        val activeClientManager = ActiveClientManager(context, repository)
        val entitlementManager = EntitlementManager()
        val homeViewModel = HomeViewModel(context, repository, activeClientManager, entitlementManager)
        val historyViewModel = HistoryViewModel(repository, activeClientManager, entitlementManager)
        val addViewModel = AddSupplementViewModel(
            saveSupplementUseCase = SaveSupplementUseCase(repository),
            repository = repository,
            context = context,
            activeClientManager = activeClientManager,
            entitlementManager = entitlementManager
        )
        return AppDependencies(
            homeViewModel,
            historyViewModel,
            addViewModel,
            activeClientManager,
            entitlementManager,
            GooglePlayBillingService(context, entitlementManager)
        )
    }
}
