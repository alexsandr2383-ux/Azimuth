package ru.azimut.app.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Douglas–Peucker line simplification. Recorded tracks at 1 Hz carry tens of
 * thousands of points; keeping only shape-defining ones cuts GPX size, sync
 * traffic and polyline rendering cost with a shape error below GPS accuracy.
 * Always keeps the first and the last point; returns a subset of the input.
 * Iterative stack instead of recursion — a 36k-point track must not risk a
 * stack overflow.
 */
fun <T> simplifyPoints(
    points: List<T>,
    lat: (T) -> Double,
    lon: (T) -> Double,
    epsilonMeters: Double = 1.5,
): List<T> {
    if (points.size <= 2 || epsilonMeters <= 0.0) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.size - 1)
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        if (end - start < 2) continue
        var maxDist = 0.0
        var maxIdx = -1
        for (i in start + 1 until end) {
            val d = pointSegmentDistanceMeters(
                lat(points[i]), lon(points[i]),
                lat(points[start]), lon(points[start]),
                lat(points[end]), lon(points[end]),
            )
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }
        if (maxDist > epsilonMeters && maxIdx > 0) {
            keep[maxIdx] = true
            stack.addLast(start to maxIdx)
            stack.addLast(maxIdx to end)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Simplification of a recorded track (TrackPoint keeps its timestamp). */
fun simplifyTrack(points: List<TrackPoint>, epsilonMeters: Double = 1.5): List<TrackPoint> {
    if (points.none { it.segStart }) return simplifyPoints(points, { it.lat }, { it.lon }, epsilonMeters)
    // Segments are simplified separately so gap boundaries (segStart points) always survive.
    return splitSegments(points).flatMap { simplifyPoints(it, { p -> p.lat }, { p -> p.lon }, epsilonMeters) }
}

/** Splits a recorded track into continuous runs at every [TrackPoint.segStart]. */
fun splitSegments(points: List<TrackPoint>): List<List<TrackPoint>> {
    val result = mutableListOf<List<TrackPoint>>()
    var from = 0
    for (i in 1 until points.size) {
        if (points[i].segStart) {
            result.add(points.subList(from, i))
            from = i
        }
    }
    if (points.isNotEmpty()) result.add(points.subList(from, points.size))
    return result
}

/** Simplification of a lightweight lat/lon line (GeoPoint). */
fun simplifyGeoLine(points: List<GeoPoint>, epsilonMeters: Double = 1.5): List<GeoPoint> =
    simplifyPoints(points, { it.lat }, { it.lon }, epsilonMeters)

/** Perpendicular distance point→segment in metres (local flat approximation). */
fun pointSegmentDistanceMeters(
    pLat: Double, pLon: Double,
    aLat: Double, aLon: Double,
    bLat: Double, bLon: Double,
): Double {
    val latRef = (aLat + bLat) / 2.0
    val kx = 111_320.0 * cos(latRef * Math.PI / 180.0)
    val ky = 111_320.0
    val ax = aLon * kx
    val ay = aLat * ky
    val bx = bLon * kx
    val by = bLat * ky
    val px = pLon * kx
    val py = pLat * ky
    val dx = bx - ax
    val dy = by - ay
    val lenSq = dx * dx + dy * dy
    if (lenSq < 1e-12) {
        val ex = px - ax
        val ey = py - ay
        return sqrt(ex * ex + ey * ey)
    }
    var t = ((px - ax) * dx + (py - ay) * dy) / lenSq
    t = t.coerceIn(0.0, 1.0)
    val ex = px - (ax + t * dx)
    val ey = py - (ay + t * dy)
    return sqrt(ex * ex + ey * ey)
}
