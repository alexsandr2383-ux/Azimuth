package ru.azimut.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.ui.tr
import kotlin.math.round

private fun r6(v: Double): String = (round(v * 1e6) / 1e6).toString()

/** Guru-style place card: title and coordinates on top, grouped actions below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSheet(
    point: GeoPoint,
    hitPoint: Waypoint?,
    hitTrack: Track?,
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
    fun act(block: () -> Unit): () -> Unit = { block(); onDismiss() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF2F2F7),
        contentColor = Color(0xFF1C1C1E),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    hitPoint?.name?.ifBlank { null } ?: hitTrack?.name ?: tr("Точка на карте", "Map point"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${r6(point.lat)}, ${r6(point.lon)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E8E93),
                )
            }
            MenuGroup {
                MenuRow(Icons.Filled.Route, Color(0xFF0F766E), tr("Маршрут сюда", "Build route here"), act { onRouteHere(point) })
                MenuDivider()
                MenuRow(Icons.Filled.Flag, Color(0xFF65A30D), tr("Откуда — эта точка", "Route from here"), act { onRouteFrom(point) })
                MenuDivider()
                MenuRow(Icons.Filled.Navigation, Color(0xFFD97706), tr("Куда — эта точка", "Route to here"), act { onRouteTo(point) })
                MenuDivider()
                MenuRow(Icons.Filled.Place, Color(0xFF7C5C3B), tr("Через эту точку", "Via this point"), act { onVia(point) })
                if (hasRoute) {
                    MenuDivider()
                    MenuRow(Icons.Filled.Clear, Color(0xFF8E8E93), tr("Очистить маршрут", "Clear route"), act { onClearRoute() })
                }
            }
            MenuGroup {
                MenuRow(Icons.Filled.AddLocationAlt, Color(0xFF0891B2), tr("Добавить точку/POI", "Add waypoint/POI"), act { onAddPoint(point) })
                if (hitPoint != null) {
                    MenuDivider()
                    MenuRow(Icons.Filled.ContentCopy, Color(0xFF8E8E93), tr("Копировать координаты", "Copy coordinates"), act { onCopyCoords(hitPoint) })
                    MenuDivider()
                    MenuRow(Icons.Filled.Explore, Color(0xFF0F766E), tr("Компас: к этой точке", "Compass: navigate to it"), act { onNavigateTo(hitPoint) })
                    MenuDivider()
                    MenuRow(Icons.Filled.Delete, Color(0xFFFF3B30), tr("Удалить точку", "Delete point"), act { onDeletePoint(hitPoint) })
                }
            }
            if (hitTrack != null) {
                val trackShown = hitTrack.id == shownTrack?.id || backgroundTracks.any { it.id == hitTrack.id }
                MenuGroup {
                    MenuRow(
                        Icons.Filled.Visibility,
                        Color(0xFF65A30D),
                        if (trackShown) tr("Скрыть трек", "Hide track") else tr("Показать трек", "Show track"),
                        act { onToggleTrack(hitTrack) },
                    )
                    MenuDivider()
                    MenuRow(Icons.Filled.SaveAlt, Color(0xFF0F766E), tr("Сохранить трек GPX", "Save track GPX"), act { onExportTrack(hitTrack) })
                    MenuDivider()
                    MenuRow(Icons.Filled.Edit, Color(0xFFD97706), tr("Переименовать трек", "Rename track"), act { onRenameTrack(hitTrack) })
                    MenuDivider()
                    MenuRow(Icons.Filled.Delete, Color(0xFFFF3B30), tr("Удалить трек", "Delete track"), act { onDeleteTrack(hitTrack) })
                }
            }
        }
    }
}
