package com.windwidget

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextPaint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wind + tide 4x2 widget, modelled on the right half of the Echo Show dashboard (echo-show-vento):
 * wind on the left, tide on the right, gust/temperature and the next two extremes along the bottom.
 * The layout is designed on a 180-unit-tall box, about 360 units wide at the default size.
 */
class WindTideRenderer(private val context: Context) {

    companion object {
        // Echo dashboard palette
        private const val COLOR_BG = 0xF2131313.toInt()          // --surface
        private const val COLOR_WIND = 0xFF60A5FA.toInt()        // --primary-container
        private const val COLOR_TIDE = 0xFFA4C9FF.toInt()        // --primary
        private const val COLOR_TEXT = 0xFFE5E2E1.toInt()        // --on-surface
        private const val COLOR_TEXT_VARIANT = 0xFFC1C7D3.toInt() // --on-surface-variant
        private const val COLOR_LABEL = 0xFF8B919D.toInt()       // --outline
        private const val COLOR_DIVIDER = 0x4D414751             // --outline-variant, 30%
        private const val COLOR_ARROW_BG = 0xFF262A31.toInt()    // a step above --surface-container
        private const val COLOR_ARROW_RING = 0xFF414751.toInt()  // --outline-variant
        private const val COLOR_RISING = 0xFF4EDEA3.toInt()      // --secondary (ENCHENDO)
        private const val COLOR_FALLING = 0xFFFABD34.toInt()     // VAZANDO
    }

    /**
     * [tide] null draws the tide column empty ("sem dados"). [clipCorners]: draw rounded corners into
     * the bitmap; only needed before Android 12 (from 12 on the layout clips to a 16dp outline).
     */
    fun render(
        wind: WindData,
        tide: TideData?,
        width: Int,
        height: Int,
        clipCorners: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = height / 180f

        val radius = if (clipCorners) 16f * s else 0f
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BG }
        )

        val pad = 16f * s
        val mid = width / 2f
        val gutter = 14f * s
        drawHeader(canvas, wind, width, pad, s)
        canvas.drawLine(mid, 42f * s, mid, height - 12f * s, dividerPaint(s))
        drawWind(canvas, wind, pad, mid - gutter, s)
        drawTide(canvas, tide, mid + gutter, width - pad, s)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, wind: WindData, width: Int, pad: Float, s: Float) {
        val baseline = 28f * s
        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LABEL
            textSize = 11f * s
            textAlign = Paint.Align.RIGHT
        }
        val status = wind.statusText().uppercase()
        val pill = if (StatusBadge.hasPill(wind)) 12f * s else 0f
        StatusBadge.draw(canvas, wind, status, width - pad, baseline - 1f * s, statusPaint, s)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 13f * s
        }
        val titleWidth = width - 2 * pad - statusPaint.measureText(status) - pill - 12f * s
        canvas.drawText(fitText(wind.locationName, titlePaint, titleWidth), pad, baseline, titlePaint)
    }

    private fun drawWind(canvas: Canvas, wind: WindData, left: Float, right: Float, s: Float) {
        canvas.drawText("VENTO", left, 52f * s, labelPaint(9f * s))

        // Speed, large; direction arrow in a circle at the column's right edge
        val speedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_WIND
            textSize = 48f * s
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -0.04f
        }
        canvas.drawText("%.1f".format(wind.currentSpeed), left - 2f * s, 98f * s, speedPaint)

        val r = 15f * s
        val cx = right - r
        val cy = 78f * s
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ARROW_BG })
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ARROW_RING
            style = Paint.Style.STROKE
            strokeWidth = 1f * s
        })
        drawNavArrow(canvas, cx, cy, wind.currentDirection, r * 0.6f, COLOR_TIDE)

        canvas.drawText("NÓS", left, 116f * s, labelPaint(9f * s))
        val dirPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 12f * s
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("${wind.directionCardinal} ${wind.currentDirection.toInt()}°", right, 116f * s, dirPaint)

        canvas.drawLine(left, 128f * s, right, 128f * s, dividerPaint(s))
        val half = (right - left) / 2
        drawStat(canvas, "RAJADA", "%.1f".format(wind.currentGust), " nós", left, s)
        drawStat(canvas, "TEMP.", wind.temperatureC?.let { "%.1f".format(it) } ?: "—", " °C", left + half, s)
    }

    private fun drawTide(canvas: Canvas, tide: TideData?, left: Float, right: Float, s: Float) {
        canvas.drawText("MARÉ", left, 52f * s, labelPaint(9f * s))
        canvas.drawLine(left, 128f * s, right, 128f * s, dividerPaint(s))

        if (tide == null) {
            val empty = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_LABEL; textSize = 12f * s }
            canvas.drawText("Sem dados de maré", left, 98f * s, empty)
            return
        }

        // ▲ ENCHENDO / ▼ VAZANDO on the label line
        val trendPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (tide.rising) COLOR_RISING else COLOR_FALLING
            textSize = 10f * s
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(if (tide.rising) "▲ ENCHENDO" else "▼ VAZANDO", right, 52f * s, trendPaint)

        // Height now, large
        val heightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TIDE
            textSize = 40f * s
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -0.03f
        }
        val heightText = "%.2f".format(tide.heightNowM)
        canvas.drawText(heightText, left - 2f * s, 98f * s, heightPaint)
        val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_LABEL; textSize = 14f * s }
        canvas.drawText("m", left + heightPaint.measureText(heightText) + 2f * s, 98f * s, unitPaint)

        // Last extreme, as a clock time: a widget repaints every 30 min, so "há 1h13" would go stale
        tide.lastExtreme?.let { last ->
            val lastPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TEXT_VARIANT; textSize = 11f * s }
            val text = "${typeName(last.type)} %.2f m às %s".format(last.heightM, clock(last.timeMillis))
            canvas.drawText(fitText(text, lastPaint, right - left), left, 116f * s, lastPaint)
        }

        val half = (right - left) / 2
        tide.upcoming.take(2).forEachIndexed { i, e ->
            drawStat(canvas, typeName(e.type).uppercase(), "%.1f".format(e.heightM), " m · ${clock(e.timeMillis)}", left + i * half, s)
        }
    }

    /** Small spaced label over a value with a unit, like the Echo's sub-grid. */
    private fun drawStat(canvas: Canvas, label: String, value: String, unit: String, x: Float, s: Float) {
        canvas.drawText(label, x, 146f * s, labelPaint(8.5f * s))
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TEXT; textSize = 19f * s }
        canvas.drawText(value, x, 168f * s, valuePaint)
        val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_LABEL; textSize = 10f * s }
        canvas.drawText(unit, x + valuePaint.measureText(value), 168f * s, unitPaint)
    }

    /** Echo's filled navigation arrow, pointing where the wind blows to. */
    private fun drawNavArrow(canvas: Canvas, cx: Float, cy: Float, direction: Float, size: Float, color: Int) {
        val path = Path().apply {
            moveTo(0f, -size)
            lineTo(-size * 0.62f, size * 0.85f)
            lineTo(0f, size * 0.55f)
            lineTo(size * 0.62f, size * 0.85f)
            close()
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(direction + 180f)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.restore()
    }

    private fun labelPaint(size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_LABEL
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.22f
    }

    private fun dividerPaint(s: Float) = Paint().apply {
        color = COLOR_DIVIDER
        strokeWidth = 1f * s
    }

    private fun typeName(type: TideType) = if (type == TideType.HIGH) "Alta" else "Baixa"

    private fun clock(millis: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
