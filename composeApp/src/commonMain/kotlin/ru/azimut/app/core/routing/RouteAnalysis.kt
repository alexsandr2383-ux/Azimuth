package ru.azimut.app.core.routing

import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.distanceMeters
import kotlin.math.abs
import kotlin.math.roundToInt

enum class RouteSectionKind { ROAD, TRAIL }

/** Consecutive route points that share one kind; neighbouring sections share their boundary point. */
data class RouteSection(val kind: RouteSectionKind, val points: List<GeoPoint>)

/** One BRouter message: the OSM tags of the way that ends at ([lat], [lon]). */
data class WayMessage(val lat: Double, val lon: Double, val tags: Map<String, String>)

/** A built route as a planned track: GPX-exportable, with trail flags kept per point. */
fun RouteResult.toPlannedTrack(id: String, name: String, profile: String, createdAt: Long): Track {
    val points = ArrayList<TrackPoint>()
    val parts = sections.ifEmpty { listOf(RouteSection(RouteSectionKind.ROAD, this.points)) }
    parts.forEachIndexed { index, section ->
        val trail = section.kind == RouteSectionKind.TRAIL
        // Sections share their boundary point; it belongs to the section that ends there.
        val own = if (index == 0) section.points else section.points.drop(1)
        own.forEachIndexed { i, p ->
            points += TrackPoint(lat = p.lat, lon = p.lon, altitude = p.altitude, trail = trail && (index > 0 || i > 0))
        }
    }
    return Track(
        id = id,
        name = name,
        profile = profile,
        startedAt = createdAt,
        points = points,
        distanceMeters = distanceMeters.toDouble(),
        kind = KIND_ROUTE,
        plannedTimeSeconds = timeSeconds.toDouble(),
    )
}

/** Splits a track into road/trail sections using the per-point trail flags. */
fun trackSections(points: List<TrackPoint>): List<RouteSection> {
    if (points.size < 2) return emptyList()
    val sections = mutableListOf<RouteSection>()
    var kind = if (points[1].trail) RouteSectionKind.TRAIL else RouteSectionKind.ROAD
    var current = mutableListOf(GeoPoint(points[0].lat, points[0].lon, points[0].altitude))
    for (i in 1 until points.size) {
        val p = points[i]
        val next = if (p.trail) RouteSectionKind.TRAIL else RouteSectionKind.ROAD
        val geo = GeoPoint(p.lat, p.lon, p.altitude)
        if (next != kind) {
            sections += RouteSection(kind, current)
            current = mutableListOf(current.last())
            kind = next
        }
        current += geo
    }
    sections += RouteSection(kind, current)
    return sections
}

object RouteAnalysis {
    private val TRAIL_HIGHWAYS = setOf("path", "footway", "bridleway", "pedestrian", "cycleway")
    private val BANNED_VALUES = setOf("no", "private", "agricultural", "forestry")
    private const val GRADE_WINDOW_M = 100.0

    /**
     * Parses `OsmTrack.aggregateMessages()`: tab-separated
     * `lon*1e6, lat*1e6, elevation, distance, ..., WayTags, NodeTags, ...`.
     */
    fun parseMessages(lines: List<String>): List<WayMessage> = lines.mapNotNull { line ->
        val cols = line.split('\t')
        val lon = cols.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val lat = cols.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        val tags = cols.getOrNull(9).orEmpty()
            .split(' ')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
            }
            .toMap()
        WayMessage(lat / 1_000_000.0, lon / 1_000_000.0, tags)
    }

    fun isTrail(tags: Map<String, String>): Boolean = tags["highway"] in TRAIL_HIGHWAYS

    /** Most specific access tag wins, as in the enduro profile. */
    fun isMotorBanned(tags: Map<String, String>): Boolean {
        val value = tags["motorcycle"] ?: tags["motor_vehicle"] ?: tags["vehicle"] ?: tags["access"]
        return value in BANNED_VALUES
    }

    fun isFord(tags: Map<String, String>): Boolean = tags["ford"] in setOf("yes", "stepping_stones")

    fun analyze(base: RouteResult, messages: List<WayMessage>): RouteResult {
        val points = base.points
        if (points.size < 2) return base

        // Each message covers the edges up to and including its end point.
        val edgeTags = ArrayList<Map<String, String>>(points.size - 1)
        var k = 0
        for (i in 1 until points.size) {
            edgeTags += messages.getOrNull(k)?.tags.orEmpty()
            val end = messages.getOrNull(k)
            if (end != null && abs(end.lat - points[i].lat) < 2e-6 && abs(end.lon - points[i].lon) < 2e-6) k++
        }

        val sections = mutableListOf<RouteSection>()
        var trailMeters = 0.0
        var restrictedMeters = 0.0
        var fordCount = 0
        var previousFord = false
        var current = mutableListOf(points[0])
        var currentKind = if (isTrail(edgeTags[0])) RouteSectionKind.TRAIL else RouteSectionKind.ROAD
        for (i in 1 until points.size) {
            val tags = edgeTags[i - 1]
            val kind = if (isTrail(tags)) RouteSectionKind.TRAIL else RouteSectionKind.ROAD
            if (kind != currentKind) {
                sections += RouteSection(currentKind, current)
                current = mutableListOf(points[i - 1])
                currentKind = kind
            }
            current += points[i]
            val length = distanceMeters(points[i - 1], points[i])
            if (kind == RouteSectionKind.TRAIL) trailMeters += length
            if (isMotorBanned(tags)) restrictedMeters += length
            val ford = isFord(tags)
            if (ford && !previousFord) fordCount++
            previousFord = ford
        }
        sections += RouteSection(currentKind, current)

        return base.copy(
            descentMeters = descent(points),
            maxGradePercent = maxGradePercent(points),
            sections = sections,
            trailMeters = trailMeters.roundToInt(),
            trailCount = sections.count { it.kind == RouteSectionKind.TRAIL },
            restrictedMeters = restrictedMeters.roundToInt(),
            fordCount = fordCount,
        )
    }

    private fun descent(points: List<GeoPoint>): Int {
        var total = 0.0
        var last: Double? = null
        for (p in points) {
            val ele = p.altitude ?: continue
            if (last != null && ele < last) total += last - ele
            last = ele
        }
        return total.roundToInt()
    }

    /** Steepest grade over any stretch of at least [GRADE_WINDOW_M], which smooths DEM noise. */
    private fun maxGradePercent(points: List<GeoPoint>): Int {
        val withEle = points.filter { it.altitude != null }
        if (withEle.size < 2) return 0
        val cumulative = DoubleArray(withEle.size)
        for (i in 1 until withEle.size) {
            cumulative[i] = cumulative[i - 1] + distanceMeters(withEle[i - 1], withEle[i])
        }
        var best = 0.0
        var j = 0
        for (i in withEle.indices) {
            if (j < i) j = i
            while (j < withEle.size - 1 && cumulative[j] - cumulative[i] < GRADE_WINDOW_M) j++
            val run = cumulative[j] - cumulative[i]
            if (run < GRADE_WINDOW_M) break
            val rise = abs(withEle[j].altitude!! - withEle[i].altitude!!)
            best = maxOf(best, rise / run * 100.0)
        }
        return best.roundToInt()
    }
}
