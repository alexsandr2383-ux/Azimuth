package ru.azimut.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.azimut.app.core.compare.OFF_ROUTE_THRESHOLD_M
import ru.azimut.app.core.compare.TrackCompareResult
import ru.azimut.app.core.compare.compareTracks
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.ui.components.ElevationChart
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackSummary
import ru.azimut.app.core.model.TransportProfile
import ru.azimut.app.core.model.formatDistance
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.routing.formatRouteDuration
import ru.azimut.app.ui.LocalLang
import ru.azimut.app.ui.components.BackHeader
import ru.azimut.app.ui.components.EmptyState
import ru.azimut.app.ui.components.InfoRow
import ru.azimut.app.ui.components.gpxFileName
import ru.azimut.app.ui.components.rememberBytesSaver
import ru.azimut.app.ui.components.rememberGpxSaver
import ru.azimut.app.ui.tr
import kotlin.math.abs

@Composable
fun JournalScreen(
    /** True = the «Маршруты» tab: only planned routes; false = recorded tracks. */
    routesOnly: Boolean = false,
    summaries: List<TrackSummary>,
    loadTrack: (id: String) -> Track?,
    onShow: (track: Track, follow: Boolean) -> Unit,
    /** Track ids currently drawn on the map (the active one plus background ones). */
    shownTrackIds: Set<String> = emptySet(),
    /** Adds/removes a background track line on the map; the active track is hidden too. */
    onToggleShown: (track: Track) -> Unit = {},
    /** From the comparison dialog: recorded becomes the active track, the plan — a background line. */
    onShowComparison: (recorded: Track, planned: Track) -> Unit = { _, _ -> },
    onDelete: (id: String) -> Unit,
    /** Recently deleted tracks (kept 30 days). */
    trash: List<ru.azimut.app.core.storage.TrashedTrack> = emptyList(),
    onRestore: (id: String) -> Unit = {},
    onPurge: (id: String) -> Unit = {},
    onSwitchTab: (ru.azimut.app.Screen) -> Unit = {},
    onBack: () -> Unit,
) {
    var showTrash by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ru.azimut.app.core.model.TrackSort.DATE) }
    val scoped = remember(summaries, routesOnly) {
        if (routesOnly) summaries.filter { it.kind == KIND_ROUTE } else summaries.filter { it.kind != KIND_ROUTE }
    }
    val shown = remember(scoped, query, sort) {
        ru.azimut.app.core.model.TrackListOps.filterSort(scoped, query, sort)
    }
    var confirmPurge by remember { mutableStateOf<ru.azimut.app.core.storage.TrashedTrack?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<TrackSummary?>(null) }
    var precheck by remember { mutableStateOf<Pair<Track, ru.azimut.app.core.export.TrackPrecheck>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var comparePicker by remember { mutableStateOf<Track?>(null) }

    // Expanded track card: full track + DEM profile are loaded lazily on first expand.
    var expandedId by remember { mutableStateOf<String?>(null) }
    var expandedData by remember {
        mutableStateOf<Pair<Track, ru.azimut.app.core.dem.ElevationProfile>?>(null)
    }
    var compare by remember { mutableStateOf<Triple<Track, Track, TrackCompareResult>?>(null) }
    val loadFailed = tr("Не удалось открыть трек.", "Could not open the track.")
    val noPlansMessage = tr(
        "Нет сохранённых маршрутов для сравнения. Постройте маршрут на карте и сохраните его.",
        "No saved routes to compare. Build a route on the map and save it first.",
    )

    val savedPrefix = tr("GPX сохранён: ", "GPX saved: ")
    val saveFailed = tr("Не удалось сохранить GPX.", "Could not save GPX.")
    val saveGpx = rememberGpxSaver(onSaved = { message = savedPrefix + it }, onError = { message = saveFailed })
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val demStore = remember { ru.azimut.app.core.dem.DemStore() }
    val sheetSavedPrefix = tr("Сохранено: ", "Saved: ")
    val sheetSaveFailed = tr("Не удалось сохранить файл.", "Could not save the file.")
    val saveSheet = rememberBytesSaver(
        onSaved = { message = sheetSavedPrefix + it },
        onError = { message = sheetSaveFailed },
    )

    val sheetBuildFailed = tr("Не удалось построить лист экспорта.", "Could not build the export sheet.")

    fun exportSheet(track: Track, pdf: Boolean) {
        exporting = true
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                val elev = ru.azimut.app.core.dem.TrackElevation.analyze(track, demStore)
                val zones = ru.azimut.app.core.protected.ProtectedAreas.allPolygons()
                val precheckResult = ru.azimut.app.core.export.TrackPrecheckEngine.check(track, demStore, zones, zones.isNotEmpty())
                val lines = trackSheetLines(track, elev, precheckResult, lang == "en")
                val sheet = ru.azimut.app.core.export.TrackSheet(
                    track = track,
                    waypoints = emptyList(),
                    lines = lines,
                    heights = elev.heights,
                )
                val png = ru.azimut.app.core.export.TrackImageRenderer.renderPng(sheet, 1200, 840)
                if (png == null) {
                    null
                } else if (pdf) {
                    val (rgb, w, h) = ru.azimut.app.core.export.TrackImagePixels.fromPng(png) ?: return@withContext null
                    ru.azimut.app.core.export.PdfWriter().build(
                        rgb, w, h,
                        footerLines = listOf(
                            "Azimuth export",
                            "Distance km: ${"%.1f".format(track.distanceMeters / 1000.0)}",
                            "Points: ${track.points.size}",
                            if (precheckResult.zoneHits.isEmpty()) "No zone data / no hits" else "Zones: " +
                                precheckResult.zoneHits.joinToString("; ").take(200),
                            if (track.susanin) "Explorer mode (Susanin): yes" else "Explorer mode: no",
                        ),
                    )
                } else {
                    png
                }
            }
            exporting = false
            if (bytes == null) {
                message = sheetBuildFailed
            } else {
                val stamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val ext = if (pdf) ".pdf" else ".png"
                saveSheet(
                    "azimut-track-$stamp$ext",
                    bytes,
                    if (pdf) "application/pdf" else "image/png",
                )
            }
        }
    }

    fun withTrack(summary: TrackSummary, action: (Track) -> Unit) {
        scope.launch {
            val track = withContext(Dispatchers.IO) { loadTrack(summary.id) }
            if (track == null) message = loadFailed else action(track)
        }
    }

    fun toggleExpand(summary: TrackSummary) {
        if (expandedId == summary.id) {
            expandedId = null
            expandedData = null
            return
        }
        expandedId = summary.id
        expandedData = null
        scope.launch {
            val track = withContext(Dispatchers.IO) { loadTrack(summary.id) }
            if (track == null) {
                message = loadFailed
                expandedId = null
                return@launch
            }
            val profile = withContext(Dispatchers.Default) {
                ru.azimut.app.core.dem.TrackElevation.analyze(track, demStore)
            }
            if (expandedId == summary.id) expandedData = track to profile
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("Коллекции", "Collections"), onBack)
        ru.azimut.app.ui.components.CollectionsTabs(
                if (routesOnly) ru.azimut.app.Screen.Routes else ru.azimut.app.Screen.Journal,
                onSwitchTab,
            )
        if (trash.isNotEmpty()) {
            TextButton(onClick = { showTrash = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(tr("Недавно удалённые (${trash.size})", "Recently deleted (${trash.size})"))
            }
        }
        if (summaries.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(tr("Поиск по названию", "Search by name")) },
                )
                TextButton(onClick = { sort = ru.azimut.app.core.model.TrackListOps.next(sort) }) {
                    Text(
                        when (sort) {
                            ru.azimut.app.core.model.TrackSort.DATE -> tr("По дате", "By date")
                            ru.azimut.app.core.model.TrackSort.NAME -> tr("По имени", "By name")
                            ru.azimut.app.core.model.TrackSort.DISTANCE -> tr("По длине", "By length")
                        },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                if (routesOnly) {
                    tr(
                        "Маршрутов пока нет. Постройте маршрут на карте и сохраните его.",
                        "No routes yet. Build a route on the map and save it.",
                    )
                } else {
                    tr(
                        "Поездок пока нет. Нажмите «Записать» на карте или сохраните построенный маршрут.",
                        "No trips yet. Record a track or save a built route.",
                    )
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { track ->
                    val expanded = expandedId == track.id
                    Card(Modifier.fillMaxWidth().clickable { toggleExpand(track) }) {
                        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(track.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (track.kind == KIND_ROUTE) {
                                    Text(
                                        tr("План", "Plan"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (track.susanin) {
                                    Text(
                                        " " + tr("Сусанин", "Explorer"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                IconButton(onClick = { toggleExpand(track) }) {
                                    Icon(
                                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = tr("Подробнее", "Details"),
                                    )
                                }
                                var menuOpen by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = tr("Действия", "Actions"))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(tr("На карте", "On map")) },
                                            onClick = { menuOpen = false; withTrack(track) { onShow(it, false) } },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Вести", "Navigate")) },
                                            onClick = { menuOpen = false; withTrack(track) { onShow(it, true) } },
                                        )
                                        if (track.kind != KIND_ROUTE) {
                                            DropdownMenuItem(
                                                text = { Text(tr("Сравнить", "Compare")) },
                                                onClick = {
                                                    menuOpen = false
                                                    withTrack(track) { t ->
                                                        if (summaries.none { it.kind == KIND_ROUTE }) {
                                                            message = noPlansMessage
                                                        } else {
                                                            comparePicker = t
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("GPX") },
                                            onClick = {
                                                menuOpen = false
                                                withTrack(track) { t ->
                                                    // GPX serialization of a long track — off the UI thread.
                                                    scope.launch {
                                                        val content = withContext(Dispatchers.Default) { Gpx.writeTrack(t) }
                                                        saveGpx(gpxFileName(t.name), content)
                                                    }
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Проверка", "Precheck")) },
                                            onClick = {
                                                menuOpen = false
                                                withTrack(track) { t ->
                                                    scope.launch {
                                                        val result = withContext(Dispatchers.Default) {
                                                            val zones = ru.azimut.app.core.protected.ProtectedAreas.allPolygons()
                                                            ru.azimut.app.core.export.TrackPrecheckEngine.check(
                                                                t, demStore, zones, zones.isNotEmpty(),
                                                            )
                                                        }
                                                        precheck = t to result
                                                    }
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("PNG") },
                                            onClick = { menuOpen = false; withTrack(track) { exportSheet(it, pdf = false) } },
                                            enabled = !exporting,
                                        )
                                        DropdownMenuItem(
                                            text = { Text("PDF") },
                                            onClick = { menuOpen = false; withTrack(track) { exportSheet(it, pdf = true) } },
                                            enabled = !exporting,
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Показать/скрыть на карте", "Show/hide on map")) },
                                            onClick = { menuOpen = false; withTrack(track) { onToggleShown(it) } },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    tr("Удалить", "Delete"),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = { menuOpen = false; confirmDelete = track },
                                        )
                                    }
                                }
                            }
                            Text(
                                "${formatDate(track.startedAt)} · ${TransportProfile.byId(track.profile).title(lang)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            val data = if (expanded) expandedData?.takeIf { it.first.id == track.id } else null
                            Text(
                                formatDistance(track.distanceMeters) + " · " +
                                    (track.durationSeconds?.let { formatDuration(it * 1000) } ?: "—") +
                                    " · " + track.pointCount + " " + tr("точ.", "pts"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (data != null) {
                                    val t = data.first
                                    val plannedSec = t.plannedTimeSeconds
                                    val durationMs = if (t.kind == KIND_ROUTE && plannedSec != null) {
                                        (plannedSec * 1000).toLong()
                                    } else {
                                        val last = t.points.lastOrNull()?.timeMillis ?: 0
                                        (last - t.startedAt).coerceAtLeast(0)
                                    }
                                    Stat(tr("Время", "Time"), formatDuration(durationMs))
                                    if (data.second.hasData) {
                                        Stat(tr("Набор", "Ascend"), "↑ ${data.second.ascendMeters.toInt()} м")
                                        Stat(tr("Сброс", "Descend"), "↓ ${data.second.descendMeters.toInt()} м")
                                    }
                                }
                            }
                            if (expanded) {
                                Spacer(Modifier.height(6.dp))
                                if (data == null) {
                                    Text(
                                        tr("Загрузка…", "Loading…"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    ElevationChart(data.second.heights, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            tr("Папка данных: ", "Data folder: ") + PlatformFiles.navDataRoot() + "/tracks",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    precheck?.let { (track, result) ->
        AlertDialog(
            onDismissRequest = { precheck = null },
            title = { Text(tr("Проверка перед выездом", "Pre-ride check")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Трек: ", "Track: ") + track.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("Расстояние: ", "Distance: ") + formatDistance(result.distanceMeters),
                    )
                    val profile = result.profile
                    if (profile != null) {
                        Text(
                            tr(
                                "Высоты: ${profile.minMeters?.toInt()}–${profile.maxMeters?.toInt()} м · " +
                                    "набор ↑${profile.ascendMeters.toInt()} м · сброс ↓${profile.descendMeters.toInt()} м" +
                                    (profile.maxGradePercent?.let { " · уклон до ${it.toInt()}%" } ?: ""),
                                "Heights: ${profile.minMeters?.toInt()}–${profile.maxMeters?.toInt()} m · " +
                                    "ascend ↑${profile.ascendMeters.toInt()} m · descend ↓${profile.descendMeters.toInt()} m" +
                                    (profile.maxGradePercent?.let { " · max grade ${it.toInt()}%" } ?: ""),
                            ),
                        )
                        if (!result.demCoversTrack) {
                            Text(
                                tr(
                                    "⚠ DEM покрывает трек не полностью (${(profile.coverage * 100).toInt()}%).",
                                    "⚠ DEM does not cover the whole track (${(profile.coverage * 100).toInt()}%).",
                                ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            if (result.demAvailable) {
                                tr("Нет DEM в этом районе — высоты неизвестны.", "No DEM for this area — heights unknown.")
                            } else {
                                tr("Нет DEM — высоты неизвестны.", "No DEM — heights unknown.")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (result.zoneHits.isNotEmpty()) {
                        Text(
                            "⚠ " + tr("Зоны: ", "Zones: ") + result.zoneHits.joinToString(", "),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (result.demAvailable) {
                        Text(tr("Пересечений с зонами нет.", "No zone crossings."))
                    } else {
                        Text(
                            tr("Слой ООПТ не загружен — проверка зон невозможна.", "No protected layer loaded — zones not checked."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (result.fords > 0) {
                        Text(tr("Броды по маршруту: ${result.fords}", "Fords on the route: ${result.fords}"))
                    }
                    // Missing DEM for this district: offer the download right here.
                    if (!result.demCoversTrack && track.points.isNotEmpty()) {
                        var fetchingDem by remember { mutableStateOf(false) }
                        val demFetchDone = tr("DEM загружен: ", "DEM loaded: ")
                        val demFetchFail = tr("DEM не скачан.", "DEM not fetched.")
                        TextButton(
                            enabled = !fetchingDem,
                            onClick = {
                                fetchingDem = true
                                val start = track.points.first()
                                scope.launch {
                                    val report = ru.azimut.app.core.dem.DataFetcher.fetch(
                                        listOf(ru.azimut.app.core.dem.DataNeed.DEM),
                                        start.lat, start.lon,
                                        ru.azimut.app.core.dem.DemSources.builtIn.first(),
                                        "",
                                    )
                                    fetchingDem = false
                                    message = if (report.demImported > 0) {
                                        demFetchDone + report.demImported
                                    } else {
                                        demFetchFail
                                    }
                                    // Recompute the precheck with the fresh DEM.
                                    val zones = ru.azimut.app.core.protected.ProtectedAreas.allPolygons()
                                    val updated = ru.azimut.app.core.export.TrackPrecheckEngine.check(
                                        track, demStore, zones, zones.isNotEmpty(),
                                    )
                                    precheck = track to updated
                                }
                            },
                        ) {
                            Text(
                                if (fetchingDem) tr("Загрузка…", "Fetching…")
                                else tr("Скачать DEM по треку", "Fetch DEM along the track"),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { precheck = null }) { Text(tr("Готово", "Done")) }
            },
        )
    }

    comparePicker?.let { recorded ->
        val plans = summaries.filter { it.kind == KIND_ROUTE }
        AlertDialog(
            onDismissRequest = { comparePicker = null },
            title = { Text(tr("Сравнить с маршрутом", "Compare with a route")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(recorded.name, style = MaterialTheme.typography.titleSmall)
                    plans.forEach { plan ->
                        TextButton(onClick = {
                            scope.launch {
                                val planned = withContext(Dispatchers.Default) { loadTrack(plan.id) }
                                if (planned == null || planned.points.size < 2) {
                                    message = loadFailed
                                } else {
                                    compare = Triple(
                                        recorded,
                                        planned,
                                        withContext(Dispatchers.Default) {
                                            compareTracks(recorded, planned, demStore)
                                        },
                                    )
                                }
                                comparePicker = null
                            }
                        }) {
                            Text(plan.name + " · " + formatDate(plan.startedAt))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { comparePicker = null }) { Text(tr("Отмена", "Cancel")) }
            },
        )
    }

    compare?.let { (recorded, planned, r) ->
        val ascPlan = r.plannedElevation?.ascendMeters?.toInt()?.toString() ?: "—"
        val ascAct = r.actualElevation?.ascendMeters?.toInt()?.toString() ?: "—"
        val descPlan = r.plannedElevation?.descendMeters?.toInt()?.toString() ?: "—"
        val descAct = r.actualElevation?.descendMeters?.toInt()?.toString() ?: "—"
        val plannedSec = r.plannedTimeSeconds
        val actualSec = r.actualTimeSeconds
        AlertDialog(
            onDismissRequest = { compare = null },
            title = { Text(tr("Сравнение с маршрутом", "Route comparison")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recorded.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("Расстояние: план ", "Distance: plan ") + formatDistance(r.plannedDistanceMeters) +
                            (r.actualDistanceMeters
                                ?.let { tr(", факт ", ", actual ") + formatDistance(it) }
                                ?: tr(", факт — нет точек", ", actual — no points")),
                    )
                    val plannedT = plannedSec?.let { formatRouteDuration(it.toInt()) }
                    val actualT = actualSec?.let { formatRouteDuration(it.toInt()) }
                    Text(
                        when {
                            plannedT != null && actualT != null -> {
                                val delta = (actualSec - plannedSec).toInt()
                                val sign = if (delta >= 0) "+" else "−"
                                tr("Время: план ", "Time: plan ") + plannedT + tr(", факт ", ", actual ") + actualT +
                                    " (Δ " + sign + formatRouteDuration(abs(delta)) + ")"
                            }
                            plannedT != null -> tr("Время: план ", "Time: plan ") + plannedT +
                                tr(", факт — нет таймстампов", ", actual — no timestamps")
                            actualT != null -> tr("Время: план — нет данных, факт ", "Time: plan unknown, actual ") + actualT
                            else -> tr("Время: нет данных.", "Time: no data.")
                        },
                    )
                    if (r.lateralAvgMeters != null && r.lateralMaxMeters != null) {
                        val offPct = ((r.offRouteShare ?: 0.0) * 100).toInt()
                        Text(
                            tr(
                                "Боковое отклонение: в среднем ${r.lateralAvgMeters.toInt()} м · максимум ${r.lateralMaxMeters.toInt()} м · дальше ${OFF_ROUTE_THRESHOLD_M.toInt()} м: $offPct% точек.",
                                "Lateral deviation: avg ${r.lateralAvgMeters.toInt()} m · max ${r.lateralMaxMeters.toInt()} m · beyond ${OFF_ROUTE_THRESHOLD_M.toInt()} m: $offPct% of points.",
                            ),
                        )
                    }
                    Text(
                        tr(
                            "Высоты: план ↑$ascPlan ↓$descPlan м · факт ↑$ascAct ↓$descAct м",
                            "Heights: plan ↑$ascPlan ↓$descPlan m · actual ↑$ascAct ↓$descAct m",
                        ),
                    )
                    if (r.plannedElevation?.fullyCovered == false || r.actualElevation?.fullyCovered == false) {
                        Text(
                            tr("⚠ DEM покрывает трек не полностью.", "⚠ DEM does not cover the whole track."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (r.plannedElevation == null && r.actualElevation == null) {
                        Text(
                            tr("Высот нет: нет DEM и записанных высот.", "No heights: no DEM and no recorded altitude."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowComparison(recorded, planned) }) {
                    Text(tr("Показать на карте", "Show on map"))
                }
            },
            dismissButton = {
                TextButton(onClick = { compare = null }) { Text(tr("Закрыть", "Close")) }
            },
        )
    }

    if (showTrash) {
        AlertDialog(
            onDismissRequest = { showTrash = false },
            title = { Text(tr("Недавно удалённые", "Recently deleted")) },
            text = {
                if (trash.isEmpty()) {
                    Text(tr("Пусто", "Empty"))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(trash, key = { it.summary.id }) { item ->
                            Column {
                                Text(item.summary.name, style = MaterialTheme.typography.bodyLarge)
                                Row {
                                    TextButton(onClick = { onRestore(item.summary.id) }) {
                                        Text(tr("Восстановить", "Restore"))
                                    }
                                    TextButton(onClick = { confirmPurge = item }) {
                                        Text(tr("Удалить навсегда", "Delete forever"))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTrash = false }) { Text(tr("Закрыть", "Close")) } },
        )
    }

    confirmPurge?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text(tr("Удалить навсегда?", "Delete forever?")) },
            text = {
                Text(
                    "«${item.summary.name}»\n" +
                        "${formatDate(item.summary.startedAt)} · ${formatDistance(item.summary.distanceMeters)}\n" +
                        tr("Восстановить будет нельзя.", "This cannot be undone."),
                )
            },
            confirmButton = {
                TextButton(onClick = { onPurge(item.summary.id); confirmPurge = null }) {
                    Text(tr("Удалить навсегда", "Delete forever"))
                }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text(tr("Отмена", "Cancel")) } },
        )
    }

    confirmDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("Удалить «${track.name}»?", "Delete “${track.name}”?")) },
            text = { Text(tr("Трек попадёт в «Недавно удалённые» на 30 дней.", "The track goes to Recently deleted for 30 days.")) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(track.id)
                    confirmDelete = null
                }) { Text(tr("Удалить", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Отмена", "Cancel")) } },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            text = { Text(text) },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Text block for the exported sheet (Cyrillic is fine: drawn by the platform renderer). */
private fun trackSheetLines(
    track: Track,
    profile: ru.azimut.app.core.dem.ElevationProfile,
    precheck: ru.azimut.app.core.export.TrackPrecheck,
    en: Boolean,
): List<String> = buildList {
    val title = if (track.kind == KIND_ROUTE) (if (en) "Route: " else "Маршрут: ") else (if (en) "Track: " else "Трек: ")
    add(title + track.name)
    add((if (en) "Distance: " else "Расстояние: ") + formatDistance(track.distanceMeters) +
        (if (en) " · points: " else " · точек: ") + track.points.size)
    if (profile.hasData) {
        add(
            (if (en) "Heights: " else "Высоты: ") +
                "${profile.minMeters?.toInt()}–${profile.maxMeters?.toInt()} m " +
                (if (en) "ascend " else "набор ") + "↑${profile.ascendMeters.toInt()} " +
                (if (en) "descend " else "сброс ") + "↓${profile.descendMeters.toInt()}" +
                (profile.maxGradePercent?.let { (if (en) " · max grade " else " · уклон до ") + "${it.toInt()}%" } ?: ""),
        )
        if (!precheck.demCoversTrack) {
            add(
                (if (en) "DEM coverage partial: " else "DEM покрывает не полностью: ") +
                    "${(profile.coverage * 100).toInt()}%",
            )
        }
    } else {
        add(if (en) "No DEM data for heights" else "Нет DEM — высоты неизвестны")
    }
    if (precheck.zoneHits.isNotEmpty()) {
        add((if (en) "Zones: " else "Зоны: ") + precheck.zoneHits.joinToString("; "))
    }
    if (precheck.fords > 0) add((if (en) "Fords: " else "Броды: ") + precheck.fords)
    if (track.susanin) add(if (en) "Explorer mode (Susanin)" else "Режим Сусанина")
}

private fun formatDate(millis: Long): String {
    if (millis <= 0) return "—"
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dt.date.toString()
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    return "$day $hh:$mm"
}

/** "5 ч 47 м" / "12 мин"; recorded from the first to the last point, plans use router time. */
private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "—"
    val totalMin = millis / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h ч $m м" else "$m мин"
}
