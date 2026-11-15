package ru.azimut.app.core.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.gpx.GpxImportOutcome
import ru.azimut.app.core.gpx.GpxImporter
import ru.azimut.app.core.model.PoiTypes
import ru.azimut.app.core.model.Waypoint
import kotlin.math.floor

/**
 * Waypoint import/export in the popular formats: GPX, KML, KMZ, CSV, GeoJSON.
 * CSV uses plain "lat,lon,name,description,category" rows (RFC 4180 quoting);
 * the GeoJSON side handles Point Features.
 */

/** Packs a KML document as doc.kml into a minimal KMZ archive (stored entries). */
expect object ZipKmlWriter {
    fun zipKml(kml: String): ByteArray
}

object PointsIO {
    enum class Format { GPX, KML, KMZ, CSV, GEOJSON }

    fun formatOf(fileName: String): Format? = when (fileName.lowercase().substringAfterLast('.').substringAfterLast('/')) {
        "gpx" -> Format.GPX
        "kml" -> Format.KML
        "kmz" -> Format.KMZ
        "csv" -> Format.CSV
        "geojson", "json" -> Format.GEOJSON
        else -> null
    }

    // ---- Export ----

    fun export(format: Format, points: List<Waypoint>, title: String): Pair<String, ByteArray> = when (format) {
        Format.GPX -> {
            val content = Gpx.writeWaypoints(points, title)
            ".gpx" to content.toByteArray(Charsets.UTF_8)
        }
        Format.KML -> ".kml" to exportKml(points, title).toByteArray(Charsets.UTF_8)
        Format.KMZ -> ".kmz" to ZipKmlWriter.zipKml(exportKml(points, title))
        Format.CSV -> ".csv" to exportCsv(points).toByteArray(Charsets.UTF_8)
        Format.GEOJSON -> ".geojson" to exportGeoJson(points).toByteArray(Charsets.UTF_8)
    }

    fun exportKml(points: List<Waypoint>, title: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        append("<name>${xmlEscape(title)}</name>\n")
        for (w in points) {
            append("<Placemark>\n")
            append("<name>${xmlEscape(w.name)}</name>\n")
            if (w.description.isNotBlank()) append("<description>${xmlEscape(w.description)}</description>\n")
            append("<Point><coordinates>${fmt(w.lon)},${fmt(w.lat)}${w.altitude?.let { ",$it" } ?: ""}</coordinates></Point>\n")
            append("</Placemark>\n")
        }
        append("</Document>\n</kml>\n")
    }

    fun exportCsv(points: List<Waypoint>): String = buildString {
        append("lat,lon,name,description,category\n")
        for (w in points) {
            append(listOf(
                fmt(w.lat),
                fmt(w.lon),
                csvEscape(w.name),
                csvEscape(w.description),
                csvEscape(w.category),
            ).joinToString(","))
            append("\n")
        }
    }

    fun exportGeoJson(points: List<Waypoint>): String = buildString {
        val features = buildJsonArray {
            for (w in points) {
                add(buildJsonObject {
                    put("type", "Feature")
                    put("geometry", buildJsonObject {
                        put("type", "Point")
                        put("coordinates", buildJsonArray {
                            add(JsonPrimitive(w.lon))
                            add(JsonPrimitive(w.lat))
                            w.altitude?.let { add(JsonPrimitive(it)) }
                        })
                    })
                    put("properties", buildJsonObject {
                        put("name", w.name)
                        if (w.description.isNotBlank()) put("description", w.description)
                        put("category", w.category)
                    })
                })
            }
        }
        append("{\"type\":\"FeatureCollection\",\"features\":")
        append(features.toString())
        append("}")
    }

    // ---- Import ----

    /**
     * Parses any supported format into waypoints. KMZ bytes are unzipped here.
     * Returns a GPX-style outcome so the UI stays uniform.
     */
    fun import(fileName: String, content: String, bytes: ByteArray?): List<Waypoint> = when (formatOf(fileName)) {
        Format.GPX -> GpxImporter.parse(fileName, content, "enduro").waypoints
        Format.KML -> TrackImport.parse(fileName, content, "enduro").waypoints
        Format.KMZ -> {
            val kml = bytes?.let { ZipKml.extract(it) }
            if (kml == null) emptyList()
            else TrackImport.parse(fileName.substringBeforeLast('.') + ".kml", kml, "enduro").waypoints
        }
        Format.CSV -> parseCsv(content)
        Format.GEOJSON -> parseGeoJsonPoints(content)
        null -> emptyList()
    }

    fun parseCsv(content: String): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        val rows = content.removePrefix("\uFEFF").split('\n')
        var startIndex = 0
        if (rows.firstOrNull()?.lowercase()?.startsWith("lat,") == true) startIndex = 1
        val now = System.currentTimeMillis()
        for ((index, row) in rows.drop(startIndex).withIndex()) {
            val cells = splitCsvRow(row)
            if (cells.size < 2) continue
            val lat = cells[0].trim().toDoubleOrNull() ?: continue
            val lon = cells[1].trim().toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val name = cells.getOrNull(2)?.trim().orEmpty().ifBlank { "Точка ${index + 1}" }
            val description = cells.getOrNull(3)?.trim().orEmpty()
            val category = cells.getOrNull(4)?.trim().orEmpty()
            result.add(
                Waypoint(
                    id = "csv-$now-$index",
                    name = name,
                    description = description,
                    lat = lat,
                    lon = lon,
                    createdAt = now,
                    kind = "imported",
                    category = if (category in PoiTypes.ALL) category else PoiTypes.OTHER,
                    isPoi = true,
                ),
            )
        }
        return result
    }

    /** CSV splitting that respects quotes; a quoted field may contain commas/newlines. */
    private fun splitCsvRow(row: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val ch = row[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < row.length && row[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    cells.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        cells.add(sb.toString())
        return cells
    }

    fun parseGeoJsonPoints(geoJson: String): List<Waypoint> {
        val root = runCatching { Json.parseToJsonElement(geoJson) }.getOrNull() as? JsonObject ?: return emptyList()
        val features: List<JsonObject> = when (root["type"]?.let { (it as? JsonPrimitive)?.content }) {
            "FeatureCollection" -> (root["features"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject } ?: emptyList()
            "Feature" -> listOf(root)
            else -> emptyList()
        }
        val now = System.currentTimeMillis()
        val result = mutableListOf<Waypoint>()
        for ((index, feature) in features.withIndex()) {
            val geometry = (feature["geometry"] as? JsonObject) ?: continue
            val type = geometry["type"]?.let { (it as? JsonPrimitive)?.content }
            if (type != "Point") continue
            val coords = geometry["coordinates"] as? JsonArray ?: continue
            val lon = coords.getOrNull(0)?.let { (it as? JsonPrimitive)?.doubleOrNull() } ?: continue
            val lat = coords.getOrNull(1)?.let { (it as? JsonPrimitive)?.doubleOrNull() } ?: continue
            val altitude = coords.getOrNull(2)?.let { (it as? JsonPrimitive)?.doubleOrNull() }
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val props = feature["properties"] as? JsonObject
            val name = props?.get("name")?.let { (it as? JsonPrimitive)?.content }
                ?: "Точка ${index + 1}"
            val description = props?.get("description")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
            val category = props?.get("category")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
            result.add(
                Waypoint(
                    id = "gj-$now-$index",
                    name = name,
                    description = description,
                    lat = lat,
                    lon = lon,
                    altitude = altitude,
                    createdAt = now,
                    kind = "imported",
                    category = if (category in PoiTypes.ALL) category else PoiTypes.OTHER,
                    isPoi = true,
                ),
            )
        }
        return result
    }

    private fun JsonPrimitive.doubleOrNull(): Double? = runCatching { content.toDoubleOrNull() }.getOrNull()

    private fun fmt(value: Double): String = ru.azimut.app.core.model.formatFixed(value, 7)

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun csvEscape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}
