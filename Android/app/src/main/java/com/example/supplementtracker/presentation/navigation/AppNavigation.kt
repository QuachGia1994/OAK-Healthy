package com.example.supplementtracker.presentation.navigation

import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.SettingsScreen

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

sealed class Screen(val route: String, val titleRes: Int, val icon: @Composable () -> Unit) {
    data object Home : Screen("home", R.string.nav_home, { Icon(Icons.Default.Home, contentDescription = null) })
    data object History : Screen("history", R.string.nav_history, { Icon(Icons.Default.DateRange, contentDescription = null) })
    data object Settings : Screen("settings", R.string.nav_settings, { Icon(Icons.Default.Settings, contentDescription = null) })
    data object AddSupplement : Screen("add_supplement", R.string.app_name, { })
    data object EditSupplement : Screen("edit_supplement/{id}", R.string.app_name, { })
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

    val items = listOf(Screen.Home, Screen.History, Screen.Settings)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navContainerColor = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.70f)
    }

    Scaffold(
        bottomBar = {
            if (currentDestination?.route != Screen.AddSupplement.route) {
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
        NavHost(navController = navController, startDestination = Screen.Home.route, modifier = Modifier.padding(innerPadding)) {
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
                    onThemeChange = onThemeChange
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
    }
}
