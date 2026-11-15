package ru.azimut.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.azimut.app.core.map.MapStyle
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.routing.RouteData
import ru.azimut.app.ui.tr
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

internal enum class SelectionShape { RECT, LASSO }

/** What to download: a drawn area, or a strip of ±[radiusMeters] around a route line. */
internal sealed class DownloadShape {
    abstract val outline: List<GeoPoint>

    data class Polygon(val points: List<GeoPoint>) : DownloadShape() {
        override val outline get() = points
    }

    data class Corridor(val line: List<GeoPoint>, val radiusMeters: Double, val name: String) : DownloadShape() {
        override val outline get() = line
    }

    fun tiles(z: Int, limit: Long): Set<Long>? = when (this) {
        is Polygon -> tilesInPolygon(points, z, limit)
        is Corridor -> tilesAlongLine(line, radiusMeters, z, limit)
    }

    fun areaKm2(): Double = when (this) {
        is Polygon -> polygonAreaKm2(points)
        is Corridor -> lineLengthMeters(line) * 2 * radiusMeters / 1e6
    }
}

/** Lowest zoom fetched for an area: a cheap overview so the offline map can be zoomed out. */
private const val AREA_MIN_ZOOM = 8

internal data class AreaOptions(
    val map: Boolean,
    val mapMaxZoom: Int,
    val satellite: Boolean,
    val satelliteMaxZoom: Int,
    val segments: Boolean,
    val exportMbtiles: Boolean,
)

internal data class AreaPlan(
    val jobs: List<Pair<String, TileKey>>,
    /** Number of tiles, or the count reached when the limit was exceeded. */
    val tileCount: Long,
    val tooLarge: Boolean,
    val segments: List<String>,
)

/**
 * Tiles of zoom [z] that touch [polygon]: tiles whose centre lies inside (scanline)
 * plus every tile crossed by the outline. Returns null once more than [limit] tiles are found.
 */
internal fun tilesInPolygon(polygon: List<GeoPoint>, z: Int, limit: Long): Set<Long>? {
    if (polygon.size < 3) return emptySet()
    val n = 2.0.pow(z)
    val maxIndex = (1 shl z) - 1
    val xs = polygon.map { (it.lon + 180.0) / 360.0 * n }
    val ys = polygon.map {
        val lat = it.lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
        (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * n
    }
    val result = HashSet<Long>()
    fun add(x: Int, y: Int): Boolean {
        if (x !in 0..maxIndex || y !in 0..maxIndex) return true
        result += x.toLong() shl 32 or y.toLong()
        return result.size <= limit
    }

    val minRow = floor(ys.min()).toInt().coerceAtLeast(0)
    val maxRow = floor(ys.max()).toInt().coerceAtMost(maxIndex)
    val count = polygon.size
    for (row in minRow..maxRow) {
        val yc = row + 0.5
        val crossings = ArrayList<Double>()
        for (i in 0 until count) {
            val j = (i + 1) % count
            val y1 = ys[i]
            val y2 = ys[j]
            if ((y1 <= yc && yc < y2) || (y2 <= yc && yc < y1)) {
                crossings += xs[i] + (yc - y1) / (y2 - y1) * (xs[j] - xs[i])
            }
        }
        crossings.sort()
        for (k in 0 until crossings.size - 1 step 2) {
            val from = ceil(crossings[k] - 0.5).toInt()
            val to = floor(crossings[k + 1] - 0.5).toInt()
            for (col in from..to) if (!add(col, row)) return null
        }
    }
    // Outline: sample each edge finely enough not to skip a tile.
    for (i in 0 until count) {
        val j = (i + 1) % count
        val steps = (max(abs(xs[j] - xs[i]), abs(ys[j] - ys[i])) * 4).roundToInt().coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s.toDouble() / steps
            if (!add(floor(xs[i] + t * (xs[j] - xs[i])).toInt(), floor(ys[i] + t * (ys[j] - ys[i])).toInt())) return null
        }
    }
    return result
}

/**
 * Tiles of zoom [z] within about [radiusMeters] of the polyline [line]
 * (square stamps along the densified line). Null once more than [limit] tiles are found.
 */
internal fun tilesAlongLine(line: List<GeoPoint>, radiusMeters: Double, z: Int, limit: Long): Set<Long>? {
    if (line.isEmpty()) return emptySet()
    val n = 2.0.pow(z)
    val maxIndex = (1 shl z) - 1
    fun tx(p: GeoPoint) = (p.lon + 180.0) / 360.0 * n
    fun ty(p: GeoPoint): Double {
        val lat = p.lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
        return (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * n
    }
    val result = HashSet<Long>()
    fun stamp(x: Double, y: Double, lat: Double): Boolean {
        val tileMeters = 40_075_016.686 * cos(lat * PI / 180.0) / n
        val r = radiusMeters / tileMeters
        for (col in floor(x - r).toInt()..floor(x + r).toInt()) {
            if (col !in 0..maxIndex) continue
            for (row in floor(y - r).toInt()..floor(y + r).toInt()) {
                if (row !in 0..maxIndex) continue
                result += col.toLong() shl 32 or row.toLong()
                if (result.size > limit) return false
            }
        }
        return true
    }
    if (!stamp(tx(line[0]), ty(line[0]), line[0].lat)) return null
    for (i in 1 until line.size) {
        val a = line[i - 1]
        val b = line[i]
        val ax = tx(a)
        val ay = ty(a)
        val bx = tx(b)
        val by = ty(b)
        // Stamps every half tile, so the strip has no gaps even at high zooms.
        val steps = (max(abs(bx - ax), abs(by - ay)) * 2).roundToInt().coerceAtLeast(1)
        for (s in 1..steps) {
            val t = s.toDouble() / steps
            if (!stamp(ax + t * (bx - ax), ay + t * (by - ay), a.lat + t * (b.lat - a.lat))) return null
        }
    }
    return result
}

internal fun lineLengthMeters(line: List<GeoPoint>): Double =
    line.zipWithNext { a, b -> ru.azimut.app.core.model.distanceMeters(a, b) }.sum()

internal fun planArea(shape: DownloadShape, options: AreaOptions, baseLayer: String): AreaPlan {
    val specs = buildList {
        if (options.map) for (z in AREA_MIN_ZOOM..options.mapMaxZoom) add(baseLayer to z)
        if (options.satellite) {
            for (z in AREA_MIN_ZOOM..options.satelliteMaxZoom) add("satellite_hd" to z)
            // Place names drawn over the satellite layer exist up to z16.
            for (z in AREA_MIN_ZOOM..min(16, options.satelliteMaxZoom)) add("places" to z)
        }
    }
    val jobs = ArrayList<Pair<String, TileKey>>()
    var total = 0L
    var tooLarge = false
    for ((layer, z) in specs) {
        val tiles = shape.tiles(z, MAX_AREA_DOWNLOAD_TILES - total)
        if (tiles == null) {
            tooLarge = true
            total = MAX_AREA_DOWNLOAD_TILES + 1
            break
        }
        total += tiles.size
        tiles.forEach { key -> jobs += layer to TileKey(z, (key shr 32).toInt(), (key and 0xFFFFFFFFL).toInt()) }
    }
    val segments = if (options.segments && shape.outline.isNotEmpty()) segmentNames(shape.outline) else emptyList()
    return AreaPlan(if (tooLarge) emptyList() else jobs, total, tooLarge, segments)
}

/** All BRouter 5°×5° segment files covering the polygon's bounding box. */
internal fun segmentNames(polygon: List<GeoPoint>): List<String> {
    val names = linkedSetOf<String>()
    var lat = polygon.minOf { it.lat }
    while (true) {
        var lon = polygon.minOf { it.lon }
        while (true) {
            names += RouteData.segmentFileName(GeoPoint(lat, lon))
            if (lon >= polygon.maxOf { it.lon }) break
            lon = min(lon + 5.0, polygon.maxOf { it.lon })
        }
        if (lat >= polygon.maxOf { it.lat }) break
        lat = min(lat + 5.0, polygon.maxOf { it.lat })
    }
    return names.toList()
}

internal fun segmentFile(name: String) = File(PlatformFiles.navDataRoot(), "${RouteData.SEGMENTS_DIR}/$name")

/**
 * Downloads one BRouter segment from brouter.de into NavData/routing/segments4.
 * [progress] gets 0..1; returns false on failure or when [cancelled].
 */
internal fun downloadSegment(name: String, progress: (Float) -> Unit, cancelled: () -> Boolean): Boolean {
    val target = segmentFile(name)
    val tmp = File(target.parentFile, "$name.part")
    target.parentFile.mkdirs()
    var connection: HttpURLConnection? = null
    return try {
        connection = (URI("https://brouter.de/brouter/segments4/$name").toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Azimuth/0.1 (personal offline navigation)")
        }
        if (connection.responseCode !in 200..299) return false
        val total = connection.contentLengthLong
        var done = 0L
        connection.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (cancelled()) return false
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
        java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (_: Exception) {
        false
    } finally {
        connection?.disconnect()
        tmp.delete()
    }
}

/** Area in km² of a lat/lon polygon (equirectangular approximation, fine for planning). */
internal fun polygonAreaKm2(polygon: List<GeoPoint>): Double {
    if (polygon.size < 3) return 0.0
    val lat0 = polygon.sumOf { it.lat } / polygon.size * PI / 180.0
    val kx = 111.32 * cos(lat0)
    val ky = 110.57
    var sum = 0.0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        sum += a.lon * kx * b.lat * ky - b.lon * kx * a.lat * ky
    }
    return abs(sum) / 2.0
}

@Composable
internal fun AreaDownloadDialog(
    initialShape: DownloadShape,
    baseStyle: MapStyle,
    baseLayerTitle: String,
    onDismiss: () -> Unit,
    onStart: (AreaPlan, AreaOptions, DownloadShape) -> Unit,
) {
    val corridor = initialShape is DownloadShape.Corridor
    var shape by remember { mutableStateOf(initialShape) }
    val mapAllowed = baseStyle.bulkDownload && baseStyle.localPath == null
    var map by remember { mutableStateOf(mapAllowed) }
    // A corridor is narrow, so it can afford the detailed zooms a whole region cannot.
    var mapMax by remember { mutableStateOf(min(if (corridor) 17 else 16, baseStyle.maxZoom)) }
    var satellite by remember { mutableStateOf(true) }
    var satMax by remember { mutableStateOf(if (corridor) 18 else 17) }
    var segments by remember { mutableStateOf(true) }
    var export by remember { mutableStateOf(true) }
    val options = AreaOptions(map && mapAllowed, mapMax, satellite, satMax, segments, export)
    val plan by produceState<AreaPlan?>(null, options, shape) {
        value = null
        value = withContext(Dispatchers.Default) { planArea(shape, options, baseStyle.cacheLayer) }
    }
    val missing = plan?.segments.orEmpty().filter { !segmentFile(it).isFile }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (corridor) tr("Карты вдоль маршрута", "Maps along the route")
                else tr("Что загрузить для этого участка?", "What to download for this area?")
            )
        },
        text = {
            val currentPlan = plan
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (shape as? DownloadShape.Corridor)?.let { c ->
                    Text(
                        "${c.name} · " + tr("длина ", "length ") +
                            "${(lineLengthMeters(c.line) / 1000).roundToInt()} км",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("полоса ±", "strip ±"), style = MaterialTheme.typography.bodySmall)
                        listOf(500.0, 1000.0, 2000.0, 3000.0).forEach { r ->
                            FilterChip(
                                selected = c.radiusMeters == r,
                                onClick = { shape = c.copy(radiusMeters = r) },
                                label = { Text(if (r < 1000) "${r.toInt()} м" else "${(r / 1000).toInt()} км") },
                            )
                        }
                    }
                }
                Text(
                    tr("Площадь ≈ ", "Area ≈ ") + "${shape.areaKm2().roundToInt()} км²",
                    style = MaterialTheme.typography.bodySmall,
                )
                CheckRow(map, mapAllowed, tr("Карта «$baseLayerTitle»", "Map “$baseLayerTitle”")) { map = it }
                if (!mapAllowed) {
                    Text(
                        tr(
                            "Этот стиль нельзя скачать массово — выберите CyclOSM, OpenTopoMap или Esri.",
                            "This style can't be bulk-downloaded — choose CyclOSM, OpenTopoMap or Esri.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (map) {
                    ZoomChips(tr("до зума", "up to zoom"), (13..baseStyle.maxZoom).toList(), mapMax) { mapMax = it }
                }
                CheckRow(satellite, true, tr("Спутник Esri + подписи", "Esri satellite + labels")) { satellite = it }
                if (satellite) ZoomChips(tr("до зума", "up to zoom"), (13..19).toList(), satMax) { satMax = it }
                if (corridor) {
                    Text(
                        tr(
                            "Мелкие зумы (8–12) тоже загружаются вдоль полосы — для обзора хватит карт всего района.",
                            "Coarse zooms (8–12) are fetched along the strip too; use region maps for the overview.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CheckRow(segments, true, tr("Данные для маршрутов (BRouter)", "Routing data (BRouter)")) { segments = it }
                if (segments && currentPlan != null && currentPlan.segments.isNotEmpty()) {
                    val presentMark = tr(" (есть)", " (present)")
                    Text(
                        currentPlan.segments.joinToString(", ") { name ->
                            name + if (segmentFile(name).isFile) presentMark else ""
                        } + tr(" · обычно 10–200 МБ каждый", " · usually 10–200 MB each"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                CheckRow(export, true, tr("Сохранить .mbtiles для телефона", "Save .mbtiles for the phone")) { export = it }
                Text(
                    when {
                        currentPlan == null -> tr("Подсчёт…", "Counting…")
                        currentPlan.tooLarge -> tr(
                            "Слишком много тайлов (> $MAX_AREA_DOWNLOAD_TILES). Уменьшите зум или участок.",
                            "Too many tiles (> $MAX_AREA_DOWNLOAD_TILES). Lower the zoom or the area.",
                        )
                        else -> tr("Тайлов: ", "Tiles: ") + "${currentPlan.tileCount} (≈ ${formatDownloadSize(currentPlan.tileCount)})"
                    },
                    color = if (currentPlan?.tooLarge == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            val currentPlan = plan
            TextButton(
                enabled = currentPlan != null && !currentPlan.tooLarge &&
                    (currentPlan.jobs.isNotEmpty() || missing.isNotEmpty()),
                onClick = { if (currentPlan != null) onStart(currentPlan.copy(segments = missing), options, shape) },
            ) { Text(tr("Загрузить", "Download")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun ZoomChips(label: String, zooms: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        zooms.forEach { z ->
            FilterChip(selected = z == selected, onClick = { onSelect(z) }, label = { Text("$z") })
        }
    }
}
