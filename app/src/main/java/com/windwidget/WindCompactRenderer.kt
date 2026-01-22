package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Compact 2x2 wind widget renderer.
 * Shows only direction, speed, and gust in a clean style.
 */
class WindCompactRenderer(private val context: Context) {

    companion object {
        // Wind speed color scale (knots -> color)
        private val WIND_COLORS = intArrayOf(
            0xFF4ADE80.toInt(),  // 0-5 kts: Green
            0xFF86EFAC.toInt(),  // 5-8 kts: Light green
            0xFF22D3EE.toInt(),  // 8-12 kts: Cyan
            0xFFFDE047.toInt(),  // 12-16 kts: Yellow
            0xFFFBBF24.toInt(),  // 16-20 kts: Amber
            0xFFF97316.toInt(),  // 20-25 kts: Orange
            0xFFEF4444.toInt(),  // 25-30 kts: Red
            0xFFA855F7.toInt(),  // 30-40 kts: Purple
            0xFF7C3AED.toInt()   // 40+ kts: Dark purple
        )

        private val WIND_THRESHOLDS = floatArrayOf(0f, 5f, 8f, 12f, 16f, 20f, 25f, 30f, 40f)

        // Colors
        private const val COLOR_BG = 0xCC3A3D42.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_ARROW_BG = 0xFF505560.toInt()
        private const val COLOR_GUST = 0xFFFF6B6B.toInt()
    }

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = minOf(width, height) / 120f

        // Draw background
        drawBackground(canvas, width, height, scale)

        // Draw content centered
        drawWindInfo(canvas, data, width, height, scale)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_BG
            isAntiAlias = true
        }
        val cornerRadius = 24f * scale
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    private fun drawWindInfo(canvas: Canvas, data: WindData, width: Int, height: Int, scale: Float) {
        val centerX = width / 2f
        val centerY = height / 2f

        // Direction arrow in circle (top center)
        val arrowCircleRadius = 28f * scale
        val arrowCircleY = centerY - 30f * scale

        val circlePaint = Paint().apply {
            color = COLOR_ARROW_BG
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, arrowCircleY, arrowCircleRadius, circlePaint)

        // Draw arrow
        drawDirectionArrow(canvas, centerX, arrowCircleY, data.currentDirection, arrowCircleRadius * 0.65f, scale)

        // Direction text (e.g., "E 74°") below arrow
        val dirPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 14f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("${data.directionCardinal} ${data.currentDirection.roundToInt()}°", centerX, arrowCircleY + arrowCircleRadius + 16f * scale, dirPaint)

        // Wind speed (large, colored)
        val speedColor = getColorForSpeed(data.currentSpeed)
        val speedPaint = TextPaint().apply {
            color = speedColor
            textSize = 28f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val speedY = centerY + 36f * scale
        canvas.drawText("%.1f".format(data.currentSpeed), centerX, speedY, speedPaint)

        // "kts" label
        val unitPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 12f * scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("kts", centerX, speedY + 14f * scale, unitPaint)

        // Gust info (bottom)
        val gustPaint = TextPaint().apply {
            color = COLOR_GUST
            textSize = 13f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("gust %.1f".format(data.currentGust), centerX, speedY + 32f * scale, gustPaint)
    }

    private fun drawDirectionArrow(canvas: Canvas, cx: Float, cy: Float, direction: Float, size: Float, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_TEXT_PRIMARY
            strokeWidth = 3f * scale
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(direction)

        val path = Path().apply {
            moveTo(0f, -size)
            lineTo(0f, size)
            moveTo(-size * 0.5f, size * 0.4f)
            lineTo(0f, size)
            lineTo(size * 0.5f, size * 0.4f)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun getColorForSpeed(speed: Float): Int {
        for (i in WIND_THRESHOLDS.indices.reversed()) {
            if (speed >= WIND_THRESHOLDS[i]) {
                return WIND_COLORS[i]
            }
        }
        return WIND_COLORS[0]
    }
}
