package com.example.supplementtracker.presentation.share

import com.example.supplementtracker.presentation.designsystem.OakColors
import android.app.Activity
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
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.supplementtracker.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class StackShareItem(
    val name: String,
    val dose: String,
    val time: String
)

object StackShareImageGenerator {
    suspend fun generate(
        activity: Activity,
        context: Context,
        lifecycleOwner: LifecycleOwner,
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        compositionContext: CompositionContext,
        items: List<StackShareItem>,
        isDark: Boolean
    ): Bitmap {
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val desiredWidth = context.resources.displayMetrics.widthPixels

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setParentCompositionContext(compositionContext)
            alpha = 0f
        }

        rootView.addView(
            composeView,
            ViewGroup.LayoutParams(
                desiredWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        composeView.setContent {
            StackShareCapture(
                items = items,
                isDark = isDark
            )
        }

        return suspendCancellableCoroutine { continuation ->
            composeView.post {
                try {
                    val width = if (composeView.width > 0) composeView.width else desiredWidth
                    if (composeView.height <= 0) {
                        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        composeView.measure(widthSpec, heightSpec)
                        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
                    }

                    val height = if (composeView.height > 0) composeView.height else maxOf(composeView.measuredHeight, 2000)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val fallback = if (isDark) "#121212" else "#FFFFFF"
                    canvas.drawColor(android.graphics.Color.parseColor(fallback))
                    composeView.draw(canvas)
                    rootView.removeView(composeView)
                    composeView.disposeComposition()

                    if (continuation.isActive) {
                        continuation.resume(bitmap)
                    }
                } catch (e: Exception) {
                    rootView.removeView(composeView)
                    composeView.disposeComposition()
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

}

@Composable
private fun StackShareCapture(
    items: List<StackShareItem>,
    isDark: Boolean
) {
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(OakColors.ShareDarkStart, OakColors.ShareDarkEnd))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
    }

    val panelColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderColor = Color.White.copy(alpha = if (isDark) 0.20f else 0.12f)
    val titleColor = if (isDark) Color.White else OakColors.TextPrimary
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.70f) else OakColors.TextSecondary
    val tertiaryColor = if (isDark) Color.White.copy(alpha = 0.70f) else OakColors.TextTertiary
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
                text = stringResource(R.string.app_name),
                color = titleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.stack_share_my_stack),
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
                text = stringResource(R.string.stack_share_footer),
                color = tertiaryColor,
                fontSize = 12.sp
            )
        }
    }
}
