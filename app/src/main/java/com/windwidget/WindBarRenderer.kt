package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Horizontal wind bar renderer for 4x1 widget.
 * Displays a color-coded wind speed bar similar to Windy.app style.
 */
class WindBarRenderer(private val context: Context) {

    companion object {
        // Wind speed color scale (knots -> color)
        // Green (light) -> Yellow -> Orange -> Red -> Purple (strong)
        private val WIND_COLORS = intArrayOf(
            0xFF4ADE80.toInt(),  // 0-5 kts: Green
            0xFF86EFAC.toInt(),  // 5-8 kts: Light green
            0xFFFDE047.toInt(),  // 8-12 kts: Yellow
            0xFFFBBF24.toInt(),  // 12-16 kts: Amber
            0xFFF97316.toInt(),  // 16-20 kts: Orange
            0xFFEF4444.toInt(),  // 20-25 kts: Red
            0xFFDC2626.toInt(),  // 25-30 kts: Dark red
            0xFFA855F7.toInt(),  // 30-40 kts: Purple
            0xFF7C3AED.toInt()   // 40+ kts: Dark purple
        )

        private val WIND_THRESHOLDS = floatArrayOf(0f, 5f, 8f, 12f, 16f, 20f, 25f, 30f, 40f)

        // Colors
        private const val COLOR_BG = 0xF0101316.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_ARROW = 0xFFFFFFFF.toInt()
    }

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Calculate scale factor based on actual size
        val scale = height / 100f  // Base height is 100 units

        // Draw background with rounded corners
        val bgPaint = Paint().apply {
            color = COLOR_BG
            isAntiAlias = true
        }
        val cornerRadius = 20f * scale
        val bgRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        // Layout measurements (scaled)
        val paddingH = 20f * scale
        val paddingV = 8f * scale
        val barHeight = 16f * scale
        val barPaddingH = 28f * scale  // Extra padding for bar so time labels don't reach edges
        val barTop = height - paddingV - barHeight - 14f * scale
        val barBottom = barTop + barHeight

        // Draw location and time (top row)
        drawHeader(canvas, data, width, paddingH, paddingV, scale)

        // Draw current wind info (middle section) - more gap before bar
        drawCurrentWind(canvas, data, paddingH, paddingV + 18f * scale, barTop - 10f * scale, scale)

        // Draw wind bar (with extra horizontal padding)
        drawWindBar(canvas, data, barPaddingH, barTop, width - barPaddingH, barBottom, scale)

        // Draw time labels
        drawTimeLabels(canvas, data, barPaddingH, width - barPaddingH, barBottom + 2f * scale, scale)

        return bitmap
    }

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int, paddingH: Float, paddingV: Float, scale: Float) {
        // Location name (left)
        val locationPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 12f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val maxLocationWidth = width * 0.6f
        val locationText = ellipsize(data.locationName, locationPaint, maxLocationWidth)
        canvas.drawText(locationText, paddingH, paddingV + 14f * scale, locationPaint)

        // Current time (right)
        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 11f * scale
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        canvas.drawText(timeStr, width - paddingH, paddingV + 14f * scale, timePaint)
    }

    private fun drawCurrentWind(canvas: Canvas, data: WindData, left: Float, top: Float, bottom: Float, scale: Float) {
        val centerY = (top + bottom) / 2

        // Draw direction arrow
        val arrowSize = 16f * scale
        val arrowX = left + arrowSize / 2
        drawWindArrow(canvas, arrowX, centerY, data.currentDirection, arrowSize, scale)

        // Direction text (e.g., "ENE")
        val dirPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val dirX = arrowX + arrowSize / 2 + 8f * scale
        canvas.drawText(data.directionCardinal, dirX, centerY + 5f * scale, dirPaint)

        // Wind speed (large)
        val speedPaint = TextPaint().apply {
            color = getColorForSpeed(data.currentSpeed)
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val speedText = "%.1f".format(data.currentSpeed)
        val speedX = dirX + dirPaint.measureText(data.directionCardinal) + 12f * scale
        canvas.drawText(speedText, speedX, centerY + 6f * scale, speedPaint)

        // Unit
        val unitPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 12f * scale
            isAntiAlias = true
        }
        val unitX = speedX + speedPaint.measureText(speedText) + 4f * scale
        canvas.drawText("kts", unitX, centerY + 5f * scale, unitPaint)

        // Gust info (if significant)
        if (data.currentGust > data.currentSpeed * 1.15f) {
            val gustPaint = TextPaint().apply {
                color = 0xFFFF6B6B.toInt()
                textSize = 12f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val gustText = "G${data.currentGust.roundToInt()}"
            val gustX = unitX + unitPaint.measureText("kts") + 10f * scale
            canvas.drawText(gustText, gustX, centerY + 5f * scale, gustPaint)
        }
    }

    private fun drawWindArrow(canvas: Canvas, cx: Float, cy: Float, direction: Float, size: Float, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_ARROW
            strokeWidth = 2f * scale
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        canvas.save()
        canvas.translate(cx, cy)
        // Arrow points where wind GOES TO (no +180)
        canvas.rotate(direction)

        val halfSize = size / 2
        val path = Path().apply {
            moveTo(0f, -halfSize)
            lineTo(0f, halfSize)
            moveTo(-halfSize * 0.4f, halfSize * 0.4f)
            lineTo(0f, halfSize)
            lineTo(halfSize * 0.4f, halfSize * 0.4f)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawWindBar(canvas: Canvas, data: WindData, left: Float, top: Float, right: Float, bottom: Float, scale: Float) {
        if (data.speeds.isEmpty()) return

        val barWidth = right - left
        val segmentWidth = barWidth / data.speeds.size
        val cornerRadius = 6f * scale

        // Create clipping path for rounded corners
        val clipPath = Path().apply {
            addRoundRect(RectF(left, top, right, bottom), cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // Draw each segment with color based on wind speed
        data.speeds.forEachIndexed { i, speed ->
            val segLeft = left + i * segmentWidth
            val segRight = segLeft + segmentWidth + 1
            val color = getColorForSpeed(speed)

            val paint = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas.drawRect(segLeft, top, segRight, bottom, paint)
        }

        canvas.restore()

        // Draw subtle segment dividers
        val dividerPaint = Paint().apply {
            color = 0x30000000
            strokeWidth = 1f * scale
        }
        val step = maxOf(1, data.speeds.size / 12)
        for (i in data.speeds.indices step step) {
            if (i > 0) {
                val x = left + i * segmentWidth
                canvas.drawLine(x, top + 2f * scale, x, bottom - 2f * scale, dividerPaint)
            }
        }
    }

    private fun drawTimeLabels(canvas: Canvas, data: WindData, left: Float, right: Float, top: Float, scale: Float) {
        if (data.times.isEmpty()) return

        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f * scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val barWidth = right - left
        val labelCount = minOf(5, data.times.size)
        if (labelCount < 2) return

        val step = (data.times.size - 1) / (labelCount - 1)

        for (i in 0 until labelCount) {
            val idx = minOf(i * step, data.times.size - 1)
            val x = left + (barWidth * idx / (data.times.size - 1).coerceAtLeast(1))
            val timeStr = formatTime(data.times.getOrElse(idx) { "" })
            canvas.drawText(timeStr, x, top + 10f * scale, timePaint)
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

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated..."
    }
}
