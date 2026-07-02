package com.example.supplementtracker.presentation.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R

@Composable
fun OakLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    val shape = RoundedCornerShape(size * 0.22f)
    val background = remember {
        Brush.linearGradient(listOf(Color(0xFF22E58F), Color(0xFF06564A)))
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 14.dp, shape = shape)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_oak_logo),
            contentDescription = androidx.compose.ui.res.stringResource(com.example.supplementtracker.R.string.a11y_oak_logo),
            modifier = Modifier.size(size * 0.72f)
        )
    }
}

