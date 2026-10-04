package com.windwidget

/**
 * Wind data model containing:
 * - History data (for the chart) - last 3 hours
 * - Real-time current values (for the bottom bar)
 */
data class WindData(
    val locationName: String,
    // History data for chart (last 3 hours)
    val times: List<String>,      // ISO format: "2024-01-20T17:00"
    val speeds: List<Float>,      // Wind speed in knots
    val directions: List<Float>,  // Degrees (0=N, 90=E, 180=S, 270=W)
    val gusts: List<Float>,       // Gust speed in knots
    // Real-time current values (from real_time endpoint)
    val currentSpeed: Float = speeds.lastOrNull() ?: 0f,
    val currentDirection: Float = directions.lastOrNull() ?: 0f,
    val currentGust: Float = gusts.lastOrNull() ?: 0f,
    val temperatureC: Float? = null,  // Not every source reports it (Windguru does)
    val dataStatus: WindDataStatus = WindDataStatus.LIVE,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val maxSpeed: Float get() = speeds.maxOrNull() ?: 0f
    val maxGust: Float get() = gusts.maxOrNull() ?: 0f

    /** 16-point compass (22.5° sectors centred on each point), e.g. 74° -> "ENE". */
    val directionCardinal: String get() {
        val normalized = ((currentDirection % 360f) + 360f) % 360f
        return CARDINALS[((normalized + 11.25f) / 22.5f).toInt() % 16]
    }

    companion object {
        private val CARDINALS = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )

        fun knotsToBeaufort(knots: Float): Int = when {
            knots < 1 -> 0
            knots < 4 -> 1
            knots < 7 -> 2
            knots < 11 -> 3
            knots < 17 -> 4
            knots < 22 -> 5
            knots < 28 -> 6
            knots < 34 -> 7
            knots < 41 -> 8
            knots < 48 -> 9
            knots < 56 -> 10
            knots < 64 -> 11
            else -> 12
        }
    }

    /** Just the time of the reading; offline is told apart by colour (see StatusBadge). */
    fun statusText(): String {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(lastUpdatedMillis))
        return when (dataStatus) {
            WindDataStatus.LIVE, WindDataStatus.CACHED, WindDataStatus.STALE -> time
            WindDataStatus.DEMO -> "Demo"
        }
    }
}

enum class WindDataStatus {
    LIVE,
    CACHED,
    STALE,
    DEMO
}
