package com.example.supplementtracker.presentation.designsystem

import android.provider.Settings
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class OakAdaptiveLayout(
    val compactWidth: Boolean,
    val wideWidth: Boolean,
    val largeText: Boolean,
    val horizontalPadding: Dp,
    val stackMetrics: Boolean
)

@Composable
fun rememberOakAdaptiveLayout(): OakAdaptiveLayout {
    val width = LocalConfiguration.current.screenWidthDp
    val fontScale = LocalDensity.current.fontScale
    return remember(width, fontScale) {
        OakAdaptiveLayout(
            compactWidth = width <= 360,
            wideWidth = width >= 600,
            largeText = fontScale >= 1.3f,
            horizontalPadding = if (width <= 360) 12.dp else if (width >= 600) 28.dp else 16.dp,
            stackMetrics = width <= 360 || fontScale >= 1.3f
        )
    }
}

@Composable
fun rememberOakReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

fun Modifier.oakTouchTarget(): Modifier = sizeIn(minWidth = 48.dp, minHeight = 48.dp)
