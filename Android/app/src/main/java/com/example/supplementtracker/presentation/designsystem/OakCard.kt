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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OakCardVariant {
    Paper,
    Surface
}

@Composable
fun OakCard(
    modifier: Modifier = Modifier,
    variant: OakCardVariant = OakCardVariant.Paper,
    accent: Color? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    when (variant) {
        OakCardVariant.Paper -> PaperCard(modifier, accent, shape, contentPadding, content)
        OakCardVariant.Surface -> SurfaceCard(modifier, shape, contentPadding, elevation, content)
    }
}

@Composable
private fun PaperCard(
    modifier: Modifier,
    accent: Color?,
    shape: Shape,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit
) {
    val stroke = accent?.copy(alpha = 0.24f) ?: MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, stroke, shape)
            .clip(shape)
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SurfaceCard(
    modifier: Modifier,
    shape: Shape,
    contentPadding: PaddingValues,
    elevation: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.coerceAtMost(1.dp))
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
