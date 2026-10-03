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
        // ARGB_8888 so the area outside the rounded card stays transparent
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
        // Layout is designed on a 120x120 box; centre that box vertically when the widget isn't square
        val top = (height - 120f * scale) / 2f
        fun y(units: Float) = top + units * scale

        // Status (top)
        val statusPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 8f * scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        StatusBadge.draw(canvas, data, data.statusText(), centerX, y(15f), statusPaint, scale)

        // Direction arrow in circle
        val arrowCircleRadius = 17f * scale
        val arrowCircleY = y(38f)

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
            textSize = 11f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("${data.directionCardinal} ${data.currentDirection.roundToInt()}°", centerX, y(69f), dirPaint)

        // Wind speed (large, colored) with "kts" inline, centred as one group
        val speedPaint = TextPaint().apply {
            color = getColorForSpeed(data.currentSpeed)
            textSize = 26f * scale
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val unitPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 11f * scale
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        val speedText = "%.1f".format(data.currentSpeed)
        val unitGap = 3f * scale
        val groupWidth = speedPaint.measureText(speedText) + unitGap + unitPaint.measureText("kts")
        val speedX = centerX - groupWidth / 2
        val speedY = y(96f)
        canvas.drawText(speedText, speedX, speedY, speedPaint)
        canvas.drawText("kts", speedX + speedPaint.measureText(speedText) + unitGap, speedY, unitPaint)

        // Gust info (bottom)
        val gustPaint = TextPaint().apply {
            color = COLOR_GUST
            textSize = 11f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("gust %.1f".format(data.currentGust), centerX, y(111f), gustPaint)
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
