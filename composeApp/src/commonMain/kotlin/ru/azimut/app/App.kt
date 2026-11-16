package ru.azimut.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.azimut.app.core.gpx.GpxImportOutcome
import ru.azimut.app.core.import.TrackImportDetect
import ru.azimut.app.core.gpx.GpxImporter
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.map.UserMaps
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackSummary
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.formatDistance
import ru.azimut.app.core.platform.AppCommands
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.platform.applySystemBars
import ru.azimut.app.core.storage.AutoScan
import ru.azimut.app.core.storage.PointsStore
import ru.azimut.app.core.storage.SettingsStore
import ru.azimut.app.core.storage.TrackStore
import ru.azimut.app.ui.LocalLang
import ru.azimut.app.ui.components.PlatformBackHandler
import ru.azimut.app.ui.components.deletePhoto
import ru.azimut.app.ui.components.deleteVoice
import ru.azimut.app.ui.screens.AboutScreen
import ru.azimut.app.ui.screens.HelpScreen
import ru.azimut.app.ui.screens.JournalScreen
import ru.azimut.app.ui.screens.MapScreen
import ru.azimut.app.ui.screens.PointsScreen
import ru.azimut.app.ui.screens.SettingsScreen
import ru.azimut.app.ui.theme.AzimutTheme
import ru.azimut.app.ui.tr

enum class Screen {
    Map,
    Points,
    Journal,
    Settings,
    Help,
    About,
}

@Composable
fun App() {
    val settingsStore = remember { SettingsStore() }
    val pointsStore = remember { PointsStore() }
    val trackStore = remember { TrackStore() }
    val scope = rememberCoroutineScope()
    val pointsSaveMutex = remember { Mutex() }

    var settings by remember { mutableStateOf(AppSettings()) }
    var points by remember { mutableStateOf<List<Waypoint>>(emptyList()) }
    var summaries by remember { mutableStateOf<List<TrackSummary>>(emptyList()) }
    var trash by remember { mutableStateOf<List<ru.azimut.app.core.storage.TrashedTrack>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.Map) }
    var previousScreen by remember { mutableStateOf(Screen.Map) }
    val showMapRequest by AppCommands.showMap.collectAsState()
    LaunchedEffect(showMapRequest) {
        if (showMapRequest > 0) {
            previousScreen = Screen.Map
            screen = Screen.Map
        }
    }
    var ready by remember { mutableStateOf(false) }
    // Bumped whenever NavData content may have changed, so DEM caches and the
    // protected-areas layer reload without an app restart.
    var dataVersion by remember { mutableIntStateOf(0) }
    var pendingRecovery by remember { mutableStateOf<Track?>(null) }
    var shownTrack by remember { mutableStateOf<Track?>(null) }
    var backgroundTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var following by remember { mutableStateOf(false) }
    var startupScanMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        PlatformFiles.ensureDirs()
        UserMaps.refresh()
        ru.azimut.app.core.map.UserXyz.refresh()
        // points.json can hold thousands of imported POIs; keep decoding off the UI thread.
        settings = withContext(Dispatchers.Default) { settingsStore.load() }
        points = withContext(Dispatchers.Default) { pointsStore.load() }
        summaries = withContext(Dispatchers.Default) { trackStore.list() }
        trash = withContext(Dispatchers.Default) { runCatching { trackStore.purgeExpired() }; trackStore.listTrash() }
        // The autosave belongs to the live recording if the service is still running.
        pendingRecovery = if (TrackRecorder.state.value.recording) {
            null
        } else {
            withContext(Dispatchers.Default) { trackStore.loadAutosave() }
        }
        ready = true
    }

    if (!ready) {
        AzimutTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    LaunchedEffect(settings.gpsSource, settings.gpsBaud) {
        PlatformLocation.configure(settings.gpsSource, settings.gpsBaud)
    }

    LaunchedEffect(settings.recordingIntervalSec, settings.batterySaver, settings.backgroundRecording) {
        TrackRecorder.configure(settings.recordingIntervalSec, settings.batterySaver)
        PlatformLocation.applySettings(
            settings.recordingIntervalSec,
            settings.batterySaver,
            settings.backgroundRecording,
        )
        // Re-arm GPS updates when the cadence changes mid-recording.
        if (TrackRecorder.state.value.recording) PlatformLocation.start()
    }

    LaunchedEffect(settings.hideSystemButtons) {
        applySystemBars(settings.hideSystemButtons)
    }

    // The journal and the Explorer counter follow every saved track (map button or tray).
    LaunchedEffect(Unit) {
        TrackRecorder.saved.collect { track ->
            summaries = trackStore.list()
            if (track.susanin) {
                settings = settings.copy(susaninCount = settings.susaninCount + 1)
                settingsStore.save(settings)
            }
        }
    }

    // Debounced async save: settings changes arrive in bursts (camera-pan interval,
    // slider drags) and the blocking settings.json write must not run on the UI thread.
    val appScope = rememberCoroutineScope()
    val settingsSaveJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val updateSettings: (AppSettings) -> Unit = { next ->
        settings = next
        settingsSaveJob.value?.cancel()
        settingsSaveJob.value = appScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            runCatching { settingsStore.save(next) }
        }
    }

    fun navigate(next: Screen) {
        if (next != screen) {
            previousScreen = screen
            screen = next
        }
    }

    // Switching between the collection tabs keeps Back pointing to where the user came from.
    fun switchTab(next: Screen) {
        screen = next
    }

    fun goBack() {
        val target = previousScreen
        previousScreen = Screen.Map
        screen = target
    }

    fun toggleTrackShown(track: Track) {
        if (shownTrack?.id == track.id) {
            shownTrack = null
            following = false
        } else if (backgroundTracks.any { it.id == track.id }) {
            backgroundTracks = backgroundTracks.filterNot { it.id == track.id }
        } else {
            backgroundTracks = backgroundTracks + track
        }
    }

    fun renameTrack(track: Track, name: String) {
        runCatching { trackStore.save(track.copy(name = name)) }
        summaries = trackStore.list()
        if (shownTrack?.id == track.id) shownTrack = track.copy(name = name)
        backgroundTracks = backgroundTracks.map { if (it.id == track.id) track.copy(name = name) else it }
    }

    fun deleteTrack(id: String) {
        runCatching { trackStore.delete(id) }
        if (shownTrack?.id == id) {
            shownTrack = null
            following = false
        }
        backgroundTracks = backgroundTracks.filterNot { it.id == id }
        summaries = trackStore.list()
        trash = trackStore.listTrash()
    }
    // Saving rewrites the whole points.json/points.gpx, so it runs off the UI thread.
    // The mutex keeps writes in order when several edits happen quickly.
    val savePoints: suspend (List<Waypoint>) -> Boolean = { next ->
        pointsSaveMutex.withLock {
            withContext(Dispatchers.Default) { runCatching { pointsStore.save(next) }.isSuccess }
        }
    }
    val addPoint: (Waypoint) -> Unit = { point ->
        val next = points + point
        points = next
        scope.launch { savePoints(next) }
    }
    val updatePoint: (Waypoint) -> Unit = { point ->
        val next = points.map { if (it.id == point.id) point else it }
        points = next
        scope.launch { savePoints(next) }
    }
    val deletePoint: (Waypoint) -> Unit = { point ->
        val next = points.filter { it.id != point.id }
        points = next
        scope.launch { savePoints(next) }
        point.photos.forEach(::deletePhoto)
        point.voiceNotes.forEach(::deleteVoice)
    }
    val importGpx: suspend (String, String) -> GpxImportOutcome = { fileName, xml ->
        val parsed = withContext(Dispatchers.Default) {
            GpxImporter.parse(fileName, xml, settings.transportProfile)
        }
        if (parsed.outcome.failed || parsed.outcome.empty) {
            parsed.outcome
        } else {
            var savedTracks = 0
            withContext(Dispatchers.Default) {
                parsed.tracks.forEach { track ->
                    if (runCatching { trackStore.save(track) }.isSuccess) savedTracks++
                }
            }
            var savedWaypoints = 0
            if (parsed.waypoints.isNotEmpty()) {
                val next = withContext(Dispatchers.Default) {
                    val importedIds = parsed.waypoints.map { it.id }.toSet()
                    points.filterNot { it.id in importedIds } + parsed.waypoints
                }
                if (savePoints(next)) {
                    points = next
                    savedWaypoints = parsed.waypoints.size
                }
            }
            summaries = withContext(Dispatchers.Default) { trackStore.list() }
            parsed.outcome.copy(
                tracks = savedTracks,
                waypoints = savedWaypoints,
                empty = savedTracks == 0 && savedWaypoints == 0,
            )
        }
    }

    fun reloadData() {
        dataVersion++
        scope.launch {
            settings = withContext(Dispatchers.Default) { settingsStore.load() }
            points = withContext(Dispatchers.Default) { pointsStore.load() }
            summaries = withContext(Dispatchers.Default) { trackStore.list() }
            UserMaps.refresh()
            ru.azimut.app.core.map.UserXyz.refresh()
            shownTrack = null
            backgroundTracks = emptyList()
            following = false
            pendingRecovery = null
        }
    }

    // Files dropped into NavData/import/ are picked up once, without blocking the first frame.
    LaunchedEffect(Unit) {
        if (!settings.autoScan) return@LaunchedEffect
        val pending = withContext(Dispatchers.Default) { AutoScan.pending() }
        if (pending.isEmpty()) return@LaunchedEffect
        var tracks = 0
        var waypoints = 0
        var maps = 0
        var zones = 0
        val done = mutableListOf<AutoScan.Pending>()
        val failed = mutableListOf<String>()
        for (file in pending) {
            val lower = file.name.lowercase()
            when {
                lower.endsWith(".gpx") || lower.endsWith(".kml") || lower.endsWith(".tcx") ||
                    lower.endsWith(".geojson") || lower.endsWith(".json") -> {
                    val content = withContext(Dispatchers.Default) { AutoScan.readText(file.name) }
                    if (content == null) {
                        failed += file.name
                        continue
                    }
                    if (TrackImportDetect.isOopt(content)) {
                        val result = withContext(Dispatchers.Default) {
                            ru.azimut.app.core.import.TrackImport.importOopt(file.name, content)
                        }
                        if (result == ru.azimut.app.core.protected.ProtectedImportResult.OK) {
                            zones++
                            done += file
                        } else {
                            failed += file.name
                        }
                    } else {
                        val parsed = withContext(Dispatchers.Default) {
                            ru.azimut.app.core.import.TrackImport.parse(
                                file.name,
                                content,
                                settings.transportProfile,
                            )
                        }
                        if (parsed.outcome.failed || parsed.outcome.empty) {
                            failed += file.name
                        } else {
                            val saved = withContext(Dispatchers.Default) {
                                parsed.tracks.mapNotNull { track ->
                                    runCatching { trackStore.save(track) }.getOrNull()
                                }.size
                            }
                            tracks += saved
                            if (parsed.waypoints.isNotEmpty()) {
                                val next = withContext(Dispatchers.Default) {
                                    val ids = parsed.waypoints.map { it.id }.toSet()
                                    points.filterNot { it.id in ids } + parsed.waypoints
                                }
                                if (savePoints(next)) {
                                    points = next
                                    waypoints += parsed.waypoints.size
                                }
                            }
                            done += file
                        }
                    }
                }
                lower.endsWith(".kmz") -> {
                    val bytes = withContext(Dispatchers.Default) { AutoScan.readBytes(file.name) }
                    val kml = bytes?.let { ru.azimut.app.core.import.ZipKml.extract(it) }
                    val parsed = kml?.let {
                        ru.azimut.app.core.import.TrackImport.parse(
                            file.name.substringBeforeLast('.') + ".kml",
                            it,
                            settings.transportProfile,
                        )
                    }
                    if (parsed == null || parsed.outcome.empty || parsed.outcome.failed) {
                        failed += file.name
                    } else {
                        val saved = withContext(Dispatchers.Default) {
                            parsed.tracks.mapNotNull { track ->
                                runCatching { trackStore.save(track) }.getOrNull()
                            }.size
                        }
                        tracks += saved
                        if (parsed.waypoints.isNotEmpty()) {
                            val next = points + parsed.waypoints
                            if (savePoints(next)) {
                                points = next
                                waypoints += parsed.waypoints.size
                            }
                        }
                        done += file
                    }
                }
                lower.endsWith(".mbtiles") -> {
                    val imported = withContext(Dispatchers.Default) { AutoScan.importMap(file.name) }
                    if (imported) {
                        maps++
                        done += file
                    } else {
                        failed += file.name
                    }
                }
                lower.endsWith(".sqlitedb") || lower.endsWith(".gpkg") -> {
                    val converted = withContext(Dispatchers.Default) { AutoScan.importTileDb(file.name) }
                    if (converted) {
                        maps++
                        done += file
                    } else {
                        failed += file.name
                    }
                }
                lower.endsWith(".hgt") || lower.endsWith(".tif") || lower.endsWith(".tiff") -> {
                    val result = withContext(Dispatchers.Default) {
                        ru.azimut.app.core.dem.DemImport.moveImported(file.name)
                    }
                    if (result == ru.azimut.app.core.dem.DemImportResult.OK) {
                        done += file
                    } else if (result != ru.azimut.app.core.dem.DemImportResult.DUPLICATE) {
                        failed += file.name
                    } else {
                        done += file
                    }
                }
                else -> failed += file.name
            }
        }
        withContext(Dispatchers.Default) { AutoScan.mark(done) }
        if (maps > 0) UserMaps.refresh()
        if (done.isNotEmpty()) dataVersion++
        val en = settings.language == "en"
        val summary = if (done.isNotEmpty()) {
            if (en) {
                "Tracks: $tracks, waypoints: $waypoints, maps: $maps, DEM/zones: $zones."
            } else {
                "Треков: $tracks, точек: $waypoints, карт: $maps, DEM/зон: $zones."
            }
        } else {
            ""
        }
        val problems = if (failed.isNotEmpty()) {
            (if (en) "Could not import: " else "Не удалось импортировать: ") +
                failed.joinToString(", ")
        } else {
            ""
        }
        val text = listOf(summary, problems)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (text.isNotBlank()) startupScanMessage = text
    }

    CompositionLocalProvider(LocalLang provides settings.language) {
        AzimutTheme(darkTheme = settings.theme != "light") {
            PlatformBackHandler(enabled = screen != Screen.Map) { goBack() }
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                // Navigation lives in the map's right rail menu; non-map screens keep clear of the system bar.
                Column(
                    Modifier.fillMaxSize().then(
                        if (screen == Screen.Map) Modifier else Modifier.navigationBarsPadding()
                    )
                ) {
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                Screen.Map -> MapScreen(
                    settings = settings,
                    onSettingsChange = updateSettings,
                    points = points,
                    onAddPoint = addPoint,
                    onDeletePoint = deletePoint,
                    onOpenPoints = { navigate(Screen.Points) },
                    onOpenJournal = { navigate(Screen.Journal) },
                    onOpenSettings = { navigate(Screen.Settings) },
                    onOpenHelp = { navigate(Screen.Help) },
                    onOpenAbout = { navigate(Screen.About) },
                    shownTrack = shownTrack,
                    backgroundTracks = backgroundTracks,
                    following = following,
                    onShowTrack = { track, follow ->
                        shownTrack = track
                        backgroundTracks = backgroundTracks.filterNot { track != null && it.id == track.id }
                        following = follow && track != null
                    },
                    onSavePlan = { plan ->
                        // GPX serialization + index rewrite — off the UI thread.
                        withContext(Dispatchers.IO) {
                            val result = runCatching { trackStore.save(plan) }
                            summaries = trackStore.list()
                            result
                        }
                    },
                    onToggleTrackShown = ::toggleTrackShown,
                    onRenameTrack = ::renameTrack,
                    onDeleteTrack = ::deleteTrack,
                    dataVersion = dataVersion,
                )

                Screen.Points -> PointsScreen(
                    points = points,
                    photoQuality = settings.photoQuality,
                    voiceEnabled = settings.voiceNotes,
                    onAddPoint = addPoint,
                    onUpdatePoint = updatePoint,
                    onDeletePoint = deletePoint,
                    onAddPoints = { fresh ->
                        val next = points + fresh
                        points = next
                        scope.launch { savePoints(next) }
                    },
                    onSwitchTab = ::switchTab,
                    onBack = { goBack() },
                )

                Screen.Journal -> JournalScreen(
                    summaries = summaries,
                    loadTrack = { id -> trackStore.load(id) },
                    onShow = { track, follow ->
                        shownTrack = track
                        backgroundTracks = backgroundTracks.filterNot { it.id == track.id }
                        following = follow
                        screen = Screen.Map
                    },
                    shownTrackIds = buildSet {
                        shownTrack?.let { add(it.id) }
                        backgroundTracks.forEach { add(it.id) }
                    },
                    onToggleShown = { track ->
                        if (shownTrack?.id == track.id) {
                            shownTrack = null
                            following = false
                        } else if (backgroundTracks.any { it.id == track.id }) {
                            backgroundTracks = backgroundTracks.filterNot { it.id == track.id }
                        } else {
                            backgroundTracks = backgroundTracks + track
                        }
                    },
                    onShowComparison = { recorded, planned ->
                        shownTrack = recorded
                        following = false
                        // The plan is always a background line: drop both from backgrounds, keep the plan.
                        backgroundTracks = backgroundTracks
                            .filterNot { it.id == recorded.id || it.id == planned.id } + planned
                        screen = Screen.Map
                    },
                    onDelete = { id -> deleteTrack(id) },
                    trash = trash,
                    onRestore = { id ->
                        runCatching { trackStore.restore(id) }
                        summaries = trackStore.list()
                        trash = trackStore.listTrash()
                    },
                    onPurge = { id ->
                        runCatching { trackStore.purge(id) }
                        trash = trackStore.listTrash()
                    },
                    onSwitchTab = ::switchTab,
                    onBack = { goBack() },
                )

                Screen.Settings -> SettingsScreen(
                    settings = settings,
                    onSettingsChange = updateSettings,
                    onImportGpx = importGpx,
                    onOpenAbout = { navigate(Screen.About) },
                    onOpenHelp = { navigate(Screen.Help) },
                    onDataRestored = { reloadData() },
                    onBack = { goBack() },
                )

                Screen.Help -> HelpScreen(onBack = { goBack() })

                Screen.About -> AboutScreen(onBack = { goBack() })
                    }
                    }
                }
            }
            }

            startupScanMessage?.let { text ->
                AlertDialog(
                    onDismissRequest = { startupScanMessage = null },
                    title = { Text(tr("Автоимпорт", "Auto-import")) },
                    text = { Text(text) },
                    confirmButton = {
                        TextButton(onClick = { startupScanMessage = null }) { Text("OK") }
                    },
                )
            }

            pendingRecovery?.let { track ->
                AlertDialog(
                    onDismissRequest = { pendingRecovery = null },
                    title = { Text(tr("Незавершённая запись", "Unfinished recording")) },
                    text = {
                        Text(
                            tr(
                                "Найден автосейв «${track.name}»: ${track.points.size} точек, " +
                                    "${formatDistance(track.distanceMeters)}.",
                                "Found autosave \"${track.name}\": ${track.points.size} points, " +
                                    "${formatDistance(track.distanceMeters)}.",
                            )
                        )
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (TrackRecorder.restore(track)) {
                                    PlatformLocation.start()
                                    pendingRecovery = null
                                    screen = Screen.Map
                                }
                            }) {
                                Text(tr("Продолжить", "Resume"))
                            }
                            TextButton(onClick = {
                                // GPX serialization + index rewrite — off the UI thread.
                                appScope.launch(Dispatchers.IO) {
                                    val ok = runCatching { trackStore.save(track) }.isSuccess
                                    if (ok) {
                                        trackStore.clearAutosave()
                                        summaries = trackStore.list()
                                        pendingRecovery = null
                                    }
                                }
                            }) {
                                Text(tr("Сохранить", "Save"))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            trackStore.clearAutosave()
                            pendingRecovery = null
                        }) {
                            Text(tr("Удалить", "Delete"))
                        }
                    },
                )
            }
        }
    }
}
