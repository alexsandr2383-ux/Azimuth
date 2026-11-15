package ru.azimut.app.core.compare

import ru.azimut.app.core.dem.DemStore
import ru.azimut.app.core.dem.ElevationProfile
import ru.azimut.app.core.dem.TrackElevation
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.distanceMeters
import ru.azimut.app.core.model.distanceToPolylineMeters

/** A recorded point farther than this from the planned line counts as off-route. */
const val OFF_ROUTE_THRESHOLD_M = 50.0

/** One side of the comparison: recorded ride vs planned route ([ru.azimut.app.core.model.KIND_ROUTE]). */
data class TrackCompareResult(
    val plannedName: String,
    /** Lateral deviation of recorded points from the planned polyline; null when either track is empty. */
    val lateralAvgMeters: Double?,
    val lateralMaxMeters: Double?,
    /** 0..1 share of recorded points beyond [OFF_ROUTE_THRESHOLD_M]; null when not computable. */
    val offRouteShare: Double?,
    val plannedDistanceMeters: Double,
    /** Sum of recorded point-to-point segments; null with fewer than 2 points. */
    val actualDistanceMeters: Double?,
    /** Planned total duration; null for plans saved before the field existed. */
    val plannedTimeSeconds: Double?,
    /** last−first recorded timestamp; null when timestamps are absent/monotonicity broken. */
    val actualTimeSeconds: Double?,
    /** Heights per TrackElevation (DEM first, recorded altitude fallback); null when no data at all. */
    val plannedElevation: ElevationProfile?,
    val actualElevation: ElevationProfile?,
)

/**
 * Pure computation for the journal «Compare with route» feature. O(N×M) over the two
 * point lists plus two DEM profiles — callers run it on Dispatchers.Default.
 */
fun compareTracks(recorded: Track, planned: Track, dem: DemStore): TrackCompareResult {
    val plannedGeo = planned.points.map { GeoPoint(it.lat, it.lon) }

    val lateralAvg: Double?
    val lateralMax: Double?
    val offShare: Double?
    if (recorded.points.isNotEmpty() && plannedGeo.size >= 2) {
        val deviations = recorded.points.map { p ->
            distanceToPolylineMeters(GeoPoint(p.lat, p.lon), plannedGeo)
        }
        lateralAvg = deviations.average()
        lateralMax = deviations.max()
        offShare = deviations.count { it > OFF_ROUTE_THRESHOLD_M }.toDouble() / deviations.size
    } else {
        lateralAvg = null
        lateralMax = null
        offShare = null
    }

    val actualDistance: Double? = if (recorded.points.size >= 2) {
        var sum = 0.0
        for (i in 1 until recorded.points.size) {
            val a = recorded.points[i - 1]
            val b = recorded.points[i]
            sum += distanceMeters(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon))
        }
        sum
    } else {
        null
    }

    val actualTime: Double? = recorded.points.firstOrNull()?.timeMillis?.takeIf { it > 0 }
        ?.let { start ->
            val end = recorded.points.last().timeMillis
            if (end > start) (end - start) / 1000.0 else null
        }

    val plannedElevation = TrackElevation.analyze(planned.points, dem).takeIf { it.hasData }
    val actualElevation = TrackElevation.analyze(recorded.points, dem).takeIf { it.hasData }

    return TrackCompareResult(
        plannedName = planned.name,
        lateralAvgMeters = lateralAvg,
        lateralMaxMeters = lateralMax,
        offRouteShare = offShare,
        plannedDistanceMeters = planned.distanceMeters,
        actualDistanceMeters = actualDistance,
        plannedTimeSeconds = planned.plannedTimeSeconds,
        actualTimeSeconds = actualTime,
        plannedElevation = plannedElevation,
        actualElevation = actualElevation,
    )
}
