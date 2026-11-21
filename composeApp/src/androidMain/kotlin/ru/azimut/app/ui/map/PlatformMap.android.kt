package ru.azimut.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.min
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.net.ConnectivityReceiver
import ru.azimut.app.ui.components.GRADE_COLORS
import ru.azimut.app.ui.components.gradeBucketOf
import ru.azimut.app.ui.components.gradeHex
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.azimut.app.core.map.MapStyle
import ru.azimut.app.core.map.MapStyles
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.routing.RouteSection
import java.io.File
import ru.azimut.app.core.routing.RouteSectionKind

@Composable
actual fun PlatformMap(
    modifier: Modifier,
    currentLocation: GeoPoint?,
    trackPoints: List<TrackPoint>,
    waypoints: List<Waypoint>,
    routePoints: List<GeoPoint>,
    routeSections: List<RouteSection>,
    routeStops: List<GeoPoint>,
    fuelReserve: GeoPoint?,
    fuelEmpty: GeoPoint?,
    mapLayer: String,
    mapStyle: String,
    baseOpacity: Float,
    overlayEnabled: Boolean,
    overlayLayer: String,
    overlayOpacity: Float,
    cacheOnly: Boolean,
    shownTrackPoints: List<GeoPoint>,
    shownTrackGrades: List<Float>?,
    showPois: Boolean,
    mapBearing: Float,
    orientationResetKey: Int,
    onCameraBearingChange: (Float) -> Unit,
    initialCamera: MapCamera?,
    onViewportChange: (lat: Double, lon: Double, zoom: Double) -> Unit,
    recenterKey: Int,
    areaDownloadKey: Int,
    onAreaDownloadProgress: (downloaded: Long, total: Long, complete: Boolean, error: Boolean) -> Unit,
    followLocation: Boolean,
    downloadCorridor: Pair<String, List<GeoPoint>>?,
    /** Saved tracks drawn as thin background lines (journal eye toggle). */
    backgroundTracks: List<List<GeoPoint>>,
    protectedAreas: List<ru.azimut.app.core.protected.ProtectedPolygon>,
    trackGrades: List<Float>?,
    sunsetAzimuthDeg: Double?,
    gotoTarget: GeoPoint?,
    gotoKey: Int,
    hillshadeTiles: List<ru.azimut.app.core.dem.HillshadeTile>,
    headingDegrees: Float?,
    corridorKey: Int,
    corridorBounds: Pair<GeoPoint, GeoPoint>?,
    corridorMaxZoom: Int,
    onCorridorProgress: (Long, Long, Boolean, Boolean) -> Unit,
    onDownloadCorridorDone: () -> Unit,
    onWaypointTap: (Waypoint) -> Unit,
    onTap: (GeoPoint) -> Unit,
    onSecondaryTap: (GeoPoint, Offset) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val onTapState by rememberUpdatedState(onTap)
    val onSecondaryTapState by rememberUpdatedState(onSecondaryTap)
    val onWaypointTapState by rememberUpdatedState(onWaypointTap)
    val waypointsState by rememberUpdatedState(waypoints)
    val onCameraBearingState by rememberUpdatedState(onCameraBearingChange)
    val onViewportChangeState by rememberUpdatedState(onViewportChange)
    val currentLocationState by rememberUpdatedState(currentLocation)
    val layerState by rememberUpdatedState(mapLayer)
    val styleState by rememberUpdatedState(mapStyle)
    // Cache-only: force MapLibre off the network. A forced value is sticky —
    // connectivity broadcasts do not reset it until it is cleared with null.
    LaunchedEffect(cacheOnly) {
        ConnectivityReceiver.instance(context).setConnected(if (cacheOnly) false else null)
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var appliedStyleKey by remember { mutableStateOf(styleKey(mapLayer, mapStyle)) }
    var centered by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            if (map == null) {
                view.getMapAsync { mapLibreMap ->
                    map = mapLibreMap
                    // Restore the last saved viewport before the first frame; skip the
                    // "jump to first fix" behaviour when a saved position exists.
                    val restored = initialCamera
                    if (restored != null) {
                        mapLibreMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(restored.lat, restored.lon),
                                restored.zoom,
                            )
                        )
                        centered = true
                    }
                    // The app has its own orientation control in the right-side button stack;
                    // MapLibre's compass sat in the top-right corner and fought that control.
                    mapLibreMap.uiSettings.isCompassEnabled = false
                    mapLibreMap.uiSettings.isRotateGesturesEnabled = true
                    mapLibreMap.addOnCameraIdleListener {
                        onCameraBearingState(mapLibreMap.cameraPosition.bearing.toFloat())
                        mapLibreMap.cameraPosition.target?.let { target ->
                            onViewportChangeState(
                                target.latitude,
                                target.longitude,
                                mapLibreMap.cameraPosition.zoom,
                            )
                        }
                    }
                    mapLibreMap.addOnMapClickListener { latLng ->
                        val screen = mapLibreMap.projection.toScreenLocation(latLng)
                        val features = mapLibreMap.queryRenderedFeatures(screen, "waypoints-layer")
                        val wpId = features.firstOrNull()?.getStringProperty("id")
                        val wp = wpId?.let { id -> waypointsState.firstOrNull { it.id == id } }
                        if (wp != null) onWaypointTapState(wp)
                        else onTapState(GeoPoint(latLng.latitude, latLng.longitude))
                        true
                    }
                    mapLibreMap.addOnMapLongClickListener { latLng ->
                        // Screen position in px, so the context menu opens under the finger.
                        val screen = mapLibreMap.projection.toScreenLocation(latLng)
                        onSecondaryTapState(
                            GeoPoint(latLng.latitude, latLng.longitude),
                            Offset(screen.x, screen.y),
                        )
                        true
                    }
                    appliedStyleKey = styleKey(layerState, styleState)
                    applyMapStyle(mapLibreMap, layerState, styleState) { styleVersion++ }
                }
            }
        },
    )

    LaunchedEffect(mapLayer, mapStyle) {
        val key = styleKey(mapLayer, mapStyle)
        if (key != appliedStyleKey) {
            appliedStyleKey = key
            val target = map ?: return@LaunchedEffect
            applyMapStyle(target, mapLayer, mapStyle) { styleVersion++ }
        }
    }

    LaunchedEffect(styleVersion) {
        val style = map?.style ?: return@LaunchedEffect
        ensureSources(style)
    }

    LaunchedEffect(routePoints, routeSections, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("route-src") ?: return@LaunchedEffect
        val sections = routeSections.ifEmpty { listOf(RouteSection(RouteSectionKind.ROAD, routePoints)) }
        val features = sections.filter { it.points.size >= 2 }.map { section ->
            Feature.fromGeometry(
                LineString.fromLngLats(section.points.map { Point.fromLngLat(it.lon, it.lat) })
            ).apply { addBooleanProperty("trail", section.kind == RouteSectionKind.TRAIL) }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    LaunchedEffect(routeStops, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("stops-src") ?: return@LaunchedEffect
        val features = routeStops.mapIndexed { index, stop ->
            Feature.fromGeometry(Point.fromLngLat(stop.lon, stop.lat)).apply {
                addStringProperty(
                    "role",
                    when (index) {
                        0 -> "start"
                        routeStops.lastIndex -> "finish"
                        else -> "via"
                    },
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    // Fuel ramps: reserve crossing and dry-tank point on the planned route.
    LaunchedEffect(fuelReserve, fuelEmpty, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("fuel-src") ?: return@LaunchedEffect
        val features = listOfNotNull(
            fuelReserve?.let {
                Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))
                    .apply { addStringProperty("role", "reserve") }
            },
            fuelEmpty?.let {
                Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))
                    .apply { addStringProperty("role", "empty") }
            },
        )
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    // Recorded track: single feature, or grade-bucketed features when DEM grades are available.
    LaunchedEffect(trackPoints, trackGrades, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("track-src") ?: return@LaunchedEffect
        val grades = trackGrades
        if (grades != null && grades.size == trackPoints.size - 1 && trackPoints.size >= 2) {
            val features = withContext(Dispatchers.Default) {
                gradeFeatures(trackPoints.map { it.lat to it.lon }, grades)
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        } else {
            val points = ru.azimut.app.core.model.simplifyTrack(trackPoints)
            .map { Point.fromLngLat(it.lon, it.lat) }
            source.setGeoJson(
                if (points.size >= 2) {
                    FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
                } else {
                    FeatureCollection.fromFeatures(emptyArray())
                }
            )
        }
    }

    // Track opened from the journal: grade-colored when grades are available, else plain orange.
    LaunchedEffect(shownTrackPoints, shownTrackGrades, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("shown-src") ?: return@LaunchedEffect
        val grades = shownTrackGrades
        if (grades != null && grades.size == shownTrackPoints.size - 1 && shownTrackPoints.size >= 2) {
            val features = withContext(Dispatchers.Default) {
                gradeFeatures(shownTrackPoints.map { it.lat to it.lon }, grades)
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        } else {
            val points = ru.azimut.app.core.model.simplifyGeoLine(shownTrackPoints)
                .map { Point.fromLngLat(it.lon, it.lat) }
            source.setGeoJson(
                if (points.size >= 2) {
                    FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
                } else {
                    FeatureCollection.fromFeatures(emptyArray())
                }
            )
        }
    }

    LaunchedEffect(waypoints, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("waypoints-src") ?: return@LaunchedEffect
        // Building 10k GeoJSON features on the UI thread blocks the map for a moment.
        val features = withContext(Dispatchers.Default) {
            waypoints.map { waypoint ->
                Feature.fromGeometry(Point.fromLngLat(waypoint.lon, waypoint.lat)).apply {
                    addStringProperty("id", waypoint.id)
                    addStringProperty("name", waypoint.name)
                    addStringProperty("type", waypoint.poiType)
                    addStringProperty("icon", "poi-" + waypoint.poiType)
                }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    LaunchedEffect(currentLocation, headingDegrees, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("location-src") ?: return@LaunchedEffect
        if (currentLocation == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        } else {
            val props = com.google.gson.JsonObject()
            props.addProperty("heading", headingDegrees ?: 0f)
            source.setGeoJson(
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(
                        Point.fromLngLat(currentLocation.lon, currentLocation.lat),
                        props,
                    )
                )
            )
        }
    }

    // Saved tracks from the journal: thin background lines under everything else.
    LaunchedEffect(backgroundTracks, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("saved-tracks-src") ?: return@LaunchedEffect
        val features = withContext(Dispatchers.Default) {
            backgroundTracks.mapIndexed { index, line ->
                Feature.fromGeometry(
                    // Douglas-Peucker 1.5 m: fewer LatLngs for MapLibre to tessellate.
                    LineString.fromLngLats(
                        ru.azimut.app.core.model.simplifyGeoLine(line)
                            .map { Point.fromLngLat(it.lon, it.lat) }
                    )
                ).apply { addNumberProperty("c", index) }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    // Protected areas (OOPT): translucent fill + solid outline under the route lines.
    LaunchedEffect(protectedAreas, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("protected-src") ?: return@LaunchedEffect
        val features = withContext(Dispatchers.Default) {
            protectedAreas.mapNotNull { polygon ->
                val rings = polygon.rings.map { ring ->
                    ring.map { p -> Point.fromLngLat(p[0], p[1]) }
                }
                if (rings.isEmpty()) return@mapNotNull null
                Feature.fromGeometry(
                    org.maplibre.geojson.Polygon.fromLngLats(rings)
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    // Sunset azimuth arrow: a short dashed yellow line at the given azimuth from the fix.
    LaunchedEffect(currentLocation, sunsetAzimuthDeg, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("sunset-src") ?: return@LaunchedEffect
        val az = sunsetAzimuthDeg
        val loc = currentLocation
        if (az == null || loc == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        } else {
            val lengthM = 2_000.0
            val dLat = (lengthM * kotlin.math.cos(az * kotlin.math.PI / 180.0)) / 111_320.0
            val dLon = (lengthM * kotlin.math.sin(az * kotlin.math.PI / 180.0)) /
                (111_320.0 * kotlin.math.cos(loc.lat * kotlin.math.PI / 180.0))
            val line = LineString.fromLngLats(
                listOf(Point.fromLngLat(loc.lon, loc.lat), Point.fromLngLat(loc.lon + dLon, loc.lat + dLat)),
            )
            source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(line)))
        }
    }

    // Hillshade overlays from the DEM: one ImageSource per tile.
    LaunchedEffect(hillshadeTiles, styleVersion) {
        val style = map?.style ?: return@LaunchedEffect
        val wanted = hillshadeTiles.associateBy { it.name }
        for (tile in wanted.values) {
            val id = "shade-" + tile.name
            val source = style.getSource(id)
            val quad = org.maplibre.android.geometry.LatLngQuad(
                org.maplibre.android.geometry.LatLng(tile.latTop, tile.lonLeft),
                org.maplibre.android.geometry.LatLng(tile.latTop, tile.lonRight),
                org.maplibre.android.geometry.LatLng(tile.latBottom, tile.lonRight),
                org.maplibre.android.geometry.LatLng(tile.latBottom, tile.lonLeft),
            )
            val bitmap = android.graphics.Bitmap.createBitmap(
                tile.width, tile.height, android.graphics.Bitmap.Config.ARGB_8888,
            ).apply { setPixels(tile.pixels, 0, tile.width, 0, 0, tile.width, tile.height) }
            val existing = source as? org.maplibre.android.style.sources.ImageSource
            if (existing == null) {
                style.addSource(org.maplibre.android.style.sources.ImageSource(id, quad, bitmap))
                style.addLayerBelow(
                    org.maplibre.android.style.layers.RasterLayer("shade-layer-" + tile.name, id),
                    "saved-tracks-line",
                )
            } else {
                // ImageSource has no in-place bitmap swap in this version: replace the source.
                style.removeLayer("shade-layer-" + tile.name)
                style.removeSource(id)
                style.addSource(org.maplibre.android.style.sources.ImageSource(id, quad, bitmap))
                style.addLayerBelow(
                    org.maplibre.android.style.layers.RasterLayer("shade-layer-" + tile.name, id),
                    "saved-tracks-line",
                )
            }
        }
        // Drop shade sources no longer requested (DEM removed, tile scrolled away).
        val stale = style.sources
            .filterIsInstance<org.maplibre.android.style.sources.ImageSource>()
            .map { it.id }
            .filter { it.startsWith("shade-") && it.removePrefix("shade-") !in wanted.keys }
        for (name in stale) {
            style.removeLayer("shade-layer-" + name.removePrefix("shade-"))
            style.removeSource(name)
        }
    }

    // "Go to coordinates": recentre on the requested point.
    LaunchedEffect(map, gotoKey) {
        val target = gotoTarget ?: return@LaunchedEffect
        if (gotoKey == 0) return@LaunchedEffect
        map?.easeCamera(
            CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(target.lat, target.lon),
                maxOf(map?.cameraPosition?.zoom ?: 12.0, 13.0),
            ),
            250,
        )
    }

    // In north-up mode location updates must not move the camera, or panning would be impossible.
    LaunchedEffect(map, mapBearing, orientationResetKey, followLocation, currentLocation.takeIf { followLocation }) {
        val target = map ?: return@LaunchedEffect
        val camera = CameraPosition.Builder(target.cameraPosition).bearing(mapBearing.toDouble())
        if (followLocation && currentLocation != null) {
            camera.target(LatLng(currentLocation.lat, currentLocation.lon))
        }
        target.easeCamera(CameraUpdateFactory.newCameraPosition(camera.build()), 250)
    }

    // "Back to location" button: re-centre on the current fix, keep the current zoom.
    LaunchedEffect(map, recenterKey) {
        val target = map ?: return@LaunchedEffect
        if (recenterKey == 0) return@LaunchedEffect
        val location = currentLocationState ?: return@LaunchedEffect
        target.easeCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.lat, location.lon),
                target.cameraPosition.zoom,
            ),
            250,
        )
    }

    // Visible-area offline download: the mobile counterpart of the desktop "Download" button.
    // MapLibre keeps the tiles in its own offline database, so the map serves them with no network.
    val areaProgressState by rememberUpdatedState(onAreaDownloadProgress)
    val corridorProgressState by rememberUpdatedState(onCorridorProgress)
    val corridorMaxZoomState by rememberUpdatedState(corridorMaxZoom)
    val corridorBoundsState by rememberUpdatedState(corridorBounds)
    var corridorRegion by remember { mutableStateOf<OfflineRegion?>(null) }
    LaunchedEffect(corridorKey) {
        if (corridorKey == 0) return@LaunchedEffect
        val target = map ?: return@LaunchedEffect
        if (corridorRegion != null) return@LaunchedEffect // one region at a time
        val bounds = corridorBoundsState ?: return@LaunchedEffect
        val style = MapStyles.byId(styleState)
        val styleUrl = offlineStyleUrl(context, style, layerState == "satellite")
            ?: return@LaunchedEffect
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            LatLngBounds.Builder()
                .include(LatLng(bounds.first.lat, bounds.first.lon))
                .include(LatLng(bounds.second.lat, bounds.second.lon))
                .build(),
            8.0,
            corridorMaxZoomState.toDouble().coerceAtLeast(8.0),
            context.resources.displayMetrics.density,
        )
        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            ByteArray(0),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    corridorRegion = region
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            corridorProgressState(
                                status.completedResourceCount,
                                status.requiredResourceCount,
                                status.isComplete,
                                false,
                            )
                            if (status.isComplete) corridorRegion = null
                        }

                        override fun onError(error: OfflineRegionError) {
                            corridorProgressState(0, 0, false, true)
                            corridorRegion = null
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            corridorProgressState(0, limit, false, true)
                            corridorRegion = null
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    corridorProgressState(0, 0, false, true)
                }
            },
        )
    }
    var offlineRegion by remember { mutableStateOf<OfflineRegion?>(null) }
    LaunchedEffect(areaDownloadKey) {
        if (areaDownloadKey == 0) return@LaunchedEffect
        val target = map ?: return@LaunchedEffect
        if (offlineRegion != null) return@LaunchedEffect // one region at a time
        val style = MapStyles.byId(styleState)
        val styleUrl = offlineStyleUrl(context, style, layerState == "satellite")
            ?: return@LaunchedEffect
        val zoom = target.cameraPosition.zoom
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            target.projection.visibleRegion.latLngBounds,
            zoom.coerceAtLeast(1.0),
            min(zoom + 2, style.androidMaxZoom.toDouble()).coerceAtLeast(zoom),
            context.resources.displayMetrics.density,
        )
        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            ByteArray(0),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    offlineRegion = region
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            areaProgressState(
                                status.completedResourceCount,
                                status.requiredResourceCount,
                                status.isComplete,
                                false,
                            )
                            if (status.isComplete) offlineRegion = null
                        }

                        override fun onError(error: OfflineRegionError) {
                            areaProgressState(0, 0, false, true)
                            offlineRegion = null
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            areaProgressState(0, limit, false, true)
                            offlineRegion = null
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    areaProgressState(0, 0, false, true)
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            // Stop downloads and drop the reference: the region stays in the
            // OfflineManager (downloaded tiles are kept) but is no longer driven.
            offlineRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
            offlineRegion = null
        }
    }

    LaunchedEffect(currentLocation) {
        val location = currentLocation ?: return@LaunchedEffect
        if (!centered) {
            centered = true
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lon), 14.0)
            )
        }
    }
}

private fun ensureSources(style: Style) {
    val gradeColors = GRADE_COLORS.map { gradeHex(it) }
    if (style.getSource("saved-tracks-src") == null) {
        // Registered first so journal tracks stay under the recorded/route lines.
        // One vivid color per track (cycled) instead of a single dull grey.
        style.addSource(GeoJsonSource("saved-tracks-src", FeatureCollection.fromFeatures(emptyArray())))
        BG_TRACK_COLORS.forEachIndexed { index, color ->
            style.addLayer(
                LineLayer("saved-tracks-line-$index", "saved-tracks-src").withProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineOpacity(0.9f),
                ).withFilter(Expression.eq(Expression.get("c"), index))
            )
        }
    }
    if (style.getSource("protected-src") == null) {
        style.addSource(GeoJsonSource("protected-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            org.maplibre.android.style.layers.FillLayer("protected-fill", "protected-src").withProperties(
                PropertyFactory.fillColor("#C62828"),
                PropertyFactory.fillOpacity(0.18f),
            )
        )
        style.addLayer(
            org.maplibre.android.style.layers.LineLayer("protected-line", "protected-src").withProperties(
                PropertyFactory.lineColor("#EF5350"),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.9f),
            )
        )
    }
    if (style.getSource("route-src") == null) {
        style.addSource(GeoJsonSource("route-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("route-line", "route-src").withProperties(
                PropertyFactory.lineColor("#1565C0"),
                PropertyFactory.lineWidth(6f),
            ).withFilter(Expression.eq(Expression.get("trail"), false))
        )
        // Trails: dashed orange so "TRAIL (not a road)" stands out from the road route.
        style.addLayer(
            LineLayer("route-trail", "route-src").withProperties(
                PropertyFactory.lineColor("#E65100"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineDasharray(arrayOf(1.6f, 1.0f)),
            ).withFilter(Expression.eq(Expression.get("trail"), true))
        )
    }
    if (style.getSource("stops-src") == null) {
        style.addSource(GeoJsonSource("stops-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            CircleLayer("stops-layer", "stops-src").withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#1565C0"),
                PropertyFactory.circleColor(
                    Expression.match(
                        Expression.get("role"),
                        Expression.color(android.graphics.Color.WHITE),
                        Expression.stop("start", Expression.color(android.graphics.Color.parseColor("#2E7D32"))),
                        Expression.stop("finish", Expression.color(android.graphics.Color.parseColor("#C62828"))),
                    )
                ),
            )
        )
    }
    if (style.getSource("fuel-src") == null) {
        style.addSource(GeoJsonSource("fuel-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            CircleLayer("fuel-layer", "fuel-src").withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleColor(
                    Expression.match(
                        Expression.get("role"),
                        Expression.color(android.graphics.Color.parseColor("#E65100")),
                        Expression.stop("reserve", Expression.color(android.graphics.Color.parseColor("#FF9800"))),
                        Expression.stop("empty", Expression.color(android.graphics.Color.parseColor("#6A1B9A"))),
                    )
                ),
            )
        )
    }
    if (style.getSource("track-src") == null) {
        style.addSource(GeoJsonSource("track-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("track-line", "track-src").withProperties(
                PropertyFactory.lineColor("#FF6D00"),
                PropertyFactory.lineWidth(5f),
            ).withFilter(
                    Expression.eq(Expression.has(Expression.get("grade")), Expression.literal(false))
                )
        )
        // Grade-colored segments when DEM grades are available.
        gradeColors.forEachIndexed { index, color ->
            style.addLayer(
                LineLayer("track-grade-" + index, "track-src").withProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(5f),
                ).withFilter(Expression.eq(Expression.get("grade"), index + 1))
            )
        }
    }
    if (style.getSource("shown-src") == null) {
        style.addSource(GeoJsonSource("shown-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("shown-line", "shown-src").withProperties(
                PropertyFactory.lineColor("#FF6D00"),
                PropertyFactory.lineWidth(5f),
            ).withFilter(
                Expression.eq(Expression.has(Expression.get("grade")), Expression.literal(false))
            )
        )
        gradeColors.forEachIndexed { index, color ->
            style.addLayer(
                LineLayer("shown-grade-" + index, "shown-src").withProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(5f),
                ).withFilter(Expression.eq(Expression.get("grade"), index + 1))
            )
        }
    }
    if (style.getSource("sunset-src") == null) {
        style.addSource(GeoJsonSource("sunset-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("sunset-line", "sunset-src").withProperties(
                PropertyFactory.lineColor("#FFD54F"),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
            )
        )
    }
    if (style.getSource("waypoints-src") == null) {
        style.addSource(GeoJsonSource("waypoints-src", FeatureCollection.fromFeatures(emptyArray())))
        ensurePoiIcons(style)
        style.addLayer(
            org.maplibre.android.style.layers.SymbolLayer("waypoints-layer", "waypoints-src").withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10f, 0.72f), Expression.stop(13f, 1.0f), Expression.stop(16f, 1.25f))
                ),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconIgnorePlacement(false),
            ).apply { minZoom = 10f }
        )
    }
    if (style.getSource("location-src") == null) {
        style.addSource(GeoJsonSource("location-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addImage("location-arrow", locationArrowBitmap())
        style.addLayer(
            org.maplibre.android.style.layers.SymbolLayer("location-layer", "location-src").withProperties(
                PropertyFactory.iconImage("location-arrow"),
                PropertyFactory.iconRotate(
                    Expression.coalesce(Expression.get("heading"), Expression.literal(0f))
                ),
                PropertyFactory.iconRotationAlignment(org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
    }
}

/**
 * Compass arrow for the current location: blue pointer with a thin white outline,
 * tip = north at iconRotate 0. Drawn in code — no per-density assets. MAP rotation
 * alignment keeps the world azimuth correct when the map itself is rotated (heading-up).
 */
private fun ensurePoiIcons(style: Style) {
    for (type in ru.azimut.app.core.model.PoiTypes.ALL) {
        val id = "poi-$type"
        if (style.getImage(id) == null) style.addImage(id, poiIconBitmap(type))
    }
}

internal fun poiIconBitmap(type: String): android.graphics.Bitmap {
    val size = 72
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    val r = 26f
    val color = android.graphics.Color.parseColor(ru.azimut.app.ui.components.poiTypeColorHex(type))
    // flat OsmAnd-like circle, but colored by type
    p.style = android.graphics.Paint.Style.FILL
    p.color = android.graphics.Color.argb(45, 0, 0, 0); c.drawCircle(cx + 1.5f, cy + 2f, r + 2f, p)
    p.color = android.graphics.Color.WHITE; c.drawCircle(cx, cy, r + 3f, p)
    p.color = color; c.drawCircle(cx, cy, r, p)

    p.color = android.graphics.Color.WHITE
    p.style = android.graphics.Paint.Style.STROKE
    p.strokeWidth = 4.2f
    p.strokeCap = android.graphics.Paint.Cap.ROUND
    p.strokeJoin = android.graphics.Paint.Join.ROUND
    val s = 10f
    when (type) {
        ru.azimut.app.core.model.PoiTypes.WATER -> {
            val d = android.graphics.Path()
            d.moveTo(cx, cy - s); d.quadTo(cx + s, cy + s * 0.35f, cx, cy + s); d.quadTo(cx - s, cy + s * 0.35f, cx, cy - s)
            p.style = android.graphics.Paint.Style.FILL; c.drawPath(d, p)
        }
        ru.azimut.app.core.model.PoiTypes.SHELTER -> {
            c.drawPath(android.graphics.Path().apply { moveTo(cx - s, cy); lineTo(cx, cy - s); lineTo(cx + s, cy) }, p)
            p.style = android.graphics.Paint.Style.FILL; c.drawRect(cx - s * 0.75f, cy, cx + s * 0.75f, cy + s * 0.85f, p)
            p.color = color; c.drawRect(cx - s * 0.22f, cy + s * 0.25f, cx + s * 0.22f, cy + s * 0.85f, p)
        }
        ru.azimut.app.core.model.PoiTypes.CAMP -> {
            c.drawPath(android.graphics.Path().apply { moveTo(cx - s, cy + s * 0.8f); lineTo(cx, cy - s); lineTo(cx + s, cy + s * 0.8f); close() }, p)
            c.drawLine(cx, cy - s, cx, cy + s * 0.8f, p)
        }
        ru.azimut.app.core.model.PoiTypes.FOOD -> {
            c.drawLine(cx - s * 0.45f, cy - s, cx - s * 0.45f, cy + s, p)
            c.drawLine(cx - s * 0.75f, cy - s, cx - s * 0.75f, cy - s * 0.1f, p)
            c.drawLine(cx - s * 0.15f, cy - s, cx - s * 0.15f, cy - s * 0.1f, p)
            c.drawArc(cx - s * 0.75f, cy - s * 0.45f, cx - s * 0.15f, cy + s * 0.15f, 0f, 180f, false, p)
            c.drawLine(cx + s * 0.55f, cy - s, cx + s * 0.55f, cy + s, p)
            c.drawOval(cx + s * 0.25f, cy - s, cx + s * 0.85f, cy - s * 0.1f, p)
        }
        ru.azimut.app.core.model.PoiTypes.FUEL -> {
            c.drawRoundRect(cx - s * 0.7f, cy - s, cx + s * 0.2f, cy + s, 2.5f, 2.5f, p)
            c.drawLine(cx + s * 0.2f, cy - s * 0.3f, cx + s * 0.7f, cy + s * 0.15f, p)
            c.drawLine(cx + s * 0.7f, cy + s * 0.15f, cx + s * 0.7f, cy + s * 0.85f, p)
        }
        ru.azimut.app.core.model.PoiTypes.MEDICAL -> {
            p.style = android.graphics.Paint.Style.FILL
            c.drawRect(cx - s * 0.28f, cy - s, cx + s * 0.28f, cy + s, p)
            c.drawRect(cx - s, cy - s * 0.28f, cx + s, cy + s * 0.28f, p)
        }
        ru.azimut.app.core.model.PoiTypes.SIGHT -> {
            val d = android.graphics.Path()
            for (i in 0 until 10) {
                val a = Math.toRadians((i * 36 - 90).toDouble())
                val rad = if (i % 2 == 0) s else s * 0.42f
                val x = cx + (rad * kotlin.math.cos(a)).toFloat()
                val y = cy + (rad * kotlin.math.sin(a)).toFloat()
                if (i == 0) d.moveTo(x, y) else d.lineTo(x, y)
            }
            d.close(); p.style = android.graphics.Paint.Style.FILL; c.drawPath(d, p)
        }
        ru.azimut.app.core.model.PoiTypes.TRANSPORT -> {
            c.drawRoundRect(cx - s, cy - s * 0.1f, cx + s, cy + s * 0.8f, 3f, 3f, p)
            c.drawLine(cx - s * 0.7f, cy - s * 0.1f, cx - s * 0.35f, cy - s * 0.8f, p)
            c.drawLine(cx + s * 0.7f, cy - s * 0.1f, cx + s * 0.35f, cy - s * 0.8f, p)
            c.drawLine(cx - s * 0.35f, cy - s * 0.8f, cx + s * 0.35f, cy - s * 0.8f, p)
        }
        ru.azimut.app.core.model.PoiTypes.OWN -> {
            c.drawLine(cx - s * 0.55f, cy - s, cx - s * 0.55f, cy + s, p)
            val d = android.graphics.Path(); d.moveTo(cx - s * 0.55f, cy - s * 0.85f); d.lineTo(cx + s * 0.75f, cy - s * 0.25f); d.lineTo(cx - s * 0.55f, cy + s * 0.25f); d.close()
            p.style = android.graphics.Paint.Style.FILL; c.drawPath(d, p)
        }
        else -> { p.style = android.graphics.Paint.Style.FILL; c.drawCircle(cx, cy, s * 0.45f, p) }
    }
    return bmp
}

internal fun locationArrowBitmap(): android.graphics.Bitmap {
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val arrow = android.graphics.Path()
    arrow.moveTo(size / 2f, size * 0.12f)
    arrow.lineTo(size * 0.80f, size * 0.84f)
    arrow.lineTo(size / 2f, size * 0.66f)
    arrow.lineTo(size * 0.20f, size * 0.84f)
    arrow.close()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // Thin white rim so the arrow stays visible on any base layer.
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 8f
    paint.strokeJoin = android.graphics.Paint.Join.ROUND
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(arrow, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#1565C0")
    canvas.drawPath(arrow, paint)
    return bmp
}

/** Grade percent -> color bucket 1..4 (flat, gentle, steep, very steep). */
internal fun gradeBucket(percent: Float): Int = gradeBucketOf(percent)

/** Vivid per-track colors for journal «eye» lines (cycled by track index). */
private val BG_TRACK_COLORS = listOf(
    "#7E57C2", "#00ACC1", "#FF7043", "#66BB6A", "#5C6BC0", "#EC407A",
)

/** Chains consecutive same-bucket segments into per-bucket line features. */
internal fun gradeFeatures(points: List<Pair<Double, Double>>, grades: List<Float>): List<Feature> {
    val tails = arrayOfNulls<Point>(4)
    val buckets = Array(4) { mutableListOf<Point>() }
    for (i in 1 until points.size) {
        val b = gradeBucket(grades[i - 1]) - 1
        val prev = Point.fromLngLat(points[i - 1].second, points[i - 1].first)
        val cur = Point.fromLngLat(points[i].second, points[i].first)
        if (tails[b] != prev) {
            if (tails[b] != null && buckets[b].isNotEmpty()) buckets[b].clear()
            buckets[b].add(prev)
        }
        buckets[b].add(cur)
        tails[b] = cur
    }
    return buckets.mapIndexed { index, pts ->
        if (pts.size >= 2) {
            Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                addNumberProperty("grade", index + 1)
            }
        } else {
            null
        }
    }.filterNotNull()
}

private const val STYLE_SAT = "asset://map_style_sat.json"

/**
 * Offline regions cannot fetch asset:// style URLs, so any style used for an
 * offline download is dumped to a cache file and referenced via file://.
 */
private fun offlineStyleUrl(context: android.content.Context, style: MapStyle, satellite: Boolean): String? {
    if (style.localPath != null) return null // local .mbtiles: nothing to fetch
    val file = if (satellite) {
        File(context.cacheDir, "offline-style-sat.json")
    } else {
        File(context.cacheDir, "offline-style-${style.id}.json")
    }
    if (!file.exists()) {
        file.writeText(
            if (satellite) {
                context.assets.open("map_style_sat.json").use { inp -> inp.readBytes().decodeToString() }
            } else {
                baseStyleJson(style)
            },
        )
    }
    return "file://${file.absolutePath}"
}

private fun styleKey(mapLayer: String, mapStyle: String): String =
    if (mapLayer == "satellite") "satellite" else "base:$mapStyle"

private fun applyMapStyle(
    map: MapLibreMap,
    mapLayer: String,
    mapStyle: String,
    onLoaded: () -> Unit,
) {
    if (mapLayer == "satellite") {
        map.setStyle(STYLE_SAT) { onLoaded() }
    } else {
        val style = MapStyles.byId(mapStyle)
        map.setStyle(Style.Builder().fromJson(baseStyleJson(style))) { onLoaded() }
    }
}

private fun baseStyleJson(style: MapStyle): String {
    val id = jsonEscape(style.cacheLayer)
    val url = jsonEscape(style.tileUrl)
    val attribution = jsonEscape(style.attribution)
    val maxZoom = style.androidMaxZoom
    // Imported .mbtiles: MapLibre reads the file itself (TileJSON comes from the file's metadata).
    val source = if (style.localPath != null) {
        """{"type":"raster","url":"mbtiles://${jsonEscape(style.localPath)}","tileSize":256}"""
    } else {
        """{"type":"raster","tiles":["$url"],"tileSize":256,"minzoom":0,"maxzoom":$maxZoom,"attribution":"$attribution"}"""
    }
    return """{"version":8,"name":"Azimuth ${jsonEscape(style.id)}","sources":{"$id":$source},"layers":[{"id":"background","type":"background","paint":{"background-color":"#1b2416"}},{"id":"$id","type":"raster","source":"$id","minzoom":0,"maxzoom":22}]}"""
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}
