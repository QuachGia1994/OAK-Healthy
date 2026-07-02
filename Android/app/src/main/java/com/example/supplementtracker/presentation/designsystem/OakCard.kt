package com.example.supplementtracker.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OakCardVariant {
    Glass,
    Surface
}

@Composable
fun OakCard(
    modifier: Modifier = Modifier,
    variant: OakCardVariant = OakCardVariant.Glass,
    accent: Color? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    when (variant) {
        OakCardVariant.Glass -> {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val base = remember(isDark) { if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f) }
            val stroke = remember(isDark, accent) { (accent ?: Color.White).copy(alpha = if (isDark) 0.24f else 0.28f) }
            val highlight = remember(isDark, base) {
                if (isDark) listOf(base.copy(alpha = 0.22f), base) else listOf(base.copy(alpha = 0.74f), base)
            }
            Box(
                modifier = modifier
                    .background(Brush.linearGradient(highlight), shape)
                    .border(1.dp, stroke, shape)
                    .clip(shape)
                    .padding(contentPadding)
            ) {
                Column(content = content)
            }
        }
        OakCardVariant.Surface -> {
            val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            Card(
                modifier = modifier,
                shape = shape,
                border = border,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation)
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }
    }
}
