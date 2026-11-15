package ru.azimut.app.core.routing

import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.TransportProfile
import ru.azimut.app.core.platform.PlatformFiles
import kotlin.math.floor
import kotlin.math.min

enum class RouteError {
    NONE,
    NO_SEGMENTS,
    NO_ROUTE,
    FAILED,
}

data class RouteRequest(
    val from: GeoPoint,
    val to: GeoPoint,
    val profile: TransportProfile,
    /** Intermediate points, visited in order. */
    val via: List<GeoPoint> = emptyList(),
    /** Route along roads only: no hiking trails, ways a motorcycle can legally use. */
    val avoidTrails: Boolean = false,
    /** Explorer mode: pick the most permissive profile even where passage is doubtful. */
    val susanin: Boolean = false,
) {
    val stops: List<GeoPoint> get() = listOf(from) + via + to
}

data class RouteResult(
    val distanceMeters: Int = 0,
    val ascendMeters: Int = 0,
    val timeSeconds: Int = 0,
    val points: List<GeoPoint> = emptyList(),
    val error: RouteError = RouteError.NONE,
    val details: String? = null,
    val descentMeters: Int = 0,
    val maxGradePercent: Int = 0,
    val sections: List<RouteSection> = emptyList(),
    val trailMeters: Int = 0,
    val trailCount: Int = 0,
    val restrictedMeters: Int = 0,
    val fordCount: Int = 0,
) {
    val ok: Boolean
        get() = error == RouteError.NONE && points.size >= 2
}

expect object RouteEngine {
    suspend fun route(request: RouteRequest): RouteResult
}

object RouteData {
    const val SEGMENTS_DIR = "routing/segments4"
    const val PROFILES_DIR = "routing/profiles2"

    fun segmentCount(): Int =
        PlatformFiles.listFiles(SEGMENTS_DIR).count { it.endsWith(".rd5", ignoreCase = true) }

    /** BRouter segment covering [point]: 5°×5° tiles named by their south-west corner, e.g. E35_N40.rd5. */
    fun segmentFileName(point: GeoPoint): String {
        val lon = (floor((point.lon + 180.0) / 5.0) * 5.0 - 180.0).toInt()
        val lat = (floor((point.lat + 90.0) / 5.0) * 5.0 - 90.0).toInt()
        val lonPart = if (lon < 0) "W${-lon}" else "E$lon"
        val latPart = if (lat < 0) "S${-lat}" else "N$lat"
        return "${lonPart}_$latPart.rd5"
    }

    /** Segments in the bounding box of [stops] that are not imported yet. */
    fun missingSegments(stops: List<GeoPoint>): List<String> {
        val names = linkedSetOf<String>()
        val minLat = stops.minOf { it.lat }
        val maxLat = stops.maxOf { it.lat }
        val minLon = stops.minOf { it.lon }
        val maxLon = stops.maxOf { it.lon }
        var lat = minLat
        while (true) {
            var lon = minLon
            while (true) {
                names += segmentFileName(GeoPoint(lat, lon))
                if (lon >= maxLon) break
                lon = min(lon + 5.0, maxLon)
            }
            if (lat >= maxLat) break
            lat = min(lat + 5.0, maxLat)
        }
        val present = PlatformFiles.listFiles(SEGMENTS_DIR).map { it.lowercase() }.toSet()
        return names.filter { it.lowercase() !in present }
    }
}

fun TransportProfile.brouterProfileFile(): String = when (this) {
    TransportProfile.FOOT -> "hiking-mountain.brf"
    TransportProfile.BIKE -> "trekking.brf"
    TransportProfile.MTB -> "mtb.brf"
    TransportProfile.CAR -> "car-fast.brf"
    TransportProfile.ENDURO,
    TransportProfile.ATV,
    -> "enduro.brf"
    TransportProfile.MOTORCYCLE -> "moped.brf"
    TransportProfile.OFFROAD -> "car-vario.brf"
}

/**
 * BRouter profile for a request, honouring the route-dialog switches.
 * "Avoid trails" pins the profile to roads and vehicle tracks; Explorer (Сусанин) mode
 * picks the most permissive profile, where a motorcycle may not get through at all.
 */
fun RouteRequest.routingProfileFile(): String {
    if (avoidTrails) return when (profile) {
        TransportProfile.ENDURO, TransportProfile.ATV,
        TransportProfile.MOTORCYCLE, TransportProfile.OFFROAD,
        -> "car-vario.brf"
        TransportProfile.MTB -> "trekking.brf"
        else -> profile.brouterProfileFile()
    }
    if (susanin) return when (profile) {
        TransportProfile.BIKE -> "mtb.brf"
        TransportProfile.MOTORCYCLE -> "enduro.brf"
        TransportProfile.CAR -> "car-vario.brf"
        else -> profile.brouterProfileFile()
    }
    return profile.brouterProfileFile()
}

fun formatRouteDuration(seconds: Int): String {
    if (seconds < 60) return "$seconds с"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}
