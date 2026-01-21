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
        private const val COLOR_BG = 0xE6101316.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_ARROW = 0xFFFFFFFF.toInt()
    }

    private val density = context.resources.displayMetrics.density

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        val bgPaint = Paint().apply { color = COLOR_BG }
        val bgRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, 20f * density, 20f * density, bgPaint)

        // Layout constants
        val paddingH = 12f * density
        val paddingV = 8f * density
        val barHeight = 24f * density
        val barTop = height - paddingV - barHeight - 14f * density  // Leave room for time labels
        val barBottom = barTop + barHeight

        // Draw location and time (top row)
        drawHeader(canvas, data, width, paddingH, paddingV)

        // Draw current wind info (middle section)
        drawCurrentWind(canvas, data, paddingH, paddingV + 18f * density, barTop - 4f * density)

        // Draw wind bar
        drawWindBar(canvas, data, paddingH, barTop, width - paddingH, barBottom)

        // Draw time labels
        drawTimeLabels(canvas, data, paddingH, width - paddingH, barBottom + 2f * density)

        return bitmap
    }

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int, paddingH: Float, paddingV: Float) {
        // Location name (left)
        val locationPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val maxLocationWidth = width * 0.6f
        val locationText = ellipsize(data.locationName, locationPaint, maxLocationWidth)
        canvas.drawText(locationText, paddingH, paddingV + 12f * density, locationPaint)

        // Current time (right)
        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f * density
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        canvas.drawText(timeStr, width - paddingH, paddingV + 12f * density, timePaint)
    }

    private fun drawCurrentWind(canvas: Canvas, data: WindData, left: Float, top: Float, bottom: Float) {
        val centerY = (top + bottom) / 2

        // Draw direction arrow
        val arrowSize = 16f * density
        val arrowX = left + arrowSize / 2 + 4f * density
        drawWindArrow(canvas, arrowX, centerY, data.currentDirection, arrowSize)

        // Direction text (e.g., "ENE")
        val dirPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 13f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val dirX = arrowX + arrowSize / 2 + 8f * density
        canvas.drawText(data.directionCardinal, dirX, centerY + 5f * density, dirPaint)

        // Wind speed
        val speedPaint = TextPaint().apply {
            color = getColorForSpeed(data.currentSpeed)
            textSize = 18f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val speedText = "%.1f".format(data.currentSpeed)
        val speedX = dirX + dirPaint.measureText(data.directionCardinal) + 12f * density
        canvas.drawText(speedText, speedX, centerY + 6f * density, speedPaint)

        // Unit
        val unitPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 11f * density
            isAntiAlias = true
        }
        val unitX = speedX + speedPaint.measureText(speedText) + 4f * density
        canvas.drawText("kts", unitX, centerY + 5f * density, unitPaint)

        // Gust info (if significant)
        if (data.currentGust > data.currentSpeed * 1.2f) {
            val gustPaint = TextPaint().apply {
                color = COLOR_TEXT_SECONDARY
                textSize = 10f * density
                isAntiAlias = true
            }
            val gustText = "▲${data.currentGust.roundToInt()}"
            val gustX = unitX + unitPaint.measureText("kts") + 8f * density
            canvas.drawText(gustText, gustX, centerY + 4f * density, gustPaint)
        }
    }

    private fun drawWindArrow(canvas: Canvas, cx: Float, cy: Float, direction: Float, size: Float) {
        val paint = Paint().apply {
            color = COLOR_ARROW
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        canvas.save()
        canvas.translate(cx, cy)
        // Rotate: direction is where wind comes FROM, arrow points in that direction
        canvas.rotate(direction + 180)

        val halfSize = size / 2
        val path = Path().apply {
            // Arrow shaft
            moveTo(0f, -halfSize)
            lineTo(0f, halfSize)
            // Arrow head
            moveTo(-halfSize * 0.4f, halfSize * 0.4f)
            lineTo(0f, halfSize)
            lineTo(halfSize * 0.4f, halfSize * 0.4f)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawWindBar(canvas: Canvas, data: WindData, left: Float, top: Float, right: Float, bottom: Float) {
        if (data.speeds.isEmpty()) return

        val barWidth = right - left
        val segmentWidth = barWidth / data.speeds.size
        val cornerRadius = 6f * density

        // Create clipping path for rounded corners
        val clipPath = Path().apply {
            addRoundRect(RectF(left, top, right, bottom), cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // Draw each segment with color based on wind speed
        data.speeds.forEachIndexed { i, speed ->
            val segLeft = left + i * segmentWidth
            val segRight = segLeft + segmentWidth + 1  // +1 to avoid gaps
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
            color = 0x20FFFFFF
            strokeWidth = 1f
        }
        val step = maxOf(1, data.speeds.size / 12)  // Show ~12 dividers max
        for (i in data.speeds.indices step step) {
            if (i > 0) {
                val x = left + i * segmentWidth
                canvas.drawLine(x, top + 2f * density, x, bottom - 2f * density, dividerPaint)
            }
        }
    }

    private fun drawTimeLabels(canvas: Canvas, data: WindData, left: Float, right: Float, top: Float) {
        if (data.times.isEmpty()) return

        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val barWidth = right - left
        val labelCount = minOf(6, data.times.size)
        val step = maxOf(1, (data.times.size - 1) / (labelCount - 1))

        for (i in 0 until labelCount) {
            val idx = minOf(i * step, data.times.size - 1)
            val x = left + (barWidth * idx / (data.times.size - 1).coerceAtLeast(1))
            val timeStr = formatTime(data.times.getOrElse(idx) { "" })
            canvas.drawText(timeStr, x, top + 10f * density, timePaint)
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
