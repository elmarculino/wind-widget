package com.windwidget

enum class TideType { HIGH, LOW }

/** A high or low tide from the tide table. */
data class TideExtreme(val type: TideType, val heightM: Float, val timeMillis: Long)

/**
 * Tide now, for the wind + tide widget: the height interpolated between the surrounding extremes,
 * whether it is rising, the last extreme and the next ones.
 */
data class TideData(
    val locationName: String,
    val heightNowM: Float,
    val rising: Boolean,
    val lastExtreme: TideExtreme?,
    val upcoming: List<TideExtreme>
)
