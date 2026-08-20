package com.example.supplementtracker.presentation.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.presentation.designsystem.rememberOakReduceMotion

@Stable
class OakBottomBarScrollState internal constructor(
    private val thresholdPx: Float
) {
    var compact by mutableStateOf(false)
        private set

    private var accumulatedY = 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.Drag || available.y == 0f) return Offset.Zero
            accumulatedY = when {
                available.y < 0f -> minOf(0f, accumulatedY) + available.y
                else -> maxOf(0f, accumulatedY) + available.y
            }
            if (accumulatedY <= -thresholdPx) {
                compact = true
                accumulatedY = 0f
            } else if (accumulatedY >= thresholdPx) {
                compact = false
                accumulatedY = 0f
            }
            return Offset.Zero
        }
    }

    fun expand() {
        compact = false
        accumulatedY = 0f
    }
}

@Composable
fun rememberOakBottomBarScrollState(): OakBottomBarScrollState {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    return remember(thresholdPx) { OakBottomBarScrollState(thresholdPx) }
}

@Composable
fun OakBottomBar(
    items: List<Screen>,
    currentRoute: String?,
    overdueCount: Int,
    scrollState: OakBottomBarScrollState,
    onTabSelected: (String) -> Unit
) {
    val reduceMotion = rememberOakReduceMotion()
    val targetHeight = if (scrollState.compact) 56.dp else 80.dp
    val contentHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 180),
        label = "oak-bottom-bar-height"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                NavigationBar(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .height(contentHeight),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    items.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    scrollState.expand()
                                    onTabSelected(screen.route)
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (screen == Screen.Home && overdueCount > 0) {
                                            Badge {
                                                Text(if (overdueCount > 99) "99+" else overdueCount.toString())
                                            }
                                        }
                                    }
                                ) { screen.icon() }
                            },
                            label = if (scrollState.compact) {
                                null
                            } else {
                                { Text(stringResource(screen.titleRes), maxLines = 1) }
                            },
                            alwaysShowLabel = !scrollState.compact,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}
