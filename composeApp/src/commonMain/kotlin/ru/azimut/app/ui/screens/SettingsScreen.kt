package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.azimut.app.AppInfo
import ru.azimut.app.core.astro.Daylight
import ru.azimut.app.core.astro.SunState
import ru.azimut.app.core.astro.SunTimes
import ru.azimut.app.core.gpx.GpxImportOutcome
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.map.MapImportResult
import ru.azimut.app.core.map.MapStyles
import ru.azimut.app.core.map.UserMaps
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.model.TransportProfile
import ru.azimut.app.core.model.formatCoord
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.platform.SIMULATOR_SOURCE
import ru.azimut.app.core.platform.PlatformInfo
import ru.azimut.app.core.routing.RouteData
import ru.azimut.app.core.storage.Backup
import ru.azimut.app.core.storage.BackupEntry
import ru.azimut.app.core.storage.pruneBackups
import ru.azimut.app.core.weather.WeatherSnapshot
import ru.azimut.app.core.weather.WeatherStore
import ru.azimut.app.core.weather.weatherText
import ru.azimut.app.ui.components.AccordionSection
import ru.azimut.app.ui.components.BackHeader
import ru.azimut.app.ui.components.ChoiceRow
import ru.azimut.app.ui.components.DropdownRow
import ru.azimut.app.ui.components.InfoRow
import ru.azimut.app.ui.components.SwitchRow
import ru.azimut.app.ui.components.rememberGpxFilePicker
import ru.azimut.app.ui.components.rememberMapFilePicker
import ru.azimut.app.ui.components.rememberFolderPicker
import ru.azimut.app.ui.components.rememberTrackFilePicker
import ru.azimut.app.ui.components.rememberRouteDataPicker
import kotlin.math.roundToInt
import ru.azimut.app.ui.tr

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onImportGpx: suspend (fileName: String, xml: String) -> GpxImportOutcome,
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit,
    onDataRestored: () -> Unit,
    onBack: () -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    val langEn = settings.language == "en"
    // Hoisted message strings: tr() is composable and cannot run inside onClick/launch lambdas.
    val msgMapConverted = tr("Карта конвертирована: ", "Map converted: ")
    val msgTileDbFailed = tr(
        "Не удалось конвертировать базу тайлов (нужен растровый .sqlitedb или .gpkg).",
        "Could not convert the tile database (a raster .sqlitedb or .gpkg is required).",
    )
    val msgDemOk = tr("DEM импортирован.", "DEM imported.")
    val msgDemDuplicate = tr("Такой DEM уже есть.", "This DEM is already imported.")
    val msgDemNoSpace = tr("Недостаточно места.", "Not enough free space.")
    val msgDemBad = tr(
        "Файл не является поддерживаемым DEM (.hgt/.tif).",
        "The file is not a supported DEM (.hgt/.tif).",
    )
    val msgDownloadCancelled = tr("Загрузка отменена.", "Download cancelled.")
    val msgNoNetwork = tr("Нет сети.", "No network.")
    val msgTilesDone = tr("Загружено тайлов: ", "Tiles downloaded: ")
    val msgErrors = tr("Ошибки: ", "Failed: ")
    val msgDemRemoved = tr("Удалено файлов DEM: ", "DEM files removed: ")
    val msgZoneRemoved = tr("Слой удалён: ", "Layer removed: ")
    val msgOoptOk = tr("Слой ООПТ импортирован.", "Protected layer imported.")
    val msgOoptFailed = tr("Не удалось разобрать GeoJSON с полигонами.", "Could not parse the polygons GeoJSON.")
    val msgXyzAdded = tr("Источник добавлен. Он появится в «Слои».", "Source added. It appears in Layers.")
    val msgXyzInvalid = tr("Нужны название и URL с {z}/{x}/{y}.", "Provide a name and a URL with {z}/{x}/{y}.")
    val msgPasswordSaved = tr("Пароль сохранён.", "Password saved.")
    val msgNoSyncTarget = tr("Не выбран источник синхронизации.", "No sync target selected.")
    val msgSyncDone = tr("Синхронизация: ↑", "Sync: up ")
    val msgSyncConflicts = tr(" конфликтов: ", " conflicts: ")
    val msgSyncStopped = tr("Синхронизация остановлена.", "Sync stopped.")
    val conflictsShowLabel = tr("Показать", "Show")
    val conflictsTitle = tr("Конфликт-копии", "Conflict copies")
    val conflictsHint = tr(
        "Это копии проигравшей стороны при одновременной правке файла на двух устройствах. " +
            "Актуальная версия уже в исходном файле — копии можно безопасно удалить.",
        "These are the losing-side copies from a file edited on two devices at once. " +
            "The current version is already in the original file — the copies are safe to delete.",
    )
    val conflictsDeleteAll = tr("Удалить все", "Delete all")
    val conflictsClose = tr("Закрыть", "Close")
    val conflictsDeletedMsg = tr("Конфликт-копии удалены.", "Conflict copies deleted.")
    val msgSyncPhaseDownload = tr("Скачано: ", "Downloaded: ")
    val msgSyncPhaseUpload = tr("Отправлено: ", "Uploaded: ")
    val msgSyncPhaseConflicts = tr("Конфликты: ", "Conflicts: ")
    val removeLabel = tr("Удалить", "Remove")
    val savePasswordLabel = tr("Сохранить пароль", "Save password")
    val msgLanStopped = tr("Сервер остановлен.", "Server stopped.")
    val msgZonesDone = tr("Загружено зон из OSM: ", "Zones fetched from OSM: ")
    val msgZonesNone = tr("В этом районе OSM зон не найдено.", "No OSM zones in this area.")
    val msgZonesNoNet = tr("Нет сети.", "No network.")
    val msgZonesBusy = tr("Сервер Overpass перегружен, попробуйте позже.", "The Overpass server is busy, try later.")
    val msgZonesFail = tr("Не удалось загрузить зоны.", "Could not fetch the zones.")
    val msgLanFailed = tr("Не удалось запустить сервер.", "Could not start the server.")
    val msgTokenSaved = tr("Токен сохранён для синхронизации.", "Token saved for sync.")
    var lanTokenForSync by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var restoreChoices by remember { mutableStateOf<List<BackupEntry>?>(null) }
    // File/directory reads must not run in composition — load async, null meanwhile.
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var weatherBusy by remember { mutableStateOf(false) }
    var routeSegments by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        weather = withContext(Dispatchers.IO) { WeatherStore.load() }
        routeSegments = withContext(Dispatchers.IO) { RouteData.segmentCount() }
    }
    val recorder by TrackRecorder.state.collectAsState()
    val scope = rememberCoroutineScope()

    val rallySavedMessage = tr("Rally point сохранён.", "Rally point saved.")
    val noFixMessage = tr("Нет GPS-фикса.", "No GPS fix.")
    val baseSavedMessage = tr("База сохранена.", "Base saved.")
    val backupCreatedPrefix = tr("Резервная копия: ", "Backup: ")
    val backupFailedMessage = tr("Не удалось создать резервную копию.", "Could not create a backup.")
    val restoreBlockedMessage = tr("Сначала остановите запись.", "Stop recording first.")
    val restoreFailedMessage = tr("Не удалось восстановить данные.", "Could not restore the data.")
    val restoreEmptyMessage = tr("Резервных копий пока нет.", "No backups yet.")
    val restoreDoneMessage = tr("Данные восстановлены.", "Data restored.")
    val noWeatherCoordsMessage = tr(
        "Нужен GPS-фикс или координаты базы.",
        "A GPS fix or base coordinates are required.",
    )
    val weatherFailedMessage = tr("Не удалось загрузить погоду.", "Could not load the weather.")
    val importButtonLabel = tr("Импорт GPX", "Import GPX")
    val importingLabel = tr("Импорт…", "Importing…")
    val importFailedMessage = tr("Не удалось разобрать GPX-файл.", "Could not parse the GPX file.")
    val importEmptyMessage = tr(
        "В файле нет треков или точек.",
        "The file contains no tracks or waypoints.",
    )
    val importReadErrorMessage = tr("Не удалось прочитать файл.", "Could not read the file.")
    val pickGpx = rememberGpxFilePicker(
        onPicked = { fileName, xml ->
            if (!importing) {
                importing = true
                scope.launch {
                    try {
                        val outcome = onImportGpx(fileName, xml)
                        message = when {
                            outcome.failed -> importFailedMessage
                            outcome.empty -> importEmptyMessage
                            settings.language == "en" ->
                                "Imported tracks: ${outcome.tracks}, waypoints: ${outcome.waypoints}."
                            else ->
                                "Импортировано треков: ${outcome.tracks}, точек: ${outcome.waypoints}."
                        }
                    } finally {
                        importing = false
                    }
                }
            }
        },
        onError = { message = importReadErrorMessage },
    )
    val routeImportFailedMessage = tr(
        "Не удалось импортировать сегменты BRouter.",
        "Could not import BRouter segments.",
    )
    val pickRouteData = rememberRouteDataPicker(
        onImported = { count ->
            routeSegments = RouteData.segmentCount()
            message = if (settings.language == "en") {
                "Imported BRouter segments: $count."
            } else {
                "Импортировано сегментов BRouter: $count."
            }
        },
        onError = { message = routeImportFailedMessage },
    )
    val pickSyncFolder = rememberFolderPicker { location ->
        onSettingsChange(settings.copy(syncFolderPath = location))
    }
    val pickExportFolder = rememberFolderPicker { location ->
        onSettingsChange(settings.copy(autoExportFolder = location))
    }

    val mapImportFailedMessage = tr("Не удалось скопировать файл карты.", "Could not copy the map file.")
    val mapVectorMessage = tr(
        "векторные .mbtiles пока не поддерживаются",
        "vector .mbtiles are not supported yet",
    )
    val mapBrokenMessage = tr("не является файлом MBTiles", "is not an MBTiles file")
    val mapImportedLabel = tr("Импортировано карт", "Imported maps")
    val pickMap = rememberMapFilePicker(
        onImported = { names ->
            val results = names.map { it to UserMaps.checkImported(it) }
            UserMaps.refresh()
            val ok = results.filter { it.second == MapImportResult.OK }.map { it.first }
            val problems = results.filter { it.second != MapImportResult.OK }.map { (name, result) ->
                "$name: " + if (result == MapImportResult.VECTOR) mapVectorMessage else mapBrokenMessage
            }
            message = (listOf("$mapImportedLabel: ${ok.size}.") + problems).joinToString("\n")
            ok.firstOrNull()?.let {
                onSettingsChange(
                    settings.copy(mapLayer = "base", mapStyle = UserMaps.PREFIX + it, overlayEnabled = false)
                )
            }
        },
        onError = { message = mapImportFailedMessage },
    )

    fun setExpanded(id: String) {
        val set = settings.expandedCategories.toMutableSet()
        if (!set.add(id)) set.remove(id)
        onSettingsChange(settings.copy(expandedCategories = set.toList()))
    }

    fun expanded(id: String) = settings.expandedCategories.contains(id)

    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    val daylightCoords = recorder.lastFix?.let { it.lat to it.lon }
        ?: settings.basePoint?.let { it.lat to it.lon }
    val daylight = daylightCoords?.let { (lat, lon) ->
        runCatching { SunTimes.forDate(today, lat, lon, timeZone) }.getOrNull()
    }
    // Terrain-corrected daylight when the user asked for it; falls back to nominal.
    val terrainDaylight = if (settings.weatherRelief) {
        daylightCoords?.let { (lat, lon) ->
            runCatching {
                ru.azimut.app.core.astro.TerrainSunTimes.forDate(today, lat, lon, timeZone, ru.azimut.app.core.dem.DemStore())
            }.getOrNull()
        }
    } else {
        null
    }

    // Компактный вид: только часто нужные секции; «Подробно» показывает все.
    val compact = !settings.detailedSettings
    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("Настройки", "Settings"), onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                onSettingsChange(settings.copy(detailedSettings = !settings.detailedSettings))
            }) {
                Text(
                    if (compact) tr("Подробные настройки", "Detailed settings")
                    else tr("Компактный вид", "Compact view")
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AccordionSection(
                tr("Карты и слои", "Maps & layers"),
                expanded("maps"),
                { setExpanded("maps") },
                icon = Icons.Filled.Layers,
                iconTint = Color(0xFF2E7D32),
                summary = if (settings.mapLayer == "satellite") tr("Спутник", "Satellite") else tr("Базовая", "Base"),
            ) {
                ChoiceRow(
                    tr("Слой карты", "Map layer"),
                    listOf("base" to tr("Базовая", "Base"), "satellite" to tr("Спутник", "Satellite")),
                    settings.mapLayer,
                ) { onSettingsChange(settings.copy(mapLayer = it)) }
                DropdownRow(
                    tr("Стиль базовой карты", "Base map style"),
                    MapStyles.all.map { it.id to it.title(settings.language) } +
                        ru.azimut.app.core.map.UserXyz.asStyles().map { it.id to it.title(settings.language) },
                    settings.mapStyle,
                ) { onSettingsChange(settings.copy(mapLayer = "base", mapStyle = it)) }
                Text(tr("Прозрачность слоя", "Layer opacity"), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.mapOpacity,
                    onValueChange = { onSettingsChange(settings.copy(mapOpacity = it)) },
                    valueRange = 0.2f..1f,
                )
                Text(
                    tr("Прозрачность спутника поверх карты", "Satellite overlay opacity"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = settings.overlayOpacity,
                    onValueChange = { onSettingsChange(settings.copy(overlayOpacity = it)) },
                    valueRange = 0f..1f,
                )
                SwitchRow(
                    tr("Только кэш (без сети)", "Cache only (no network)"),
                    settings.cacheOnly,
                    { onSettingsChange(settings.copy(cacheOnly = it)) },
                    tr(
                        "Карта не ходит в сеть за тайлами, показывает скачанное",
                        "The map never fetches tiles online and shows what is already cached",
                    ),
                )
                SwitchRow(
                    tr("Автосканирование файлов", "Auto-scan files"),
                    settings.autoScan,
                    { onSettingsChange(settings.copy(autoScan = it)) },
                    tr("Искать GPX и карты в доверенных папках", "Scan trusted folders for GPX and maps"),
                )
                SwitchRow(
                    tr("Искать по всему телефону", "Scan the whole phone"),
                    settings.phoneScan,
                    { onSettingsChange(settings.copy(phoneScan = it)) },
                    tr(
                        "GPX, карты и DEM в любых папках телефона",
                        "GPX, maps and DEM in any folder of the device",
                    ),
                )
                if (settings.phoneScan && !PlatformFiles.hasAllFilesAccess()) {
                    TextButton(onClick = { PlatformFiles.requestAllFilesAccess() }) {
                        Text(tr("Выдать доступ ко всем файлам", "Grant all-files access"))
                    }
                }
                SwitchRow(
                    tr("Отрисовка рельефа (хиллшейд)", "Hillshade overlay"),
                    settings.hillshade,
                    { onSettingsChange(settings.copy(hillshade = it)) },
                    tr(
                        "Затенение по импортированному DEM, от zoom 9",
                        "Shading from the imported DEM, from zoom 9",
                    ),
                )
                InfoRow(tr("DEM-подложка", "DEM basemap"), "Copernicus GLO-30 / GLO-90")
                InfoRow(
                    tr("Рельеф", "Terrain"),
                    tr("OpenTopoMap и Esri Topo — с горизонталями", "OpenTopoMap and Esri Topo include contours"),
                )
                InfoRow(
                    tr("Офлайн OSM", "OSM offline"),
                    tr("кэш просмотренных тайлов; массовая загрузка недоступна", "viewed tiles cache; bulk download unavailable"),
                )
                InfoRow(
                    tr("Офлайн-карты", "Offline maps"),
                    UserMaps.stylesFlow.collectAsState().value.size.let { n -> tr("$n шт. в NavData/maps", "$n in NavData/maps") },
                )
                OutlinedButton(onClick = pickMap) { Text(tr("Импорт карты (.mbtiles)", "Import map (.mbtiles)")) }
                val pickTileDb = rememberTrackFilePicker(
                    onPicked = { fileName, _, bytes ->
                        if (bytes == null) {
                            message = importReadErrorMessage
                        } else {
                            importing = true
                            scope.launch {
                                val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        PlatformFiles.writeBytes("import/$safeName", bytes)
                                        ru.azimut.app.core.storage.AutoScan.importTileDb(safeName)
                                    }.getOrDefault(false)
                                }
                                PlatformFiles.delete("import/$safeName")
                                importing = false
                                if (ok) {
                                    UserMaps.refresh()
                                    message = msgMapConverted + safeName.substringBeforeLast('.') + ".mbtiles"
                                } else {
                                    message = msgTileDbFailed
                                }
                            }
                        }
                    },
                    onError = { message = importReadErrorMessage },
                )
                OutlinedButton(onClick = pickTileDb, enabled = !importing) {
                    Text(tr("Конвертировать .sqlitedb/.gpkg", "Convert .sqlitedb/.gpkg"))
                }
                Spacer(Modifier.height(8.dp))
            }

                SwitchRow(
                    tr("POI вместе с областью карт", "POI with map area download"),
                    settings.poiAutoLoad,
                    { onSettingsChange(settings.copy(poiAutoLoad = it)) },
                    tr(
                        "При скачивании области карт загружать точки интереса (нужна сеть)",
                        "When downloading a map area, fetch POI for it too (needs network)",
                    ),
                )
            AccordionSection(
                tr("Запись и треки", "Recording & tracks"),
                expanded("tracking"),
                { setExpanded("tracking") },
                icon = Icons.Filled.Mic,
                iconTint = Color(0xFFE65100),
                summary = "${settings.recordingIntervalSec} с",
            ) {
                var gpsPorts by remember { mutableStateOf<List<String>?>(null) }
                LaunchedEffect(settings.expandedCategories) {
                    gpsPorts = withContext(Dispatchers.IO) { PlatformLocation.externalPorts() }
                }
                val ports = gpsPorts
                if (ports != null) {
                    val sources = listOf(SIMULATOR_SOURCE to tr("Симулятор (тест без GPS)", "Simulator (no GPS)")) +
                        (ports + settings.gpsSource).filter { it != SIMULATOR_SOURCE }.distinct()
                            .map { it to tr("$it (NMEA)", "$it (NMEA)") }
                    DropdownRow(
                        tr("GPS-приёмник", "GPS receiver"),
                        sources,
                        settings.gpsSource,
                    ) { onSettingsChange(settings.copy(gpsSource = it)) }
                    if (settings.gpsSource != SIMULATOR_SOURCE) {
                        ChoiceRow(
                            tr("Скорость порта", "Baud rate"),
                            listOf("4800", "9600", "38400", "115200").map { it to it },
                            settings.gpsBaud.toString(),
                        ) { onSettingsChange(settings.copy(gpsBaud = it.toIntOrNull() ?: 9600)) }
                    }
                }
                DropdownRow(
                    tr("Профиль транспорта", "Transport profile"),
                    TransportProfile.entries.map { it.id to it.title(settings.language) },
                    settings.transportProfile,
                ) { onSettingsChange(settings.copy(transportProfile = it)) }
                ChoiceRow(
                    tr("Интервал записи", "Recording interval"),
                    listOf("1" to "1 с", "3" to "3 с", "10" to "10 с"),
                    settings.recordingIntervalSec.toString(),
                ) { onSettingsChange(settings.copy(recordingIntervalSec = it.toIntOrNull() ?: 1)) }
                SwitchRow(
                    tr("Фоновая запись", "Background recording"),
                    settings.backgroundRecording,
                    { onSettingsChange(settings.copy(backgroundRecording = it)) },
                    tr("Продолжать запись при заблокированном экране", "Keep recording with screen off"),
                )
                SwitchRow(
                    tr("Экономия заряда", "Battery saver"),
                    settings.batterySaver,
                    { onSettingsChange(settings.copy(batterySaver = it)) },
                    tr("Реже GPS, тусклее экран", "Less frequent GPS, dimmer screen"),
                )
                SwitchRow(
                    tr("Не писать на остановках", "Skip stops"),
                    settings.skipStops,
                    { onSettingsChange(settings.copy(skipStops = it)) },
                    tr(
                        "Стоячие точки не добавляются в трек",
                        "Standing-still fixes are not added to the track",
                    ),
                )
                SwitchRow(
                    tr("Автоэкспорт трека", "Auto-export track"),
                    settings.autoExport,
                    { onSettingsChange(settings.copy(autoExport = it)) },
                    tr(
                        "GPX-копия записанного трека при сохранении",
                        "GPX copy of each recorded track once saved",
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Папка экспорта: ", "Export folder: ") +
                            (settings.autoExportFolder.ifBlank {
                                tr("NavData/export (папка приложения)", "NavData/export (app folder)")
                            }),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.autoExportFolder.isNotBlank()) {
                        TextButton(onClick = {
                            onSettingsChange(settings.copy(autoExportFolder = ""))
                        }) { Text(tr("Сброс", "Reset")) }
                    }
                    TextButton(onClick = pickExportFolder) { Text(tr("Выбрать", "Choose")) }
                }
                SwitchRow(
                    tr("Режим Сусанина", "Explorer Mode"),
                    settings.susaninEnabled,
                    {
                        onSettingsChange(settings.copy(susaninEnabled = it))
                        TrackRecorder.setSusanin(it)
                    },
                    tr("Экспериментальные маршруты с предохранителем", "Experimental routes with a safety net"),
                )
                InfoRow(tr("Счётчик «был Сусанином»", "Explorer counter"), settings.susaninCount.toString())
                InfoRow(tr("Автосохранение", "Autosave"), tr("каждые 5 точек", "every 5 points"))
            }

            if (!compact) AccordionSection(
                tr("Ориентация карты", "Map orientation"),
                expanded("orientation"),
                { setExpanded("orientation") },
                icon = Icons.Filled.Explore,
                iconTint = Color(0xFF1565C0),
                summary = if (settings.orientationMode == "course") tr("По ходу", "Course up") else tr("Север вверху", "North up"),
            ) {
                ChoiceRow(
                    tr("Режим", "Mode"),
                    listOf(
                        "north" to tr("Север", "North"),
                        "course" to tr("Курс", "Course"),
                    ),
                    if (settings.orientationMode == "course") "course" else "north",
                ) { onSettingsChange(settings.copy(orientationMode = it)) }
            }

            AccordionSection(
                tr("Топливо", "Fuel"),
                expanded("fuel"),
                { setExpanded("fuel") },
                icon = Icons.Filled.LocalGasStation,
                iconTint = Color(0xFF6A1B9A),
                summary = if (settings.fuelTankLiters > 0) "${"%.0f".format(settings.fuelTankLiters)} л" else tr("выкл", "off"),
            ) {
                FuelField(
                    tr("Бак, л", "Tank, l"),
                    settings.fuelTankLiters,
                ) { onSettingsChange(settings.copy(fuelTankLiters = it)) }
                FuelField(
                    tr("Расход, л/100 км", "Consumption, l/100 km"),
                    settings.fuelConsumptionL100,
                ) { onSettingsChange(settings.copy(fuelConsumptionL100 = it)) }
                FuelField(
                    tr("Резерв, л", "Reserve, l"),
                    settings.fuelReserveLiters,
                ) { onSettingsChange(settings.copy(fuelReserveLiters = it)) }
                Text(
                    tr(
                        "На полном баке: трамплины на маршруте — резерв и сухой бак. Пусто или 0 — расчёт выключен.",
                        "Full-tank basis: ramps on the route — reserve and dry tank. Empty or 0 — calculation off.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AccordionSection(
                tr("Точки и фото", "Waypoints & media"),
                expanded("points"),
                { setExpanded("points") },
                icon = Icons.Filled.PhotoCamera,
                iconTint = Color(0xFFEF6C00),
                summary = settings.photoQuality,
            ) {
                SwitchRow(
                    tr("Голосовые заметки", "Voice notes"),
                    settings.voiceNotes,
                    { onSettingsChange(settings.copy(voiceNotes = it)) },
                )
                SwitchRow(
                    tr("Предупреждать о точках", "Waypoint proximity alerts"),
                    settings.proximityAlerts,
                    { onSettingsChange(settings.copy(proximityAlerts = it)) },
                    tr("Сигнал при приближении к точке (200 м)", "Alert when approaching a waypoint (200 m)"),
                )
                ChoiceRow(
                    tr("Качество фото", "Photo quality"),
                    listOf("high" to tr("Высокое", "High"), "medium" to "Среднее", "low" to "Низкое"),
                    settings.photoQuality,
                ) { onSettingsChange(settings.copy(photoQuality = it)) }
                InfoRow(
                    tr("База", "Base"),
                    settings.basePoint?.let { formatCoord(it.lat, it.lon) } ?: tr("не задана", "not set"),
                )
                InfoRow(
                    tr("Точка сбора", "Rally point"),
                    settings.rallyPoint?.let { formatCoord(it.lat, it.lon) } ?: tr("не задана", "not set"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val fix = recorder.lastFix
                        if (fix != null) {
                            onSettingsChange(settings.copy(rallyLat = fix.lat, rallyLon = fix.lon))
                            message = rallySavedMessage
                        } else {
                            message = noFixMessage
                        }
                    }) { Text(tr("Rally здесь", "Rally here")) }
                    OutlinedButton(onClick = {
                        val fix = recorder.lastFix
                        if (fix != null) {
                            onSettingsChange(settings.copy(baseLat = fix.lat, baseLon = fix.lon))
                            message = baseSavedMessage
                        } else {
                            message = noFixMessage
                        }
                    }) { Text(tr("База здесь", "Base here")) }
                }
                Spacer(Modifier.height(8.dp))
            }

            AccordionSection(
                tr("Погода и световой день", "Weather & daylight"),
                expanded("weather"),
                { setExpanded("weather") },
                icon = Icons.Filled.WbSunny,
                iconTint = Color(0xFFF9A825),
                summary = if (settings.weatherRelief) tr("с рельефом", "terrain") else tr("без рельефа", "flat"),
            ) {
                val daylightValue = when {
                    daylightCoords == null -> tr("нужен GPS-фикс или база", "needs a GPS fix or base")
                    daylight == null -> tr("ошибка расчёта", "calculation error")
                    else -> formatDaylight(terrainDaylight?.effective ?: daylight, settings.language)
                }
                InfoRow(tr("Световой день", "Daylight"), daylightValue)
                if (terrainDaylight != null) {
                    InfoRow(
                        tr("Рельеф", "Terrain"),
                        if (terrainDaylight.terrainAdjusted) {
                            tr("с учётом рельефа (DEM)", "terrain-corrected (DEM)")
                        } else {
                            tr("без учёта рельефа — нет DEM", "no terrain data — DEM missing")
                        },
                    )
                }
                InfoRow(
                    tr("Точка расчёта", "Calculation point"),
                    daylightCoords?.let { formatCoord(it.first, it.second) } ?: tr("не задана", "not set"),
                )
                InfoRow(
                    tr("Погода", "Weather"),
                    weather?.let { weatherText(it, settings.language) } ?: tr("нет данных", "no data"),
                )
                weather?.hourly?.takeIf { it.isNotEmpty() }?.let { hourly ->
                    Text(
                        tr("Почасовой прогноз (24 ч)", "Hourly forecast (24 h)"),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val rowStyle = MaterialTheme.typography.bodySmall
                    hourly.take(24).forEach { entry ->
                        val hh = entry.time.substringAfter('T').take(5)
                        val temp = entry.temperatureC?.let { "${it.roundToInt()}°C" } ?: "—"
                        val precip = entry.precipitationPercent?.let { "$it%" } ?: "—"
                        val wind = entry.windMs?.let { "${it.roundToInt()} м/с" } ?: "—"
                        Text("$hh · $temp · дождь $precip · $wind", style = rowStyle, maxLines = 1)
                    }
                }
                weather?.let { snapshot ->
                    InfoRow(tr("Обновлено", "Updated"), formatStamp(snapshot.fetchedAt, timeZone))
                    InfoRow(
                        tr("Точка прогноза", "Forecast point"),
                        formatCoord(snapshot.lat, snapshot.lon),
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (weatherBusy) return@OutlinedButton
                        val point = daylightCoords
                        if (point == null) {
                            message = noWeatherCoordsMessage
                            return@OutlinedButton
                        }
                        weatherBusy = true
                        scope.launch {
                            val snapshot = WeatherStore.refresh(point.first, point.second)
                            weatherBusy = false
                            if (snapshot != null) {
                                weather = snapshot
                            } else {
                                message = weatherFailedMessage
                            }
                        }
                    },
                    enabled = !weatherBusy,
                ) {
                    Text(
                        if (weatherBusy) tr("Загрузка…", "Loading…")
                        else tr("Обновить погоду", "Refresh weather"),
                    )
                }
                SwitchRow(
                    tr("Учитывать рельеф", "Account for terrain"),
                    settings.weatherRelief,
                    { onSettingsChange(settings.copy(weatherRelief = it)) },
                    tr(
                        "Восход/закат корректируются по DEM (NavData/dem)",
                        "Sunrise/sunset are corrected from the DEM (NavData/dem)",
                    ),
                )
                SwitchRow(
                    tr("Линия заката на карте", "Sunset line on map"),
                    settings.showSunsetAzimuth,
                    { onSettingsChange(settings.copy(showSunsetAzimuth = it)) },
                    tr(
                        "Жёлтый пунктир от позиции в сторону заката",
                        "Yellow dashed line from your position toward sunset",
                    ),
                )
            }

            AccordionSection(
                tr("Данные, хранилище и синхронизация", "Data, storage & sync"),
                expanded("data"),
                { setExpanded("data") },
                icon = Icons.Filled.Storage,
                iconTint = Color(0xFF546E7A),
                summary = if (settings.autoScan) tr("автоскан", "auto-scan") else tr("вручную", "manual"),
            ) {
                InfoRow(tr("Общее хранилище", "Shared storage"), PlatformFiles.navDataRoot())
                InfoRow(tr("Структура", "Structure"), "maps/ · tracks/ · dem/ · routing/")
                InfoRow(
                    tr("Сегменты BRouter", "BRouter segments"),
                    tr("$routeSegments файлов .rd5", "$routeSegments .rd5 files"),
                )
                DropdownRow(
                    tr("Хранить копий", "Keep backups"),
                    listOf("5" to "5", "10" to "10", "20" to "20", "50" to "50", "100" to "100"),
                    settings.backupKeep.toString(),
                ) { value ->
                    onSettingsChange(settings.copy(backupKeep = value.toIntOrNull() ?: 20))
                }
                OutlinedButton(
                    onClick = {
                        if (backupBusy) return@OutlinedButton
                        backupBusy = true
                        scope.launch {
                            val name = withContext(Dispatchers.IO) {
                                val created = Backup.create()
                                // Retention: keep only the newest backupKeep snapshots.
                                if (created != null) pruneBackups(settings.backupKeep)
                                created
                            }
                            backupBusy = false
                            message = if (name != null) {
                                backupCreatedPrefix + "backups/$name"
                            } else {
                                backupFailedMessage
                            }
                        }
                    },
                    enabled = !backupBusy,
                ) {
                    Text(
                        if (backupBusy) tr("Копирование…", "Copying…")
                        else tr("Создать резервную копию", "Create backup"),
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (backupBusy) return@OutlinedButton
                        if (recorder.recording) {
                            message = restoreBlockedMessage
                            return@OutlinedButton
                        }
                        scope.launch {
                            val entries = withContext(Dispatchers.IO) { Backup.list() }
                            if (entries.isEmpty()) {
                                message = restoreEmptyMessage
                            } else {
                                restoreChoices = entries
                            }
                        }
                    },
                    enabled = !backupBusy,
                ) {
                    Text(tr("Восстановить из копии", "Restore from backup"))
                }
                Spacer(Modifier.height(8.dp))
                InfoRow(tr("Синхронизация", "Sync"), "USB · Wi-Fi · облако")
                InfoRow(tr("Экспорт GPX", "GPX export"), "tracks/*.gpx")
                OutlinedButton(onClick = pickGpx, enabled = !importing) {
                    Text(if (importing) importingLabel else importButtonLabel)
                }
                OutlinedButton(onClick = pickRouteData) {
                    Text(tr("Импорт BRouter (.rd5)", "Import BRouter (.rd5)"))
                }
                Spacer(Modifier.height(8.dp))
            }

            AccordionSection(
                tr("Рельеф (DEM)", "Terrain (DEM)"),
                expanded("dem"),
                { setExpanded("dem") },
                icon = Icons.Filled.Terrain,
                iconTint = Color(0xFF33691E),
                summary = tr("NavData/dem", "NavData/dem"),
            ) {
                var demFiles by remember { mutableStateOf<List<ru.azimut.app.core.dem.DemImport.DemEntry>>(emptyList()) }
                LaunchedEffect(settings.expandedCategories) {
                    demFiles = withContext(Dispatchers.IO) { ru.azimut.app.core.dem.DemImport.list() }
                }
                InfoRow(
                    tr("Файлов DEM", "DEM files"),
                    "${demFiles.size} · ${PlatformFiles.navDataRoot()}/dem",
                )
                Text(tr("Интенсивность рельефа", "Hillshade intensity"), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.hillshadeOpacity,
                    onValueChange = { onSettingsChange(settings.copy(hillshadeOpacity = it)) },
                    valueRange = 0.1f..0.8f,
                )
                var demBusy by remember { mutableStateOf(false) }
                var demProgress by remember { mutableStateOf<String?>(null) }
                val pickDem = rememberTrackFilePicker(
                    onPicked = { fileName, _, bytes ->
                        if (bytes == null) {
                            message = importReadErrorMessage
                        } else {
                            demBusy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    ru.azimut.app.core.dem.DemImport.importFromBytes(fileName, bytes)
                                }
                                demBusy = false
                                message = when (result) {
                                    ru.azimut.app.core.dem.DemImportResult.OK -> {
                                        onDataRestored()
                                        msgDemOk
                                    }
                                    ru.azimut.app.core.dem.DemImportResult.DUPLICATE -> msgDemDuplicate
                                    ru.azimut.app.core.dem.DemImportResult.NO_SPACE -> msgDemNoSpace
                                    else -> msgDemBad
                                }
                            }
                        }
                    },
                    onError = { message = importReadErrorMessage },
                )
                OutlinedButton(onClick = pickDem, enabled = !demBusy) {
                    Text(tr("Импорт DEM (.hgt/.tif)", "Import DEM (.hgt/.tif)"))
                }
                Spacer(Modifier.height(8.dp))
                DropdownRow(
                    tr("Источник загрузки", "Download source"),
                    ru.azimut.app.core.dem.DemSources.builtIn.map { it.id to it.title(settings.language) } +
                        listOf("custom" to tr("Свой URL", "Custom URL")),
                    settings.demSource,
                ) { onSettingsChange(settings.copy(demSource = it)) }
                if (settings.demSource == "custom") {
                    OutlinedTextField(
                        value = settings.demUrlTemplate,
                        onValueChange = { onSettingsChange(settings.copy(demUrlTemplate = it)) },
                        label = { Text(tr("Шаблон URL ({tile})", "URL template ({tile})")) },
                        singleLine = true,
                    )
                }
                demProgress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(
                    enabled = !demBusy && daylightCoords != null,
                    onClick = {
                        val point = daylightCoords ?: return@OutlinedButton
                        val def = if (settings.demSource == "custom") {
                            ru.azimut.app.core.dem.DemSourceDef(
                                "custom", "URL", "URL", settings.demUrlTemplate,
                            )
                        } else {
                            ru.azimut.app.core.dem.DemSources.byId(settings.demSource)
                                ?: ru.azimut.app.core.dem.DemSources.builtIn.first()
                        }
                        demBusy = true
                        scope.launch {
                            val plans = withContext(Dispatchers.Default) {
                                ru.azimut.app.core.dem.DemImport.planTiles(
                                    def, settings.demUrlTemplate,
                                    point.first - 1, point.first + 1,
                                    point.second - 1, point.second + 1,
                                )
                            }
                            val demProgressPrefix = if (langEn) "DEM download: " else "Загрузка DEM: "
                            val outcome = ru.azimut.app.core.dem.DemImport.downloadTiles(
                                plans,
                                onProgress = { done, total ->
                                    demProgress = demProgressPrefix + "$done/$total"
                                },
                            )
                            demBusy = false
                            demProgress = null
                            message = when {
                                outcome.cancelled -> msgDownloadCancelled
                                outcome.offline && outcome.imported == 0 -> msgNoNetwork
                                else -> {
                                    val errPart = if (outcome.errors.isEmpty()) "" else
                                        "\n" + msgErrors + outcome.errors.joinToString("; ")
                                    val failedPart = if (outcome.failed.isEmpty() || outcome.errors.isNotEmpty()) "" else
                                        "\n" + msgErrors + outcome.failed.joinToString(", ")
                                    msgTilesDone + "${outcome.imported}$errPart$failedPart"
                                }
                            }
                        }
                    },
                ) {
                    Text(if (demBusy) tr("Загрузка…", "Downloading…") else tr("Скачать DEM вокруг меня (3×3 тайла)", "Download DEM around me (3×3 tiles)"))
                }
                if (demFiles.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        demBusy = true
                        scope.launch {
                            val removed = withContext(Dispatchers.IO) {
                                demFiles.count { ru.azimut.app.core.dem.DemImport.remove(it.name) }
                            }
                            demBusy = false
                            message = msgDemRemoved + removed
                        }
                    }, enabled = !demBusy) {
                        Text(tr("Удалить все DEM", "Remove all DEM"))
                    }
                }
            }

            AccordionSection(
                tr("ООПТ и запреты", "Protected areas"),
                expanded("protected"),
                { setExpanded("protected") },
                icon = Icons.Filled.GppGood,
                iconTint = Color(0xFFC62828),
                summary = if (settings.protectedWarnings) tr("предупреждения вкл", "warnings on") else tr("выкл", "off"),
            ) {
                var zoneFiles by remember { mutableStateOf<List<ru.azimut.app.core.protected.ProtectedAreaFile>>(emptyList()) }
                LaunchedEffect(settings.expandedCategories) {
                    zoneFiles = withContext(Dispatchers.IO) {
                        ru.azimut.app.core.protected.ProtectedAreas.list()
                    }
                }
                if (zoneFiles.isEmpty()) {
                    InfoRow(tr("Загружено зон", "Loaded zones"), tr("нет", "none"))
                } else {
                    zoneFiles.forEach { file ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.fileName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${file.polygons.size} полиг. · " +
                                        tr("источник: ", "source: ") + file.source.ifBlank { "?" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                ru.azimut.app.core.protected.ProtectedAreas.remove(file.fileName)
                                onDataRestored()
                                message = msgZoneRemoved + file.fileName
                            }) { Text(removeLabel) }
                        }
                    }
                }
                val pickOopt = rememberTrackFilePicker(
                    onPicked = { fileName, content, _ ->
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ru.azimut.app.core.import.TrackImport.importOopt(fileName, content)
                            }
                            message = when (result) {
                                ru.azimut.app.core.protected.ProtectedImportResult.OK -> {
                                    onDataRestored()
                                    msgOoptOk
                                }
                                else -> msgOoptFailed
                            }
                        }
                    },
                    onError = { message = importReadErrorMessage },
                )
                OutlinedButton(onClick = pickOopt) {
                    Text(tr("Импорт GeoJSON (Polygon)", "Import GeoJSON (Polygon)"))
                }
                var overpassBusy by remember { mutableStateOf(false) }
                OutlinedButton(
                    enabled = !overpassBusy && daylightCoords != null,
                    onClick = {
                        val point = daylightCoords ?: return@OutlinedButton
                        overpassBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ru.azimut.app.core.protected.OverpassZones.fetchAround(
                                    point.first, point.second, 0.5,
                                )
                            }
                            overpassBusy = false
                            onDataRestored()
                            message = when (result.outcome) {
                                ru.azimut.app.core.protected.OverpassZones.Outcome.OK -> msgZonesDone + result.zones
                                ru.azimut.app.core.protected.OverpassZones.Outcome.EMPTY -> msgZonesNone
                                ru.azimut.app.core.protected.OverpassZones.Outcome.NO_NET -> msgZonesNoNet
                                ru.azimut.app.core.protected.OverpassZones.Outcome.SERVER_BUSY -> msgZonesBusy
                                else -> msgZonesFail
                            }
                        }
                    },
                ) {
                    Text(
                        if (overpassBusy) tr("Загрузка…", "Fetching…")
                        else tr("Скачать зоны вокруг меня (OSM)", "Fetch zones around me (OSM)"),
                    )
                }
                Text(
                    tr(
                        "Зоны берутся из OpenStreetMap (© OSM contributors, ODbL): " +
                            "полность определяется данными OSM, крупные зоны-отношения упрощаются до контуров.",
                        "Zones come from OpenStreetMap (© OSM contributors, ODbL): completeness equals " +
                            "OSM data; large relation zones are simplified to outlines.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tr(
                        "Данные о зонах могут быть неполными: слой отражает только загруженные файлы, а не официальный статус земли.",
                        "Zone data may be incomplete: the layer reflects the loaded files only, not the official land status.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    tr("Слой ООПТ на карте", "Protected layer on the map"),
                    settings.showProtected,
                    { onSettingsChange(settings.copy(showProtected = it)) },
                )
                SwitchRow(
                    tr("Предупреждать о въезде", "Warn on entering"),
                    settings.protectedWarnings,
                    { onSettingsChange(settings.copy(protectedWarnings = it)) },
                    tr("Один раз на зону за поездку", "Once per zone per trip"),
                )
            }

            AccordionSection(
                tr("XYZ-карты по ссылке", "XYZ map sources"),
                expanded("xyz"),
                { setExpanded("xyz") },
                icon = Icons.Filled.Public,
                iconTint = Color(0xFF00838F),
            ) {
                Text(
                    tr(
                        "XYZ — это онлайн-карта, заданная адресом-шаблоном вида " +
                            "https://сервер/{z}/{x}/{y}.png. Добавьте название и адрес — карта " +
                            "появится в меню «Слои» как базовая; просмотренные тайлы кэшируются " +
                            "для офлайна. Подходит для любых серверов с таким шаблоном.",
                        "XYZ means an online map defined by a template URL such as " +
                            "https://server/{z}/{x}/{y}.png. Add a name and the address — the map " +
                            "appears in Layers as a base map; viewed tiles are cached for offline use.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ru.azimut.app.core.map.UserXyz.sourcesFlow.collectAsState().value.forEach { src ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(src.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${src.minZoom}–${src.maxZoom} · ${src.attribution.ifBlank { "—" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        TextButton(onClick = {
                            ru.azimut.app.core.map.UserXyz.remove(src.id)
                            onSettingsChange(
                                if (settings.mapStyle == ru.azimut.app.core.map.UserXyz.PREFIX + src.id) {
                                    settings.copy(mapLayer = "base", mapStyle = ru.azimut.app.core.map.MapStyles.DEFAULT_ID)
                                } else settings
                            )
                        }) { Text(tr("Удалить", "Remove")) }
                    }
                }
                var xyzName by remember { mutableStateOf("") }
                var xyzUrl by remember { mutableStateOf("") }
                var xyzMaxZoom by remember { mutableStateOf("18") }
                OutlinedTextField(
                    value = xyzName,
                    onValueChange = { xyzName = it },
                    label = { Text(tr("Название", "Name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = xyzUrl,
                    onValueChange = { xyzUrl = it },
                    label = { Text(tr("URL-шаблон {z}/{x}/{y}", "URL template {z}/{x}/{y}")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = xyzMaxZoom,
                    onValueChange = { xyzMaxZoom = it },
                    label = { Text(tr("Максимальный zoom", "Max zoom")) },
                    singleLine = true,
                )
                OutlinedButton(onClick = {
                    val url = xyzUrl.trim()
                    val name = xyzName.trim()
                    val maxZoom = xyzMaxZoom.toIntOrNull() ?: 18
                    if (url.contains("{z}") && url.contains("{x}") && url.contains("{y}") && name.isNotBlank()) {
                        ru.azimut.app.core.map.UserXyz.save(
                            ru.azimut.app.core.map.XyzSource(
                                id = "u${System.currentTimeMillis()}",
                                name = name,
                                urlTemplate = url,
                                maxZoom = maxZoom,
                                addedAt = System.currentTimeMillis(),
                            ),
                        )
                        xyzName = ""; xyzUrl = ""; xyzMaxZoom = "18"
                        message = msgXyzAdded
                    } else {
                        message = msgXyzInvalid
                    }
                }) { Text(tr("Добавить источник", "Add source")) }
                Text(
                    tr(
                        "Лицензия источника — ваша ответственность: проверьте условия использования тайлов.",
                        "Source licensing is your responsibility: check the tile usage terms.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AccordionSection(
                tr("Синхронизация NavData", "NavData sync"),
                expanded("sync"),
                { setExpanded("sync") },
                icon = Icons.Filled.Sync,
                iconTint = Color(0xFF0277BD),
            ) {
                Text(
                    tr(
                        "Перед первой синхронизацией создаётся zip-бэкап. Файлы не удаляются молча: конфликтные копии получают суффикс.",
                        "A zip backup is created before the first sync. Files are never silently deleted: conflicts get a copy suffix.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = settings.syncWebdavUrl,
                    onValueChange = { onSettingsChange(settings.copy(syncWebdavUrl = it)) },
                    label = { Text(tr("WebDAV URL", "WebDAV URL")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = settings.syncWebdavUser,
                    onValueChange = { onSettingsChange(settings.copy(syncWebdavUser = it)) },
                    label = { Text(tr("Логин", "User name")) },
                    singleLine = true,
                )
                var webdavPassword by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = webdavPassword,
                    onValueChange = { webdavPassword = it },
                    label = { Text(tr("Пароль (в защищённом хранилище)", "Password (in the secure store)")) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                TextButton(onClick = {
                    ru.azimut.app.core.sync.SecretStore.saveSecret("webdav", webdavPassword)
                    webdavPassword = ""
                    message = msgPasswordSaved
                }) { Text(savePasswordLabel) }
                ChoiceRow(
                    tr("Тип синхронизации", "Sync kind"),
                    listOf(
                        "folder" to tr("Папка / USB", "Folder / USB"),
                        "webdav" to "WebDAV",
                        "lan" to tr("LAN (сервер на компьютере)", "LAN (server on the desktop)"),
                    ),
                    settings.syncKind,
                ) { onSettingsChange(settings.copy(syncKind = it)) }
                if (settings.syncKind == "lan") {
                    if (PlatformInfo.isDesktop) {
                        var lanInfo by remember { mutableStateOf<ru.azimut.app.core.sync.LanServerInfo?>(null) }
                        if (lanInfo != null) {
                            val info = lanInfo
                            InfoRow(tr("Адрес для телефона", "Address for the phone"), info?.addressLine() ?: "—")
                            InfoRow(tr("Токен", "Token"), info?.token ?: "—")
                            OutlinedButton(onClick = {
                                ru.azimut.app.core.sync.LanSyncService.stop()
                                lanInfo = null
                                message = msgLanStopped
                            }) { Text(tr("Остановить сервер", "Stop server")) }
                        } else {
                            OutlinedButton(onClick = {
                                val info = ru.azimut.app.core.sync.LanSyncService.start()
                                lanInfo = info
                                if (info == null) message = msgLanFailed
                            }) { Text(tr("Запустить сервер", "Start server")) }
                            Text(
                                tr(
                                    "Телефон и компьютер должны быть в одной сети. Телефон: тип LAN, адрес и токен выше.",
                                    "The phone and the computer must share a network. Phone: LAN kind, the address and token above.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = settings.syncFolderPath,
                            onValueChange = { onSettingsChange(settings.copy(syncFolderPath = it)) },
                            label = { Text(tr("Адрес сервера (http://IP:порт)", "Server address (http://IP:port)")) },
                            singleLine = true,
                        )
                        var lanToken by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = lanToken,
                            onValueChange = { lanToken = it },
                            label = { Text(tr("Токен с экрана компьютера", "Token shown on the computer")) },
                            singleLine = true,
                        )
                        TextButton(onClick = {
                            lanTokenForSync = lanToken.trim()
                            message = msgTokenSaved
                        }) { Text(tr("Запомнить токен", "Remember token")) }
                    }
                }
                if (settings.syncKind != "lan") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Папка: ", "Folder: ") +
                                (settings.syncFolderPath.ifBlank { tr("не выбрана", "not chosen") }),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = pickSyncFolder) { Text(tr("Выбрать", "Choose")) }
                    }
                }
                var syncBusy by remember { mutableStateOf(false) }
                var syncProgress by remember { mutableStateOf<String?>(null) }
                syncProgress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                // Conflict copies left by two-sided edits: visible, listable, removable.
                var conflictFiles by remember { mutableStateOf<List<String>>(emptyList()) }
                var conflictsOpen by remember { mutableStateOf(false) }
                fun reloadConflicts() {
                    scope.launch {
                        conflictFiles = withContext(Dispatchers.IO) {
                            PlatformFiles.listAllFiles("").filter { it.contains("conflict-") }
                        }
                    }
                }
                LaunchedEffect(Unit) { reloadConflicts() }
                if (conflictFiles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Конфликт-копии синка: ", "Sync conflict copies: ") + conflictFiles.size,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { conflictsOpen = true }) { Text(conflictsShowLabel) }
                    }
                }
                OutlinedButton(
                    enabled = !syncBusy && when (settings.syncKind) {
                        "webdav" -> settings.syncWebdavUrl.isNotBlank()
                        "lan" -> settings.syncFolderPath.isNotBlank()
                        else -> settings.syncFolderPath.isNotBlank()
                    },
                    onClick = {
                        syncBusy = true
                        scope.launch {
                            val name = withContext(Dispatchers.IO) { Backup.create() }
                            if (name == null) {
                                syncBusy = false
                                message = backupFailedMessage
                                return@launch
                            }
                            val tokenForLan = lanTokenForSync.ifBlank {
                                ru.azimut.app.core.sync.SecretStore.loadSecret("lan").orEmpty()
                            }
                            if (lanTokenForSync.isNotBlank()) {
                                ru.azimut.app.core.sync.SecretStore.saveSecret("lan", lanTokenForSync)
                            }
                            val storage: ru.azimut.app.core.sync.RemoteStorage? = when (settings.syncKind) {
                                "webdav" -> if (settings.syncWebdavUrl.isNotBlank()) {
                                    val password = ru.azimut.app.core.sync.SecretStore.loadSecret("webdav").orEmpty()
                                    ru.azimut.app.core.sync.WebDavStorage(
                                        settings.syncWebdavUrl,
                                        settings.syncWebdavUser,
                                        password,
                                    )
                                } else {
                                    null
                                }
                                "lan" -> if (settings.syncFolderPath.isNotBlank() && tokenForLan.isNotBlank()) {
                                    ru.azimut.app.core.sync.LanHttpStorage(settings.syncFolderPath, tokenForLan)
                                } else {
                                    null
                                }
                                else -> if (settings.syncFolderPath.isNotBlank()) {
                                    ru.azimut.app.core.sync.LocalFolderStorage(settings.syncFolderPath)
                                } else {
                                    null
                                }
                            }
                            if (storage == null) {
                                syncBusy = false
                                message = msgNoSyncTarget
                                return@launch
                            }
                            val result = ru.azimut.app.core.sync.SyncEngine.run(storage, isCancelled = { false }) { done, total, phase ->
                                syncProgress = when (phase) {
                                    "download" -> msgSyncPhaseDownload + "$done/$total"
                                    "upload" -> msgSyncPhaseUpload + "$done/$total"
                                    else -> msgSyncPhaseConflicts + "$done/$total"
                                }
                            }
                            syncBusy = false
                            syncProgress = null
                            message = when (result) {
                                is ru.azimut.app.core.sync.SyncEngine.Result.Done ->
                                    msgSyncDone + result.uploaded +
                                        " ↓" + result.downloaded +
                                        msgSyncConflicts + result.conflictCopies
                                is ru.azimut.app.core.sync.SyncEngine.Result.Cancelled -> msgSyncStopped
                                is ru.azimut.app.core.sync.SyncEngine.Result.Failed -> result.message
                            }
                            if (result is ru.azimut.app.core.sync.SyncEngine.Result.Done) {
                                onDataRestored()
                                reloadConflicts()
                            }
                        }
                    },
                ) {
                    Text(if (syncBusy) tr("Синхронизация…", "Syncing…") else tr("Синхронизировать сейчас", "Sync now"))
                }
                if (conflictsOpen) {
                    AlertDialog(
                        onDismissRequest = { conflictsOpen = false },
                        title = { Text(conflictsTitle) },
                        text = {
                            Column {
                                Text(conflictsHint, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                conflictFiles.take(20).forEach { path ->
                                    Text(path, style = MaterialTheme.typography.bodySmall)
                                }
                                if (conflictFiles.size > 20) {
                                    Text("… +" + (conflictFiles.size - 20), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        conflictFiles.forEach { PlatformFiles.delete(it) }
                                    }
                                    conflictFiles = emptyList()
                                    conflictsOpen = false
                                    message = conflictsDeletedMsg
                                }
                            }) { Text(conflictsDeleteAll) }
                        },
                        dismissButton = {
                            TextButton(onClick = { conflictsOpen = false }) { Text(conflictsClose) }
                        },
                    )
                }
                Text(
                    tr(
                        "Пароль шифруется ключом платформы; на Windows — локальным файлом ключа (DPAPI недоступен из JVM).",
                        "The password is encrypted with a platform key; on Windows with a local key file (DPAPI is unreachable from a JVM).",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InterfaceSection(settings, onSettingsChange, { expanded(it) }, { setExpanded(it) })

            AboutSection({ expanded(it) }, { setExpanded(it) }, onOpenHelp, onOpenAbout)
        }
    }

    restoreChoices?.let { entries ->
        AlertDialog(
            onDismissRequest = { restoreChoices = null },
            title = { Text(tr("Восстановить данные", "Restore data")) },
            text = {
                Column {
                    Text(
                        tr(
                            "Текущее состояние сначала сохранится в новую копию. Выберите копию:",
                            "The current state is saved as a new backup first. Choose a backup:",
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    entries.forEach { entry ->
                        TextButton(
                            onClick = {
                                if (recorder.recording) {
                                    restoreChoices = null
                                    message = restoreBlockedMessage
                                    return@TextButton
                                }
                                restoreChoices = null
                                backupBusy = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { Backup.restore(entry.name) }
                                    backupBusy = false
                                    message = if (ok) restoreDoneMessage else restoreFailedMessage
                                    if (ok) onDataRestored()
                                }
                            },
                        ) {
                            Text(
                                entry.name + "\n" + formatStamp(entry.modifiedAt, timeZone) +
                                    " · " + (entry.size / 1024) + tr(" КБ", " KB"),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { restoreChoices = null }) { Text(tr("Отмена", "Cancel")) }
            },
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

/** Fuel numeric field: blank = 0 (off); commits on every valid keystroke, comma accepted. */
@Composable
private fun FuelField(label: String, value: Double, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            if (input.isBlank()) {
                onCommit(0.0)
            } else {
                input.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let(onCommit)
            }
        },
        label = { Text(label) },
        singleLine = true,
    )
}

private fun formatClock(time: LocalTime): String =
    time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0')

private fun formatDaylight(daylight: Daylight, lang: String): String {
    val en = lang == "en"
    return when (daylight.state) {
        SunState.POLAR_DAY -> if (en) "polar day" else "полярный день"
        SunState.POLAR_NIGHT -> if (en) "polar night" else "полярная ночь"
        SunState.NORMAL -> {
            val rise = daylight.sunrise
            val set = daylight.sunset
            if (rise == null || set == null) {
                if (en) "no data" else "нет данных"
            } else {
                val minutes = daylight.durationMinutes ?: 0
                val length = if (en) {
                    "${minutes / 60} h ${minutes % 60} min"
                } else {
                    "${minutes / 60} ч ${minutes % 60} мин"
                }
                "${formatClock(rise)}–${formatClock(set)} · $length"
            }
        }
    }
}

private fun formatStamp(epochMillis: Long, timeZone: TimeZone): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return "${formatClock(local.time)} ${local.date}"
}
