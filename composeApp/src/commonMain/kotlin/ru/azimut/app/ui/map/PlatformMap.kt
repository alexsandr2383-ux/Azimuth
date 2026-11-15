package ru.azimut.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.routing.RouteSection

@Composable
expect fun PlatformMap(
    modifier: Modifier,
    currentLocation: GeoPoint?,
    trackPoints: List<TrackPoint>,
    waypoints: List<Waypoint>,
    routePoints: List<GeoPoint>,
    routeSections: List<RouteSection>,
    /** Start, via points and finish of the route being planned. */
    routeStops: List<GeoPoint> = emptyList(),
    /** Fuel-ramp markers on the planned route: reserve crossing and dry-tank point. */
    fuelReserve: GeoPoint? = null,
    fuelEmpty: GeoPoint? = null,
    mapLayer: String,
    mapStyle: String,
    baseOpacity: Float = 1f,
    overlayEnabled: Boolean = false,
    overlayLayer: String = "satellite_hd",
    overlayOpacity: Float = 0.6f,
    showPois: Boolean = true,
    /** Map rotation in degrees from true north (0 = north up). */
    mapBearing: Float = 0f,
    /** Re-applies [mapBearing] even when it did not change (snap back after manual rotation). */
    orientationResetKey: Int = 0,
    /** Actual camera bearing after a user gesture; null/ignored on platforms without rotation. */
    onCameraBearingChange: (Float) -> Unit = {},
    /** Camera to restore at map creation (last saved viewport); null = platform default. */
    initialCamera: MapCamera? = null,
    /** Reports the viewport centre and zoom after each camera move. */
    onViewportChange: (lat: Double, lon: Double, zoom: Double) -> Unit = { _, _, _ -> },
    /** Increment to ease the camera back to the current location. */
    recenterKey: Int = 0,
    /** Increment to download the visible area for offline use (mobile; desktop has its own button). */
    areaDownloadKey: Int = 0,
    /** Visible-area offline download progress; complete/error terminate a run. */
    onAreaDownloadProgress: (downloaded: Long, total: Long, complete: Boolean, error: Boolean) -> Unit = { _, _, _, _ -> },
    /** Keep the current location centred (heading-up modes). */
    followLocation: Boolean = false,
    /** Desktop: route name and line to offer a "maps along the route" download for. */
    downloadCorridor: Pair<String, List<GeoPoint>>? = null,
    /** Saved tracks drawn as thin background lines (journal eye toggle). */
    backgroundTracks: List<List<GeoPoint>> = emptyList(),
    /** Protected area (OOPT) polygons drawn as translucent fill + outline. */
    protectedAreas: List<ru.azimut.app.core.protected.ProtectedPolygon> = emptyList(),
    /** Per-segment track grade in percent (same count as trackPoints-1); null = plain color. */
    trackGrades: List<Float>? = null,
    /** Sunset azimuth at the current position (terrain-corrected when DEM allows); null = hide. */
    sunsetAzimuthDeg: Double? = null,
    /** Jump the camera to an arbitrary point; [gotoKey] increments per request. */
    gotoTarget: GeoPoint? = null,
    gotoKey: Int = 0,
    /** Pre-rendered hillshade tiles from the DEM (settings.hillshade). */
    hillshadeTiles: List<ru.azimut.app.core.dem.HillshadeTile> = emptyList(),
    onDownloadCorridorDone: () -> Unit = {},
    onTap: (GeoPoint) -> Unit,
    onSecondaryTap: (GeoPoint, Offset) -> Unit,
)

/** A map viewport: centre point and zoom level. */
data class MapCamera(val lat: Double, val lon: Double, val zoom: Double)
