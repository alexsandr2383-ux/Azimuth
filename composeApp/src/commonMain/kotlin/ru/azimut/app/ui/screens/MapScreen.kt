package ru.azimut.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import ru.azimut.app.core.location.RecordingControl
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.platform.AppCommands
import ru.azimut.app.core.map.MapStyles
import ru.azimut.app.core.map.UserMaps
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.PoiTypes
import ru.azimut.app.core.model.TransportProfile
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.bearingDegrees
import ru.azimut.app.core.model.distanceMeters
import ru.azimut.app.core.model.formatCoord
import ru.azimut.app.core.model.parseCoordPair
import ru.azimut.app.core.model.formatDistance
import ru.azimut.app.core.model.formatSpeed
import ru.azimut.app.core.platform.PlatformInfo
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.fuel.FuelRangeResult
import ru.azimut.app.core.fuel.fuelRange
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.platform.PlatformAlert
import ru.azimut.app.core.routing.FollowProgress
import ru.azimut.app.core.routing.RouteEngine
import ru.azimut.app.core.routing.RouteFollower
import ru.azimut.app.core.routing.toPlannedTrack
import ru.azimut.app.core.routing.trackSections
import ru.azimut.app.core.astro.SolarPosition
import ru.azimut.app.core.astro.TerrainSunTimes
import ru.azimut.app.core.astro.TimeUtil
import ru.azimut.app.core.dem.DemIndex
import ru.azimut.app.core.dem.DataNeed
import ru.azimut.app.core.dem.DemShade
import ru.azimut.app.core.dem.HillshadeTile
import ru.azimut.app.ui.components.gpxFileName
import ru.azimut.app.ui.components.rememberGpxSaver
import ru.azimut.app.core.routing.RouteData
import ru.azimut.app.core.routing.RouteError
import ru.azimut.app.core.routing.RouteRequest
import ru.azimut.app.core.routing.RouteResult
import ru.azimut.app.core.routing.SegmentDownloader
import ru.azimut.app.ui.components.HoverTooltip
import ru.azimut.app.ui.components.PhotoEditor
import ru.azimut.app.ui.components.PlatformBackHandler
import ru.azimut.app.ui.components.PoiTypePicker
import ru.azimut.app.ui.components.VoiceEditor
import ru.azimut.app.ui.components.deletePhoto
import ru.azimut.app.ui.components.deleteVoice
import ru.azimut.app.ui.components.poiTypeLabel
import ru.azimut.app.ui.map.MapCamera
import ru.azimut.app.ui.map.PlatformMap
import ru.azimut.app.ui.tr
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    points: List<Waypoint>,
    onAddPoint: (Waypoint) -> Unit,
    onDeletePoint: (Waypoint) -> Unit = {},
    onOpenPoints: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    /** Bumped by the app when NavData content changes; reloads DEM cache and OOPT layer. */
    dataVersion: Int = 0,
    /** Saved track/plan shown on the map (from the journal or a built route). */
    shownTrack: Track?,
    /** Extra saved tracks drawn as thin background lines (journal eye toggle). */
    backgroundTracks: List<Track> = emptyList(),
    following: Boolean,
    onShowTrack: (track: Track?, follow: Boolean) -> Unit,
    onSavePlan: suspend (Track) -> Result<Unit>,
    /** Journal eye toggle from the map context menu: show a hidden track or hide a shown one. */
    onToggleTrackShown: (Track) -> Unit = {},
    onRenameTrack: (Track, String) -> Unit = { _, _ -> },
    onDeleteTrack: (String) -> Unit = {},
) {
    val recorder by TrackRecorder.state.collectAsState()
    val locateRequest by AppCommands.locate.collectAsState()
    val current = recorder.lastFix
    val currentGeo = current?.let { GeoPoint(it.lat, it.lon, it.altitude) }

    // Two orientation modes: course-up while moving and north-up.
    val orientation = if (settings.orientationMode == "course") "course" else "north"
    val moving = current?.bearing != null && (current.speed ?: 0f) >= MIN_COURSE_SPEED_MPS
    var lastCourse by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(current) { if (moving) lastCourse = current?.bearing }
    val course = if (moving) current?.bearing else null
    val mapBearing = (if (orientation == "course") (course ?: lastCourse ?: 0f) else 0f)
        .roundToInt().toFloat()
    // Actual camera bearing reported by the map; null on platforms without manual rotation.
    var cameraBearing by remember { mutableStateOf<Float?>(null) }
    var orientationReset by remember { mutableStateOf(0) }
    val visiblePoints = remember(points, settings.showPois, settings.poiTypes) {
        if (!settings.showPois) emptyList()
        else points.filter { it.poiType in settings.poiTypes }
    }
    val poiTypeCounts = remember(points) { points.groupingBy { it.poiType }.eachCount() }

    var showMenu by remember { mutableStateOf(false) }
    var controlsExpanded by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showPoiTypes by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<GeoPoint?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showExit by remember { mutableStateOf(false) }
    var showGoto by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showStartRecording by remember { mutableStateOf(false) }
    var showStopRecording by remember { mutableStateOf(false) }
    var gotoText by remember { mutableStateOf("") }
    var gotoTarget by remember { mutableStateOf<GeoPoint?>(null) }
    var gotoKey by remember { mutableStateOf(0) }
    var routeDialogOpen by remember { mutableStateOf(false) }
    var routeFrom by remember { mutableStateOf<GeoPoint?>(null) }
    var routeTo by remember { mutableStateOf<GeoPoint?>(null) }
    var routeVia by remember { mutableStateOf(emptyList<GeoPoint>()) }
    var planNameFor by remember { mutableStateOf<RouteResult?>(null) }
    var corridorRequest by remember { mutableStateOf<Pair<String, List<GeoPoint>>?>(null) }
    var routeProfile by remember { mutableStateOf(settings.transportProfile) }
    var routeAvoidTrails by remember { mutableStateOf(settings.avoidTrails) }
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }
    var routeBusy by remember { mutableStateOf(false) }
    var segmentsProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var fuelResult by remember { mutableStateOf<FuelRangeResult?>(null) }
    val segmentsSavedMessage = tr("Данные для роутинга загружены.", "Routing data downloaded.")
    val segmentsFailedPrefix = tr("Не удалось скачать: ", "Could not download: ")
    var trailWarning by remember { mutableStateOf<RouteResult?>(null) }
    var pickTarget by remember { mutableStateOf<RouteTarget?>(null) }
    var secondaryMenu by remember { mutableStateOf<SecondaryMenu?>(null) }

    // Fuel ramps: recomputed when the route result or the fuel settings change (off = tank/consumption 0).
    val fuelEnabled = settings.fuelTankLiters > 0 && settings.fuelConsumptionL100 > 0
    LaunchedEffect(
        routeResult, fuelEnabled, settings.fuelTankLiters, settings.fuelConsumptionL100, settings.fuelReserveLiters,
    ) {
        val res = routeResult
        if (!fuelEnabled || res?.ok != true || res.points.size < 2) {
            fuelResult = null
        } else {
            fuelResult = withContext(Dispatchers.Default) {
                fuelRange(res.points, settings.fuelTankLiters, settings.fuelConsumptionL100, settings.fuelReserveLiters)
            }
        }
    }
    var renamingTrack by remember { mutableStateOf<Track?>(null) }

    // Fuel line for the top route card and the route dialog; hidden entirely when
    // fuel settings are zero/blank — display must not rely on stale fuelResult alone.
    val fuelText = if (!fuelEnabled) {
        null
    } else {
        fuelResult?.let { f ->
            val range = f.rangeKm ?: return@let null
            val route = f.routeKm ?: return@let null
            val fmt: (Double) -> String = { formatDistance(it * 1000.0) }
            val parts = mutableListOf(tr("Бак хватает на ", "The tank covers ") + fmt(range))
            if (f.emptyCut == null) {
                parts += tr("— маршрут ", "— the route ") + fmt(route) + tr(" помещается", " fits")
            } else {
                parts += tr("из маршрута ", "of the route ") + fmt(route)
            }
            if (f.reserveCutKm != null) parts += tr("резерв через ", "reserve after ") + fmt(f.reserveCutKm)
            if (f.emptyCutKm != null) parts += tr("сухой бак через ", "dry tank after ") + fmt(f.emptyCutKm)
            parts.joinToString(" · ")
        }
    }
    var renameTrackText by remember { mutableStateOf("") }
    var infoExpanded by rememberSaveable { mutableStateOf(false) }
    var routeCollapsed by rememberSaveable { mutableStateOf(false) }
    var exitArmed by remember { mutableStateOf(false) }
    var bottomPanelHeightPx by remember { mutableStateOf(0) }
    val satellites by PlatformLocation.satellites.collectAsState()
    val routeScope = rememberCoroutineScope()

    // DEM + zone watches: one store and one episode-watcher for the whole screen.
    val demStore = remember { ru.azimut.app.core.dem.DemStore() }
    LaunchedEffect(dataVersion) {
        if (dataVersion > 0) {
            demStore.clearCache()
            ru.azimut.app.core.dem.DemShade.clearCache()
        }
    }
    val susaninWatch = remember { ru.azimut.app.core.dem.SusaninWatch(demStore) }
    // Parsing every zone JSON on the main thread stalls the first frame and every
    // dataVersion bump — load the polygons on Default and start empty meanwhile.
    var protectedPolygons by remember(settings.showProtected) {
        mutableStateOf<List<ru.azimut.app.core.protected.ProtectedPolygon>>(emptyList())
    }
    var zoneFilesLoaded by remember { mutableStateOf(false) }
    val protectedWatch = remember(protectedPolygons, settings.protectedWarnings) {
        ru.azimut.app.core.protected.ProtectedWatch(protectedPolygons)
    }
    // Fired once per zone/condition; clears when the condition goes away.
    val langEn = settings.language == "en"
    val zoneNameFallback = if (langEn) "Protected area" else "ООПТ"

    // --- A1 pause / A2 proximity / A3 compass target state ---
    val navTarget = remember { mutableStateOf<Waypoint?>(null) }
    val proximityAlerted = remember { HashSet<String>() }
    val nearPointPrefix = tr("Точка рядом: ", "Waypoint nearby: ")
    val copiedCoordsMessage = tr("Координаты скопированы (DMS и десятичные).", "Coordinates copied (DMS and decimal).")
    val clipboard = LocalClipboardManager.current
    val heading by ru.azimut.app.core.platform.PlatformHeading.heading.collectAsState()
    DisposableEffect(navTarget.value, settings.headingSmoothing) {
        if (navTarget.value != null && ru.azimut.app.core.platform.PlatformHeading.available) {
            ru.azimut.app.core.platform.PlatformHeading.start(settings.headingSmoothing)
        } else {
            ru.azimut.app.core.platform.PlatformHeading.stop()
        }
        // Leaving the map screen must release the compass sensor too.
        onDispose { ru.azimut.app.core.platform.PlatformHeading.stop() }
    }

    // --- Unified "Загрузить?" funnel: one scanner, one dialog, one downloader ---
    var dataOfferShown by rememberSaveable { mutableStateOf(false) }
    var dataOfferNeeds by remember { mutableStateOf<List<ru.azimut.app.core.dem.DataNeed>?>(null) }
    var localZonesReload by remember { mutableStateOf(0) }
    LaunchedEffect(settings.showProtected, dataVersion, localZonesReload) {
        val polygons = if (settings.showProtected) {
            withContext(Dispatchers.Default) {
                ru.azimut.app.core.protected.ProtectedAreas.allPolygons()
            }
        } else {
            emptyList()
        }
        protectedPolygons = polygons
        zoneFilesLoaded = withContext(Dispatchers.Default) {
            ru.azimut.app.core.protected.ProtectedAreas.list().isNotEmpty()
        }
    }
    fun offerFor(lat: Double, lon: Double) {
        val needs = ru.azimut.app.core.dem.DataNeeds.missing(demStore, lat, lon, zoneFilesLoaded)
        if (needs.isNotEmpty()) dataOfferNeeds = needs
    }
    LaunchedEffect(currentGeo, recorder.recording) {
        val fixNow = currentGeo ?: return@LaunchedEffect
        if (!recorder.recording || dataOfferShown) return@LaunchedEffect
        dataOfferShown = true
        offerFor(fixNow.lat, fixNow.lon)
    }
    // Hillshade switched on without DEM around the fix.
    LaunchedEffect(settings.hillshade, currentGeo) {
        if (settings.hillshade && currentGeo != null && !dataOfferShown) {
            offerFor(currentGeo.lat, currentGeo.lon)
            dataOfferShown = true
        }
    }
    // A track opened on the map in a district without DEM.
    LaunchedEffect(shownTrack?.id) {
        val start = shownTrack?.points?.firstOrNull() ?: return@LaunchedEffect
        if (!dataOfferShown && !demStore.hasTileFor(start.lat, start.lon)) {
            offerFor(start.lat, start.lon)
            dataOfferShown = true
        }
    }
    val offerTitle = tr("Загрузить данные района?", "Fetch district data?")
    val offerText = tr(
        "Для этого района нет DEM-рельефа и/или зон запретов из OSM. Загрузить сейчас? " +
            "Нужна сеть один раз; дальше работает офлайн.",
        "This district has no DEM terrain and/or OSM restriction zones yet. Fetch now? " +
            "Network is needed once; everything works offline afterwards.",
    )
    val offerYes = tr("Загрузить", "Fetch")
    val offerNo = tr("Не сейчас", "Not now")
    val offerZonesDone = tr("Зоны OSM загружены: ", "OSM zones loaded: ")
    val offerZonesNone = tr("Зон OSM в районе не найдено.", "No OSM zones in the district.")
    val offerZonesBusy = tr("Сервер OSM перегружен, попробуйте позже.", "The OSM server is busy, try later.")
    val offerZonesFail = tr("Зоны OSM загрузить не удалось.", "Could not fetch OSM zones.")
    val offerDemDone = tr("DEM загружен.", "DEM loaded.")
    val offerDemNoNet = tr("DEM не скачан: нет сети.", "DEM not fetched: no network.")
    val offerDemFail = tr("DEM не скачан.", "DEM not fetched.")
    dataOfferNeeds?.let { needs ->
        val needsText = needs.joinToString(", ") {
            if (langEn) it.titleEn else it.titleRu
        }
        AlertDialog(
            onDismissRequest = { dataOfferNeeds = null },
            title = { Text(offerTitle) },
            text = { Text(offerText + " (" + needsText + ")") },
            confirmButton = {
                TextButton(onClick = {
                    val needsToFetch = needs
                    dataOfferNeeds = null
                    val loc = currentGeo ?: return@TextButton
                    routeScope.launch {
                        val report = ru.azimut.app.core.dem.DataFetcher.fetch(
                            needsToFetch, loc.lat, loc.lon,
                            ru.azimut.app.core.dem.DemSources.byId(settings.demSource)
                                ?: ru.azimut.app.core.dem.DemSources.builtIn.first(),
                            settings.demUrlTemplate,
                        )
                        demStore.clearCache()
                        ru.azimut.app.core.dem.DemShade.clearCache()
                        if (ru.azimut.app.core.dem.DataNeed.ZONES in needsToFetch &&
                            (report.zones > 0 || !report.offline && !report.serverBusy && !report.failed)
                        ) {
                            localZonesReload++
                        }
                        message = buildString {
                            if (ru.azimut.app.core.dem.DataNeed.ZONES in needsToFetch) {
                                append(
                                    when {
                                        report.zones > 0 -> offerZonesDone + report.zones
                                        report.serverBusy -> offerZonesBusy
                                        report.offline -> offerDemNoNet
                                        report.failed -> offerZonesFail
                                        else -> offerZonesNone
                                    }
                                )
                            }
                            if (ru.azimut.app.core.dem.DataNeed.DEM in needsToFetch) {
                                if (isNotEmpty()) append("\n")
                                append(
                                    when {
                                        report.demImported > 0 -> offerDemDone + " (${report.demImported})"
                                        report.offline -> offerDemNoNet
                                        report.failed -> offerDemFail
                                        else -> offerDemDone + " (0)"
                                    }
                                )
                            }
                        }
                    }
                }) { Text(offerYes) }
            },
            dismissButton = {
                TextButton(onClick = { dataOfferNeeds = null }) { Text(offerNo) }
            },
        )
    }

    // --- A6 sunset azimuth: terrain-corrected when DEM covers the point ---
    val timeZoneNow = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(timeZoneNow).date
    // TerrainSunTimes rebuilds the full horizon (hundreds of DEM samples) — recompute
    // only on ~1 km movement (0.01° cells) and off the main thread.
    val sunsetCellLat = currentGeo?.lat?.let { kotlin.math.round(it * 100.0) }
    val sunsetCellLon = currentGeo?.lon?.let { kotlin.math.round(it * 100.0) }
    var sunsetAzimuthDeg by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(sunsetCellLat, sunsetCellLon, today, demStore) {
        val loc = currentGeo ?: run {
            sunsetAzimuthDeg = null
            return@LaunchedEffect
        }
        sunsetAzimuthDeg = withContext(Dispatchers.Default) {
            runCatching {
                val terrain = TerrainSunTimes.forDate(today, loc.lat, loc.lon, timeZoneNow, demStore)
                val sunset = terrain?.effective?.sunset ?: return@runCatching null
                val millis = TimeUtil.millis(today, sunset, timeZoneNow) ?: return@runCatching null
                SolarPosition.at(millis, loc.lat, loc.lon)?.azimuthDeg
            }.getOrNull()
        }
    }

    // --- A4 grade colors for the recorded track (DEM first, GPS altitude fallback) ---
    // O(N) DEM sampling per fix: run on Default, keyed by point count.
    var trackGrades by remember { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(recorder.points.size, demStore) {
        val pts = recorder.points
        if (pts.size < 2) {
            trackGrades = null
            return@LaunchedEffect
        }
        trackGrades = withContext(Dispatchers.Default) {
            runCatching { computeGrades(pts, demStore) }.getOrNull()
        }
    }


    LaunchedEffect(current, settings.susaninEnabled, settings.protectedWarnings) {
        val fixNow = current
        if (fixNow == null) return@LaunchedEffect
        val course = fixNow.bearing?.toDouble()?.takeIf { (fixNow.speed ?: 0f) >= MIN_COURSE_SPEED_MPS }
        if (settings.susaninEnabled && recorder.recording) {
            val warning = susaninWatch.check(
                fixNow.lat,
                fixNow.lon,
                kotlinx.datetime.TimeZone.currentSystemDefault(),
                movingCourseDeg = course,
            )
            if (warning != null) message = if (langEn) warning.messageEn else warning.messageRu
        }
        if (settings.protectedWarnings && recorder.recording && protectedPolygons.isNotEmpty()) {
            val zone = protectedWatch.onPosition(fixNow.lat, fixNow.lon)
            if (zone != null) {
                val name = zone.polygon.name.ifBlank { zoneNameFallback }
                val access = zone.polygon.access
                message = if (access.isNotBlank()) {
                    if (langEn) "You entered zone $name (access=$access)"
                    else "Вы въехали в зону $name (access=$access)"
                } else {
                    if (langEn) "You entered zone $name"
                    else "Вы въехали в зону $name"
                }
            }
        }
        // A2: waypoint proximity alarm (once per waypoint until we move away again).
        if (settings.proximityAlerts) {
            val pos = GeoPoint(fixNow.lat, fixNow.lon)
            val target = navTarget.value
            if (target != null && distanceMeters(pos, GeoPoint(target.lat, target.lon)) < 15.0) {
                navTarget.value = null
            }
            var nearest: Waypoint? = null
            var nearestDist = Double.MAX_VALUE
            for (waypoint in points) {
                val d = distanceMeters(pos, GeoPoint(waypoint.lat, waypoint.lon))
                if (d < nearestDist) {
                    nearestDist = d
                    nearest = waypoint
                }
            }
            if (nearest != null && nearestDist < 200.0) {
                if (proximityAlerted.add(nearest.id)) {
                    message = nearPointPrefix + nearest.name + " · " + formatDistance(nearestDist)
                    PlatformAlert.offRoute()
                }
            } else if (nearest != null && nearestDist > 400.0) {
                proximityAlerted.clear()
            }
        }
    }

    fun buildRoute(from: GeoPoint, to: GeoPoint) {
        val profile = TransportProfile.byId(routeProfile)
        routeBusy = true
        routeResult = null
        routeCollapsed = false
        routeDialogOpen = true
        routeScope.launch {
            val result = RouteEngine.route(
                RouteRequest(
                    from,
                    to,
                    profile,
                    routeVia,
                    avoidTrails = routeAvoidTrails,
                    susanin = settings.susaninEnabled,
                )
            )
            routeResult = result
            routeBusy = false
            if (result.ok) {
                routeDialogOpen = false
                pickTarget = null
            }
            // Shown once per built route, not on every redraw.
            if (result.ok && (result.trailCount > 0 || result.restrictedMeters > 0)) trailWarning = result
        }
    }

    // Follow Track: progress along the shown line, alarm once when leaving it (repeated each minute).
    val follower = remember(shownTrack) {
        shownTrack?.let { track -> RouteFollower(track.points.map { GeoPoint(it.lat, it.lon) }) }
    }
    var followProgress by remember(shownTrack) { mutableStateOf<FollowProgress?>(null) }
    var offRoute by remember(shownTrack) { mutableStateOf(false) }
    var lastAlarmAt by remember(shownTrack) { mutableStateOf(0L) }
    val finishedMessage = tr("Финиш маршрута!", "Route finished!")
    LaunchedEffect(current, following, follower) {
        val position = currentGeo
        if (!following || follower == null || position == null) {
            followProgress = null
            offRoute = false
            return@LaunchedEffect
        }
        val progress = follower.update(position) ?: return@LaunchedEffect
        followProgress = progress
        val now = Clock.System.now().toEpochMilliseconds()
        if (follower.offRoute && (!offRoute || now - lastAlarmAt > 60_000)) {
            PlatformAlert.offRoute()
            lastAlarmAt = now
        }
        offRoute = follower.offRoute
        if (progress.finished) {
            message = finishedMessage
            onShowTrack(shownTrack, false)
        }
    }

    val saveGpxErrorMessage = tr("Не удалось сохранить GPX.", "Could not save GPX.")
    val gpxSavedPrefix = tr("GPX сохранён: ", "GPX saved: ")
    val saveGpx = rememberGpxSaver(
        onSaved = { message = gpxSavedPrefix + it },
        onError = { message = saveGpxErrorMessage },
    )
    val planSavedMessage = tr("Маршрут сохранён в журнал.", "Route saved to the journal.")
    val planSaveErrorMessage = tr("Не удалось сохранить маршрут: ", "Could not save the route: ")
    val planNamePrefix = tr("Маршрут", "Route")
    fun defaultPlanName(): String = planNamePrefix + " " + shortDateTime(Clock.System.now().toEpochMilliseconds())
    fun planOf(result: RouteResult, name: String): Track {
        val now = Clock.System.now().toEpochMilliseconds()
        return result.toPlannedTrack("plan-$now", name.ifBlank { defaultPlanName() }, routeProfile, now)
    }

    fun clearRoute() {
        routeFrom = null
        routeTo = null
        routeVia = emptyList()
        routeResult = null
        pickTarget = null
    }
    val hasRoute = routeFrom != null || routeTo != null || routeVia.isNotEmpty() || routeResult != null

    val shownRoutePoints = remember(shownTrack) {
        shownTrack?.points?.map { GeoPoint(it.lat, it.lon) }.orEmpty()
    }
    val shownRouteSections = remember(shownTrack) {
        shownTrack?.let { track -> trackSections(track.points) }.orEmpty()
    }

    val noFixMessage = tr("Нет GPS-фикса. Дождитесь определения координат.", "No GPS fix yet.")
    val baseSavedMessage = tr("Точка «база» сохранена.", "Base point saved.")
    val waypointName = tr("Точка", "Waypoint")
    val waypointSavedMessage = tr("Точка сохранена.", "Waypoint saved.")

    // Offered right in the route dialog when the router has no segment data for the area.
    fun downloadRoutingData() {
        val from = routeFrom ?: currentGeo
        val to = routeTo
        if (from == null || to == null) {
            message = noFixMessage
            return
        }
        val missing = RouteData.missingSegments(listOf(from) + routeVia + listOf(to))
        if (missing.isEmpty() || segmentsProgress != null) return
        segmentsProgress = 0 to missing.size
        routeScope.launch {
            val failed = SegmentDownloader.download(missing) { done, total ->
                segmentsProgress = done to total
            }
            segmentsProgress = null
            message = if (failed.isEmpty()) {
                segmentsSavedMessage
            } else {
                segmentsFailedPrefix + failed.joinToString(", ")
            }
            if (failed.isEmpty()) buildRoute(from, to)
        }
    }

    val startRecordingTooltip = tr("Начать запись трека (Ctrl+R)", "Start track recording (Ctrl+R)")
    val stopRecordingTooltip = tr("Остановить запись трека (Ctrl+R)", "Stop track recording (Ctrl+R)")

    val exitHint = tr("Нажмите «Назад» ещё раз для выхода", "Press Back again to exit")
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2000)
            exitArmed = false
            if (message == exitHint) message = null
        }
    }
    PlatformBackHandler(enabled = !exitArmed) {
        when {
            pickTarget != null -> pickTarget = null
            secondaryMenu != null -> secondaryMenu = null
            showMenu -> showMenu = false
            showLayers -> showLayers = false
            infoExpanded -> infoExpanded = false
            else -> {
                exitArmed = true
                message = exitHint
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val compact = maxHeight < 600.dp
        val panelMaxHeight = maxHeight * 0.6f
        val controlSize = if (compact) 44.dp else 48.dp
        val controlSpacing = if (compact) 6.dp else 8.dp
        val recordSize = if (compact) 52.dp else 62.dp
        val density = LocalDensity.current
        LaunchedEffect(maxHeight) {
            if (maxHeight < 420.dp) infoExpanded = false
        }
        // Viewport reported by the map: drives the "back to location" button and the
        // saved last position restored on the next launch (mobile).
        var camera by remember { mutableStateOf<MapCamera?>(null) }
        var recenter by remember { mutableStateOf(0) }
        var handledLocate by remember { mutableStateOf(0L) }
        LaunchedEffect(locateRequest, currentGeo) {
            if (locateRequest > handledLocate && currentGeo != null) {
                handledLocate = locateRequest
                recenter++
            }
        }
        var areaDownload by remember { mutableStateOf(0) }
        // --- Hillshade tiles around the viewport centre (A4 companion / DEM shading) ---
        val c0 = camera
        val hillshadeKey = if (settings.hillshade && c0 != null && c0.zoom >= 9.0) {
            val c = c0
            // Key on the 1-degree tile names of the same three probe points: full-precision
            // lat/lon would rebuild the shaded tiles on every camera move.
            buildSet {
                add(ru.azimut.app.core.dem.DemIndex.tileOf(c.lat, c.lon))
                add(ru.azimut.app.core.dem.DemIndex.tileOf(c.lat + 0.35, c.lon - 0.35))
                add(ru.azimut.app.core.dem.DemIndex.tileOf(c.lat - 0.35, c.lon + 0.35))
            }.sorted().joinToString()
        } else {
            null
        }
        val hillshadeTiles = remember(hillshadeKey) {
            if (hillshadeKey == null) return@remember emptyList<HillshadeTile>()
            runCatching {
                buildList {
                    for (part in hillshadeKey.split(",")) {
                        val bounds = ru.azimut.app.core.dem.DemIndex.tileBounds(part.trim()) ?: continue
                        DemShade.tileFor(demStore, bounds.first + 0.5, bounds.second + 0.5)?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }
        val areaDownloadProgressPrefix = tr("Загрузка карт: ", "Downloading maps: ")
        val areaDownloadDoneMessage = tr("Область сохранена для офлайн-просмотра.", "Area saved for offline viewing.")
        val areaDownloadFailedMessage = tr("Не удалось загрузить область.", "Could not download the area.")
        val locationVisible = currentGeo != null && camera != null && run {
            val c = camera!!
            val metersPerPx = 156543.03392 * cos(c.lat * PI / 180.0) / 2.0.pow(c.zoom)
            val radiusPx = with(density) { minOf(maxWidth, maxHeight).toPx() } / 2.0
            distanceMeters(currentGeo!!, GeoPoint(c.lat, c.lon)) < metersPerPx * radiusPx * 0.85
        }
        val latestSettings by rememberUpdatedState(settings)
        val changeSettings by rememberUpdatedState(onSettingsChange)
        LaunchedEffect(camera) {
            val c = camera ?: return@LaunchedEffect
            if (PlatformInfo.isDesktop) return@LaunchedEffect
            delay(1_000)
            changeSettings(latestSettings.copy(mapCenterLat = c.lat, mapCenterLon = c.lon, mapZoom = c.zoom))
        }
        DisposableEffect(Unit) {
            onDispose {
                if (!PlatformInfo.isDesktop) {
                    camera?.let { c ->
                        changeSettings(latestSettings.copy(mapCenterLat = c.lat, mapCenterLon = c.lon, mapZoom = c.zoom))
                    }
                }
            }
        }
        PlatformMap(
            modifier = Modifier.fillMaxSize(),
            currentLocation = currentGeo,
            trackPoints = recorder.points,
            waypoints = visiblePoints,
            routePoints = routeResult?.takeIf { it.ok }?.points ?: shownRoutePoints,
            routeSections = routeResult?.takeIf { it.ok }?.sections ?: shownRouteSections,
            routeStops = if (routeResult != null || routeDialogOpen || pickTarget != null) {
                listOfNotNull(routeFrom) + routeVia + listOfNotNull(routeTo)
            } else {
                emptyList()
            },
            fuelReserve = fuelResult?.reserveCut,
            fuelEmpty = fuelResult?.emptyCut,
            mapLayer = settings.mapLayer,
            mapStyle = settings.mapStyle,
            baseOpacity = settings.mapOpacity,
            overlayEnabled = settings.overlayEnabled,
            overlayLayer = settings.overlayLayer,
            overlayOpacity = settings.overlayOpacity,
            showPois = settings.showPois,
            mapBearing = mapBearing,
            orientationResetKey = orientationReset,
            onCameraBearingChange = { cameraBearing = it },
            initialCamera = settings.mapCenterLat?.let { lat ->
                MapCamera(lat, settings.mapCenterLon ?: lat, settings.mapZoom ?: 14.0)
            },
            onViewportChange = { lat, lon, zoom -> camera = MapCamera(lat, lon, zoom) },
            recenterKey = recenter,
            backgroundTracks = remember(backgroundTracks) {
                backgroundTracks.map { track -> track.points.map { GeoPoint(it.lat, it.lon) } }
            },
            protectedAreas = if (settings.showProtected) protectedPolygons else emptyList(),
            trackGrades = trackGrades,
            sunsetAzimuthDeg = sunsetAzimuthDeg,
            gotoTarget = gotoTarget,
            gotoKey = gotoKey,
            hillshadeTiles = hillshadeTiles,
            areaDownloadKey = areaDownload,
            onAreaDownloadProgress = { downloaded, total, complete, error ->
                message = when {
                    error -> areaDownloadFailedMessage
                    complete -> areaDownloadDoneMessage
                    else -> areaDownloadProgressPrefix + "$downloaded/$total"
                }
            },
            followLocation = orientation == "course",
            downloadCorridor = corridorRequest,
            onDownloadCorridorDone = { corridorRequest = null },
            onTap = { point ->
                val target = pickTarget
                if (target == null) {
                    // Desktop: a plain click is just a click; "Add waypoint" is in the right-click menu.
                    if (!PlatformInfo.isDesktop) picked = point
                } else {
                    when (target) {
                        RouteTarget.FROM -> routeFrom = point
                        RouteTarget.TO -> routeTo = point
                    }
                    pickTarget = null
                    routeDialogOpen = true
                }
            },
            onSecondaryTap = { point, offset ->
                secondaryMenu = SecondaryMenu(point, offset)
            },
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            // A3: compass panel to the selected waypoint.
            navTarget.value?.let { target ->
                val targetPos = GeoPoint(target.lat, target.lon)
                val bearing = currentGeo?.let { bearingDegrees(it, targetPos) }
                val distance = currentGeo?.let { distanceMeters(it, targetPos) }
                val headingNow = heading
                val rotation = if (headingNow != null && bearing != null) {
                    (bearing - headingNow).toFloat()
                } else {
                    0f
                }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
                .gestureBarrier(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.97f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = tr("Направление", "Direction"),
                    modifier = Modifier.size(26.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(target.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        (distance?.let { formatDistance(it) } ?: "—") +
                            (bearing?.let { " · ${it.roundToInt()}°" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { navTarget.value = null }) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Закрыть", "Close"))
                }
            }
        }
    }

        MapContextMenu(
            menu = secondaryMenu,
            points = points,
            camera = camera,
            shownTrack = shownTrack,
            backgroundTracks = backgroundTracks,
            hasRoute = hasRoute,
            onDismiss = { secondaryMenu = null },
            onRouteFrom = {
                routeFrom = it
                routeDialogOpen = true
            },
            onRouteTo = {
                routeTo = it
                routeDialogOpen = true
            },
            onRouteHere = { point ->
                val from = routeFrom ?: currentGeo
                if (from == null) {
                    message = noFixMessage
                } else {
                    routeFrom = from
                    routeTo = point
                    buildRoute(from, point)
                }
            },
            onVia = { point ->
                routeVia = routeVia + point
                val from = routeFrom ?: currentGeo
                val to = routeTo
                if (routeResult?.ok == true && from != null && to != null) buildRoute(from, to)
            },
            onClearRoute = { clearRoute() },
            onToggleTrack = { onToggleTrackShown(it) },
            onRenameTrack = {
                renamingTrack = it
                renameTrackText = it.name
            },
            onDeleteTrack = { onDeleteTrack(it.id) },
            onExportTrack = { hit ->
                routeScope.launch {
                    val content = withContext(Dispatchers.Default) { Gpx.writeTrack(hit) }
                    saveGpx(gpxFileName(hit.name), content)
                }
            },
            onCopyCoords = {
                clipboard.setText(
                    AnnotatedString(formatCoord(it.lat, it.lon, dms = true) + "\n" +
                        formatCoord(it.lat, it.lon)),
                )
                message = copiedCoordsMessage
            },
            onNavigateTo = { navTarget.value = it },
            onDeletePoint = { onDeletePoint(it) },
            onAddPoint = { picked = it },
        )

        renamingTrack?.let { track ->
            AlertDialog(
                onDismissRequest = { renamingTrack = null },
                title = { Text(tr("Переименовать трек", "Rename track")) },
                text = {
                    OutlinedTextField(
                        value = renameTrackText,
                        onValueChange = { renameTrackText = it },
                        singleLine = true,
                        label = { Text(tr("Название", "Name")) },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = renameTrackText.trim()
                            if (name.isNotEmpty()) onRenameTrack(track, name)
                            renamingTrack = null
                        },
                    ) { Text(tr("Сохранить", "Save")) }
                },
                dismissButton = {
                    TextButton(onClick = { renamingTrack = null }) { Text(tr("Отмена", "Cancel")) }
                },
            )
        }

        // Top status: satellites used in the fix.
        val accuracy = current?.accuracy
        val badgeColor = if (accuracy == null || accuracy > 30f) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 10.dp, start = 10.dp)
                .gestureBarrier(),
            shape = MapButtonShape,
            color = MapPanelColor,
            contentColor = MapPanelContent,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    formatSpeed(current?.speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    current?.altitude?.let { "▲ ${it.roundToInt()} " + tr("м", "m") } ?: "▲ —",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    current?.bearing?.takeIf { (current.speed ?: 0f) >= MIN_COURSE_SPEED_MPS }
                        ?.let { "⌖ ${it.roundToInt()}°" } ?: "⌖ —",
                    style = MaterialTheme.typography.titleMedium,
                    color = MapAccent,
                )
                Icon(
                    Icons.Filled.SatelliteAlt,
                    contentDescription = tr("Спутники", "Satellites"),
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    satellites?.toString() ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeColor,
                )
                if (PlatformLocation.isSimulated) {
                    Text(
                        tr("СИМ", "SIM"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        pickTarget?.let { target ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .gestureBarrier(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    if (target == RouteTarget.FROM) {
                        tr("Укажите точку «Откуда» (клик по карте)", "Tap map for “From” point")
                    } else {
                        tr("Укажите точку «Куда» (клик по карте)", "Tap map for “To” point")
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Layers button: top-right corner like Guru, always visible.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .gestureBarrier(),
        ) {
            MapControl(
                Icons.Filled.Layers,
                tr("Слои", "Layers"),
                settings.mapLayer == "satellite" || settings.overlayEnabled,
                size = controlSize,
            ) {
                showLayers = true
            }
        if (showLayers) {
            MapLayersSheet(
                settings = settings,
                onSettingsChange = onSettingsChange,
                allStyles = MapStyles.all,
                overlayOptions = MapStyles.satelliteOptions +
                    UserMaps.stylesFlow.collectAsState().value.map { it.cacheLayer to Pair(it.titleRu, it.titleEn) },
                onPoiTypes = { showPoiTypes = true },
                onDismiss = { showLayers = false },
            )
        }
        }

        // Right side controls; the strip swallows taps so misses hit nothing.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 10.dp, end = 10.dp, bottom = 10.dp)
                .verticalScroll(rememberScrollState())
                .gestureBarrier(),
            verticalArrangement = Arrangement.spacedBy(controlSpacing),
            horizontalAlignment = Alignment.End,
        ) {
            HoverTooltip(if (recorder.recording) stopRecordingTooltip else startRecordingTooltip) {
                FilledIconButton(
                    onClick = {
                        if (recorder.recording) showStopRecording = true else showStartRecording = true
                    },
                    modifier = Modifier.size(recordSize).shadow(4.dp, MapButtonShape),
                    shape = MapButtonShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (recorder.recording) MapRecordRed else MapPanelColor,
                        contentColor = if (recorder.recording) Color.White else MapPanelContent,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (recorder.recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = if (recorder.recording) tr("Остановить", "Stop") else tr("Записать", "Record"),
                            tint = if (recorder.recording) Color.White else MapRecordRed,
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                        )
                        Text(
                            if (recorder.recording) "STOP" else "REC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (recorder.recording) {
                MapControl(
                    Icons.Filled.Pause,
                    if (recorder.paused) tr("Продолжить запись", "Resume recording")
                    else tr("Пауза записи", "Pause recording"),
                    recorder.paused,
                    size = controlSize,
                ) { TrackRecorder.setPaused(!recorder.paused) }
            }

            if (currentGeo != null && camera != null) {
                MapControl(
                    Icons.Filled.MyLocation,
                    tr("К текущему местоположению", "Back to location"),
                    size = controlSize,
                ) { recenter++ }
            }

            // Mobile: always-visible download button (the "Layers" menu item is tucked
            // away under "More"). Desktop has its own button with area selection.
            if (!PlatformInfo.isDesktop) {
                MapControl(
                    Icons.Filled.CloudDownload,
                    tr("Скачать видимую область", "Download visible area"),
                    size = controlSize,
                ) { areaDownload++ }
            }

            MapControl(
                Icons.Filled.Route,
                tr("Маршрут", "Route"),
                routeResult?.ok == true,
                size = controlSize,
            ) {
                routeDialogOpen = true
                secondaryMenu = null
            }

            MapControl(Icons.Filled.Search, tr("Поиск", "Search"), size = controlSize) { showSearch = true }
            if (showSearch) {
                MapSearchSheet(
                    points = points,
                    onGo = { gotoTarget = it; gotoKey++ },
                    onDismiss = { showSearch = false },
                )
            }

            MapControl(Icons.Filled.Menu, tr("Меню", "Menu"), size = controlSize) { showMenu = true }
            if (showMenu) {
                MapMenuSheet(
                    onDismiss = { showMenu = false },
                    onGoto = { showMenu = false; showGoto = true },
                    onPoints = { showMenu = false; onOpenPoints() },
                    onJournal = { showMenu = false; onOpenJournal() },
                    onSettings = { showMenu = false; onOpenSettings() },
                    onHelp = { showMenu = false; onOpenHelp() },
                    onAbout = { showMenu = false; onOpenAbout() },
                )
            }

            if (controlsExpanded) {
            MapControl(Icons.Filled.AddLocationAlt, tr("Точка", "Waypoint"), size = controlSize) {
                if (currentGeo == null) message = noFixMessage else picked = currentGeo
            }

            if (showPoiTypes) {
                AlertDialog(
                    onDismissRequest = { showPoiTypes = false },
                    title = { Text(tr("Типы POI", "POI types")) },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            PoiTypes.ALL.forEach { type ->
                                val checked = type in settings.poiTypes
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val next = if (checked) {
                                                settings.poiTypes - type
                                            } else {
                                                settings.poiTypes + type
                                            }
                                            onSettingsChange(settings.copy(poiTypes = next))
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Text(
                                        poiTypeLabel(type),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        (poiTypeCounts[type] ?: 0).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPoiTypes = false }) {
                            Text(tr("Готово", "Done"))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    onSettingsChange(settings.copy(poiTypes = PoiTypes.defaults))
                                },
                            ) { Text(tr("Все", "All")) }
                            TextButton(
                                onClick = {
                                    onSettingsChange(settings.copy(poiTypes = emptySet()))
                                },
                            ) { Text(tr("Ничего", "None")) }
                        }
                    },
                )
            }

            MapControl(Icons.Filled.Home, tr("База", "Base"), settings.basePoint != null, size = controlSize) {
                val fix = recorder.lastFix
                if (fix == null) {
                    message = noFixMessage
                } else {
                    onSettingsChange(settings.copy(baseLat = fix.lat, baseLon = fix.lon))
                    message = baseSavedMessage
                }
            }

            MapControl(
                Icons.Filled.Warning,
                tr("Режим Сусанина", "Explorer Mode"),
                settings.susaninEnabled,
                size = controlSize,
            ) {
                val next = !settings.susaninEnabled
                onSettingsChange(settings.copy(susaninEnabled = next))
                TrackRecorder.setSusanin(next)
            }

            if (settings.susaninEnabled) {
                MapControl(Icons.Filled.ExitToApp, tr("Выйти отсюда", "Get me out"), size = controlSize) {
                    showExit = true
                }
            }

            }

            MapControl(
                Icons.Filled.Explore,
                orientationLabel(orientation),
                orientation == "course",
                iconRotation = -mapBearing,
                size = controlSize,
            ) {
                // A manual rotation is corrected first; the next tap switches the mode.
                val actual = cameraBearing
                val manualRotation = actual != null &&
                    ((actual - mapBearing + 540f) % 360f - 180f).let { it > 3f || it < -3f }
                if (manualRotation) {
                    orientationReset++
                } else {
                    onSettingsChange(
                        settings.copy(orientationMode = if (orientation == "course") "north" else "course")
                    )
                }
            }

            MapControl(
                if (controlsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                if (controlsExpanded) tr("Свернуть", "Collapse") else tr("Ещё", "More"),
                size = controlSize,
            ) { controlsExpanded = !controlsExpanded }
        }

        // Bottom info
        Column(
            modifier = (if (wide) {
                Modifier.align(Alignment.BottomStart).widthIn(max = 480.dp)
            } else {
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            })
                .onSizeChanged { bottomPanelHeightPx = it.height }
                .heightIn(max = panelMaxHeight)
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 10.dp, end = 70.dp, bottom = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (settings.bigSpeed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MapPanelColor,
                    contentColor = MapPanelContent,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            current?.speed?.let { ((it * 3.6).roundToInt()).toString() } ?: "—",
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Text(
                            " " + tr("км/ч", "km/h"),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatDistance(recorder.distanceMeters),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            if (settings.susaninEnabled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Text(
                        tr("Режим Сусанина", "Explorer Mode"),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            routeResult?.takeIf { it.ok }?.let { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1565C0).copy(alpha = 0.94f),
                    contentColor = Color.White,
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { routeCollapsed = !routeCollapsed },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = if (routeCollapsed) Icons.Filled.ExpandMore
                                    else Icons.Filled.ExpandLess,
                                    contentDescription = if (routeCollapsed) {
                                        tr("Развернуть", "Expand")
                                    } else {
                                        tr("Свернуть", "Collapse")
                                    },
                                    tint = Color.White,
                                )
                            }
                            Text(
                                if (routeCollapsed) {
                                    tr("Маршрут", "Route") + " · " + routeSummary(result)
                                } else {
                                    tr("Маршрут", "Route")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!routeCollapsed) {
                                TextButton(
                                    onClick = { clearRoute() },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        tr("Очистить", "Clear"),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                            TextButton(
                                onClick = { routeCollapsed = true },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    tr("Скрыть", "Hide"),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        if (!routeCollapsed) {
                            Text(
                                routeSummary(result),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            fuelText?.let { ft ->
                                Text(
                                    ft,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFFCC80),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            routeWarnings(result).forEach { warning ->
                                Text(
                                    "⚠ $warning",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFFCC80),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                TextButton(
                                    onClick = { planNameFor = result },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        tr("Сохранить / GPX", "Save / GPX"),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onShowTrack(planOf(result, defaultPlanName()), true)
                                        routeResult = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        tr("Вести по маршруту", "Navigate"),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                if (PlatformInfo.isDesktop) {
                                    TextButton(
                                        onClick = { corridorRequest = defaultPlanName() to result.points },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            tr("Карты вдоль маршрута", "Maps along route"),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (routeResult?.ok != true) shownTrack?.let { track ->
                ShownTrackCard(
                    track = track,
                    following = following,
                    progress = followProgress,
                    offRoute = offRoute,
                    onFollow = { onShowTrack(track, it) },
                    onHide = { onShowTrack(null, false) },
                    onDownloadMaps = if (PlatformInfo.isDesktop) {
                        { corridorRequest = track.name to track.points.map { GeoPoint(it.lat, it.lon) } }
                    } else {
                        null
                    },
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { infoExpanded = !infoExpanded },
                shape = RoundedCornerShape(12.dp),
                color = MapPanelColor,
                contentColor = MapPanelContent,
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (current != null) formatCoord(current.lat, current.lon) else "—") +
                                " · " + formatSpeed(current?.speed),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            " · " + (if (accuracy != null) "±${accuracy.roundToInt()} м" else "±—"),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (accuracy == null || accuracy > 30f) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                        )
                    }
                    if (infoExpanded) {
                        val distanceLabel = tr("Пробег", "Distance")
                        val pointsLabel = tr("Точек", "Points")
                        val baseLabel = tr("До базы", "To base")
                        val rallyLabel = tr("До сбора", "To rally")
                        val details = mutableListOf<Pair<String, String>>()
                        details.add(distanceLabel to formatDistance(recorder.distanceMeters))
                        details.add(pointsLabel to recorder.points.size.toString())
                        settings.basePoint?.let { base ->
                            currentGeo?.let { position ->
                                details.add(baseLabel to formatDistance(distanceMeters(position, base)))
                            }
                        }
                        settings.rallyPoint?.let { rally ->
                            currentGeo?.let { position ->
                                details.add(rallyLabel to formatDistance(distanceMeters(position, rally)))
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            details.forEach { (label, value) ->
                                Text(
                                    "$label $value",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showStartRecording) {
        StartRecordingDialog(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onStart = { showStartRecording = false; RecordingControl.start(settings) },
            onDismiss = { showStartRecording = false },
        )
    }
    if (showStopRecording) {
        StopRecordingDialog(
            onStop = { showStopRecording = false; RecordingControl.stop() },
            onDismiss = { showStopRecording = false },
        )
    }

    if (showGoto) {
        GotoDialog(
            text = gotoText,
            onTextChange = { gotoText = it },
            onDismiss = { showGoto = false },
            onGo = { gotoTarget = it; gotoKey++; showGoto = false },
            onInvalid = { message = it },
        )
    }

    picked?.let { point ->
        AddPointDialog(
            point = point,
            photoQuality = settings.photoQuality,
            voiceEnabled = settings.voiceNotes,
            onDismiss = { picked = null },
            onConfirm = { name, description, photos, voiceNotes, type ->
                onAddPoint(
                    Waypoint(
                        id = "wp-${Clock.System.now().toEpochMilliseconds()}",
                        name = name.ifBlank { waypointName },
                        description = description,
                        lat = point.lat,
                        lon = point.lon,
                        altitude = point.altitude,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        category = type,
                        photos = photos,
                        voiceNotes = voiceNotes,
                    )
                )
                picked = null
                message = waypointSavedMessage
            },
        )
    }

    if (exitArmed) ExitHintOverlay(exitHint)
    message?.let { text ->
        if (!(exitArmed && text == exitHint)) MessageDialog(text) { message = null }
    }

    planNameFor?.let { result ->
        PlanSaveDialog(
            defaultName = remember(result) { defaultPlanName() },
            onDismiss = { planNameFor = null },
            onSave = { name ->
                routeScope.launch {
                    val saveResult = onSavePlan(planOf(result, name))
                    planNameFor = null
                    message = saveResult.fold(
                        onSuccess = { planSavedMessage },
                        onFailure = { planSaveErrorMessage + (it.message ?: it.toString()) },
                    )
                }
            },
            onExport = { name ->
                val plan = planOf(result, name)
                planNameFor = null
                routeScope.launch {
                    onSavePlan(plan).onFailure { message = planSaveErrorMessage + (it.message ?: it.toString()) }
                    val content = withContext(Dispatchers.Default) { Gpx.writeTrack(plan) }
                    saveGpx(gpxFileName(name), content)
                }
            },
        )
    }

    if (routeDialogOpen) {
        RouteDialog(
            from = routeFrom,
            to = routeTo,
            profileId = routeProfile,
            canUseCurrent = currentGeo != null,
            busy = routeBusy,
            result = routeResult,
            onFromCurrent = { currentGeo?.let { routeFrom = it } },
            onFromPick = {
                pickTarget = RouteTarget.FROM
                routeDialogOpen = false
            },
            onToCurrent = { currentGeo?.let { routeTo = it } },
            onToPick = {
                pickTarget = RouteTarget.TO
                routeDialogOpen = false
            },
            onSwap = {
                val from = routeFrom
                routeFrom = routeTo
                routeTo = from
                routeVia = routeVia.reversed()
            },
            viaCount = routeVia.size,
            onClearVia = { routeVia = emptyList() },
            onClearAll = { clearRoute() },
            onProfileChange = { routeProfile = it },
            avoidTrails = routeAvoidTrails,
            onAvoidTrailsChange = { next ->
                routeAvoidTrails = next
                onSettingsChange(settings.copy(avoidTrails = next))
            },
            susanin = settings.susaninEnabled,
            onBuild = {
                val from = routeFrom ?: currentGeo
                val to = routeTo
                if (from != null && to != null) {
                    if (routeFrom == null) routeFrom = from
                    buildRoute(from, to)
                } else {
                    message = noFixMessage
                }
            },
            onDismiss = {
                routeDialogOpen = false
                pickTarget = null
            },
            segmentsProgress = segmentsProgress,
            missingSegmentCount = run {
                val from = routeFrom ?: currentGeo
                val to = routeTo
                if (routeResult?.error == RouteError.NO_SEGMENTS && from != null && to != null) {
                    RouteData.missingSegments(listOf(from) + routeVia + listOf(to)).size
                } else {
                    0
                }
            },
            onDownloadSegments = { downloadRoutingData() },
            fuelText = fuelText.orEmpty(),
        )
    }

    trailWarning?.let { result ->
        AlertDialog(
            onDismissRequest = { trailWarning = null },
            title = { Text(tr("На маршруте есть тропы", "The route uses trails")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    routeWarnings(result).forEach { Text("⚠ $it") }
                    Text(
                        tr(
                            "Участки-тропы показаны оранжевым пунктиром. Это не дорога: возможны " +
                                "узкие и сложные места, а также запрет проезда на мототехнике.",
                            "Trail sections are drawn as an orange dashed line. They are not roads: " +
                                "expect narrow, technical spots and possible bans on motor vehicles.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { trailWarning = null }) { Text(tr("Понятно", "Got it")) } },
        )
    }

    if (showExit) {
        val targets = buildList {
            settings.basePoint?.let { add(tr("База", "Base") to it) }
            settings.rallyPoint?.let { add(tr("Точка сбора", "Rally point") to it) }
            recorder.points.lastOrNull()?.let {
                add(tr("Последняя точка трека", "Last track point") to GeoPoint(it.lat, it.lon))
            }
        }
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(tr("Выйти отсюда", "Get me out")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        tr(
                            "Куда строим безопасный маршрут? Будет построен офлайн-маршрут по данным BRouter.",
                            "Where should we route to? An offline BRouter route will be built.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (targets.isEmpty()) {
                        Text(tr("Нет сохранённых целей.", "No saved targets."))
                    }
                    targets.forEach { (title, target) ->
                        val distance = currentGeo?.let { distanceMeters(it, target) }
                        TextButton(
                            onClick = {
                                val from = currentGeo
                                if (from == null) {
                                    message = noFixMessage
                                } else {
                                    routeFrom = from
                                    routeTo = target
                                    buildRoute(from, target)
                                }
                                showExit = false
                            },
                        ) {
                            Text(
                                buildString {
                                    append(title)
                                    if (distance != null) append(" · " + formatDistance(distance))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExit = false }) { Text(tr("Отмена", "Cancel")) }
            },
        )
    }

}
