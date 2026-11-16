package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.distanceMeters
import ru.azimut.app.core.model.distanceToPolylineMeters
import ru.azimut.app.ui.map.MapCamera
import ru.azimut.app.ui.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class SecondaryMenu(val point: GeoPoint, val offset: Offset)

/**
 * Контекст-меню карты (правый клик / долгое нажатие). Состояния и действия остаются
 * в [MapScreen] — сюда приходят данные для хит-теста и колбэки действий;
 * дисмисс после действия выполняется здесь (как раньше — в конце каждого onClick).
 */
@Composable
internal fun MapContextMenu(
    menu: SecondaryMenu?,
    points: List<Waypoint>,
    camera: MapCamera?,
    shownTrack: Track?,
    backgroundTracks: List<Track>,
    hasRoute: Boolean,
    onDismiss: () -> Unit,
    onRouteFrom: (GeoPoint) -> Unit,
    onRouteTo: (GeoPoint) -> Unit,
    onRouteHere: (GeoPoint) -> Unit,
    onVia: (GeoPoint) -> Unit,
    onClearRoute: () -> Unit,
    onToggleTrack: (Track) -> Unit,
    onRenameTrack: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onExportTrack: (Track) -> Unit,
    onCopyCoords: (Waypoint) -> Unit,
    onNavigateTo: (Waypoint) -> Unit,
    onDeletePoint: (Waypoint) -> Unit,
    onAddPoint: (GeoPoint) -> Unit,
) {
    if (menu == null) return
    // A tap counts as a waypoint hit when it lands within 16 dp of a saved point,
    // so the context menu can offer deleting exactly that point.
    val densityPx = LocalDensity.current.density
    val metersPerPx = camera?.let {
        156543.03392 * cos(it.lat * PI / 180.0) / 2.0.pow(it.zoom)
    } ?: 10.0
    val hitThresholdMeters = metersPerPx * 16f * densityPx
    val hitPoint = points.minByOrNull { distanceMeters(GeoPoint(it.lat, it.lon), menu.point) }
        ?.takeIf { distanceMeters(GeoPoint(it.lat, it.lon), menu.point) <= hitThresholdMeters }
    // Same hit test against track lines: the active track plus the background ones.
    var hitTrack: Track? = null
    var trackDistance = hitThresholdMeters
    for (track in sequenceOf(shownTrack) + backgroundTracks.asSequence()) {
        if (track == null) continue
        val distance = distanceToPolylineMeters(
            menu.point,
            track.points.map { GeoPoint(it.lat, it.lon) },
        )
        if (distance <= trackDistance) {
            trackDistance = distance
            hitTrack = track
        }
    }
    PlaceSheet(
        point = menu.point,
        hitPoint = hitPoint,
        hitTrack = hitTrack,
        shownTrack = shownTrack,
        backgroundTracks = backgroundTracks,
        hasRoute = hasRoute,
        onDismiss = onDismiss,
        onRouteFrom = onRouteFrom,
        onRouteTo = onRouteTo,
        onRouteHere = onRouteHere,
        onVia = onVia,
        onClearRoute = onClearRoute,
        onToggleTrack = onToggleTrack,
        onRenameTrack = onRenameTrack,
        onDeleteTrack = onDeleteTrack,
        onExportTrack = onExportTrack,
        onCopyCoords = onCopyCoords,
        onNavigateTo = onNavigateTo,
        onDeletePoint = onDeletePoint,
        onAddPoint = onAddPoint,
    )
}
