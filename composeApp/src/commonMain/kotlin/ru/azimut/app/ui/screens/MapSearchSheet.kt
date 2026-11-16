package ru.azimut.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.parseCoordPair
import ru.azimut.app.ui.tr

/** Offline search: coordinates ("55.75, 37.62") or the name of a saved point. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapSearchSheet(
    points: List<Waypoint>,
    onGo: (GeoPoint) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val coords = remember(q) { if (q.isEmpty()) null else parseCoordPair(q) }
    val matches = remember(points, q) {
        if (q.isEmpty()) emptyList()
        else points.filter { it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }.take(50)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF2F2F7),
        contentColor = Color(0xFF1C1C1E),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr("Название точки или координаты", "Point name or coordinates")) },
            )
            if (q.isEmpty()) {
                Text(
                    tr(
                        "Введите название сохранённой точки или координаты, например 55.75, 37.62",
                        "Type a saved point name or coordinates, e.g. 55.75, 37.62",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93),
                )
            } else if (coords == null && matches.isEmpty()) {
                Text(
                    tr("Ничего не найдено", "Nothing found"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E8E93),
                )
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                    if (coords != null) {
                        ResultRow(
                            Icons.Filled.Navigation,
                            tr("Перейти к координатам", "Go to coordinates"),
                            "${coords.first}, ${coords.second}",
                        ) { onGo(GeoPoint(coords.first, coords.second)); onDismiss() }
                    }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(matches, key = { it.id }) { point ->
                            if (coords != null || point !== matches.first()) HorizontalDivider(Modifier.padding(start = 16.dp), color = Color(0xFFE5E5EA))
                            ResultRow(
                                Icons.Filled.Place,
                                point.name.ifBlank { tr("Без названия", "Unnamed") },
                                point.description.ifBlank { "${point.lat}, ${point.lon}" },
                            ) { onGo(GeoPoint(point.lat, point.lon)); onDismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MapAccent)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1C1C1E), maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8E93), maxLines = 1)
        }
    }
}
