package com.example.supplementtracker.presentation.splash

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SplashScreen(
    onFinished: (() -> Unit)? = null,
    autoFinish: Boolean = true,
    minDurationMillis: Long = 1100
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundBrush = androidx.compose.runtime.remember(isDark) {
        if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
        } else {
            Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
        }
    }

    LaunchedEffect(autoFinish, minDurationMillis, onFinished) {
        if (!autoFinish) return@LaunchedEffect
        val action = onFinished ?: return@LaunchedEffect
        delay(minDurationMillis)
        action()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LetterStormLogo(
            word = "OAK HEALTHY",
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .height(240.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

private data class StormParticle(
    val char: Char,
    val seedA: Float,
    val seedB: Float,
    val speed: Float,
    val radius: Float
)

@Composable
private fun LetterStormLogo(
    word: String,
    modifier: Modifier = Modifier,
    particleCount: Int = 220,
    loopMillis: Int = 5200
) {
    val w = word.trim().ifEmpty { "OAK HEALTHY" }
    val density = LocalDensity.current
    val targets = remember(w) { buildWordTargets(w) }
    val particles = remember(w, particleCount) {
        val rnd = Random(421337)
        List(particleCount) { idx ->
            val pool = (w.filter { !it.isWhitespace() }.ifEmpty { "OAKHEALTHY" })
            val c = pool[idx % pool.length]
            StormParticle(
                char = c,
                seedA = rnd.nextFloat() * 10_000f,
                seedB = rnd.nextFloat() * 10_000f,
                speed = 0.6f + rnd.nextFloat() * 1.4f,
                radius = 0.12f + rnd.nextFloat() * 0.42f
            )
        }
    }

    val transition = rememberInfiniteTransition()
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(loopMillis, easing = LinearEasing))
    )

    val particlePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val wordPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)

        val stormEnd = 0.56f
        val alignEnd = 0.76f
        val holdEnd = 0.88f

        val alignProgress = when {
            t < stormEnd -> 0f
            t < alignEnd -> smoothstep((t - stormEnd) / (alignEnd - stormEnd))
            t < holdEnd -> 1f
            else -> 1f - smoothstep((t - holdEnd) / (1f - holdEnd))
        }

        val wordAlpha = when {
            t < stormEnd -> 0f
            t < alignEnd -> smoothstep((t - stormEnd) / (alignEnd - stormEnd))
            t < holdEnd -> 1f
            else -> 1f - smoothstep((t - holdEnd) / (1f - holdEnd))
        }

        val particleAlpha = 1f - (wordAlpha * 0.55f)

        val particleSizePx = with(density) { 10.sp.toPx() }
        val wordSizePx = (size.minDimension * 0.13f).coerceAtLeast(with(density) { 22.sp.toPx() })

        particlePaint.textSize = particleSizePx
        wordPaint.textSize = wordSizePx

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas

            for (i in particles.indices) {
                val p = particles[i]
                val angle = ((p.seedA * 0.001f) + t * p.speed * 2f) * (2f * PI.toFloat())
                val wobble = 0.08f * sin((t * 7f + p.seedB * 0.0007f) * (2f * PI.toFloat()))
                val r = (p.radius + wobble).coerceIn(0.05f, 0.55f)

                val stormX = cos(angle) * r
                val stormY = sin(angle * 1.03f) * r * 0.72f

                val target = targets[i % targets.size]
                val targetX = target.x * 0.92f
                val targetY = target.y * 0.30f

                val xN = lerp(stormX, targetX, alignProgress)
                val yN = lerp(stormY, targetY, alignProgress)

                val x = center.x + xN * size.width
                val y = center.y + yN * size.height

                val a = (particleAlpha * 255f).toInt().coerceIn(0, 255)
                particlePaint.alpha = a
                nc.drawText(p.char.toString(), x, y, particlePaint)
            }

            if (wordAlpha > 0f) {
                wordPaint.alpha = (wordAlpha * 255f).toInt().coerceIn(0, 255)
                val fm = wordPaint.fontMetrics
                val y = center.y - (fm.ascent + fm.descent) / 2f
                nc.drawText(w, center.x, y, wordPaint)
            }
        }
    }
}

private fun buildWordTargets(word: String): List<Offset> {
    val text = word.trim().ifEmpty { "OAK HEALTHY" }
    val width = 1000
    val height = 260
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 170f
    }
    val fm = paint.fontMetrics
    val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText(text, width / 2f, baseline, paint)

    val points = ArrayList<Offset>(1800)
    val step = 8
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            val a = (bitmap.getPixel(x, y) ushr 24) and 0xFF
            if (a > 18) {
                points.add(
                    Offset(
                        (x - width / 2f) / width.toFloat(),
                        (y - height / 2f) / height.toFloat()
                    )
                )
            }
        }
    }
    return if (points.isNotEmpty()) points else listOf(Offset.Zero)
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
