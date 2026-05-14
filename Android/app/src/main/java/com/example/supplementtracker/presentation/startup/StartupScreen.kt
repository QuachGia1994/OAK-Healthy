package com.example.supplementtracker.presentation.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(onNavigateToHome: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1_500)
        onNavigateToHome()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_oak_logo),
            contentDescription = null,
            modifier = Modifier.size(110.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "OAK Healthy",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(18.dp))
        CircularProgressIndicator(color = Color(0xFF4CAF50))
    }
}
