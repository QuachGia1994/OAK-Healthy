package com.example.supplementtracker.presentation.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.supplementtracker.presentation.add_supplement.AddSupplementScreen
import com.example.supplementtracker.presentation.add_supplement.AddSupplementViewModel
import com.example.supplementtracker.presentation.coach.CoachOverviewScreen
import com.example.supplementtracker.presentation.demo.DemoPreviewScreen
import com.example.supplementtracker.presentation.home.HistoryScreen
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.presentation.home.HomeScreen
import com.example.supplementtracker.presentation.home.HomeViewModel

import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.DoseStatus
import com.example.supplementtracker.presentation.home.HomeUiState
import com.example.supplementtracker.presentation.home.MyStackListScreen
import com.example.supplementtracker.presentation.home.NotificationCheckScreen
import com.example.supplementtracker.presentation.home.SettingsScreen
import com.example.supplementtracker.presentation.home.UserGuideScreen
import com.example.supplementtracker.presentation.sync.SyncCenterScreen
import com.example.supplementtracker.presentation.onboarding.OnboardingScreen
import com.example.supplementtracker.presentation.monetization.PlanAccessScreen
import com.example.supplementtracker.service.CommercialFeature
import com.example.supplementtracker.service.EntitlementManager
import com.example.supplementtracker.service.EntitlementPolicy
import com.example.supplementtracker.service.GooglePlayBillingService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Context
import com.example.supplementtracker.service.OakPrefs

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

sealed class Screen(val route: String, val titleRes: Int, val icon: @Composable () -> Unit) {
    data object Home : Screen("home", R.string.nav_home, { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.a11y_home)) })
    data object History : Screen("history", R.string.nav_history, { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.a11y_history)) })
    data object Settings : Screen("settings", R.string.nav_settings, { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.a11y_settings)) })
    data object MyStack : Screen("my_stack", R.string.nav_stack, { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.a11y_stack)) })
    data object UserGuide : Screen("user_guide", R.string.settings_guide_title, { })
    data object NotificationCheck : Screen("notification_check", R.string.notification_check_title, { })
    data object AddSupplement : Screen("add_supplement", R.string.app_name, { })
    data object EditSupplement : Screen("edit_supplement/{id}", R.string.app_name, { })
    data object SyncCenter : Screen("sync_center", R.string.sync_center_title, { })
    data object PlanAccess : Screen("plan_access", R.string.plan_access_title, { })
    data object CoachOverview : Screen("coach_overview", R.string.coach_overview_title, { })
    data object DemoPreview : Screen("demo_preview", R.string.demo_preview_title, { })
    data object Onboarding : Screen("onboarding", R.string.app_name, { })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    historyViewModel: HistoryViewModel,
    addSupplementViewModel: AddSupplementViewModel,
    activeClientManager: ActiveClientManager,
    entitlementManager: EntitlementManager,
    billingService: GooglePlayBillingService,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { OakPrefs.get(context) }
    val entitlementSnapshot by entitlementManager.snapshot.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val allowedClientIds = remember(clientsRaw, entitlementSnapshot.plan) {
        val unique = clientsRaw.distinctBy { it.id }
        val allowed = entitlementManager.maxClients()?.let(unique::take) ?: unique
        allowed.map { it.id }
    }
    val cloudSyncAllowed = EntitlementPolicy.allows(
        entitlementSnapshot.plan,
        CommercialFeature.ENCRYPTED_CLOUD_SYNC
    )
    var hasCompletedOnboarding by rememberSaveable { mutableStateOf(prefs.getBoolean("hasCompletedOnboarding", false)) }

    fun refreshOnboardingFlag() {
        hasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding", false)
    }

    LaunchedEffect(Unit) {
        refreshOnboardingFlag()
    }

    LaunchedEffect(allowedClientIds, currentClientId) {
        if (currentClientId != null && currentClientId !in allowedClientIds) {
            activeClientManager.setCurrentClientId(allowedClientIds.firstOrNull())
        }
    }

    LaunchedEffect(cloudSyncAllowed) {
        if (!cloudSyncAllowed) {
            prefs.edit().putBoolean("isAutoSyncEnabled", false).apply()
            homeViewModel.stopAutoSync()
        } else if (prefs.getBoolean("isAutoSyncEnabled", false)) {
            homeViewModel.startAutoSync()
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshOnboardingFlag()
                homeViewModel.refresh()
                if (cloudSyncAllowed && prefs.getBoolean("isAutoSyncEnabled", false)) {
                    homeViewModel.startAutoSync()
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                homeViewModel.pauseAutoSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = remember { listOf(Screen.Home, Screen.MyStack, Screen.History) }
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val overdueCount by remember(homeUiState) {
        derivedStateOf {
            val success = homeUiState as? HomeUiState.Success ?: return@derivedStateOf 0
            var count = 0
            success.activeSupplements.values.forEach { items ->
                items.forEach { item ->
                    if (item.doseStatus == DoseStatus.MISSED) count += 1
                }
            }
            count
        }
    }

    val startDestination = if (hasCompletedOnboarding) Screen.Home.route else Screen.Onboarding.route
    val isMainTab = items.any { it.route == currentDestination?.route }
    val bottomBarScrollState = rememberOakBottomBarScrollState()

    LaunchedEffect(currentDestination?.route) {
        bottomBarScrollState.expand()
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (isMainTab && hasCompletedOnboarding) {
                OakBottomBar(
                    items = items,
                    currentRoute = currentDestination?.route,
                    overdueCount = overdueCount,
                    scrollState = bottomBarScrollState,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val shellModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .then(
                if (isMainTab && hasCompletedOnboarding) {
                    Modifier.nestedScroll(bottomBarScrollState.nestedScrollConnection)
                } else {
                    Modifier
                }
            )

        // Single NavHost owns the complete app back stack; shell chrome stays outside route content.
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = shellModifier
        ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        entitlementManager = entitlementManager,
                        onNavigateToAdd = { navController.navigate(Screen.AddSupplement.route) },
                        onNavigateToEdit = { id -> navController.navigate("edit_supplement/$id") },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
                composable(Screen.MyStack.route) {
                    MyStackListScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        onNavigateToAdd = { navController.navigate(Screen.AddSupplement.route) },
                        onNavigateToSyncCenter = { navController.navigate(Screen.SyncCenter.route) },
                        onNavigateToUserGuide = { navController.navigate(Screen.UserGuide.route) },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToPlanAccess = { navController.navigate(Screen.PlanAccess.route) }
                    )
                }
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        onDone = {
                            prefs.edit().putBoolean("hasCompletedOnboarding", true).apply()
                            refreshOnboardingFlag()
                        }
                    )
                }
                composable(Screen.NotificationCheck.route) {
                    NotificationCheckScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.SyncCenter.route) {
                    if (cloudSyncAllowed) {
                        SyncCenterScreen(
                            homeViewModel = homeViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        PlanAccessScreen(
                            entitlementManager = entitlementManager,
                            billingService = billingService,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(Screen.UserGuide.route) {
                    UserGuideScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        entitlementManager = entitlementManager,
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        onNavigateToNotificationCheck = { navController.navigate(Screen.NotificationCheck.route) },
                        onNavigateToPlanAccess = { navController.navigate(Screen.PlanAccess.route) },
                        onNavigateToCoachOverview = { navController.navigate(Screen.CoachOverview.route) },
                        onNavigateToDemoPreview = { navController.navigate(Screen.DemoPreview.route) },
                        onClose = { navController.popBackStack() }
                    )
                }
                composable(Screen.PlanAccess.route) {
                    PlanAccessScreen(
                        entitlementManager = entitlementManager,
                        billingService = billingService,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.CoachOverview.route) {
                    CoachOverviewScreen(
                        viewModel = historyViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenPlans = { navController.navigate(Screen.PlanAccess.route) }
                    )
                }
                composable(Screen.DemoPreview.route) {
                    DemoPreviewScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.AddSupplement.route) {
                    AddSupplementScreen(
                        viewModel = addSupplementViewModel,
                        supplementId = null,
                        onBack = { navController.popBackStack() },
                        onSave = {
                            addSupplementViewModel.saveSupplement {
                                navController.popBackStack()
                            }
                        }
                    )
                }
                composable(Screen.EditSupplement.route) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")
                    AddSupplementScreen(
                        viewModel = addSupplementViewModel,
                        supplementId = id,
                        onBack = { navController.popBackStack() },
                        onSave = {
                            addSupplementViewModel.saveSupplement {
                                navController.popBackStack()
                            }
                        }
                    )
                }
        }
    }
}
