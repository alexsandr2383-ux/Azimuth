package ru.azimut.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import ru.azimut.app.core.import.PointsIO
import ru.azimut.app.core.model.PoiTypes
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.formatCoord
import ru.azimut.app.ui.components.BackHeader
import ru.azimut.app.ui.components.EmptyState
import ru.azimut.app.ui.components.PhotoEditor
import ru.azimut.app.ui.components.PhotoStrip
import ru.azimut.app.ui.components.PoiTypePicker
import ru.azimut.app.ui.components.VoiceEditor
import ru.azimut.app.ui.components.VoiceNoteStrip
import ru.azimut.app.ui.components.deletePhoto
import ru.azimut.app.ui.components.deleteVoice
import ru.azimut.app.ui.components.poiTypeLabel
import ru.azimut.app.ui.components.rememberBytesSaver
import ru.azimut.app.ui.components.rememberPhotoCapture
import ru.azimut.app.ui.components.rememberTrackFilePicker
import ru.azimut.app.ui.components.rememberVoiceRecorder
import ru.azimut.app.ui.LocalLang
import ru.azimut.app.ui.tr

@Composable
fun PointsScreen(
    points: List<Waypoint>,
    photoQuality: String,
    voiceEnabled: Boolean,
    onAddPoint: (Waypoint) -> Unit,
    onUpdatePoint: (Waypoint) -> Unit,
    onDeletePoint: (Waypoint) -> Unit,
    /** Bulk add used by the multi-format import; replaces duplicates by id. */
    onAddPoints: (List<Waypoint>) -> Unit = {},
    onSwitchTab: (ru.azimut.app.Screen) -> Unit = {},
    onBack: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    val photoErrorMessage = tr("Не удалось сохранить фото.", "Could not save the photo.")
    // One capture for the whole list: launchers inside lazy items would be recreated on scroll.
    var photoTargetId by remember { mutableStateOf<String?>(null) }
    val currentPoints by rememberUpdatedState(points)
    val capture = rememberPhotoCapture(
        quality = photoQuality,
        onSaved = { fileName, _ ->
            val target = currentPoints.firstOrNull { it.id == photoTargetId }
            if (target == null) deletePhoto(fileName) else onUpdatePoint(target.copy(photos = target.photos + fileName))
        },
        onError = { photoMessage = photoErrorMessage },
    )
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    var voiceTargetId by remember { mutableStateOf<String?>(null) }
    val voiceErrorMessage = tr("Не удалось записать заметку.", "Could not record the note.")
    val voice = rememberVoiceRecorder(
        onSaved = { fileName ->
            val target = currentPoints.firstOrNull { it.id == voiceTargetId }
            if (target == null) {
                deleteVoice(fileName)
            } else {
                onUpdatePoint(target.copy(voiceNotes = target.voiceNotes + fileName))
            }
            voiceTargetId = null
        },
        onError = {
            voiceMessage = voiceErrorMessage
            voiceTargetId = null
        },
    )
    val currentVoice by rememberUpdatedState(voice)
    DisposableEffect(Unit) {
        onDispose { if (currentVoice.recording) currentVoice.cancel() }
    }
    val invalidCoordinatesMessage = tr("Введите корректные координаты", "Invalid coordinates")
    val defaultPointName = tr("Точка", "Waypoint")
    // Sorting 10k imported POIs on every recomposition would stall the list.
    val sortedPoints = remember(points) { points.sortedByDescending { it.createdAt } }

    // Favorites and recents: chips filter the list; the star toggles favorite in place.
    val langEn = LocalLang.current == "en"
    var filter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    val filteredPoints = remember(sortedPoints, filter, searchQuery) {
        val base = when (filter) {
            "fav" -> sortedPoints.filter { it.favorite }
            "recent" -> sortedPoints.take(10)
            else -> sortedPoints
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) base
        else base.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }
    val favoriteCount = remember(points) { points.count { it.favorite } }

    val scope = rememberCoroutineScope()
    var ioMessage by remember { mutableStateOf<String?>(null) }
    var showExportChoice by remember { mutableStateOf(false) }
    val importFailed = tr("Не удалось импортировать файл.", "Could not import the file.")
    val saveFailed = tr("Не удалось сохранить файл.", "Could not save the file.")
    val importNothing = tr("Импортировано точек: 0.", "Imported waypoints: 0.")
    val pickImport = rememberTrackFilePicker(
        onPicked = { fileName, content, bytes ->
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching { PointsIO.import(fileName, content, bytes) }.getOrDefault(emptyList())
                }
                if (imported.isEmpty()) {
                    ioMessage = importFailed
                } else {
                    val existingIds = points.map { it.id }.toSet()
                    val fresh = imported.filterNot { it.id in existingIds }
                    ioMessage = if (langEn) {
                        "Imported waypoints: ${fresh.size} (of ${imported.size})."
                    } else {
                        "Импортировано точек: ${fresh.size} (из ${imported.size})."
                    }
                    if (fresh.isNotEmpty()) onAddPoints(fresh)
                }
            }
        },
        onError = { ioMessage = importFailed },
    )
    val savedNowPrefix = tr("Сохранено: ", "Saved: ")
    val saveBytes = rememberBytesSaver(
        onSaved = { ioMessage = savedNowPrefix + it },
        onError = { ioMessage = saveFailed },
    )
    val exportTitle = tr("Точки Azimuth", "Azimuth waypoints")
    fun exportPoints(format: PointsIO.Format) {
        val title = exportTitle
        scope.launch {
            val (ext, bytes) = withContext(Dispatchers.IO) {
                PointsIO.export(format, points, title)
            }
            val stamp = Clock.System.now().toEpochMilliseconds()
            saveBytes("azimut-points-$stamp$ext", bytes, "application/octet-stream")
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = tr("Добавить", "Add"))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BackHeader(tr("Коллекции", "Collections"), onBack)
            ru.azimut.app.ui.components.CollectionsTabs(ru.azimut.app.Screen.Points, onSwitchTab)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(tr("Поиск по названию", "Search by name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filter == "all",
                    onClick = { filter = "all" },
                    label = { Text(tr("Все", "All")) },
                )
                FilterChip(
                    selected = filter == "fav",
                    onClick = { filter = "fav" },
                    label = { Text(tr("Избранные", "Favorites") + " ($favoriteCount)") },
                )
                FilterChip(
                    selected = filter == "recent",
                    onClick = { filter = "recent" },
                    label = { Text(tr("Недавние", "Recent")) },
                )
                IconButton(onClick = pickImport) {
                    Icon(Icons.Filled.Download, contentDescription = tr("Импорт точек", "Import waypoints"))
                }
                IconButton(onClick = { showExportChoice = true }) {
                    Icon(Icons.Filled.IosShare, contentDescription = tr("Экспорт точек", "Export waypoints"))
                }
            }
            if (points.isEmpty()) {
                EmptyState(tr("Пока нет точек. Добавьте точку вручную или тапом по карте.", "No waypoints yet."))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredPoints) { point ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(point.name, style = MaterialTheme.typography.titleMedium)
                                    if (point.description.isNotBlank()) {
                                        Text(
                                            point.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatCoord(point.lat, point.lon),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        poiTypeLabel(point.poiType),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (point.photos.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        PhotoStrip(point.photos)
                                    }
                                    if (point.voiceNotes.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        VoiceNoteStrip(
                                            notes = point.voiceNotes,
                                            onRemove = { note ->
                                                onUpdatePoint(
                                                    point.copy(voiceNotes = point.voiceNotes - note),
                                                )
                                                deleteVoice(note)
                                            },
                                        )
                                    }
                                }
                                capture.takePhoto?.let { take ->
                                    IconButton(onClick = { photoTargetId = point.id; take() }) {
                                        Icon(Icons.Filled.PhotoCamera, contentDescription = tr("Снять фото", "Take photo"))
                                    }
                                }
                                IconButton(onClick = { onUpdatePoint(point.copy(favorite = !point.favorite)) }) {
                                    Icon(
                                        if (point.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = tr("Избранное", "Favorite"),
                                        tint = if (point.favorite) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { photoTargetId = point.id; capture.pick() }) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = tr("Добавить фото", "Add photo"))
                                }
                                if (voiceEnabled) {
                                    val recordingThis = voice.recording && voiceTargetId == point.id
                                    IconButton(
                                        enabled = recordingThis || !voice.recording,
                                        onClick = {
                                            if (recordingThis) {
                                                voice.stop()
                                            } else {
                                                voiceTargetId = point.id
                                                voice.start()
                                            }
                                        },
                                    ) {
                                        Icon(
                                            if (recordingThis) Icons.Filled.Stop else Icons.Filled.Mic,
                                            contentDescription = if (recordingThis) {
                                                tr("Остановить запись", "Stop recording")
                                            } else {
                                                tr("Голосовая заметка", "Voice note")
                                            },
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeletePoint(point) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = tr("Удалить", "Delete"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    photoMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { photoMessage = null },
            confirmButton = { TextButton(onClick = { photoMessage = null }) { Text("OK") } },
            text = { Text(text) },
        )
    }

    if (showExportChoice) {
        AlertDialog(
            onDismissRequest = { showExportChoice = false },
            title = { Text(tr("Формат экспорта", "Export format")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "GPX" to PointsIO.Format.GPX,
                        "KML" to PointsIO.Format.KML,
                        "KMZ" to PointsIO.Format.KMZ,
                        "CSV" to PointsIO.Format.CSV,
                        "GeoJSON" to PointsIO.Format.GEOJSON,
                    ).forEach { (label, format) ->
                        TextButton(onClick = {
                            showExportChoice = false
                            exportPoints(format)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportChoice = false }) { Text(tr("Отмена", "Cancel")) }
            },
        )
    }

    ioMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { ioMessage = null },
            confirmButton = { TextButton(onClick = { ioMessage = null }) { Text("OK") } },
            text = { Text(text) },
        )
    }

    voiceMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { voiceMessage = null },
            confirmButton = { TextButton(onClick = { voiceMessage = null }) { Text("OK") } },
            text = { Text(text) },
        )
    }

    if (showAdd) {
        var photos by remember { mutableStateOf(emptyList<String>()) }
        var voiceNotes by remember { mutableStateOf(emptyList<String>()) }
        val cancel = {
            photos.forEach(::deletePhoto)
            voiceNotes.forEach(::deleteVoice)
            showAdd = false
        }
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var lat by remember { mutableStateOf("") }
        var lon by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(PoiTypes.OWN) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = cancel,
            title = { Text(tr("Новая точка", "New waypoint")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Название", "Name")) }, singleLine = true)
                    OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text(tr("Широта", "Latitude")) }, singleLine = true)
                    OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text(tr("Долгота", "Longitude")) }, singleLine = true)
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(tr("Описание", "Description")) }, minLines = 2)
                    PoiTypePicker(selected = type, onSelect = { type = it })
                    PhotoEditor(
                        photos = photos,
                        quality = photoQuality,
                        // Geotag: a photo carrying EXIF GPS fills empty coordinate fields.
                        onAdded = { fileName, geo ->
                            photos = photos + fileName
                            if (lat.isBlank() && lon.isBlank() && geo != null) {
                                lat = "%.6f".format(geo.first)
                                lon = "%.6f".format(geo.second)
                            }
                        },
                        onRemoved = {
                            photos = photos - it
                            deletePhoto(it)
                        },
                    )
                    if (voiceEnabled) {
                        VoiceEditor(
                            notes = voiceNotes,
                            onAdded = { voiceNotes = voiceNotes + it },
                            onRemoved = {
                                voiceNotes = voiceNotes - it
                                deleteVoice(it)
                            },
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val la = lat.replace(',', '.').toDoubleOrNull()
                    val lo = lon.replace(',', '.').toDoubleOrNull()
                    if (la == null || lo == null) {
                        error = invalidCoordinatesMessage
                    } else {
                        onAddPoint(
                            Waypoint(
                                id = "wp-${Clock.System.now().toEpochMilliseconds()}",
                                name = name.ifBlank { defaultPointName },
                                description = description,
                                lat = la,
                                lon = lo,
                                createdAt = Clock.System.now().toEpochMilliseconds(),
                                category = type,
                                photos = photos,
                                voiceNotes = voiceNotes,
                            )
                        )
                        showAdd = false
                    }
                }) { Text(tr("Сохранить", "Save")) }
            },
            dismissButton = { TextButton(onClick = cancel) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}
