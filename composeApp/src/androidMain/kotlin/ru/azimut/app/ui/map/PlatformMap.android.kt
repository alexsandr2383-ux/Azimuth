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
    onDownloadCorridorDone: () -> Unit,
    onTap: (GeoPoint) -> Unit,
    onSecondaryTap: (GeoPoint, Offset) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val onTapState by rememberUpdatedState(onTap)
    val onSecondaryTapState by rememberUpdatedState(onSecondaryTap)
    val onCameraBearingState by rememberUpdatedState(onCameraBearingChange)
    val onViewportChangeState by rememberUpdatedState(onViewportChange)
    val currentLocationState by rememberUpdatedState(currentLocation)
    val layerState by rememberUpdatedState(mapLayer)
    val styleState by rememberUpdatedState(mapStyle)

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
                        onTapState(GeoPoint(latLng.latitude, latLng.longitude))
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
                // Buckets: 1 flat, 2 gentle, 3 steep, 4 very steep; segments chain per bucket.
                val tails = arrayOfNulls<Point>(4)
                val buckets = Array(4) { mutableListOf<Point>() }
                for (i in 1 until trackPoints.size) {
                    val b = gradeBucket(grades[i - 1]) - 1
                    val prev = Point.fromLngLat(trackPoints[i - 1].lon, trackPoints[i - 1].lat)
                    val cur = Point.fromLngLat(trackPoints[i].lon, trackPoints[i].lat)
                    if (tails[b] != prev) {
                        if (tails[b] != null && buckets[b].isNotEmpty()) buckets[b].clear()
                        buckets[b].add(prev)
                    }
                    buckets[b].add(cur)
                    tails[b] = cur
                }
                buckets.mapIndexed { index, pts ->
                    if (pts.size >= 2) {
                        Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                            addNumberProperty("grade", index + 1)
                        }
                    } else {
                        null
                    }
                }.filterNotNull()
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

    LaunchedEffect(waypoints, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("waypoints-src") ?: return@LaunchedEffect
        // Building 10k GeoJSON features on the UI thread blocks the map for a moment.
        val features = withContext(Dispatchers.Default) {
            waypoints.map { waypoint ->
                Feature.fromGeometry(Point.fromLngLat(waypoint.lon, waypoint.lat)).apply {
                    addStringProperty("name", waypoint.name)
                }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    LaunchedEffect(currentLocation, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("location-src") ?: return@LaunchedEffect
        source.setGeoJson(
            if (currentLocation != null) {
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(Point.fromLngLat(currentLocation.lon, currentLocation.lat))
                )
            } else {
                FeatureCollection.fromFeatures(emptyArray())
            }
        )
    }

    // Saved tracks from the journal: thin background lines under everything else.
    LaunchedEffect(backgroundTracks, styleVersion) {
        val source = map?.style?.getSourceAs<GeoJsonSource>("saved-tracks-src") ?: return@LaunchedEffect
        val features = withContext(Dispatchers.Default) {
            backgroundTracks.map { line ->
                Feature.fromGeometry(
                    // Douglas-Peucker 1.5 m: fewer LatLngs for MapLibre to tessellate.
                    LineString.fromLngLats(
                        ru.azimut.app.core.model.simplifyGeoLine(line)
                            .map { Point.fromLngLat(it.lon, it.lat) }
                    )
                )
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
    var offlineRegion by remember { mutableStateOf<OfflineRegion?>(null) }
    LaunchedEffect(areaDownloadKey) {
        if (areaDownloadKey == 0) return@LaunchedEffect
        val target = map ?: return@LaunchedEffect
        if (offlineRegion != null) return@LaunchedEffect // one region at a time
        val style = MapStyles.byId(styleState)
        val styleUrl = when {
            layerState == "satellite" -> STYLE_SAT
            style.localPath != null -> return@LaunchedEffect // local .mbtiles: nothing to fetch
            else -> {
                // OfflineManager needs a style URL, not a JSON string: dump the raster
                // style to a cache file and point the definition at it.
                val file = File(context.cacheDir, "offline-style-${style.id}.json")
                file.writeText(baseStyleJson(style))
                "file://${file.absolutePath}"
            }
        }
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
    if (style.getSource("saved-tracks-src") == null) {
        // Registered first so journal tracks stay under the recorded/route lines.
        style.addSource(GeoJsonSource("saved-tracks-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("saved-tracks-line", "saved-tracks-src").withProperties(
                PropertyFactory.lineColor("#78909C"),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.85f),
            )
        )
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
        val gradeColors = listOf("#4CAF50", "#FFC107", "#FF9800", "#E53935")
        gradeColors.forEachIndexed { index, color ->
            style.addLayer(
                LineLayer("track-grade-" + index, "track-src").withProperties(
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
        style.addLayer(
            CircleLayer("waypoints-layer", "waypoints-src").withProperties(
                PropertyFactory.circleColor("#2E7D32"),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            )
        )
    }
    if (style.getSource("location-src") == null) {
        style.addSource(GeoJsonSource("location-src", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            CircleLayer("location-layer", "location-src").withProperties(
                PropertyFactory.circleColor("#2196F3"),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            )
        )
    }
}

/** Grade percent -> color bucket 1..4 (flat, gentle, steep, very steep). */
internal fun gradeBucket(percent: Float): Int = when {
    percent < 4f -> 1
    percent < 8f -> 2
    percent < 12f -> 3
    else -> 4
}

private const val STYLE_SAT = "asset://map_style_sat.json"

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
