package com.example.supplementtracker.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.supplementtracker.presentation.designsystem.OakLogoMark
import com.example.supplementtracker.presentation.designsystem.rememberOakReduceMotion
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: (() -> Unit)? = null,
    autoFinish: Boolean = true,
    minDurationMillis: Long = 1_350L
) {
    val reduceMotion = rememberOakReduceMotion()
    val progress = remember { Animatable(0f) }
    val duration = minDurationMillis.coerceIn(400L, 2_500L).toInt()

    LaunchedEffect(reduceMotion, autoFinish, minDurationMillis, onFinished) {
        progress.snapTo(if (reduceMotion) 1f else 0f)
        if (reduceMotion) {
            delay(minDurationMillis.coerceAtLeast(0L))
        } else {
            progress.animateTo(1f, tween(durationMillis = duration, easing = FastOutSlowInEasing))
        }
        if (autoFinish) onFinished?.invoke()
    }

    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GrowthRings(accent)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "OAK HEALTHY",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(16.dp))
        LoadingTrack(progress.value, MaterialTheme.colorScheme.outlineVariant, accent)
    }
}

@Composable
private fun GrowthRings(accent: Color) {
    Box(modifier = Modifier.size(184.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            listOf(0.31f, 0.39f, 0.47f).forEachIndexed { index, fraction ->
                drawCircle(
                    color = accent.copy(alpha = 0.22f - index * 0.045f),
                    radius = size.minDimension * fraction,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        OakLogoMark(size = 108.dp)
    }
}

@Composable
private fun LoadingTrack(progress: Float, trackColor: Color, accent: Color) {
    Canvas(Modifier.width(96.dp).height(4.dp)) {
        val y = size.height / 2
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        drawLine(
            accent,
            Offset(0f, y),
            Offset(size.width * progress.coerceIn(0f, 1f), y),
            size.height,
            StrokeCap.Round
        )
    }
}
