package ru.azimut.app.core.dem

import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.distanceMeters
import kotlin.math.abs

/** Height profile of a track, sampled from the DEM store. */
data class ElevationProfile(
    /** Height per track point (same index as track.points); null = DEM void/missing. */
    val heights: List<Double?>,
    val minMeters: Double?,
    val maxMeters: Double?,
    /** Cumulative up, with a smoothing threshold so GPS jitter does not count. */
    val ascendMeters: Double,
    /** Cumulative down. */
    val descendMeters: Double,
    /** Max uphill grade in percent over sections >= minGradeSectionMeters. */
    val maxGradePercent: Double?,
    /** Index of the point after which the max grade section starts; null when unknown. */
    val maxGradeAtIndex: Int?,
    /** 0..1 share of points that got a DEM height; below 1 means partial coverage. */
    val coverage: Double,
) {
    val hasData: Boolean get() = heights.any { it != null }
    val fullyCovered: Boolean get() = coverage >= 0.999
}

object TrackElevation {
    /** Points closer than this are skipped for grade calculation (GPS noise amplifies slope). */
    private const val MIN_SECTION_M = 15.0

    /** Ascend/descend accumulate only after the height drifts this far (classic hysteresis). */
    private const val SMOOTH_M = 3.0

    /**
     * Samples heights for every point: prefers the recorded GPS altitude? No — the DEM
     * is the reference here; the recorded altitude is used as fallback when DEM has no tile.
     */
    fun analyze(points: List<TrackPoint>, dem: DemStore): ElevationProfile {
        val heights = points.map { point -> dem.heightAt(point.lat, point.lon) ?: point.altitude }
        val known = heights.filterNotNull()
        val min = known.minOrNull()
        val max = known.maxOrNull()

        var ascend = 0.0
        var descend = 0.0
        var anchor: Double? = null
        for (h in heights) {
            val current = h ?: continue
            val base = anchor
            if (base == null) {
                anchor = current
                continue
            }
            when {
                current > base + SMOOTH_M -> {
                    ascend += current - base
                    anchor = current
                }
                current < base - SMOOTH_M -> {
                    descend += base - current
                    anchor = current
                }
            }
        }

        var maxGrade: Double? = null
        var maxGradeIdx: Int? = null
        var lastKnown = -1
        for (i in points.indices) {
            if (heights[i] == null) continue
            if (lastKnown >= 0) {
                val dx = distanceMeters(
                    GeoPoint(points[lastKnown].lat, points[lastKnown].lon),
                    GeoPoint(points[i].lat, points[i].lon),
                )
                if (dx >= MIN_SECTION_M) {
                    val dh = heights[i]!! - heights[lastKnown]!!
                    val grade = abs(dh) / dx * 100.0
                    if (maxGrade == null || grade > maxGrade) {
                        maxGrade = grade
                        maxGradeIdx = i
                    }
                }
            }
            lastKnown = i
        }

        val sampled = heights.count { it != null }
        val coverage = if (points.isEmpty()) 0.0 else sampled.toDouble() / points.size
        return ElevationProfile(
            heights = heights,
            minMeters = min,
            maxMeters = max,
            ascendMeters = ascend,
            descendMeters = descend,
            maxGradePercent = maxGrade,
            maxGradeAtIndex = maxGradeIdx,
            coverage = coverage,
        )
    }

    fun analyze(track: Track, dem: DemStore): ElevationProfile = analyze(track.points, dem)
}
