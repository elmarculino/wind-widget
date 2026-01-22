package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Clean Material Design style wind widget renderer.
 * Frosted glass background with simple layout.
 */
class WindCleanRenderer(private val context: Context) {

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
        private const val COLOR_BG = 0xCC3A3D42.toInt()  // Semi-transparent dark gray
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_ARROW_BG = 0xFF505560.toInt()
        private const val COLOR_HIGHLIGHT = 0xFF22D3EE.toInt()  // Cyan for current time

        private const val MAX_LOCATION_LENGTH = 28
    }

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = height / 120f  // Base height for 4x2 clean widget

        // Draw frosted glass background
        drawBackground(canvas, width, height, scale)

        // Layout
        val paddingH = 16f * scale
        val paddingV = 14f * scale

        // Draw header (location + wind info)
        drawHeader(canvas, data, width, paddingH, paddingV, scale)

        // Draw time row with wind bars
        val barAreaTop = height - 42f * scale
        drawTimeBarRow(canvas, data, paddingH, width - paddingH, barAreaTop, height - paddingV, scale)

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

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int, paddingH: Float, paddingV: Float, scale: Float) {
        // Location name (left side)
        val locationPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 15f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val locationName = if (data.locationName.length > MAX_LOCATION_LENGTH) {
            data.locationName.take(MAX_LOCATION_LENGTH - 3) + "..."
        } else {
            data.locationName
        }
        canvas.drawText(locationName, paddingH, paddingV + 18f * scale, locationPaint)

        // Right side: direction arrow in circle + wind speed
        val rightX = width - paddingH

        // Wind speed text
        val speedPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val speedText = "%.1f kts".format(data.currentSpeed)
        canvas.drawText(speedText, rightX, paddingV + 20f * scale, speedPaint)

        // Direction arrow in circle
        val arrowCircleRadius = 16f * scale
        val arrowCircleX = rightX - speedPaint.measureText(speedText) - arrowCircleRadius - 12f * scale
        val arrowCircleY = paddingV + 12f * scale

        // Draw circle background
        val circlePaint = Paint().apply {
            color = COLOR_ARROW_BG
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(arrowCircleX, arrowCircleY, arrowCircleRadius, circlePaint)

        // Draw arrow inside circle
        drawDirectionArrow(canvas, arrowCircleX, arrowCircleY, data.currentDirection, arrowCircleRadius * 0.7f, scale)

        // Gust info (smaller, below speed)
        if (data.currentGust > data.currentSpeed * 1.1f) {
            val gustPaint = TextPaint().apply {
                color = COLOR_TEXT_SECONDARY
                textSize = 11f * scale
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            canvas.drawText("gust ${data.currentGust.roundToInt()} kts", rightX, paddingV + 34f * scale, gustPaint)
        }

        // Max speed info
        val maxPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 11f * scale
            isAntiAlias = true
        }
        val maxText = "max ${data.maxSpeed.roundToInt()} kts"
        canvas.drawText(maxText, paddingH, paddingV + 36f * scale, maxPaint)
    }

    private fun drawDirectionArrow(canvas: Canvas, cx: Float, cy: Float, direction: Float, size: Float, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_TEXT_PRIMARY
            strokeWidth = 2f * scale
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(direction)

        val halfSize = size
        val path = Path().apply {
            // Arrow pointing down (wind direction)
            moveTo(0f, -halfSize)
            lineTo(0f, halfSize)
            moveTo(-halfSize * 0.5f, halfSize * 0.3f)
            lineTo(0f, halfSize)
            lineTo(halfSize * 0.5f, halfSize * 0.3f)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawTimeBarRow(canvas: Canvas, data: WindData, left: Float, right: Float, top: Float, bottom: Float, scale: Float) {
        if (data.times.isEmpty() || data.speeds.isEmpty()) return

        val rowWidth = right - left

        // Show 6-8 time slots
        val slotCount = minOf(8, data.times.size)
        val step = maxOf(1, (data.times.size - 1) / (slotCount - 1))

        val slotWidth = rowWidth / slotCount
        val barHeight = 6f * scale
        val barTop = bottom - barHeight - 2f * scale
        val timeY = barTop - 6f * scale

        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 11f * scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val highlightTimePaint = TextPaint().apply {
            color = COLOR_HIGHLIGHT
            textSize = 11f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        for (i in 0 until slotCount) {
            val idx = minOf(i * step, data.times.size - 1)
            val slotCenterX = left + slotWidth * i + slotWidth / 2

            // Time label
            val timeStr = formatTime(data.times.getOrElse(idx) { "" })
            val isRecent = i >= slotCount - 2  // Highlight last 2 time slots
            canvas.drawText(timeStr, slotCenterX, timeY, if (isRecent) highlightTimePaint else timePaint)

            // Wind bar
            val speed = data.speeds.getOrElse(idx) { 0f }
            val barColor = getColorForSpeed(speed)
            val barPaint = Paint().apply {
                color = barColor
                isAntiAlias = true
            }

            val barLeft = slotCenterX - slotWidth * 0.4f
            val barRight = slotCenterX + slotWidth * 0.4f
            val cornerRadius = 3f * scale
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, bottom - 2f * scale), cornerRadius, cornerRadius, barPaint)
        }
    }

    private fun formatTime(isoTime: String): String {
        return try {
            val timePart = isoTime.split("T").getOrElse(1) { "00:00" }
            timePart.substring(0, 5)
        } catch (e: Exception) {
            ""
        }
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
