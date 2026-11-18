package ru.azimut.app.ui.components

/**
 * Shared grade palette for the elevation chart and the map track lines
 * (flat green -> gentle yellow -> steep orange -> very steep red).
 */
const val GRADE_FLAT = 0xFF4CAF50.toInt()
const val GRADE_GENTLE = 0xFFFFC107.toInt()
const val GRADE_STEEP = 0xFFFF9800.toInt()
const val GRADE_VERY_STEEP = 0xFFE53935.toInt()

val GRADE_COLORS = listOf(GRADE_FLAT, GRADE_GENTLE, GRADE_STEEP, GRADE_VERY_STEEP)

/** Grade percent -> bucket 1..4 (same thresholds everywhere: chart, Android, desktop). */
fun gradeBucketOf(percent: Float): Int = when {
    percent < 4f -> 1
    percent < 8f -> 2
    percent < 12f -> 3
    else -> 4
}

/** ARGB int -> "#RRGGBB" for MapLibre style properties. */
fun gradeHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)
