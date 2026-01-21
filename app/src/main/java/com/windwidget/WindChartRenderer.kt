package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern wind chart renderer that creates a clean bitmap visualization
 * matching the reference design but with improved aesthetics.
 */
class WindChartRenderer(private val context: Context) {

    companion object {
        // Colors - Modern palette
        private const val COLOR_BG_TOP = 0xFF1A1D21.toInt()      // Dark gradient top
        private const val COLOR_BG_BOTTOM = 0xFF0D0F12.toInt()  // Dark gradient bottom
        private const val COLOR_CHART_LINE = 0xFF4ADE80.toInt()  // Green line
        private const val COLOR_CHART_FILL_TOP = 0x804ADE80.toInt()  // Green fill (50% alpha)
        private const val COLOR_CHART_FILL_BOTTOM = 0x004ADE80.toInt()  // Transparent
        private const val COLOR_GUST_FILL = 0x3022D3EE.toInt()   // Cyan gust overlay
        private const val COLOR_ARROW = 0xFFFFFFFF.toInt()       // White arrows
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_GRID = 0x20FFFFFF.toInt()        // Subtle grid
        private const val COLOR_SPEED_LABEL = 0xFF4ADE80.toInt()
        private const val COLOR_DIRECTION_BAR = 0xFF3B82F6.toInt()  // Blue bottom bar
        private const val COLOR_MAX_BAR = 0xFFFBBF24.toInt()     // Amber max bar

        // Dimensions (will be scaled)
        private const val CHART_PADDING_LEFT = 40f
        private const val CHART_PADDING_RIGHT = 20f
        private const val CHART_PADDING_TOP = 50f
        private const val CHART_PADDING_BOTTOM = 80f
        private const val BOTTOM_BAR_HEIGHT = 36f
    }

    private val density = context.resources.displayMetrics.density

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background gradient
        drawBackground(canvas, width, height)

        // Calculate chart area
        val chartLeft = CHART_PADDING_LEFT * density
        val chartRight = width - CHART_PADDING_RIGHT * density
        val chartTop = CHART_PADDING_TOP * density
        val chartBottom = height - CHART_PADDING_BOTTOM * density
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw header (location + date)
        drawHeader(canvas, data, width)

        // Draw grid and Y-axis labels
        val maxY = calculateMaxY(data)
        drawGrid(canvas, chartLeft, chartTop, chartRight, chartBottom, maxY)

        // Draw chart fill and line
        drawChartArea(canvas, data, chartLeft, chartTop, chartWidth, chartHeight, maxY)

        // Draw gust overlay
        drawGustOverlay(canvas, data, chartLeft, chartTop, chartWidth, chartHeight, maxY)

        // Draw wind direction arrows
        drawDirectionArrows(canvas, data, chartLeft, chartTop, chartWidth, chartHeight)

        // Draw speed labels on chart
        drawSpeedLabels(canvas, data, chartLeft, chartTop, chartWidth, chartHeight, maxY)

        // Draw time axis
        drawTimeAxis(canvas, data, chartLeft, chartBottom, chartWidth)

        // Draw bottom info bar
        drawBottomBar(canvas, data, width, height)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            COLOR_BG_TOP, COLOR_BG_BOTTOM,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int) {
        val paint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 14f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Location name (left aligned)
        canvas.drawText(data.locationName, 12f * density, 24f * density, paint)

        // Date (right aligned)
        val datePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 12f * density
            isAntiAlias = true
        }
        val dateStr = formatDate(data.times.firstOrNull() ?: "")
        val dateWidth = datePaint.measureText(dateStr)
        canvas.drawText(dateStr, width - dateWidth - 12f * density, 24f * density, datePaint)
    }

    private fun formatDate(isoTime: String): String {
        return try {
            // Input: "2024-01-20T17:00"
            val parts = isoTime.split("T")[0].split("-")
            val day = parts[2].toInt()
            val month = parts[1].toInt()
            val dayNames = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sab")
            // Simplified - just show day.month
            "Ter $day.$month."
        } catch (e: Exception) {
            ""
        }
    }

    private fun calculateMaxY(data: WindData): Float {
        val maxValue = maxOf(data.speeds.maxOrNull() ?: 0f, data.gusts.maxOrNull() ?: 0f)
        // Round up to nearest 5
        return ((maxValue / 5).toInt() + 1) * 5f
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, maxY: Float) {
        val gridPaint = Paint().apply {
            color = COLOR_GRID
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val labelPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f * density
            isAntiAlias = true
        }

        val chartHeight = bottom - top
        val steps = 5
        for (i in 0..steps) {
            val y = bottom - (chartHeight * i / steps)
            val value = (maxY * i / steps).toInt()

            // Grid line
            canvas.drawLine(left, y, right, y, gridPaint)

            // Y-axis label (Beaufort on left side)
            val beaufort = WindData.knotsToBeaufort(value.toFloat())
            canvas.drawText("$beaufort", 8f * density, y + 4f * density, labelPaint)
        }

        // Right Y-axis labels (knots)
        val knotsPaint = TextPaint().apply {
            color = COLOR_SPEED_LABEL
            textSize = 9f * density
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        for (i in 0..steps) {
            val y = bottom - (chartHeight * i / steps)
            val value = (maxY * i / steps).toInt()
            canvas.drawText("$value", right + 18f * density, y + 3f * density, knotsPaint)
        }
    }

    private fun drawChartArea(
        canvas: Canvas, data: WindData,
        left: Float, top: Float, chartWidth: Float, chartHeight: Float, maxY: Float
    ) {
        if (data.speeds.isEmpty()) return

        val path = Path()
        val fillPath = Path()
        val points = mutableListOf<PointF>()

        // Calculate points
        data.speeds.forEachIndexed { i, speed ->
            val x = left + (chartWidth * i / (data.speeds.size - 1).coerceAtLeast(1))
            val y = top + chartHeight * (1 - speed / maxY)
            points.add(PointF(x, y))
        }

        // Create smooth curve using cubic bezier
        if (points.size >= 2) {
            fillPath.moveTo(points[0].x, top + chartHeight)
            fillPath.lineTo(points[0].x, points[0].y)
            path.moveTo(points[0].x, points[0].y)

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val cp1x = prev.x + (curr.x - prev.x) / 3
                val cp2x = prev.x + 2 * (curr.x - prev.x) / 3

                path.cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
                fillPath.cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
            }

            fillPath.lineTo(points.last().x, top + chartHeight)
            fillPath.close()
        }

        // Draw gradient fill
        val fillGradient = LinearGradient(
            0f, top, 0f, top + chartHeight,
            COLOR_CHART_FILL_TOP, COLOR_CHART_FILL_BOTTOM,
            Shader.TileMode.CLAMP
        )
        val fillPaint = Paint().apply {
            shader = fillGradient
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        val linePaint = Paint().apply {
            color = COLOR_CHART_LINE
            strokeWidth = 2.5f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawGustOverlay(
        canvas: Canvas, data: WindData,
        left: Float, top: Float, chartWidth: Float, chartHeight: Float, maxY: Float
    ) {
        if (data.gusts.isEmpty()) return

        val paint = Paint().apply {
            color = COLOR_GUST_FILL
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw subtle bars for gusts where they exceed speed
        data.gusts.forEachIndexed { i, gust ->
            val speed = data.speeds.getOrElse(i) { 0f }
            if (gust > speed) {
                val x = left + (chartWidth * i / (data.gusts.size - 1).coerceAtLeast(1))
                val gustY = top + chartHeight * (1 - gust / maxY)
                val speedY = top + chartHeight * (1 - speed / maxY)
                val barWidth = 4f * density
                canvas.drawRect(x - barWidth / 2, gustY, x + barWidth / 2, speedY, paint)
            }
        }
    }

    private fun drawDirectionArrows(
        canvas: Canvas, data: WindData,
        left: Float, top: Float, chartWidth: Float, chartHeight: Float
    ) {
        val arrowPaint = Paint().apply {
            color = COLOR_ARROW
            strokeWidth = 1.5f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val arrowSize = 12f * density
        val arrowY = top + chartHeight * 0.7f  // Position arrows in lower chart area

        // Draw arrows at regular intervals (not every point)
        val step = maxOf(1, data.directions.size / 6)
        for (i in data.directions.indices step step) {
            val x = left + (chartWidth * i / (data.directions.size - 1).coerceAtLeast(1))
            val direction = data.directions[i]

            canvas.save()
            canvas.translate(x, arrowY)
            // Rotate: 0 degrees = North (up), so we add 180 to show wind coming FROM direction
            canvas.rotate(direction + 180)

            // Draw arrow pointing down (wind coming from direction)
            val path = Path().apply {
                moveTo(0f, -arrowSize / 2)  // Arrow tip
                lineTo(0f, arrowSize / 2)    // Arrow base
                // Arrow head
                moveTo(-arrowSize / 4, arrowSize / 4)
                lineTo(0f, arrowSize / 2)
                lineTo(arrowSize / 4, arrowSize / 4)
            }
            canvas.drawPath(path, arrowPaint)
            canvas.restore()
        }
    }

    private fun drawSpeedLabels(
        canvas: Canvas, data: WindData,
        left: Float, top: Float, chartWidth: Float, chartHeight: Float, maxY: Float
    ) {
        val labelPaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Draw speed values at certain points
        val step = maxOf(1, data.speeds.size / 8)
        for (i in data.speeds.indices step step) {
            val x = left + (chartWidth * i / (data.speeds.size - 1).coerceAtLeast(1))
            val y = top + chartHeight * (1 - data.speeds[i] / maxY)
            val speedInt = data.speeds[i].toInt()
            canvas.drawText("$speedInt", x, y - 6f * density, labelPaint)
        }
    }

    private fun drawTimeAxis(canvas: Canvas, data: WindData, left: Float, bottom: Float, chartWidth: Float) {
        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Show time labels at regular intervals
        val labelCount = minOf(6, data.times.size)
        val step = maxOf(1, (data.times.size - 1) / (labelCount - 1))

        for (i in 0 until labelCount) {
            val idx = minOf(i * step, data.times.size - 1)
            val x = left + (chartWidth * idx / (data.times.size - 1).coerceAtLeast(1))
            val timeStr = formatTime(data.times.getOrElse(idx) { "" })
            canvas.drawText(timeStr, x, bottom + 16f * density, timePaint)
        }
    }

    private fun formatTime(isoTime: String): String {
        return try {
            // Input: "2024-01-20T17:00"
            val timePart = isoTime.split("T").getOrElse(1) { "00:00" }
            timePart.substring(0, 5)
        } catch (e: Exception) {
            ""
        }
    }

    private fun drawBottomBar(canvas: Canvas, data: WindData, width: Int, height: Int) {
        val barTop = height - BOTTOM_BAR_HEIGHT * density
        val barHeight = BOTTOM_BAR_HEIGHT * density
        val sectionWidth = width / 3f

        // Direction section (blue)
        val dirPaint = Paint().apply {
            color = COLOR_DIRECTION_BAR
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, barTop, sectionWidth, height.toFloat(), dirPaint)

        // Speed section (green)
        val speedPaint = Paint().apply {
            color = COLOR_CHART_LINE
            style = Paint.Style.FILL
        }
        canvas.drawRect(sectionWidth, barTop, sectionWidth * 2, height.toFloat(), speedPaint)

        // Max section (amber)
        val maxPaint = Paint().apply {
            color = COLOR_MAX_BAR
            style = Paint.Style.FILL
        }
        canvas.drawRect(sectionWidth * 2, barTop, width.toFloat(), height.toFloat(), maxPaint)

        // Text for each section
        val textPaint = TextPaint().apply {
            color = COLOR_BG_TOP
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val textY = barTop + barHeight / 2 + 4f * density

        // Direction with arrow
        val dirText = "${data.directionCardinal} ${data.currentDirection.toInt()}°"
        canvas.drawText(dirText, sectionWidth / 2, textY, textPaint)

        // Draw small arrow indicator
        val arrowX = sectionWidth / 2 - textPaint.measureText(dirText) / 2 - 12f * density
        drawMiniArrow(canvas, arrowX, barTop + barHeight / 2, data.currentDirection)

        // Current speed
        val speedText = "%.1f nós".format(data.currentSpeed)
        canvas.drawText(speedText, sectionWidth * 1.5f, textY, textPaint)

        // Max gust
        val maxText = "max: ${data.maxGust.toInt()}"
        canvas.drawText(maxText, sectionWidth * 2.5f, textY, textPaint)
    }

    private fun drawMiniArrow(canvas: Canvas, x: Float, y: Float, direction: Float) {
        val paint = Paint().apply {
            color = COLOR_BG_TOP
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val size = 8f * density
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(direction + 180)

        val path = Path().apply {
            moveTo(0f, -size / 2)
            lineTo(0f, size / 2)
            moveTo(-size / 3, size / 4)
            lineTo(0f, size / 2)
            lineTo(size / 3, size / 4)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
