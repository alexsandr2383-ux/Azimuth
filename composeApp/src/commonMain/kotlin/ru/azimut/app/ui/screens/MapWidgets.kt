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
internal fun MapControl(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    iconRotation: Float = 0f,
    size: Dp = 48.dp,
    onClick: () -> Unit,
) {
    HoverTooltip(contentDescription) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = if (active) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(icon, contentDescription, Modifier.rotate(iconRotation))
        }
    }
}

/** Below this speed the GPS course is noise, so heading-up falls back to the compass. */
internal const val MIN_COURSE_SPEED_MPS = 1.5f

@Composable
internal fun orientationLabel(mode: String): String = when (mode) {
    "course" -> tr("Ориентация: по ходу движения", "Orientation: course up")
    else -> tr("Ориентация: север вверху", "Orientation: north up")
}

/**
 * Makes the whole area consume taps so gestures do not fall through to the map
 * behind the on-screen controls (buttons, badges, banners).
 */
internal fun Modifier.gestureBarrier(): Modifier = pointerInput(Unit) {
    detectTapGestures { }
}

/**
 * Per-segment grade in percent for grade coloring; null when the DEM does not
 * cover most of the track (honest fallback to the plain color). Gaps inherit
 * the previous value so short/void segments do not flash a wrong color.
 */
internal fun computeGrades(points: List<TrackPoint>, dem: ru.azimut.app.core.dem.DemStore): List<Float>? {
    if (points.size < 2) return null
    val grades = FloatArray(points.size - 1)
    var sampled = 0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val dist = distanceMeters(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon))
        val ha = if (dist >= 5.0) dem.heightAt(a.lat, a.lon) ?: a.altitude else null
        val hb = if (dist >= 5.0) dem.heightAt(b.lat, b.lon) ?: b.altitude else null
        if (ha == null || hb == null) {
            grades[i - 1] = -1f
        } else {
            sampled++
            grades[i - 1] = (abs(hb - ha) / dist * 100.0).toFloat().coerceAtMost(60f)
        }
    }
    if (sampled < (points.size - 1) * 6 / 10) return null
    var last = 0f
    for (i in grades.indices) {
        if (grades[i] < 0) grades[i] = last else last = grades[i]
    }
    return grades.toList()
}

internal fun shortDateTime(millis: Long): String {
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    fun two(v: Int) = v.toString().padStart(2, '0')
    return "${two(dt.dayOfMonth)}.${two(dt.monthNumber)} ${two(dt.hour)}:${two(dt.minute)}"
}
