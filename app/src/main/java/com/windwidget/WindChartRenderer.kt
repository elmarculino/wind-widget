package com.windwidget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Wind chart renderer for 4x2 widget with proper scaling for high-DPI displays.
 */
class WindChartRenderer(private val context: Context) {

    companion object {
        private const val COLOR_BG_TOP = 0xFF1A1D21.toInt()
        private const val COLOR_BG_BOTTOM = 0xFF0D0F12.toInt()
        private const val COLOR_CHART_LINE = 0xFF4ADE80.toInt()
        private const val COLOR_CHART_FILL_TOP = 0x804ADE80.toInt()
        private const val COLOR_CHART_FILL_BOTTOM = 0x004ADE80.toInt()
        private const val COLOR_GUST_FILL = 0x3022D3EE.toInt()
        private const val COLOR_ARROW = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val COLOR_GRID = 0x20FFFFFF.toInt()
        private const val COLOR_SPEED_LABEL = 0xFF4ADE80.toInt()
        private const val COLOR_DIRECTION_BAR = 0xFF3B82F6.toInt()
        private const val COLOR_MAX_BAR = 0xFFFBBF24.toInt()

        private const val MAX_LOCATION_LENGTH = 35
    }

    fun render(data: WindData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = height / 180f

        drawBackground(canvas, width, height)

        val paddingLeft = 35f * scale
        val paddingRight = 15f * scale
        val paddingTop = 40f * scale
        val paddingBottom = 55f * scale
        val bottomBarHeight = 38f * scale

        val chartLeft = paddingLeft
        val chartRight = width - paddingRight
        val chartTop = paddingTop
        val chartBottom = height - paddingBottom - bottomBarHeight
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val maxY = calculateMaxY(data)

        drawHeader(canvas, data, width, scale)
        drawGrid(canvas, chartLeft, chartTop, chartRight, chartBottom, maxY, scale)
        drawChartArea(canvas, data, chartLeft, chartTop, chartWidth, chartHeight, maxY, scale)
        drawGustOverlay(canvas, data, chartLeft, chartTop, chartWidth, chartHeight, maxY, scale)
        drawDirectionArrows(canvas, data, chartLeft, chartTop, chartWidth, chartHeight, scale)
        drawTimeAxis(canvas, data, chartLeft, chartBottom, chartWidth, scale)
        drawBottomBar(canvas, data, width, height, bottomBarHeight, scale)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            COLOR_BG_TOP, COLOR_BG_BOTTOM,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = gradient })
    }

    private fun drawHeader(canvas: Canvas, data: WindData, width: Int, scale: Float) {
        val paint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Truncate location name
        val locationName = if (data.locationName.length > MAX_LOCATION_LENGTH) {
            data.locationName.take(MAX_LOCATION_LENGTH - 3) + "..."
        } else {
            data.locationName
        }
        canvas.drawText(locationName, 12f * scale, 26f * scale, paint)
    }

    private fun calculateMaxY(data: WindData): Float {
        val maxValue = maxOf(data.speeds.maxOrNull() ?: 0f, data.gusts.maxOrNull() ?: 0f)
        return ((maxValue / 5).toInt() + 1) * 5f
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, maxY: Float, scale: Float) {
        val gridPaint = Paint().apply {
            color = COLOR_GRID
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val chartHeight = bottom - top
        // Only 3 grid lines to avoid overlap
        val steps = 3

        for (i in 0..steps) {
            val y = bottom - (chartHeight * i / steps)
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        // Right side: knots labels only (no Beaufort to avoid clutter)
        val knotsPaint = TextPaint().apply {
            color = COLOR_SPEED_LABEL
            textSize = 10f * scale
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        for (i in 0..steps) {
            val y = bottom - (chartHeight * i / steps)
            val value = (maxY * i / steps).toInt()
            canvas.drawText("$value", right + 4f * scale, y + 4f * scale, knotsPaint)
        }
    }

    private fun drawChartArea(canvas: Canvas, data: WindData, left: Float, top: Float, chartWidth: Float, chartHeight: Float, maxY: Float, scale: Float) {
        if (data.speeds.isEmpty()) return

        val path = Path()
        val fillPath = Path()
        val points = mutableListOf<PointF>()

        data.speeds.forEachIndexed { i, speed ->
            val x = left + (chartWidth * i / (data.speeds.size - 1).coerceAtLeast(1))
            val y = top + chartHeight * (1 - speed / maxY)
            points.add(PointF(x, y))
        }

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

        val fillGradient = LinearGradient(0f, top, 0f, top + chartHeight, COLOR_CHART_FILL_TOP, COLOR_CHART_FILL_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawPath(fillPath, Paint().apply { shader = fillGradient; style = Paint.Style.FILL; isAntiAlias = true })

        canvas.drawPath(path, Paint().apply {
            color = COLOR_CHART_LINE
            strokeWidth = 2.5f * scale
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }

    private fun drawGustOverlay(canvas: Canvas, data: WindData, left: Float, top: Float, chartWidth: Float, chartHeight: Float, maxY: Float, scale: Float) {
        if (data.gusts.isEmpty()) return
        val paint = Paint().apply { color = COLOR_GUST_FILL; style = Paint.Style.FILL; isAntiAlias = true }

        data.gusts.forEachIndexed { i, gust ->
            val speed = data.speeds.getOrElse(i) { 0f }
            if (gust > speed * 1.1f) {
                val x = left + (chartWidth * i / (data.gusts.size - 1).coerceAtLeast(1))
                val gustY = top + chartHeight * (1 - gust / maxY)
                val speedY = top + chartHeight * (1 - speed / maxY)
                val barWidth = 4f * scale
                canvas.drawRect(x - barWidth / 2, gustY, x + barWidth / 2, speedY, paint)
            }
        }
    }

    private fun drawDirectionArrows(canvas: Canvas, data: WindData, left: Float, top: Float, chartWidth: Float, chartHeight: Float, scale: Float) {
        val arrowPaint = Paint().apply {
            color = COLOR_ARROW
            strokeWidth = 1.5f * scale
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val arrowSize = 12f * scale
        val arrowY = top + chartHeight * 0.65f
        // Show fewer arrows to avoid clutter
        val step = maxOf(1, data.directions.size / 5)

        for (i in data.directions.indices step step) {
            val x = left + (chartWidth * i / (data.directions.size - 1).coerceAtLeast(1))
            val direction = data.directions[i]

            canvas.save()
            canvas.translate(x, arrowY)
            // Arrow points where wind is GOING TO (no +180)
            canvas.rotate(direction)

            val path = Path().apply {
                moveTo(0f, -arrowSize / 2)
                lineTo(0f, arrowSize / 2)
                moveTo(-arrowSize / 4, arrowSize / 4)
                lineTo(0f, arrowSize / 2)
                lineTo(arrowSize / 4, arrowSize / 4)
            }
            canvas.drawPath(path, arrowPaint)
            canvas.restore()
        }
    }

    private fun drawTimeAxis(canvas: Canvas, data: WindData, left: Float, bottom: Float, chartWidth: Float, scale: Float) {
        val timePaint = TextPaint().apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 10f * scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Show only 5 time labels
        val labelCount = minOf(5, data.times.size)
        if (labelCount < 2) return

        val step = (data.times.size - 1) / (labelCount - 1)

        for (i in 0 until labelCount) {
            val idx = minOf(i * step, data.times.size - 1)
            val x = left + (chartWidth * idx / (data.times.size - 1).coerceAtLeast(1))
            val timeStr = formatTime(data.times.getOrElse(idx) { "" })
            canvas.drawText(timeStr, x, bottom + 14f * scale, timePaint)
        }
    }

    private fun formatTime(isoTime: String): String {
        return try {
            isoTime.split("T").getOrElse(1) { "00:00" }.substring(0, 5)
        } catch (e: Exception) { "" }
    }

    private fun drawBottomBar(canvas: Canvas, data: WindData, width: Int, height: Int, barHeight: Float, scale: Float) {
        val barTop = height - barHeight
        val sectionWidth = width / 3f

        canvas.drawRect(0f, barTop, sectionWidth, height.toFloat(), Paint().apply { color = COLOR_DIRECTION_BAR })
        canvas.drawRect(sectionWidth, barTop, sectionWidth * 2, height.toFloat(), Paint().apply { color = COLOR_CHART_LINE })
        canvas.drawRect(sectionWidth * 2, barTop, width.toFloat(), height.toFloat(), Paint().apply { color = COLOR_MAX_BAR })

        val textPaint = TextPaint().apply {
            color = COLOR_BG_TOP
            textSize = 13f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val textY = barTop + barHeight / 2 + 5f * scale

        // Arrow points where wind GOES TO
        drawMiniArrow(canvas, sectionWidth / 2 - 30f * scale, barTop + barHeight / 2, data.currentDirection, scale)
        canvas.drawText("${data.directionCardinal} ${data.currentDirection.roundToInt()}", sectionWidth / 2 + 8f * scale, textY, textPaint)

        canvas.drawText("%.1f kts".format(data.currentSpeed), sectionWidth * 1.5f, textY, textPaint)
        canvas.drawText("max: ${data.maxGust.roundToInt()}", sectionWidth * 2.5f, textY, textPaint)
    }

    private fun drawMiniArrow(canvas: Canvas, x: Float, y: Float, direction: Float, scale: Float) {
        val paint = Paint().apply {
            color = COLOR_BG_TOP
            strokeWidth = 2f * scale
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val size = 10f * scale
        canvas.save()
        canvas.translate(x, y)
        // Arrow points where wind GOES TO (direction without +180)
        canvas.rotate(direction)

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
