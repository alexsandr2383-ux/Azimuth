package ru.azimut.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Compact elevation profile drawn as a polyline over the track's points.
 * [heights] is per-point DEM height; nulls (void / no DEM coverage) break the line.
 * Shows min/max labels; renders a hint instead when there is nothing to draw.
 */
@Composable
fun ElevationChart(heights: List<Double?>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val min = heights.filterNotNull().minOrNull()
    val max = heights.filterNotNull().maxOrNull()
    if (heights.size < 2 || min == null || max == null) {
        Text(
            ru.azimut.app.ui.tr("Нет DEM — высоты неизвестны", "No DEM data for heights"),
            style = MaterialTheme.typography.bodySmall,
            color = gridColor,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val padPx = 8.dp.toPx()
            val h = size.height - 2 * padPx
            val w = size.width
            val span = (max - min).coerceAtLeast(1.0)
            var path: Path? = null
            heights.forEachIndexed { i, hgt ->
                if (hgt == null) {
                    path = null
                    return@forEachIndexed
                }
                val x = w * i / (heights.size - 1).coerceAtLeast(1)
                val y = padPx + (h * ((max - hgt) / span)).toFloat()
                val p = path ?: Path().also { path = it; it.moveTo(x.toFloat(), y) }
                p.lineTo(x.toFloat(), y)
            }
            path?.let {
                drawPath(it, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Text(
            "${max.toInt()} м",
            style = MaterialTheme.typography.labelSmall,
            color = gridColor,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            "${min.toInt()} м",
            style = MaterialTheme.typography.labelSmall,
            color = gridColor,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        // Baseline hint so a flat track does not look like a rendering bug.
        if (max - min < 1.0) {
            Text(
                ru.azimut.app.ui.tr("Ровно", "Flat"),
                style = MaterialTheme.typography.labelSmall,
                color = gridColor,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
