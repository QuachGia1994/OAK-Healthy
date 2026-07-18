package com.example.supplementtracker.presentation.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.supplementtracker.presentation.designsystem.OakLogoMark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: (() -> Unit)? = null,
    autoFinish: Boolean = true,
    minDurationMillis: Long = 1_350L
) {
    val transition = rememberInfiniteTransition(label = "oak-loading")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_700, easing = FastOutSlowInEasing)),
        label = "growth-ring"
    )
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "loading-track"
    )

    LaunchedEffect(autoFinish, minDurationMillis, onFinished) {
        val finish = onFinished ?: return@LaunchedEffect
        if (!autoFinish) return@LaunchedEffect
        delay(minDurationMillis)
        finish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF165B58), Color(0xFF082F34)),
                    radius = 1_050f
                )
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GrowthRings(pulse = pulse)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "OAK HEALTHY",
            color = Color(0xFFFFF8E9),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(16.dp))
        LoadingTrack(progress = progress)
    }
}

@Composable
private fun GrowthRings(pulse: Float) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(184.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(3) { index ->
                val phase = (pulse + index / 3f) % 1f
                drawCircle(
                    color = Color(0xFF6FD9B8).copy(alpha = (1f - phase) * 0.32f),
                    radius = size.minDimension * (0.28f + phase * 0.22f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        OakLogoMark(size = 108.dp)
    }
}

@Composable
private fun LoadingTrack(progress: Float) {
    Canvas(Modifier.width(96.dp).height(4.dp)) {
        val y = size.height / 2
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        val start = (progress * 1.3f - 0.3f).coerceIn(0f, 1f) * size.width
        val end = (start + size.width * 0.3f).coerceAtMost(size.width)
        drawLine(Color(0xFF71DDBA), Offset(start, y), Offset(end, y), size.height, StrokeCap.Round)
    }
}
