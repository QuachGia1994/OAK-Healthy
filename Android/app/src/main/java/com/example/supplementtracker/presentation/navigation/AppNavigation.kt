package com.example.supplementtracker.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.supplementtracker.presentation.add_supplement.AddSupplementScreen
import com.example.supplementtracker.presentation.add_supplement.AddSupplementViewModel
import com.example.supplementtracker.presentation.home.HistoryScreen
import com.example.supplementtracker.presentation.home.HistoryViewModel
import com.example.supplementtracker.presentation.home.HomeScreen
import com.example.supplementtracker.presentation.home.HomeViewModel

import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.MyStackListScreen
import com.example.supplementtracker.presentation.home.NotificationCheckScreen
import com.example.supplementtracker.presentation.home.SettingsScreen
import com.example.supplementtracker.presentation.home.UserGuideScreen
import com.example.supplementtracker.presentation.sync.SyncCenterScreen
import com.example.supplementtracker.presentation.onboarding.OnboardingScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Context

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

sealed class Screen(val route: String, val titleRes: Int, val icon: @Composable () -> Unit) {
    data object Home : Screen("home", R.string.nav_home, { Icon(Icons.Default.Home, contentDescription = null) })
    data object History : Screen("history", R.string.nav_history, { Icon(Icons.Default.DateRange, contentDescription = null) })
    data object Settings : Screen("settings", R.string.nav_settings, { Icon(Icons.Default.Settings, contentDescription = null) })
    data object MyStack : Screen("my_stack", R.string.manage_stack, { })
    data object UserGuide : Screen("user_guide", R.string.settings_guide_title, { })
    data object NotificationCheck : Screen("notification_check", R.string.notification_check_title, { })
    data object AddSupplement : Screen("add_supplement", R.string.app_name, { })
    data object EditSupplement : Screen("edit_supplement/{id}", R.string.app_name, { })
    data object SyncCenter : Screen("sync_center", R.string.sync_center_title, { })
}

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    historyViewModel: HistoryViewModel,
    addSupplementViewModel: AddSupplementViewModel,
    activeClientManager: ActiveClientManager,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE) }
    var hasCompletedOnboarding by rememberSaveable { mutableStateOf(prefs.getBoolean("hasCompletedOnboarding", false)) }

    fun refreshOnboardingFlag() {
        hasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding", false)
    }

    LaunchedEffect(Unit) {
        refreshOnboardingFlag()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshOnboardingFlag()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = listOf(Screen.Home, Screen.History, Screen.Settings)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navContainerColor = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.70f)
    }

    Scaffold(
        bottomBar = {
            val isBottomTab = items.any { it.route == currentDestination?.route }
            if (isBottomTab && hasCompletedOnboarding) {
                NavigationBar(
                    containerColor = navContainerColor,
                    tonalElevation = 0.dp
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = screen.icon,
                            label = { Text(stringResource(screen.titleRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
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
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        onNavigateToAdd = { navController.navigate(Screen.AddSupplement.route) },
                        onNavigateToEdit = { id -> navController.navigate("edit_supplement/$id") }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        viewModel = historyViewModel
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        onNavigateToStackManager = { navController.navigate(Screen.MyStack.route) },
                        onNavigateToUserGuide = { navController.navigate(Screen.UserGuide.route) },
                        onNavigateToSyncCenter = { navController.navigate(Screen.SyncCenter.route) },
                        onNavigateToNotificationCheck = { navController.navigate(Screen.NotificationCheck.route) }
                    )
                }
                composable(Screen.NotificationCheck.route) {
                    NotificationCheckScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.SyncCenter.route) {
                    SyncCenterScreen(
                        homeViewModel = homeViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.MyStack.route) {
                    MyStackListScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.UserGuide.route) {
                    UserGuideScreen(
                        onBack = { navController.popBackStack() }
                    )
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

            if (!hasCompletedOnboarding) {
                OnboardingScreen(
                    homeViewModel = homeViewModel,
                    activeClientManager = activeClientManager,
                    onDone = {
                        prefs.edit().putBoolean("hasCompletedOnboarding", true).apply()
                        refreshOnboardingFlag()
                    }
                )
            }
        }
    }
}
