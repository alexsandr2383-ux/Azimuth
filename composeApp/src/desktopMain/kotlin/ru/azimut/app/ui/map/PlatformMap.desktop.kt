package ru.azimut.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import ru.azimut.app.core.map.MapStyles
import ru.azimut.app.core.map.MbTiles
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.routing.RouteSection
import ru.azimut.app.core.routing.RouteSectionKind
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.ui.components.HoverTooltip
import ru.azimut.app.ui.LocalLang
import ru.azimut.app.ui.tr
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

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
    // Desktop restores the viewport via MapViewStore; initialCamera is unused here.
    initialCamera: MapCamera?,
    onViewportChange: (lat: Double, lon: Double, zoom: Double) -> Unit,
    recenterKey: Int,
    // Desktop has its own "Download" button with rectangle/lasso/visible-area menu.
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
    onDownloadCorridorDone: () -> Unit,
    onTap: (GeoPoint) -> Unit,
    onSecondaryTap: (GeoPoint, Offset) -> Unit,
) {
    val dataRoot = remember { PlatformFiles.navDataRoot() }
    val savedView = remember { MapViewStore.load(dataRoot) }
    val baseStyle = MapStyles.byId(mapStyle)
    val baseLayer = baseStyle.cacheLayer
    val baseUnderlayFrom = baseStyle.underlayFromZoom
    val state = remember {
        SlippyMapState(
            initial = savedView?.let { GeoPoint(it.lat, it.lon) }
                ?: currentLocation
                ?: GeoPoint(55.751244, 37.618423),
            initialZoom = savedView?.zoom ?: 12.0,
        )
    }
    val displayLayers = remember(mapLayer, mapStyle, overlayEnabled, overlayLayer) {
        val base = if (mapLayer == "satellite") listOf("satellite_hd", "places") else listOf(baseLayer)
        if (overlayEnabled && mapLayer != "satellite") listOf(overlayLayer) + base else base
    }
    val effectiveBaseOpacity = if (overlayEnabled && mapLayer != "satellite") baseOpacity else 1f
    val effectiveOverlayOpacity = if (overlayEnabled && mapLayer != "satellite") overlayOpacity else 1f
    val repository = remember { TileRepository(File(dataRoot, "maps/tilecache")) }
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var centered by remember { mutableStateOf(savedView != null) }
    var downloadState by remember { mutableStateOf<AreaDownloadState?>(null) }
    var pendingDownload by remember { mutableStateOf<AreaDownloadPlan?>(null) }
    var downloadWarningTiles by remember { mutableStateOf<Long?>(null) }
    var downloadPolicyMessage by remember { mutableStateOf<String?>(null) }
    var downloadCancelled by remember { mutableStateOf(false) }
    var exportOffer by remember { mutableStateOf<List<Pair<String, TileKey>>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var planningDownload by remember { mutableStateOf(false) }
    var downloadMenu by remember { mutableStateOf(false) }
    var selectionShape by remember { mutableStateOf<SelectionShape?>(null) }
    var selectionDraft by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedArea by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var downloadArea by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var segmentStatus by remember { mutableStateOf<String?>(null) }
    val lang = LocalLang.current
    var cacheVersion by remember { mutableStateOf(0) }
    var secondaryTapAt by remember { mutableStateOf(0L) }
    val onTapState by rememberUpdatedState(onTap)
    val onSecondaryTapState by rememberUpdatedState(onSecondaryTap)
    // Hillshade pixels -> ImageBitmap once per tile.
    val shadeBitmaps = remember(hillshadeTiles) {
        hillshadeTiles.associate { tile ->
            tile.name to bufferedShadeBitmap(tile)
        }
    }
    val downloadLabel = tr("Загрузить район", "Download area")
    val checkingLabel = tr("Проверка…", "Checking…")
    val bulkRestrictedMessage = tr(
        "Этот источник разрешает только онлайн-просмотр. Просмотренные тайлы кэшируются автоматически; массовое скачивание отключено.",
        "This provider allows online viewing only. Viewed tiles are cached automatically; bulk download is disabled.",
    )
    val localMapOfflineMessage = tr(
        "Эта карта — локальный файл .mbtiles, она уже доступна офлайн. Скачивать нечего.",
        "This map is a local .mbtiles file and is already available offline. Nothing to download.",
    )
    val downloadTooltip = tr(
        "Выделить участок и скачать для офлайна: карту, спутник, данные для маршрутов",
        "Select an area and download it for offline use: map, satellite, routing data",
    )
    val zoomInTooltip = tr("Приблизить", "Zoom in")
    val zoomOutTooltip = tr("Отдалить", "Zoom out")
    val trailLabel = tr("ТРОПА", "TRAIL")
    val textMeasurer = rememberTextMeasurer()
    // Measured once per label: measuring inside DrawScope runs every frame.
    val trailLabelLayout = remember(trailLabel) {
        textMeasurer.measure(trailLabel, TextStyle(color = Color.White, fontSize = 10.sp))
    }

    val segmentLabel = tr("Маршрутные данные", "Routing data")
    val segmentFailedLabel = tr("не скачан", "failed")
    val segmentCancelledLabel = tr("отменено", "cancelled")
    val segmentsDoneLabel = tr("Маршрутные данные скачаны", "Routing data downloaded")
    val exportIncompleteLabel = tr(
        "Внимание: архив неполный — не скачано тайлов: ",
        "Warning: the archive is incomplete — tiles not downloaded: ",
    )

    fun startAreaDownload(
        jobs: List<Pair<String, TileKey>>,
        segments: List<String> = emptyList(),
        exportAfter: Boolean = false,
        exportPrefix: String? = null,
    ) {
        if (jobs.isEmpty() && segments.isEmpty()) return
        downloadCancelled = false
        downloadState = AreaDownloadState(jobs.size, 0, 0, 0, 0, true)
        scope.launch {
            val failedSegments = mutableListOf<String>()
            for (name in segments) {
                if (downloadCancelled) break
                segmentStatus = "$segmentLabel $name…"
                var lastPercent = -1
                val ok = withContext(Dispatchers.IO) {
                    downloadSegment(
                        name,
                        progress = { p ->
                            val percent = (p * 100).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                scope.launch { segmentStatus = "$segmentLabel $name: $percent%" }
                            }
                        },
                        cancelled = { downloadCancelled },
                    )
                }
                if (!ok) {
                    if (!downloadCancelled) {
                        failedSegments += name
                    } else {
                        scope.launch { segmentStatus = "$segmentLabel $name: $segmentCancelledLabel" }
                    }
                }
            }
            segmentStatus = when {
                segments.isEmpty() -> null
                failedSegments.isEmpty() -> "$segmentsDoneLabel: ${segments.size}"
                else -> "$segmentLabel: " + failedSegments.joinToString(", ") + " — $segmentFailedLabel"
            }
            var processed = 0
            var downloaded = 0
            var unavailable = 0
            var failed = 0
            var index = 0
            while (index < jobs.size && !downloadCancelled) {
                val end = minOf(index + 6, jobs.size)
                val chunk = jobs.subList(index, end)
                val results = coroutineScope {
                    chunk.map { (layer, tile) ->
                        async(Dispatchers.IO) {
                            repository.cacheTile(layer, tile.z, tile.x, tile.y)
                        }
                    }.awaitAll()
                }
                processed += results.size
                downloaded += results.count { it == TileCacheResult.USABLE }
                unavailable += results.count { it == TileCacheResult.UNAVAILABLE }
                failed += results.count { it == TileCacheResult.FAILED }
                downloadState = AreaDownloadState(jobs.size, processed, downloaded, unavailable, failed, true)
                index = end
            }
            downloadState = AreaDownloadState(jobs.size, processed, downloaded, unavailable, failed, false)
            cacheVersion++
            if (exportAfter && !downloadCancelled && jobs.isNotEmpty()) {
                val files = withContext(Dispatchers.IO) {
                    runCatching { exportArea(repository, File(dataRoot, "export"), baseStyle.id, jobs, exportPrefix) }
                        .getOrElse { listOf("Error: ${it.message}") }
                }
                exportMessage = buildList {
                    addAll(files)
                    val skipped = unavailable + failed
                    if (skipped > 0) add(exportIncompleteLabel + skipped)
                }.joinToString("\n")
            } else if (!exportAfter && downloaded > 0) {
                exportOffer = jobs
            }
        }
    }

    LaunchedEffect(mapBearing, orientationResetKey) { state.bearing = mapBearing.toDouble() }

    LaunchedEffect(currentLocation, followLocation) {
        val location = currentLocation ?: return@LaunchedEffect
        if (followLocation) {
            centered = true
            state.centerLat = location.lat
            state.centerLon = location.lon
        }
    }

    LaunchedEffect(currentLocation) {
        val location = currentLocation ?: return@LaunchedEffect
        if (!centered) {
            centered = true
            state.centerLat = location.lat
            state.centerLon = location.lon
            state.zoom = 14.0
        }
    }

    LaunchedEffect(state.centerLat, state.centerLon, state.zoom) {
        val lat = state.centerLat
        val lon = state.centerLon
        val zoom = state.zoom
        delay(600)
        withContext(Dispatchers.IO) {
            MapViewStore.save(dataRoot, lat, lon, zoom)
        }
    }

    val onViewportChangeState by rememberUpdatedState(onViewportChange)
    LaunchedEffect(state.centerLat, state.centerLon, state.zoom) {
        onViewportChangeState(state.centerLat, state.centerLon, state.zoom)
    }

    // "Go to coordinates": recentre on the requested point.
    LaunchedEffect(gotoKey) {
        val target = gotoTarget ?: return@LaunchedEffect
        if (gotoKey == 0) return@LaunchedEffect
        state.centerLat = target.lat
        state.centerLon = target.lon
        if (state.zoom < 13.0) state.zoom = 13.0
        centered = true
    }

    // "Back to location" button: re-centre on the current fix, keep the current zoom.
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0) currentLocation?.let { location ->
            state.centerLat = location.lat
            state.centerLon = location.lon
        }
    }

    LaunchedEffect(downloadState) {
        val result = downloadState
        if (result != null && !result.running) {
            delay(15_000)
            downloadState = null
            downloadArea = null
            segmentStatus = null
        }
    }

    LaunchedEffect(
        state.centerLat, state.centerLon, state.zoom, state.bearing,
        mapLayer, mapStyle, overlayEnabled, overlayLayer, viewport, cacheVersion,
    ) {
        if (viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        val (coverW, coverH) = state.coverSize(viewport.width, viewport.height)
        displayLayers.forEach { layer ->
            val z = tileZoom(state.zoom, layer)
            visibleTiles(state, coverW, coverH, layer).forEach { tile ->
                repository.request(scope, layer, tile.z, tile.x, tile.y)
            }
            if (layer == baseLayer && baseUnderlayFrom != null && z > baseUnderlayFrom) {
                for (underlayZ in baseUnderlayFrom until z) {
                    tilesForViewportAtZoom(state, coverW, coverH, underlayZ).forEach { tile ->
                        repository.request(scope, layer, tile.z, tile.x, tile.y)
                    }
                }
            } else if (layer == "satellite_hd" && z > 18) {
                tilesForViewportAtZoom(state, coverW, coverH, 18).forEach { tile ->
                    repository.request(scope, layer, tile.z, tile.x, tile.y)
                }
            }
        }
    }

    // Douglas-Peucker 1.5 m subsets, recomputed only when inputs change.
    val simplifiedBackgroundTracks = remember(backgroundTracks) {
        backgroundTracks.map { ru.azimut.app.core.model.simplifyGeoLine(it) }
    }
    val simplifiedTrackPoints = remember(trackPoints) {
        ru.azimut.app.core.model.simplifyTrack(trackPoints)
    }

    Box(
        modifier
            .onSizeChanged { viewport = it }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(selectionShape) {
                    val shape = selectionShape
                    if (shape == null) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.pan(dragAmount.x, dragAmount.y)
                        }
                    } else {
                        detectDragGestures(
                            onDragStart = { selectionDraft = listOf(it) },
                            onDrag = { change, _ ->
                                change.consume()
                                val p = change.position
                                val draft = selectionDraft
                                selectionDraft = when {
                                    draft.isEmpty() -> listOf(p)
                                    shape == SelectionShape.RECT -> listOf(draft.first(), p)
                                    (p - draft.last()).getDistance() >= 6f -> draft + p
                                    else -> draft
                                }
                            },
                            onDragEnd = {
                                val draft = selectionDraft
                                val w = viewport.width
                                val h = viewport.height
                                val corners = if (shape == SelectionShape.RECT && draft.size == 2) {
                                    val (a, b) = draft
                                    listOf(Offset(a.x, a.y), Offset(b.x, a.y), Offset(b.x, b.y), Offset(a.x, b.y))
                                } else {
                                    draft
                                }
                                val polygon = corners.map { state.screenToGeo(it.x, it.y, w, h) }
                                selectionDraft = emptyList()
                                selectionShape = null
                                if (polygon.size >= 3 && polygonAreaKm2(polygon) > 0.01) selectedArea = polygon
                            },
                            onDragCancel = { selectionDraft = emptyList() },
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val primaryTap = System.currentTimeMillis() - secondaryTapAt >= 350
                            if (primaryTap && viewport.width > 0 && viewport.height > 0) {
                                onTapState(state.screenToGeo(offset.x, offset.y, viewport.width, viewport.height))
                            }
                        },
                        onDoubleTap = { state.zoomBy(1.0) },
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val secondary = event.type == PointerEventType.Press && event.buttons.isSecondaryPressed
                            if (secondary) {
                                val change = event.changes.firstOrNull() ?: continue
                                if (viewport.width <= 0 || viewport.height <= 0) continue
                                secondaryTapAt = System.currentTimeMillis()
                                val point = state.screenToGeo(
                                    change.position.x,
                                    change.position.y,
                                    viewport.width,
                                    viewport.height,
                                )
                                onSecondaryTapState(point, change.position)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val scroll = change.scrollDelta.y
                            if (scroll == 0f) continue
                            change.consume()
                            if (viewport.width > 0 && viewport.height > 0) {
                                state.zoomAt(
                                    x = change.position.x,
                                    y = change.position.y,
                                    width = viewport.width,
                                    height = viewport.height,
                                    delta = -scroll * 0.5,
                                )
                            }
                        }
                    }
                }
        ) {
            drawRect(Color(0xFF1B2416))

            val originX = lonToWorldX(state.centerLon, state.zoom) - size.width / 2.0
            val originY = latToWorldY(state.centerLat, state.zoom) - size.height / 2.0

            val (coverW, coverH) = state.coverSize(size.width.toInt(), size.height.toInt())

            fun drawLevel(layer: String, z: Int, alpha: Float) {
                val scale = 2.0.pow(state.zoom - z)
                val tilePx = 256.0 * scale
                for (tile in tilesForViewportAtZoom(state, coverW, coverH, z)) {
                    val bitmap = repository.images["$layer/${tile.z}/${tile.x}/${tile.y}"] ?: continue
                    val left = tile.x * tilePx - originX
                    val top = tile.y * tilePx - originY
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(tilePx.roundToInt(), tilePx.roundToInt()),
                        alpha = alpha,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }

            // Tiles rotate with the map; markers and labels below are projected upright.
            withTransform({ rotate(-state.bearing.toFloat(), center) }) {
                if (overlayEnabled && mapLayer != "satellite") {
                    val ovLayer = overlayLayer
                    val ovZ = tileZoom(state.zoom, ovLayer)
                    val ovAlpha = effectiveOverlayOpacity.coerceIn(0f, 1f)
                    if (ovLayer == "satellite_hd" && ovZ > 18) {
                        drawLevel(ovLayer, 18, ovAlpha)
                    }
                    drawLevel(ovLayer, ovZ, ovAlpha)
                }
                for (layerName in displayLayers) {
                    if (overlayEnabled && mapLayer != "satellite" && layerName == overlayLayer) continue
                    val z = tileZoom(state.zoom, layerName)
                    val alpha = if (overlayEnabled && mapLayer != "satellite") effectiveBaseOpacity.coerceIn(0f, 1f) else 1f
                    if (layerName == baseLayer && baseUnderlayFrom != null && z > baseUnderlayFrom) {
                        for (underlayZ in baseUnderlayFrom until z) drawLevel(layerName, underlayZ, alpha)
                    } else if (layerName == "satellite_hd" && z > 18) {
                        drawLevel(layerName, 18, alpha)
                    }
                    drawLevel(layerName, z, alpha)
                }
            }

            fun project(lat: Double, lon: Double): Offset {
                val x = lonToWorldX(lon, state.zoom) - originX
                val y = latToWorldY(lat, state.zoom) - originY
                return center + state.toScreen(x - size.width / 2.0, y - size.height / 2.0)
            }

            // Saved tracks from the journal: thin background lines under the route.
            val savedTrackColor = Color(0xFF78909C).copy(alpha = 0.85f)
            for (line in simplifiedBackgroundTracks) {
                if (line.size < 2) continue
                for (i in 1 until line.size) {
                    drawLine(
                        color = savedTrackColor,
                        start = project(line[i - 1].lat, line[i - 1].lon),
                        end = project(line[i].lat, line[i].lon),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }

            // Protected areas (OOPT): translucent red fill + outline, under route lines.
            if (protectedAreas.isNotEmpty()) {
                val zoneFill = Color(0xFFC62828).copy(alpha = 0.16f)
                val zoneStroke = Color(0xFFEF5350).copy(alpha = 0.9f)
                for (polygon in protectedAreas) {
                    if (polygon.rings.isEmpty()) continue
                    for (ring in polygon.rings) {
                        if (ring.size < 3) continue
                        val path = Path().apply {
                            ring.forEachIndexed { i, p ->
                                val o = project(p[1], p[0])
                                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                            }
                            close()
                        }
                        drawPath(path, zoneFill)
                        drawPath(path, zoneStroke, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }

            if (routePoints.size >= 2) {
                for (i in 1 until routePoints.size) {
                    val a = routePoints[i - 1]
                    val b = routePoints[i]
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = project(a.lat, a.lon),
                        end = project(b.lat, b.lon),
                        strokeWidth = 8.dp.toPx(),
                    )
                }
                val sections = routeSections.ifEmpty { listOf(RouteSection(RouteSectionKind.ROAD, routePoints)) }
                val trailDash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                for (section in sections) {
                    val trail = section.kind == RouteSectionKind.TRAIL
                    for (i in 1 until section.points.size) {
                        val a = section.points[i - 1]
                        val b = section.points[i]
                        drawLine(
                            color = if (trail) TRAIL_COLOR else Color(0xFF1565C0),
                            start = project(a.lat, a.lon),
                            end = project(b.lat, b.lon),
                            strokeWidth = 5.dp.toPx(),
                            pathEffect = if (trail) trailDash else null,
                        )
                    }
                }
                for (section in sections) {
                    if (section.kind != RouteSectionKind.TRAIL || section.points.size < 2) continue
                    val start = section.points.first().let { project(it.lat, it.lon) }
                    val end = section.points.last().let { project(it.lat, it.lon) }
                    if ((end - start).getDistance() < 70.dp.toPx()) continue
                    val mid = section.points[section.points.size / 2].let { project(it.lat, it.lon) }
                    val label = trailLabelLayout
                    val topLeft = mid - Offset(label.size.width / 2f, label.size.height + 8.dp.toPx())
                    drawRoundRect(
                        color = TRAIL_COLOR,
                        topLeft = topLeft - Offset(4.dp.toPx(), 2.dp.toPx()),
                        size = Size(label.size.width + 8.dp.toPx(), label.size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawText(label, topLeft = topLeft)
                }
            }

            val areaFill = Color(0xFF2196F3).copy(alpha = 0.18f)
            val areaStroke = Color(0xFF1565C0)
            (selectedArea ?: downloadArea)?.let { area ->
                val path = Path().apply {
                    area.forEachIndexed { i, p ->
                        val o = project(p.lat, p.lon)
                        if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                    }
                    close()
                }
                drawPath(path, areaFill)
                drawPath(path, areaStroke, style = Stroke(width = 2.dp.toPx()))
            }
            if (selectionDraft.size >= 2) {
                val draft = selectionDraft
                val path = Path().apply {
                    if (selectionShape == SelectionShape.RECT) {
                        val (a, b) = draft.first() to draft.last()
                        moveTo(a.x, a.y); lineTo(b.x, a.y); lineTo(b.x, b.y); lineTo(a.x, b.y); close()
                    } else {
                        draft.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                        close()
                    }
                }
                drawPath(path, areaFill)
                drawPath(
                    path,
                    areaStroke,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }

            routeStops.forEachIndexed { index, stop ->
                val c = project(stop.lat, stop.lon)
                val fill = when (index) {
                    0 -> Color(0xFF2E7D32)
                    routeStops.lastIndex -> Color(0xFFC62828)
                    else -> Color.White
                }
                drawCircle(Color(0xFF1565C0), radius = 8.dp.toPx(), center = c)
                drawCircle(fill, radius = 6.dp.toPx(), center = c)
            }

            // Fuel ramps: orange = reserve crossing, purple = dry tank.
            fuelReserve?.let { stop ->
                drawCircle(Color(0xFFFF9800), radius = 7.dp.toPx(), center = project(stop.lat, stop.lon))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = project(stop.lat, stop.lon))
            }
            fuelEmpty?.let { stop ->
                drawCircle(Color(0xFF6A1B9A), radius = 7.dp.toPx(), center = project(stop.lat, stop.lon))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = project(stop.lat, stop.lon))
            }

            if (simplifiedTrackPoints.size >= 2) {
                val grades = trackGrades
                for (i in 1 until simplifiedTrackPoints.size) {
                    val a = simplifiedTrackPoints[i - 1]
                    val b = simplifiedTrackPoints[i]
                    val color = when {
                        b.susanin -> Color(0xFFFFC107)
                        grades != null && grades.size == trackPoints.size - 1 -> gradeColor(grades[i - 1])
                        else -> Color(0xFFFF6D00)
                    }
                    drawLine(
                        color = color,
                        start = project(a.lat, a.lon),
                        end = project(b.lat, b.lon),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }

            // Sunset azimuth: dashed yellow ray from the current fix.
            val sunAz = sunsetAzimuthDeg
            if (sunAz != null && currentLocation != null) {
                val loc = currentLocation
                val bearingRad = (sunAz - state.bearing) * PI / 180.0
                val lengthPx = 64.dp.toPx()
                val origin = project(loc.lat, loc.lon)
                drawLine(
                    color = Color(0xFFFFD54F).copy(alpha = 0.9f),
                    start = origin,
                    end = origin + Offset(
                        (sin(bearingRad) * lengthPx).toFloat(),
                        (-cos(bearingRad) * lengthPx).toFloat(),
                    ),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }

            // Hillshade tiles from the DEM, drawn under the vector layers.
            for (tile in hillshadeTiles) {
                val topLeft = project(tile.latTop, tile.lonLeft)
                val bottomRight = project(tile.latBottom, tile.lonRight)
                val bitmap = shadeBitmaps[tile.name] ?: continue
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(tile.width, tile.height),
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(
                        (bottomRight.x - topLeft.x).roundToInt().coerceAtLeast(1),
                        (bottomRight.y - topLeft.y).roundToInt().coerceAtLeast(1),
                    ),
                    alpha = 1f,
                    filterQuality = FilterQuality.Medium,
                )
            }

            if (showPois) {
                // Only project and draw what is on screen; when a 10k-POI import is fully
                // zoomed out, sample the visible set so a frame does not draw 10k circles.
                val margin = 12.dp.toPx()
                fun onScreen(waypoint: Waypoint): Offset? {
                    val center = project(waypoint.lat, waypoint.lon)
                    return center.takeIf {
                        it.x >= -margin && it.x <= size.width + margin &&
                            it.y >= -margin && it.y <= size.height + margin
                    }
                }
                var visible = 0
                for (waypoint in waypoints) {
                    if (onScreen(waypoint) != null) visible++
                }
                val stride = if (visible > MAX_POIS_ON_SCREEN) {
                    (visible + MAX_POIS_ON_SCREEN - 1) / MAX_POIS_ON_SCREEN
                } else {
                    1
                }
                var seen = 0
                for (waypoint in waypoints) {
                    val center = onScreen(waypoint) ?: continue
                    if (seen++ % stride != 0) continue
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
                    drawCircle(Color(0xFF2E7D32), radius = 5.5.dp.toPx(), center = center)
                }
            }

            currentLocation?.let { location ->
                val center = project(location.lat, location.lon)
                drawCircle(Color.White, radius = 9.dp.toPx(), center = center)
                drawCircle(Color(0xFF2196F3), radius = 7.dp.toPx(), center = center)
                drawCircle(
                    color = Color(0xFF0D47A1),
                    radius = 3.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        val runningDownload = downloadState
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 84.dp)
        ) {
            DropdownMenu(expanded = downloadMenu, onDismissRequest = { downloadMenu = false }) {
                DropdownMenuItem(
                    text = { Text(tr("Выделить участок прямоугольником", "Select area: rectangle")) },
                    onClick = {
                        downloadMenu = false
                        selectionShape = SelectionShape.RECT
                    },
                )
                DropdownMenuItem(
                    text = { Text(tr("Выделить участок произвольной линией", "Select area: free shape")) },
                    onClick = {
                        downloadMenu = false
                        selectionShape = SelectionShape.LASSO
                    },
                )
                DropdownMenuItem(
                    text = { Text(tr("Видимая область (карта + спутник)", "Visible area (map + satellite)")) },
                    onClick = {
                        downloadMenu = false
                        val current = downloadState
                        if (current?.running != true && !planningDownload && viewport.width > 0 && viewport.height > 0) {
                            if (baseStyle.localPath != null) {
                                downloadPolicyMessage = localMapOfflineMessage
                            } else if (!baseStyle.bulkDownload) {
                                downloadPolicyMessage = bulkRestrictedMessage
                            } else {
                                val width = viewport.width
                                val height = viewport.height
                                planningDownload = true
                                scope.launch {
                                    val satMax = repository.probeMaxZoom(
                                        "satellite_hd", state.centerLat, state.centerLon,
                                    )
                                    val baseMax = repository.probeMaxZoom(
                                        baseLayer, state.centerLat, state.centerLon, baseStyle.maxZoom,
                                    )
                                    // Big home district: instead of refusing, lower the
                                    // detail levels until the area fits the tile budget.
                                    var sMax = satMax
                                    var bMax = baseMax
                                    var plan = planAreaDownload(
                                        state, width, height, sMax, bMax, baseLayer,
                                        baseUnderlayFrom ?: bMax,
                                    )
                                    while (plan.total > MAX_AREA_DOWNLOAD_TILES && (sMax > 12 || bMax > 12)) {
                                        if (sMax > 12) sMax--
                                        if (bMax > 12) bMax--
                                        plan = planAreaDownload(
                                            state, width, height, sMax, bMax, baseLayer,
                                            minOf(baseUnderlayFrom ?: bMax, bMax),
                                        )
                                    }
                                    if (plan.total > MAX_AREA_DOWNLOAD_TILES) {
                                        downloadWarningTiles = plan.total
                                    } else if (plan.jobs.isNotEmpty()) {
                                        pendingDownload = plan
                                    }
                                    planningDownload = false
                                }
                            }
                        }
                    },
                )
            }
            HoverTooltip(downloadTooltip) {
                Button(
                    onClick = { downloadMenu = true },
                    enabled = runningDownload?.running != true && !planningDownload,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            runningDownload != null && runningDownload.running ->
                                "${runningDownload.processed}/${runningDownload.total}"
                            planningDownload -> checkingLabel
                            else -> downloadLabel
                        },
                        fontSize = 12.sp,
                    )
                }
            }
        }

        runningDownload?.let { progress ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 128.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.68f),
                contentColor = Color.White,
            ) {
                Column(Modifier.padding(8.dp)) {
                    segmentStatus?.let { Text(it, fontSize = 11.sp) }
                    if (progress.total > 0) Text(
                        if (progress.running) {
                            tr(
                                "Скачано ${progress.downloaded} из ${progress.total}",
                                "Downloaded ${progress.downloaded} of ${progress.total}",
                            )
                        } else {
                            tr(
                                "Скачано ${progress.downloaded} из ${progress.total}, недоступно: ${progress.unavailable}, ошибок: ${progress.failed}",
                                "Downloaded ${progress.downloaded} of ${progress.total}, unavailable: ${progress.unavailable}, errors: ${progress.failed}",
                            )
                        },
                        fontSize = 11.sp,
                    )
                    if (progress.running) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f
                            },
                            modifier = Modifier
                                .width(180.dp)
                                .height(4.dp),
                        )
                        TextButton(onClick = { downloadCancelled = true }) {
                            Text(tr("Остановить", "Stop"), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        selectionShape?.let { shape ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1565C0).copy(alpha = 0.94f),
                contentColor = Color.White,
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (shape == SelectionShape.RECT) {
                            tr("Зажмите левую кнопку и растяните прямоугольник", "Drag with the left button to draw a rectangle")
                        } else {
                            tr("Зажмите левую кнопку и обведите участок", "Drag with the left button around the area")
                        },
                        fontSize = 13.sp,
                    )
                    TextButton(onClick = {
                        selectionShape = null
                        selectionDraft = emptyList()
                    }) { Text(tr("Отмена", "Cancel"), color = Color.White) }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HoverTooltip(zoomInTooltip) {
                FilledIconButton(
                    onClick = { state.zoomBy(1.0) },
                    modifier = Modifier.size(38.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
            }
            HoverTooltip(zoomOutTooltip) {
                FilledIconButton(
                    onClick = { state.zoomBy(-1.0) },
                    modifier = Modifier.size(38.dp),
                ) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
            }
        }

        Text(
            text = if (mapLayer == "satellite") "Imagery © Esri, Maxar, Earthstar Geographics · Places © Esri"
            else baseStyle.attribution,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 58.dp, start = 10.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
        )
    }

    pendingDownload?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text(tr("Скачать офлайн-карты?", "Download offline maps?")) },
            text = {
                Text(
                    tr(
                        "Видимый район. Карта до z${plan.baseMax}, спутник до z${plan.satMax}, подписи до z16. " +
                            "Тайлов: ${plan.total} (≈ ${formatDownloadSize(plan.total)}). " +
                            "Чем ближе карта, тем меньше загрузка. Скачивание можно остановить." +
                            if (plan.baseMax < baseUnderlayFrom ?: 0 || plan.satMax < MapStyles.byId(mapStyle).maxZoom) {
                                "\n" + tr(
                                    "Область велика — детализация снижена, чтобы загрузка была реальной. " +
                                        "Для большего зума скачайте район частями.",
                                    "The area is large — detail levels were reduced to keep the download feasible. " +
                                        "Download the district in parts for deeper zoom.",
                                )
                            } else "",
                        "Visible area. Map up to z${plan.baseMax}, satellite up to z${plan.satMax}, labels up to z16. " +
                            "Tiles: ${plan.total} (≈ ${formatDownloadSize(plan.total)}). " +
                            "The closer the map, the smaller the download. You can stop it at any time." +
                            if (plan.baseMax < baseUnderlayFrom ?: 0 || plan.satMax < MapStyles.byId(mapStyle).maxZoom) {
                                "\n" + "The area is large — detail levels were reduced to keep the download feasible. " +
                                    "Download the district in parts for deeper zoom."
                            } else "",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDownload = null
                    startAreaDownload(plan.jobs)
                }) { Text(tr("Скачать", "Download")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) { Text(tr("Отмена", "Cancel")) }
            },
        )
    }

    selectedArea?.let { area ->
        AreaDownloadDialog(
            initialShape = DownloadShape.Polygon(area),
            baseStyle = baseStyle,
            baseLayerTitle = baseStyle.title(lang),
            onDismiss = { selectedArea = null },
            onStart = { plan, options, _ ->
                selectedArea = null
                downloadArea = area
                startAreaDownload(plan.jobs, plan.segments, exportAfter = options.exportMbtiles)
            },
        )
    }

    downloadCorridor?.let { (name, line) ->
        val busy = downloadState?.running == true
        if (busy) {
            AlertDialog(
                onDismissRequest = onDownloadCorridorDone,
                text = {
                    Text(tr("Сначала дождитесь окончания текущей загрузки.", "Wait for the current download to finish."))
                },
                confirmButton = { TextButton(onClick = onDownloadCorridorDone) { Text("OK") } },
            )
        } else {
            AreaDownloadDialog(
                initialShape = DownloadShape.Corridor(line, 1000.0, name),
                baseStyle = baseStyle,
                baseLayerTitle = baseStyle.title(lang),
                onDismiss = onDownloadCorridorDone,
                onStart = { plan, options, _ ->
                    onDownloadCorridorDone()
                    startAreaDownload(
                        plan.jobs,
                        plan.segments,
                        exportAfter = options.exportMbtiles,
                        exportPrefix = "route-" + name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_"),
                    )
                },
            )
        }
    }

    exportOffer?.let { jobs ->
        AlertDialog(
            onDismissRequest = { if (!exporting) exportOffer = null },
            title = { Text(tr("Сохранить район для телефона?", "Save the area for the phone?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Скачанный район будет сохранён в .mbtiles (карта и спутник отдельными файлами) " +
                                "в папку NavData/export. Скопируйте их на телефон и импортируйте: " +
                                "Настройки → Карты и слои → «Импорт карты». Карта будет работать без интернета.",
                            "The downloaded area will be saved as .mbtiles (map and satellite as separate files) " +
                                "to NavData/export. Copy them to the phone and import via " +
                                "Settings → Maps & layers → Import map. The map then works offline.",
                        )
                    )
                    if (exporting) LinearProgressIndicator(Modifier.width(220.dp))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !exporting,
                    onClick = {
                        exporting = true
                        scope.launch {
                            val files = withContext(Dispatchers.IO) {
                                runCatching {
                                    exportArea(repository, File(dataRoot, "export"), baseStyle.id, jobs)
                                }.getOrElse { listOf("Error: ${it.message}") }
                            }
                            exporting = false
                            exportOffer = null
                            exportMessage = files.joinToString("\n")
                        }
                    },
                ) { Text(tr("Сохранить .mbtiles", "Save .mbtiles")) }
            },
            dismissButton = {
                TextButton(enabled = !exporting, onClick = { exportOffer = null }) { Text(tr("Не нужно", "No thanks")) }
            },
        )
    }

    exportMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text(tr("Сохранено", "Saved")) },
            text = { Text(text.ifBlank { tr("Нечего сохранять.", "Nothing to save.") }) },
            confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } },
        )
    }

    downloadPolicyMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { downloadPolicyMessage = null },
            title = { Text(tr("Массовая загрузка недоступна", "Bulk download unavailable")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { downloadPolicyMessage = null }) { Text("OK") }
            },
        )
    }

    downloadWarningTiles?.let { total ->
        AlertDialog(
            onDismissRequest = { downloadWarningTiles = null },
            title = { Text(tr("Область слишком большая", "Area is too large")) },
            text = {
                Text(
                    tr(
                        "Слишком большая область: $total тайлов. Приблизьте карту и попробуйте снова.",
                        "Area is too large: $total tiles. Zoom in and try again.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { downloadWarningTiles = null }) { Text("OK") }
            },
        )
    }
}

/**
 * Writes the cached tiles of a finished area download into one .mbtiles per layer
 * (labels are skipped: they only make sense on top of the satellite layer).
 * Returns "file — N tiles" lines.
 */
private fun exportArea(
    repository: TileRepository,
    dir: File,
    styleId: String,
    jobs: List<Pair<String, TileKey>>,
    prefix: String? = null,
): List<String> {
    val stamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return jobs.filter { it.first != "places" }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (layer, tiles) ->
            val layerTitle = if (layer.startsWith("satellite")) "satellite" else styleId
            val title = prefix?.let { "$it-$layerTitle" } ?: layerTitle
            val dest = File(dir, "$title-$stamp.mbtiles")
            val count = MbTiles.write(dest, "$title $stamp", tiles.map { Triple(it.z, it.x, it.y) }) { z, x, y ->
                repository.cachedBytes(layer, z, x, y)
            }
            if (count > 0) "${dest.absolutePath} — $count" else null
        }
}

private class SlippyMapState(initial: GeoPoint, initialZoom: Double = 12.0) {
    var centerLat by mutableStateOf(initial.lat)
    var centerLon by mutableStateOf(initial.lon)
    var zoom by mutableStateOf(initialZoom)

    /** Map rotation, degrees clockwise from north: the direction shown at the top of the screen. */
    var bearing by mutableStateOf(0.0)

    /** Screen offset from the centre → north-up world offset. */
    fun toWorld(dx: Double, dy: Double): Pair<Double, Double> {
        val r = bearing * PI / 180.0
        return Pair(dx * cos(r) - dy * sin(r), dx * sin(r) + dy * cos(r))
    }

    /** North-up world offset from the centre → screen offset. */
    fun toScreen(dx: Double, dy: Double): Offset {
        val r = -bearing * PI / 180.0
        return Offset((dx * cos(r) - dy * sin(r)).toFloat(), (dx * sin(r) + dy * cos(r)).toFloat())
    }

    /** A rotated viewport needs tiles for its bounding square. */
    fun coverSize(width: Int, height: Int): Pair<Int, Int> {
        if (bearing == 0.0) return width to height
        val diagonal = ceil(sqrt(width.toDouble() * width + height.toDouble() * height)).toInt()
        return diagonal to diagonal
    }

    fun pan(dx: Float, dy: Float) {
        val (wx, wy) = toWorld(dx.toDouble(), dy.toDouble())
        centerLon = worldXToLon(lonToWorldX(centerLon, zoom) - wx, zoom)
        centerLat = worldYToLat(latToWorldY(centerLat, zoom) - wy, zoom).coerceIn(-85.0, 85.0)
    }

    fun zoomBy(delta: Double) {
        zoom = (zoom + delta).coerceIn(2.0, 19.0)
    }

    fun zoomAt(x: Float, y: Float, width: Int, height: Int, delta: Double) {
        val newZoom = (zoom + delta).coerceIn(2.0, 19.0)
        if (newZoom == zoom) return
        val anchor = screenToGeo(x, y, width, height)
        val (wx, wy) = toWorld(x - width / 2.0, y - height / 2.0)
        zoom = newZoom
        centerLon = worldXToLon(lonToWorldX(anchor.lon, newZoom) - wx, newZoom)
        centerLat = worldYToLat(latToWorldY(anchor.lat, newZoom) - wy, newZoom)
            .coerceIn(-85.0, 85.0)
    }

    fun screenToGeo(x: Float, y: Float, width: Int, height: Int): GeoPoint {
        val (wx, wy) = toWorld(x - width / 2.0, y - height / 2.0)
        val worldX = lonToWorldX(centerLon, zoom) + wx
        val worldY = latToWorldY(centerLat, zoom) + wy
        return GeoPoint(
            lat = worldYToLat(worldY, zoom).coerceIn(-85.0, 85.0),
            lon = worldXToLon(worldX, zoom),
        )
    }
}

internal data class TileKey(val z: Int, val x: Int, val y: Int)

private enum class TileCacheResult { USABLE, UNAVAILABLE, FAILED }

private data class AreaDownloadState(
    val total: Int,
    val processed: Int,
    val downloaded: Int,
    val unavailable: Int,
    val failed: Int,
    val running: Boolean,
)

internal const val MAX_AREA_DOWNLOAD_TILES = 250_000L

private val TRAIL_COLOR = Color(0xFFE65100)

private const val MAX_POIS_ON_SCREEN = 1500
private const val MAX_IMAGE_TILES = 600

private data class AreaDownloadPlan(
    val total: Long,
    val fromZoom: Int,
    val satMax: Int,
    val baseMax: Int,
    val jobs: List<Pair<String, TileKey>>,
)

private fun planAreaDownload(
    state: SlippyMapState,
    width: Int,
    height: Int,
    satMax: Int,
    baseMax: Int,
    baseLayer: String,
    baseStart: Int,
): AreaDownloadPlan {
    if (width <= 0 || height <= 0) return AreaDownloadPlan(0, 0, satMax, baseMax, emptyList())
    // A few coarser levels too, so the offline map can still be zoomed out a bit.
    val fromZoom = (floor(state.zoom).toInt() - 2).coerceAtLeast(0)
    val specs = ArrayList<Pair<String, Int>>()
    for ((layer, maxZ) in listOf("places" to 16, "satellite_hd" to satMax, baseLayer to baseMax)) {
        val start = when (layer) {
            "places" -> fromZoom.coerceAtMost(maxZ)
            "satellite_hd" -> fromZoom.coerceAtMost(18).coerceAtMost(maxZ)
            else -> fromZoom.coerceAtMost(baseStart).coerceAtMost(maxZ)
        }
        for (z in maxZ downTo start) specs.add(layer to z)
    }
    var total = 0L
    for ((_, z) in specs) total += countTilesForViewportAtZoom(state, width, height, z)
    if (total > MAX_AREA_DOWNLOAD_TILES) return AreaDownloadPlan(total, fromZoom, satMax, baseMax, emptyList())

    val jobs = ArrayList<Pair<String, TileKey>>(total.toInt())
    for ((layer, z) in specs) {
        for (tile in tilesForViewportAtZoom(state, width, height, z)) {
            jobs.add(layer to tile)
        }
    }
    return AreaDownloadPlan(total, fromZoom, satMax, baseMax, jobs)
}

private fun countTilesForViewportAtZoom(
    state: SlippyMapState,
    width: Int,
    height: Int,
    z: Int,
): Long {
    val (xs, ys) = viewportTileRanges(state, width, height, z) ?: return 0
    return (xs.last - xs.first + 1).toLong() * (ys.last - ys.first + 1).toLong()
}

private fun tilesForViewportAtZoom(
    state: SlippyMapState,
    width: Int,
    height: Int,
    z: Int,
): List<TileKey> {
    val (xs, ys) = viewportTileRanges(state, width, height, z) ?: return emptyList()
    val result = ArrayList<TileKey>((xs.last - xs.first + 1) * (ys.last - ys.first + 1))
    for (x in xs) {
        for (y in ys) {
            result.add(TileKey(z, x, y))
        }
    }
    return result
}

private fun viewportTileRanges(
    state: SlippyMapState,
    width: Int,
    height: Int,
    z: Int,
): Pair<IntRange, IntRange>? {
    if (width <= 0 || height <= 0) return null
    val scale = 2.0.pow(z - state.zoom)
    val originX = (lonToWorldX(state.centerLon, state.zoom) - width / 2.0) * scale
    val originY = (latToWorldY(state.centerLat, state.zoom) - height / 2.0) * scale
    val widthPx = width * scale
    val heightPx = height * scale
    val maxIndex = (1 shl z) - 1
    val minX = floor(originX / 256.0).toInt().coerceIn(0, maxIndex)
    val maxX = floor((originX + widthPx) / 256.0).toInt().coerceIn(0, maxIndex)
    val minY = floor(originY / 256.0).toInt().coerceIn(0, maxIndex)
    val maxY = floor((originY + heightPx) / 256.0).toInt().coerceIn(0, maxIndex)
    if (minX > maxX || minY > maxY) return null
    return (minX..maxX) to (minY..maxY)
}

internal fun formatDownloadSize(tiles: Long): String {
    val mb = tiles * 22 / 1024
    return if (mb >= 1024) {
        val gb = mb / 1024
        val tenth = (mb % 1024) * 10 / 1024
        "$gb.$tenth GB"
    } else {
        "$mb MB"
    }
}

private fun tileZoom(zoom: Double, layer: String): Int = when (layer) {
    "satellite_hd" -> ceil(zoom).toInt().coerceIn(0, 19)
    "places" -> floor(zoom).toInt().coerceIn(0, 16)
    else -> {
        val maxZoom = MapStyles.byCacheLayer(layer)?.maxZoom ?: 19
        floor(zoom).toInt().coerceIn(0, maxZoom)
    }
}

private fun visibleTiles(
    state: SlippyMapState,
    width: Int,
    height: Int,
    layer: String,
): List<TileKey> {
    if (width <= 0 || height <= 0) return emptyList()
    val z = tileZoom(state.zoom, layer)
    val scale = 2.0.pow(state.zoom - z)
    val tilePx = 256.0 * scale
    val originX = lonToWorldX(state.centerLon, state.zoom) - width / 2.0
    val originY = latToWorldY(state.centerLat, state.zoom) - height / 2.0
    val maxIndex = (1 shl z) - 1
    val minX = floor(originX / tilePx).toInt() - 1
    val maxX = floor((originX + width) / tilePx).toInt() + 1
    val minY = floor(originY / tilePx).toInt() - 1
    val maxY = floor((originY + height) / tilePx).toInt() + 1

    val result = ArrayList<TileKey>()
    for (x in minX..maxX) {
        if (x !in 0..maxIndex) continue
        for (y in minY..maxY) {
            if (y !in 0..maxIndex) continue
            result.add(TileKey(z, x, y))
        }
    }
    return result
}

private class TileRepository(private val cacheRoot: File) {
    val images = mutableStateMapOf<String, ImageBitmap>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val knownMissing = ConcurrentHashMap.newKeySet<String>()

    /** Decoded tiles are ~256 KB each; without eviction a long session OOMs. */
    private val accessOrder = LinkedHashMap<String, Unit>(64, 0.75f, true)

    /** Volunteer-run OSM tile servers get at most 2 parallel bulk requests from us. */
    private val volunteerServerLimit = Semaphore(2)

    fun request(scope: CoroutineScope, layer: String, z: Int, x: Int, y: Int) {
        val key = "$layer/$z/$x/$y"
        if (images.containsKey(key)) {
            synchronized(accessOrder) { accessOrder[key] = Unit }
            return
        }
        if (key in knownMissing) return
        if (!inFlight.add(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = load(layer, z, x, y)
                if (bitmap != null) {
                    images[key] = bitmap
                    trim()
                }
            } finally {
                inFlight.remove(key)
            }
        }
    }

    /** Evicts least-recently-used decoded tiles; visible ones were just touched. */
    private fun trim() {
        synchronized(accessOrder) {
            while (accessOrder.size > MAX_IMAGE_TILES) {
                val eldest = accessOrder.keys.first()
                accessOrder.remove(eldest)
                images.remove(eldest)
            }
        }
    }

    suspend fun cacheTile(layer: String, z: Int, x: Int, y: Int): TileCacheResult = withContext(Dispatchers.IO) {
        val key = "$layer/$z/$x/$y"
        if (key in knownMissing) return@withContext TileCacheResult.UNAVAILABLE
        val file = File(cacheRoot, "$layer/$z/$x/$y.tile")
        if (file.length() > 0) {
            if (isUsableTileSize(layer, file.length())) return@withContext TileCacheResult.USABLE
            file.delete()
        }
        val url = urlFor(layer, z, x, y)
        val data = (if (url.contains("arcgisonline.com")) download(url) else volunteerServerLimit.withPermit { download(url) })
            ?: return@withContext TileCacheResult.FAILED
        if (!isUsableTileSize(layer, data.size.toLong())) {
            knownMissing.add(key)
            return@withContext TileCacheResult.UNAVAILABLE
        }
        if (runCatching {
                file.parentFile?.mkdirs()
                file.writeBytes(data)
            }.isSuccess) TileCacheResult.USABLE else TileCacheResult.FAILED
    }

    suspend fun probeMaxZoom(layer: String, lat: Double, lon: Double, nativeMax: Int = 19): Int =
        withContext(Dispatchers.IO) {
            val n = 1 shl nativeMax
            val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latR = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
            val y = floor((1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n)
                .toInt().coerceIn(0, n - 1)
            val data = download(urlFor(layer, nativeMax, x, y))
            if (data != null && isUsableTileSize(layer, data.size.toLong())) nativeMax else nativeMax - 1
        }

    /** Bytes of a cached tile that is good enough to show, or null. */
    fun cachedBytes(layer: String, z: Int, x: Int, y: Int): ByteArray? {
        val file = File(cacheRoot, "$layer/$z/$x/$y.tile")
        if (!file.isFile || !isUsableTileSize(layer, file.length())) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    private fun load(layer: String, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$layer/$z/$x/$y"
        if (key in knownMissing) return null
        MapStyles.byCacheLayer(layer)?.localPath?.let { path ->
            val bytes = MbTiles.readTile(path, z, x, y)
            if (bytes == null) knownMissing.add(key)
            return bytes?.let { runCatching { Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
        }
        val file = File(cacheRoot, "$layer/$z/$x/$y.tile")
        val bytes = if (file.exists()) {
            if (!isUsableTileSize(layer, file.length())) {
                file.delete()
                knownMissing.add(key)
                return null
            }
            runCatching { file.readBytes() }.getOrNull()
        } else {
            val data = download(urlFor(layer, z, x, y)) ?: return null
            if (!isUsableTileSize(layer, data.size.toLong())) {
                knownMissing.add(key)
                return null
            }
            runCatching {
                file.parentFile?.mkdirs()
                file.writeBytes(data)
            }
            data
        }
        if (bytes == null) return null
        return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }

    private fun download(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection!!.responseCode !in 200..299) {
                null
            } else {
                connection!!.inputStream.use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isUsableTileSize(layer: String, size: Long): Boolean = when (layer) {
        "satellite_hd", "satellite_esri", "satellite_google" -> size >= 2000
        "places" -> size > 0
        else -> size >= (MapStyles.byCacheLayer(layer)?.minTileBytes ?: 1)
    }

    private fun urlFor(layer: String, z: Int, x: Int, y: Int): String = when (layer) {
        "satellite_hd" ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        "satellite_esri" ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        // "satellite_google" existed once; Google tile access from third-party apps
        // violates their ToS, so saved settings fall back to Esri imagery.
        "satellite_google" ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        "places" ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$z/$y/$x"
        else -> MapStyles.byCacheLayer(layer)?.let { MapStyles.url(it, z, x, y) }
            ?: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$z/$y/$x"
    }

    companion object {
        private const val USER_AGENT = "Azimuth/0.1 (alexsandr23.83@mail.ru)"
    }
}

private fun lonToWorldX(lon: Double, zoom: Double): Double {
    val world = 256.0 * 2.0.pow(zoom)
    return (lon + 180.0) / 360.0 * world
}

private fun latToWorldY(lat: Double, zoom: Double): Double {
    val world = 256.0 * 2.0.pow(zoom)
    val clamped = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    val y = ln(tan(clamped) + 1.0 / cos(clamped))
    return (1.0 - y / PI) / 2.0 * world
}

private fun worldXToLon(x: Double, zoom: Double): Double {
    val world = 256.0 * 2.0.pow(zoom)
    return x / world * 360.0 - 180.0
}

private fun worldYToLat(y: Double, zoom: Double): Double {
    val world = 256.0 * 2.0.pow(zoom)
    val n = PI - 2.0 * PI * y / world
    return 180.0 / PI * atan(sinh(n))
}

/** Grade percent -> line color (flat green ... very steep red). */
internal fun gradeColor(percent: Float): Color = when {
    percent < 4f -> Color(0xFF4CAF50)
    percent < 8f -> Color(0xFFFFC107)
    percent < 12f -> Color(0xFFFF9800)
    else -> Color(0xFFE53935)
}

/** ARGB pixels of a shade tile -> Compose ImageBitmap. */
private fun bufferedShadeBitmap(tile: ru.azimut.app.core.dem.HillshadeTile): ImageBitmap {
    val image = java.awt.image.BufferedImage(
        tile.width, tile.height, java.awt.image.BufferedImage.TYPE_INT_ARGB,
    )
    image.setRGB(0, 0, tile.width, tile.height, tile.pixels, 0, tile.width)
    return image.toComposeImageBitmap()
}

private data class SavedMapView(val lat: Double, val lon: Double, val zoom: Double)

private object MapViewStore {
    private fun file(root: String): File = File(root, "map_view.txt")

    fun load(root: String): SavedMapView? = runCatching {
        val saved = file(root)
        if (!saved.exists()) return null
        val parts = saved.readText(Charsets.UTF_8).removePrefix("\uFEFF").trim().split(',')
        if (parts.size != 3) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        val zoom = parts[2].toDoubleOrNull() ?: return null
        if (lat !in -85.0..85.0 || lon !in -180.0..180.0 || zoom !in 2.0..19.0) return null
        SavedMapView(lat, lon, zoom)
    }.getOrNull()

    fun save(root: String, lat: Double, lon: Double, zoom: Double) {
        runCatching {
            val saved = file(root)
            saved.parentFile?.mkdirs()
            saved.writeText("$lat,$lon,$zoom", Charsets.UTF_8)
        }
    }
}
