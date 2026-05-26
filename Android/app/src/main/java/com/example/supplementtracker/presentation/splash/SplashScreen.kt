package com.example.supplementtracker.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import com.example.supplementtracker.presentation.designsystem.OakLogoMark
import kotlinx.coroutines.delay

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
        OakLogoMark()
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}
