package com.example.supplementtracker.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.home.DoseStatus
import com.example.supplementtracker.presentation.home.HomeUiState
import com.example.supplementtracker.presentation.home.MyStackListScreen
import com.example.supplementtracker.presentation.home.NotificationCheckScreen
import com.example.supplementtracker.presentation.home.SettingsScreen
import com.example.supplementtracker.presentation.home.UserGuideScreen
import com.example.supplementtracker.presentation.sync.SyncCenterScreen
import com.example.supplementtracker.presentation.onboarding.OnboardingScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    data object MyStack : Screen("my_stack", R.string.nav_stack, { Icon(Icons.Default.List, contentDescription = null) })
    data object UserGuide : Screen("user_guide", R.string.settings_guide_title, { })
    data object NotificationCheck : Screen("notification_check", R.string.notification_check_title, { })
    data object AddSupplement : Screen("add_supplement", R.string.app_name, { })
    data object EditSupplement : Screen("edit_supplement/{id}", R.string.app_name, { })
    data object SyncCenter : Screen("sync_center", R.string.sync_center_title, { })
    data object Onboarding : Screen("onboarding", R.string.app_name, { })
}

@OptIn(ExperimentalMaterial3Api::class)
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
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshOnboardingFlag()
                homeViewModel.refresh()
                if (prefs.getBoolean("isAutoSyncEnabled", false)) {
                    homeViewModel.startAutoSync()
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                homeViewModel.stopAutoSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = listOf(Screen.Home, Screen.MyStack, Screen.History)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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

    Scaffold(
        bottomBar = {
            val isBottomTab = items.any { it.route == currentDestination?.route }
            if (isBottomTab && hasCompletedOnboarding) {
                OakBottomBar(
                    items = items,
                    currentRoute = currentDestination?.route,
                    overdueCount = overdueCount,
                    isDark = isDark,
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
        Box(modifier = Modifier.padding(innerPadding)) {
            val startDestination = if (hasCompletedOnboarding) Screen.Home.route else Screen.Onboarding.route
            NavHost(navController = navController, startDestination = startDestination) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        homeViewModel = homeViewModel,
                        activeClientManager = activeClientManager,
                        onDone = {
                            prefs.edit().putBoolean("hasCompletedOnboarding", true).apply()
                            refreshOnboardingFlag()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        activeClientManager = activeClientManager,
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
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
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
                    SyncCenterScreen(
                        homeViewModel = homeViewModel,
                        onBack = { navController.popBackStack() }
                    )
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
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        onNavigateToNotificationCheck = { navController.navigate(Screen.NotificationCheck.route) },
                        onClose = { navController.popBackStack() }
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
}

@Composable
private fun OakBottomBar(
    items: List<Screen>,
    currentRoute: String?,
    overdueCount: Int,
    isDark: Boolean,
    onTabSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.72f),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 0.dp,
        shadowElevation = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { screen ->
                val selected = currentRoute == screen.route
                OakBottomBarItem(
                    title = stringResource(screen.titleRes),
                    selected = selected,
                    badgeCount = if (screen == Screen.Home) overdueCount else 0,
                    onClick = { onTabSelected(screen.route) },
                    icon = screen.icon
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.OakBottomBarItem(
    title: String,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "oakBottomBarPress"
    )
    val pillShape = RoundedCornerShape(28.dp)
    val pillBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
            Color.White.copy(alpha = 0.14f)
        )
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(pillShape)
            .background(
                if (selected) pillBrush else Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.Transparent)
                )
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = pillShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (selected) 12.dp else 10.dp, vertical = if (selected) 13.dp else 11.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                CompositionLocalProvider(
                    LocalContentColor provides if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                if (badgeCount > 0) {
                    OakBottomBadge(count = badgeCount)
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-8).dp)
                }
            }
            Text(
                text = title,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OakBottomBadge(count: Int) {
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFF5A5F), Color(0xFFFF9F43))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = if (text.length > 2) 6.dp else 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}
