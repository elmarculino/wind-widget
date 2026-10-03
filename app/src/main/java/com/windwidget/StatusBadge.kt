package com.windwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Draws the data status line ("Updated 15:16", "Offline 15:16", ...) for every widget style.
 * STALE and DEMO data get an amber pill so an old or fake reading never looks live.
 */
object StatusBadge {

    const val COLOR_WARNING = 0xFFFBBF24.toInt()          // Amber
    private const val COLOR_WARNING_BG = 0x38FBBF24        // Amber, ~22% alpha

    fun isWarning(data: WindData): Boolean =
        data.dataStatus == WindDataStatus.STALE || data.dataStatus == WindDataStatus.DEMO

    /**
     * Draws [text] at ([x], [baseline]) honouring [paint]'s alignment. Live/cached data is drawn
     * with [paint] as is; warning states are drawn bold amber inside a pill that stays within [x].
     */
    fun draw(canvas: Canvas, data: WindData, text: String, x: Float, baseline: Float, paint: TextPaint, scale: Float) {
        if (!isWarning(data)) {
            canvas.drawText(text, x, baseline, paint)
            return
        }

        val warnPaint = TextPaint(paint).apply {
            color = COLOR_WARNING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padH = 6f * scale
        val padV = 2f * scale
        val textWidth = warnPaint.measureText(text)

        // Shift the text inwards so the pill doesn't stick out past the anchor point
        val textX = when (paint.textAlign) {
            Paint.Align.RIGHT -> x - padH
            Paint.Align.LEFT -> x + padH
            else -> x
        }
        val left = when (paint.textAlign) {
            Paint.Align.RIGHT -> textX - textWidth
            Paint.Align.CENTER -> textX - textWidth / 2
            else -> textX
        }
        val fm = warnPaint.fontMetrics
        val pill = RectF(left - padH, baseline + fm.ascent - padV, left + textWidth + padH, baseline + fm.descent + padV)
        val radius = pill.height() / 2
        canvas.drawRoundRect(pill, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_WARNING_BG })
        canvas.drawText(text, textX, baseline, warnPaint)
    }
}
