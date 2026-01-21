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
    val currentGust: Float = gusts.lastOrNull() ?: 0f
) {
    val maxSpeed: Float get() = speeds.maxOrNull() ?: 0f
    val maxGust: Float get() = gusts.maxOrNull() ?: 0f

    val directionCardinal: String get() {
        val dir = currentDirection
        return when {
            dir < 22.5 || dir >= 337.5 -> "N"
            dir < 67.5 -> "NE"
            dir < 112.5 -> "E"
            dir < 157.5 -> "SE"
            dir < 202.5 -> "S"
            dir < 247.5 -> "SW"
            dir < 292.5 -> "W"
            else -> "NW"
        }
    }

    companion object {
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
}
