package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Clean Material Design style wind widget renderer.
 * Similar to stock/finance widget with line chart.
 */
class WindCleanRenderer(private val context: Context) {

    companion object {
        // Colors
        private const val COLOR_BG = 0xCC3A3D42.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_ARROW_BG = 0xFF505560.toInt()
        private const val COLOR_CHART_LINE = 0xFFFFFFFF.toInt()
        private const val COLOR_CHART_FILL_TOP = 0x40FFFFFF.toInt()
        private const val COLOR_CHART_FILL_BOTTOM = 0x00FFFFFF.toInt()
        private const val COLOR_GUST = 0xFF4ADE80.toInt()

        private const val MAX_LOCATION_LENGTH = 22
    }

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = height / 140f

        // Draw background
        drawBackground(canvas, width, height, scale)

        val paddingH = 14f * scale
        val paddingV = 10f * scale

        // Header: location name (left), last update time (right)
        drawHeader(canvas, data, width, paddingH, paddingV, scale)

        // Main content: large speed + direction arrow
        drawMainContent(canvas, data, width, paddingH, paddingV + 24f * scale, scale)

        // Line chart at bottom
        val chartTop = height - 45f * scale
        val chartBottom = height - 8f * scale
        drawLineChart(canvas, data, paddingH, chartTop, width - paddingH, chartBottom, scale)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_BG
            isAntiAlias = true
        }
        val cornerRadius = 20f * scale
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, paint)
    }

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int, paddingH: Float, paddingV: Float, scale: Float) {
        // Location name (left)
        val locationPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 12f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val locationName = if (data.locationName.length > MAX_LOCATION_LENGTH) {
            data.locationName.take(MAX_LOCATION_LENGTH - 3) + "..."
        } else {
            data.locationName
        }
        canvas.drawText(locationName, paddingH, paddingV + 12f * scale, locationPaint)

        // Last update time (right)
        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f * scale
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        canvas.drawText("Last update $currentTime", width - paddingH, paddingV + 12f * scale, timePaint)
    }

    private fun drawMainContent(canvas: Canvas, data: WindData, width: Int, paddingH: Float, top: Float, scale: Float) {
        // Large wind speed
        val speedPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 32f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val speedText = "%.1f".format(data.currentSpeed)
        canvas.drawText(speedText, paddingH, top + 30f * scale, speedPaint)

        // Smaller decimal part styling - draw "kts" after speed
        val unitPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 14f * scale
            isAntiAlias = true
        }
        val speedWidth = speedPaint.measureText(speedText)
        canvas.drawText("kts", paddingH + speedWidth + 4f * scale, top + 30f * scale, unitPaint)

        // Direction arrow in circle (to the right of speed)
        val arrowCircleRadius = 18f * scale
        val arrowCircleX = paddingH + speedWidth + unitPaint.measureText("kts") + 24f * scale + arrowCircleRadius
        val arrowCircleY = top + 18f * scale

        val circlePaint = Paint().apply {
            color = COLOR_ARROW_BG
            isAntiAlias = true
        }
        canvas.drawCircle(arrowCircleX, arrowCircleY, arrowCircleRadius, circlePaint)
        drawDirectionArrow(canvas, arrowCircleX, arrowCircleY, data.currentDirection, arrowCircleRadius * 0.6f, scale)

        // Direction info below speed
        val infoPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 11f * scale
            isAntiAlias = true
        }
        val dirText = "${data.directionCardinal} ${data.currentDirection.roundToInt()}°"
        canvas.drawText(dirText, paddingH, top + 46f * scale, infoPaint)

        // Gust info
        val gustPaint = TextPaint().apply {
            color = COLOR_GUST
            textSize = 11f * scale
            isAntiAlias = true
        }
        val gustText = "gust ${data.currentGust.roundToInt()} kts"
        canvas.drawText(gustText, paddingH + infoPaint.measureText(dirText) + 12f * scale, top + 46f * scale, gustPaint)
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

    private fun drawLineChart(canvas: Canvas, data: WindData, left: Float, top: Float, right: Float, bottom: Float, scale: Float) {
        if (data.speeds.size < 2) return

        val chartWidth = right - left
        val chartHeight = bottom - top

        val maxSpeed = (data.speeds.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minSpeed = (data.speeds.minOrNull() ?: 0f).coerceAtMost(maxSpeed - 1f)
        val range = (maxSpeed - minSpeed).coerceAtLeast(1f)

        // Build path for line chart
        val linePath = Path()
        val fillPath = Path()
        val points = mutableListOf<PointF>()

        data.speeds.forEachIndexed { i, speed ->
            val x = left + (chartWidth * i / (data.speeds.size - 1))
            val normalizedSpeed = (speed - minSpeed) / range
            val y = bottom - (chartHeight * normalizedSpeed * 0.85f)
            points.add(PointF(x, y))
        }

        if (points.isNotEmpty()) {
            // Fill path
            fillPath.moveTo(points[0].x, bottom)
            fillPath.lineTo(points[0].x, points[0].y)

            // Line path
            linePath.moveTo(points[0].x, points[0].y)

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                // Smooth curve
                val cp1x = prev.x + (curr.x - prev.x) / 3
                val cp2x = prev.x + 2 * (curr.x - prev.x) / 3
                linePath.cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
                fillPath.cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
            }

            fillPath.lineTo(points.last().x, bottom)
            fillPath.close()

            // Draw fill gradient
            val fillGradient = LinearGradient(
                0f, top, 0f, bottom,
                COLOR_CHART_FILL_TOP, COLOR_CHART_FILL_BOTTOM,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, Paint().apply {
                shader = fillGradient
                style = Paint.Style.FILL
                isAntiAlias = true
            })

            // Draw line
            canvas.drawPath(linePath, Paint().apply {
                color = COLOR_CHART_LINE
                strokeWidth = 2f * scale
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
    }
}
