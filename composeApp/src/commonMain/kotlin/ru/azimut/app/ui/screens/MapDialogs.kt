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
import androidx.compose.runtime.Composable
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
internal fun AddPointDialog(
    point: GeoPoint,
    photoQuality: String,
    voiceEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, photos: List<String>, voiceNotes: List<String>, type: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(emptyList<String>()) }
    var voiceNotes by remember { mutableStateOf(emptyList<String>()) }
    var type by remember { mutableStateOf(PoiTypes.OWN) }
    val cancel = {
        photos.forEach(::deletePhoto)
        voiceNotes.forEach(::deleteVoice)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(tr("Новая точка", "New waypoint")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val coordsClipboard = LocalClipboardManager.current
                val coordsText = formatCoord(point.lat, point.lon, dms = true) + "\n" +
                    formatCoord(point.lat, point.lon)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatCoord(point.lat, point.lon, dms = true), style = MaterialTheme.typography.bodySmall)
                        Text(formatCoord(point.lat, point.lon), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        coordsClipboard.setText(AnnotatedString(coordsText))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = tr("Копировать координаты", "Copy coordinates"))
                    }
                    IconButton(onClick = { ru.azimut.app.ui.components.Share.shareText(coordsText) }) {
                        Icon(Icons.Filled.Share, contentDescription = tr("Поделиться координатами", "Share coordinates"))
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Название", "Name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(tr("Описание", "Description")) },
                    minLines = 2,
                )
                PoiTypePicker(selected = type, onSelect = { type = it })
                PhotoEditor(
                    photos = photos,
                    quality = photoQuality,
                    onAdded = { fileName, _ -> photos = photos + fileName },
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description, photos, voiceNotes, type) }) {
                Text(tr("Сохранить", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = cancel) { Text(tr("Отмена", "Cancel")) }
        },
    )
}

@Composable
internal fun PlanSaveDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Сохранить маршрут", "Save route")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Название", "Name")) },
                    singleLine = true,
                )
                Text(
                    tr(
                        "Маршрут попадёт в «Журнал поездок». GPX-файл можно перенести на телефон " +
                            "и открыть: Настройки → Данные → «Импорт GPX», затем Журнал → «Вести». " +
                            "Подробные карты полосы вокруг маршрута — кнопка «Карты вдоль маршрута».",
                        "The route goes to the trip journal. Copy the GPX file to the phone and import it: " +
                            "Settings → Data → Import GPX, then Journal → Navigate.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave(name) }) { Text(tr("В журнал", "To journal")) }
                TextButton(onClick = { onExport(name) }) { Text(tr("В журнал + GPX", "Journal + GPX")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}
