package ru.azimut.app.core.export

import ru.azimut.app.core.dem.DemStore
import ru.azimut.app.core.dem.TrackElevation
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.protected.ProtectedPolygon
import ru.azimut.app.core.protected.ProtectedWatch
import ru.azimut.app.core.platform.PlatformFiles

/** Offline pre-ride check: stats, DEM coverage honesty, zone contacts. */
data class TrackPrecheck(
    val distanceMeters: Double,
    val profile: ru.azimut.app.core.dem.ElevationProfile?,
    val demAvailable: Boolean,
    val demCoversTrack: Boolean,
    val zoneHits: List<String>,
    val fords: Int,
    val fordDataAvailable: Boolean,
)

object TrackPrecheckEngine {
    /**
     * [zones] are the loaded protected polygons; empty list with hasZones=false means
     * the OOPT layer is not loaded and the report says so instead of "all clear".
     */
    fun check(
        track: Track,
        dem: DemStore,
        zones: List<ProtectedPolygon>,
        zonesLoaded: Boolean,
    ): TrackPrecheck {
        val elev = TrackElevation.analyze(track, dem)
        val demAvailable = dem.hasAnyTiles()
        val watch = ProtectedWatch(zones)
        val sampleStep = (track.points.size / 400).coerceAtLeast(1)
        val zoneHits = watch.onRoute(
            track.points.map { GeoPoint(it.lat, it.lon) },
            sampleEveryNth = sampleStep,
        ).map { hit ->
            val access = hit.polygon.access
            val name = hit.polygon.name.ifBlank { "OOPT" }
            if (access.isNotBlank()) "$name ($access)" else name
        }.distinct()
        // Fords come from the router's OSM edge tags: a planned route has that data
        // (possibly zero fords), while a recorded/imported track honestly has none.
        val fordDataAvailable = track.kind == KIND_ROUTE || track.fordCount > 0
        return TrackPrecheck(
            distanceMeters = track.distanceMeters,
            profile = elev.takeIf { it.hasData },
            demAvailable = demAvailable,
            demCoversTrack = elev.fullyCovered,
            zoneHits = zoneHits,
            fords = track.fordCount,
            fordDataAvailable = fordDataAvailable,
        )
    }
}

private fun DemStore.hasAnyTiles(): Boolean = PlatformFiles.listFiles("dem").any { f ->
    f.endsWith(".hgt", true) || f.endsWith(".tif", true) || f.endsWith(".tiff", true)
}
