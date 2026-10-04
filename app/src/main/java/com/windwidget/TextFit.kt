package com.windwidget

import android.graphics.Paint

/** [text] as is when it fits in [maxWidth], otherwise cut and ended with "...". */
internal fun fitText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "..."
    val chars = paint.breakText(text, true, maxWidth - paint.measureText(ellipsis), null)
    return text.take(chars).trimEnd() + ellipsis
}
