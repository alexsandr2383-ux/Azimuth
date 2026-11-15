package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.TransportProfile
import ru.azimut.app.core.model.formatCoord
import ru.azimut.app.core.model.formatDistance
import ru.azimut.app.core.routing.RouteError
import ru.azimut.app.core.routing.RouteResult
import ru.azimut.app.core.routing.formatRouteDuration
import ru.azimut.app.ui.tr
import androidx.compose.ui.window.DialogProperties

internal enum class RouteTarget { FROM, TO }

internal fun routeSummary(result: RouteResult): String =
    "${formatDistance(result.distanceMeters.toDouble())} · ${formatRouteDuration(result.timeSeconds)} · " +
        "↑${result.ascendMeters} м ↓${result.descentMeters} м · уклон до ${result.maxGradePercent}%"

@Composable
internal fun RouteDialog(
    from: GeoPoint?,
    to: GeoPoint?,
    profileId: String,
    canUseCurrent: Boolean,
    busy: Boolean,
    result: RouteResult?,
    onFromCurrent: () -> Unit,
    onFromPick: () -> Unit,
    onToCurrent: () -> Unit,
    onToPick: () -> Unit,
    onSwap: () -> Unit,
    viaCount: Int,
    onClearVia: () -> Unit,
    onClearAll: () -> Unit,
    onProfileChange: (String) -> Unit,
    avoidTrails: Boolean = false,
    onAvoidTrailsChange: (Boolean) -> Unit = {},
    susanin: Boolean = false,
    onBuild: () -> Unit,
    segmentsProgress: Pair<Int, Int>? = null,
    missingSegmentCount: Int = 0,
    onDownloadSegments: () -> Unit = {},
    /** Fuel-range line built by the caller (null/empty = fuel settings off). */
    fuelText: String = "",
    onDismiss: () -> Unit,
) {
    var showProfiles by remember { mutableStateOf(false) }
    val profile = TransportProfile.byId(profileId)
    AlertDialog(
        // Full screen width minus symmetric margins (capped for desktop).
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Маршрут", "Route")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteEndpointRow(
                    label = tr("Откуда", "From"),
                    point = from,
                    onCurrent = onFromCurrent,
                    onPick = onFromPick,
                )
                RouteEndpointRow(
                    label = tr("Куда", "To"),
                    point = to,
                    onCurrent = onToCurrent,
                    onPick = onToPick,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(tr("Профиль", "Profile"), style = MaterialTheme.typography.labelLarge)
                    Box {
                        TextButton(onClick = { showProfiles = true }) {
                            Text(tr(profile.titleRu, profile.titleEn))
                        }
                        DropdownMenu(
                            expanded = showProfiles,
                            onDismissRequest = { showProfiles = false },
                        ) {
                            TransportProfile.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (item == profile) "✓ " else "") +
                                                tr(item.titleRu, item.titleEn)
                                        )
                                    },
                                    onClick = {
                                        onProfileChange(item.id)
                                        showProfiles = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = onSwap) { Text(tr("Поменять", "Swap")) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = avoidTrails,
                        onCheckedChange = onAvoidTrailsChange,
                    )
                    Text(
                        tr("Избегать троп (только дороги)", "Avoid trails (roads only)"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (susanin) {
                    Text(
                        tr(
                            "Сусанин: профиль самый проходимый — маршрут может пройти там, где проезд сомнителен.",
                            "Explorer mode: the most permissive profile — the route may go where passage is doubtful.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (viaCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Промежуточных точек: $viaCount", "Via points: $viaCount"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearVia) { Text(tr("Очистить", "Clear")) }
                    }
                } else {
                    Text(
                        tr(
                            "Промежуточные точки: правый клик (долгое нажатие) по карте → «Через эту точку».",
                            "Via points: right-click (long press) on the map → “Via this point”.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(tr("Строим маршрут…", "Building route…"))
                    }
                }
                result?.let {
                    if (it.ok) {
                        Text(routeSummary(it), color = MaterialTheme.colorScheme.primary)
                        if (fuelText.isNotBlank()) {
                            Text(
                                fuelText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            routeErrorText(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (it.error == RouteError.NO_SEGMENTS) {
                            val progress = segmentsProgress
                            if (progress != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(
                                        tr(
                                            "Скачиваем данные: ${progress.first}/${progress.second}",
                                            "Downloading data: ${progress.first}/${progress.second}",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            } else if (missingSegmentCount > 0) {
                                TextButton(onClick = onDownloadSegments) {
                                    Text(
                                        tr(
                                            "Скачать данные для роутинга ($missingSegmentCount)",
                                            "Download routing data ($missingSegmentCount)",
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onBuild,
                enabled = !busy && to != null && (from != null || canUseCurrent),
            ) {
                Text(tr("Построить", "Build"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClearAll, enabled = !busy && (from != null || to != null || viaCount > 0)) {
                    Text(tr("Очистить всё", "Clear all"))
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(tr("Закрыть", "Close"))
                }
            }
        },
    )
}

@Composable
private fun RouteEndpointRow(
    label: String,
    point: GeoPoint?,
    onCurrent: () -> Unit,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                point?.let { formatCoord(it.lat, it.lon) } ?: tr("не выбрано", "not set"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCurrent) { Text(tr("Моя позиция", "My location")) }
            TextButton(onClick = onPick) { Text(tr("На карте", "On map")) }
        }
    }
}

@Composable
internal fun routeWarnings(result: RouteResult): List<String> = buildList {
    if (result.trailCount > 0) {
        add(
            tr(
                "ТРОПА (не дорога): ${result.trailCount} уч., ${formatDistance(result.trailMeters.toDouble())}",
                "TRAIL (not a road): ${result.trailCount} sections, ${formatDistance(result.trailMeters.toDouble())}",
            )
        )
    }
    if (result.restrictedMeters > 0) {
        add(
            tr(
                "Запрет для мото по OSM: ${formatDistance(result.restrictedMeters.toDouble())}",
                "Motor vehicles banned per OSM: ${formatDistance(result.restrictedMeters.toDouble())}",
            )
        )
    }
    if (result.fordCount > 0) add(tr("Броды: ${result.fordCount}", "Fords: ${result.fordCount}"))
}

@Composable
private fun routeErrorText(result: RouteResult): String {
    val details = result.details?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
    return when (result.error) {
        RouteError.NO_SEGMENTS -> tr(
            "Нет данных для роутинга в этом районе. Скачайте недостающие файлы прямо отсюда:",
            "No routing data for this area. Download the missing files right here:",
        ) + details
        RouteError.NO_ROUTE -> tr(
            "Маршрут не найден. Проверьте, что обе точки лежат в загруженных сегментах.",
            "Route not found. Check that both points are inside imported segments.",
        ) + details
        RouteError.FAILED -> tr(
            "Ошибка офлайн-роутера.",
            "Offline router error.",
        ) + details
        RouteError.NONE -> ""
    }
}
