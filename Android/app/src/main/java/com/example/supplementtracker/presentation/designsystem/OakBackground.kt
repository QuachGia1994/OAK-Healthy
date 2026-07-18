package com.example.supplementtracker.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun oakBackgroundBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF071A1B), Color(0xFF06100F)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFF4FAF7), Color(0xFFEAF4F5)))
        }
    }
}

@Composable
fun OakBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val brush = oakBackgroundBrush()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush),
        content = content
    )
}
