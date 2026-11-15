package ru.azimut.app.core.routing

import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.distanceMeters
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class FollowProgress(
    /** Distance from the position to the nearest point of the line. */
    val offRouteMeters: Double,
    /** Distance along the line up to that nearest point. */
    val doneMeters: Double,
    val remainingMeters: Double,
    val finished: Boolean,
)

/**
 * Follows a planned line (Follow Track). Holds precomputed cumulative distances;
 * [offRoute] uses hysteresis so GPS noise around the threshold does not toggle alarms.
 */
class RouteFollower(private val line: List<GeoPoint>) {
    private val cumulative = DoubleArray(line.size).also { acc ->
        for (i in 1 until line.size) acc[i] = acc[i - 1] + distanceMeters(line[i - 1], line[i])
    }
    val totalMeters: Double get() = cumulative.lastOrNull() ?: 0.0

    private var doneMeters = 0.0

    var offRoute = false
        private set

    fun update(position: GeoPoint): FollowProgress? {
        if (line.size < 2) return null
        val progress = progress(position)
        offRoute = when {
            progress.offRouteMeters > OFF_ROUTE_M -> true
            progress.offRouteMeters < BACK_ON_ROUTE_M -> false
            else -> offRoute
        }
        return progress
    }

    private fun progress(position: GeoPoint): FollowProgress {
        // Distance to the whole line: being next to another part of a loop is not off route.
        val offRouteMeters = nearest(position, 0.0, totalMeters, doneMeters)!!.first

        // Window around the current progress avoids snapping to the start of a loop or a parallel leg.
        val minAlong = doneMeters - BACKTRACK_M
        val window = nearest(position, minAlong, doneMeters + LOOKAHEAD_M, doneMeters)
        if (window != null && window.first <= OFF_ROUTE_M) {
            doneMeters = window.second
        } else {
            // A shortcut can rejoin the route ahead; never go backwards past the backtrack window.
            val forward = nearest(position, minAlong, totalMeters, doneMeters)
            if (forward != null && forward.first <= OFF_ROUTE_M) doneMeters = forward.second
        }

        val remaining = (totalMeters - doneMeters).coerceAtLeast(0.0)
        return FollowProgress(
            offRouteMeters = offRouteMeters,
            doneMeters = doneMeters,
            remainingMeters = remaining,
            finished = distanceMeters(position, line.last()) < FINISH_M && remaining < FINISH_M,
        )
    }

    /**
     * Nearest point of the line part between [minAlong] and [maxAlong] metres along it,
     * as distance and distance along the line. Segments are clipped to that range, so a long
     * segment that merely ends inside the window cannot pull progress back to its start.
     * On equal distances prefers the point closest to [preferredAlong], then the forward one
     * (out-and-back routes share the same geometry in both directions).
     */
    private fun nearest(
        position: GeoPoint,
        minAlong: Double,
        maxAlong: Double,
        preferredAlong: Double,
    ): Pair<Double, Double>? {
        // Local equirectangular projection around the position: accurate enough for metres-scale checks.
        val metersPerDegLat = PI / 180.0 * 6_371_000.0
        val metersPerDegLon = metersPerDegLat * cos(position.lat * PI / 180.0)
        fun x(p: GeoPoint) = (p.lon - position.lon) * metersPerDegLon
        fun y(p: GeoPoint) = (p.lat - position.lat) * metersPerDegLat

        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 1 until line.size) {
            val segStart = cumulative[i - 1]
            val segLen = cumulative[i] - segStart
            if (cumulative[i] < minAlong || segStart > maxAlong) continue
            val tMin = if (segLen == 0.0) 0.0 else ((minAlong - segStart) / segLen).coerceIn(0.0, 1.0)
            val tMax = if (segLen == 0.0) 1.0 else ((maxAlong - segStart) / segLen).coerceIn(0.0, 1.0)
            val ax = x(line[i - 1])
            val ay = y(line[i - 1])
            val dx = x(line[i]) - ax
            val dy = y(line[i]) - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(tMin, tMax)
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            val along = cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1])
            val better = when {
                d < best - 0.01 -> true
                d > best + 0.01 -> false
                abs(along - preferredAlong) < abs(bestAlong - preferredAlong) - 0.01 -> true
                abs(along - preferredAlong) > abs(bestAlong - preferredAlong) + 0.01 -> false
                else -> along > bestAlong
            }
            if (better) {
                best = d
                bestAlong = along
            }
        }
        return if (best == Double.MAX_VALUE) null else best to bestAlong
    }

    companion object {
        const val OFF_ROUTE_M = 50.0
        const val BACK_ON_ROUTE_M = 30.0
        const val FINISH_M = 30.0
        const val BACKTRACK_M = 50.0
        const val LOOKAHEAD_M = 1000.0
    }
}
