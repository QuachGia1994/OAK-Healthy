package com.example.supplementtracker.presentation.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StackShareItem(
    val name: String,
    val dose: String,
    val time: String
)

object StackShareImageGenerator {
    fun generate(
        context: Context,
        items: List<StackShareItem>,
        isDark: Boolean
    ): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val desiredWidth = (displayMetrics.density * 1080f).toInt()

        val composeView = ComposeView(context)
        composeView.layoutParams = ViewGroup.LayoutParams(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

        composeView.setContent {
            StackShareCapture(
                items = items,
                isDark = isDark
            )
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(desiredWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)
        check(composeView.measuredWidth > 0 && composeView.measuredHeight > 0) {
            "Invalid measured size: ${composeView.measuredWidth}x${composeView.measuredHeight}"
        }
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            composeView.measuredWidth,
            composeView.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)
        composeView.disposeComposition()

        return bitmap
    }
}

@Composable
private fun StackShareCapture(
    items: List<StackShareItem>,
    isDark: Boolean
) {
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
    }

    val panelColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderColor = Color.White.copy(alpha = if (isDark) 0.20f else 0.12f)
    val titleColor = if (isDark) Color.White else Color(0xFF111111)
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF374151)
    val tertiaryColor = if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF6B7280)
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(backgroundBrush)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(panelColor, shape)
                .border(0.5.dp, borderColor, shape)
                .padding(20.dp)
        ) {
            Text(
                text = "OAK Healthy",
                color = titleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "My Stack",
                color = secondaryColor,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.time,
                        color = tertiaryColor,
                        fontSize = 12.sp,
                        modifier = Modifier.width(64.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = titleColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.dose,
                            color = secondaryColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "oakhealthy.app",
                color = tertiaryColor,
                fontSize = 12.sp
            )
        }
    }
}
