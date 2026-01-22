package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Modern dark glass style wind widget renderer.
 * Based on stock widget design with blue accent.
 */
class WindModernRenderer(private val context: Context) {

    companion object {
        // Colors
        private const val COLOR_BG = 0xE61E1E22.toInt()  // Dark glass background
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0x99FFFFFF.toInt()  // 60% white
        private const val COLOR_TEXT_MUTED = 0x66FFFFFF.toInt()  // 40% white
        private const val COLOR_ACCENT = 0xFF3B82F6.toInt()  // Blue
        private const val COLOR_ACCENT_LIGHT = 0xFF60A5FA.toInt()  // Light blue
        private const val COLOR_ACCENT_GLOW = 0x4D3B82F6.toInt()  // Blue with 30% opacity
        private const val COLOR_CHART_FILL_TOP = 0x663B82F6.toInt()
        private const val COLOR_CHART_FILL_BOTTOM = 0x003B82F6.toInt()

        private const val MAX_LOCATION_LENGTH = 24
    }

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = height / 160f

        // Draw background
        drawBackground(canvas, width, height, scale)

        val paddingH = 20f * scale
        val paddingV = 16f * scale

        // Header: location + update time
        drawHeader(canvas, data, width, paddingH, paddingV, scale)

        // Main content: speed + direction
        drawMainContent(canvas, data, width, paddingH, paddingV + 40f * scale, scale)

        // Chart
        val chartTop = height - 70f * scale
        val chartBottom = height - 32f * scale
        drawChart(canvas, data, paddingH, chartTop, width - paddingH, chartBottom, scale)

        // Footer stats
        drawFooter(canvas, data, width, height - 24f * scale, scale)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_BG
            isAntiAlias = true
        }
        val cornerRadius = 28f * scale
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, paint)

        // Subtle border
        val borderPaint = Paint().apply {
            color = 0x1AFFFFFF  // 10% white
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, borderPaint)
    }

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int, paddingH: Float, paddingV: Float, scale: Float) {
        // Location name
        val locationPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 13f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val locationName = if (data.locationName.length > MAX_LOCATION_LENGTH) {
            data.locationName.take(MAX_LOCATION_LENGTH - 3) + "..."
        } else {
            data.locationName
        }
        canvas.drawText(locationName, paddingH, paddingV + 14f * scale, locationPaint)

        // Update time
        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_MUTED
            textSize = 9f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
            isAntiAlias = true
        }
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        canvas.drawText("UPDATED $currentTime", paddingH, paddingV + 26f * scale, timePaint)
    }

    private fun drawMainContent(canvas: Canvas, data: WindData, width: Int, paddingH: Float, top: Float, scale: Float) {
        // Large wind speed
        val speedPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 36f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.02f
            isAntiAlias = true
        }
        val speedText = "%.1f".format(data.currentSpeed)
        canvas.drawText(speedText, paddingH, top + 32f * scale, speedPaint)

        // Unit
        val unitPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val speedWidth = speedPaint.measureText(speedText)
        canvas.drawText("kts", paddingH + speedWidth + 4f * scale, top + 32f * scale, unitPaint)

        // Direction section (right side)
        val rightX = width - paddingH

        // Direction arrow circle with glow
        val circleRadius = 22f * scale
        val circleX = rightX - circleRadius
        val circleY = top + 18f * scale

        // Glow effect
        val glowPaint = Paint().apply {
            color = COLOR_ACCENT_GLOW
            isAntiAlias = true
            maskFilter = BlurMaskFilter(8f * scale, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(circleX, circleY, circleRadius + 4f * scale, glowPaint)

        // Circle background
        val circleBgPaint = Paint().apply {
            color = 0x333B82F6  // 20% blue
            isAntiAlias = true
        }
        canvas.drawCircle(circleX, circleY, circleRadius, circleBgPaint)

        // Circle border
        val circleBorderPaint = Paint().apply {
            color = 0x4D3B82F6  // 30% blue
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
            isAntiAlias = true
        }
        canvas.drawCircle(circleX, circleY, circleRadius, circleBorderPaint)

        // Direction arrow
        drawDirectionArrow(canvas, circleX, circleY, data.currentDirection, circleRadius * 0.5f, scale)

        // Direction label
        val dirLabelPaint = TextPaint().apply {
            color = COLOR_TEXT_MUTED
            textSize = 8f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.15f
            isAntiAlias = true
        }
        canvas.drawText("DIRECTION", circleX - circleRadius - 8f * scale, top + 10f * scale, dirLabelPaint)

        // Direction value
        val dirValuePaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 11f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("${data.directionCardinal} (${data.currentDirection.roundToInt()}°)", circleX - circleRadius - 8f * scale, top + 24f * scale, dirValuePaint)
    }

    private fun drawDirectionArrow(canvas: Canvas, cx: Float, cy: Float, direction: Float, size: Float, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_ACCENT
            strokeWidth = 2.5f * scale
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

    private fun drawChart(canvas: Canvas, data: WindData, left: Float, top: Float, right: Float, bottom: Float, scale: Float) {
        if (data.speeds.size < 2) return

        val chartWidth = right - left
        val chartHeight = bottom - top

        val maxSpeed = (data.speeds.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minSpeed = (data.speeds.minOrNull() ?: 0f).coerceAtMost(maxSpeed - 0.5f)
        val range = (maxSpeed - minSpeed).coerceAtLeast(1f)

        val linePath = Path()
        val fillPath = Path()
        val points = mutableListOf<PointF>()

        data.speeds.forEachIndexed { i, speed ->
            val x = left + (chartWidth * i / (data.speeds.size - 1))
            val normalizedSpeed = (speed - minSpeed) / range
            val y = bottom - (chartHeight * normalizedSpeed * 0.9f)
            points.add(PointF(x, y))
        }

        if (points.isNotEmpty()) {
            fillPath.moveTo(points[0].x, bottom)
            fillPath.lineTo(points[0].x, points[0].y)
            linePath.moveTo(points[0].x, points[0].y)

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val cp1x = prev.x + (curr.x - prev.x) / 3
                val cp2x = prev.x + 2 * (curr.x - prev.x) / 3
                linePath.cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
                fillPath.cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
            }

            fillPath.lineTo(points.last().x, bottom)
            fillPath.close()

            // Fill gradient
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

            // Line
            canvas.drawPath(linePath, Paint().apply {
                color = COLOR_ACCENT_LIGHT
                strokeWidth = 2f * scale
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
    }

    private fun drawFooter(canvas: Canvas, data: WindData, width: Int, y: Float, scale: Float) {
        val paddingH = 20f * scale

        val labelPaint = TextPaint().apply {
            color = COLOR_TEXT_MUTED
            textSize = 8f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
            isAntiAlias = true
        }

        val valuePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 8f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Dot indicators
        val dotPaint = Paint().apply {
            isAntiAlias = true
        }
        val dotRadius = 2.5f * scale

        // Min speed
        var x = paddingH
        dotPaint.color = COLOR_ACCENT_GLOW
        canvas.drawCircle(x + dotRadius, y, dotRadius, dotPaint)
        x += dotRadius * 2 + 4f * scale
        canvas.drawText("SPD:", x, y + 3f * scale, labelPaint)
        x += labelPaint.measureText("SPD:") + 2f * scale
        canvas.drawText("${data.speeds.minOrNull()?.roundToInt() ?: 0} min", x, y + 3f * scale, valuePaint)

        // Max gust
        x += valuePaint.measureText("${data.speeds.minOrNull()?.roundToInt() ?: 0} min") + 16f * scale
        dotPaint.color = COLOR_ACCENT
        canvas.drawCircle(x + dotRadius, y, dotRadius, dotPaint)
        x += dotRadius * 2 + 4f * scale
        canvas.drawText("GUST:", x, y + 3f * scale, labelPaint)
        x += labelPaint.measureText("GUST:") + 2f * scale
        canvas.drawText("${data.maxGust.roundToInt()} max", x, y + 3f * scale, valuePaint)

        // Time range (right side)
        val timeRangePaint = TextPaint().apply {
            color = COLOR_TEXT_MUTED
            textSize = 8f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.1f
            isAntiAlias = true
        }
        canvas.drawText("LAST 3H", width - paddingH, y + 3f * scale, timeRangePaint)
    }
}
