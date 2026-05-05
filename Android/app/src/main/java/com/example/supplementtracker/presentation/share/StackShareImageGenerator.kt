package com.example.supplementtracker.presentation.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

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
        val width = (displayMetrics.density * 1080f).toInt()
        val rowHeight = (displayMetrics.density * 92f).toInt()
        val baseHeight = (displayMetrics.density * 520f).toInt()
        val height = max(baseHeight, (displayMetrics.density * 220f).toInt() + rowHeight * items.size.coerceAtMost(12))

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, width, height, isDark)
        drawGlassPanel(canvas, width, height, isDark)
        drawContent(canvas, width, height, items, isDark)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, isDark: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = if (isDark) {
            intArrayOf(0xFF120025.toInt(), 0xFF000000.toInt())
        } else {
            intArrayOf(0xFFEAF7FF.toInt(), 0xFFF1F8E9.toInt())
        }
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            colors[0],
            colors[1],
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawGlassPanel(canvas: Canvas, width: Int, height: Int, isDark: Boolean) {
        val density = width / 1080f
        val margin = 72f * density
        val radius = 54f * density

        val left = margin
        val top = margin
        val right = width - margin
        val bottom = height - margin
        val rect = RectF(left, top, right, bottom)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0x1AFFFFFF else 0x0A000000
        }
        canvas.drawRoundRect(rect, radius, radius, fill)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            color = 0x33FFFFFF
        }
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    private fun drawContent(canvas: Canvas, width: Int, height: Int, items: List<StackShareItem>, isDark: Boolean) {
        val density = width / 1080f
        val margin = 72f * density
        val x = margin + 40f * density
        var y = margin + 96f * density

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
            textSize = 64f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("OAK Healthy", x, y, titlePaint)

        y += 64f * density

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xB3FFFFFF.toInt() else 0xFF374151.toInt()
            textSize = 34f * density
        }
        canvas.drawText("My Stack", x, y, subtitlePaint)

        y += 72f * density

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xB3FFFFFF.toInt() else 0xFF6B7280.toInt()
            textSize = 30f * density
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
            textSize = 40f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        val dosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xB3FFFFFF.toInt() else 0xFF374151.toInt()
            textSize = 30f * density
        }

        val visible = items.take(12)
        visible.forEach { item ->
            canvas.drawText(item.time, x, y, timePaint)
            canvas.drawText(item.name, x + 160f * density, y, namePaint)
            y += 44f * density
            canvas.drawText(item.dose, x + 160f * density, y, dosePaint)
            y += 66f * density
        }

        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0x80FFFFFF.toInt() else 0xFF6B7280.toInt()
            textSize = 28f * density
        }
        canvas.drawText("oakhealthy.app", x, height - margin - 32f * density, watermarkPaint)
    }
}
