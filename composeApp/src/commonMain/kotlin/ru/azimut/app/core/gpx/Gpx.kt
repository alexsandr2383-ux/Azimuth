package ru.azimut.app.core.gpx

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.KIND_TRACK
import ru.azimut.app.core.model.PoiTypes
import ru.azimut.app.core.model.formatFixed
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.distanceMeters

data class ParsedGpx(
    val tracks: List<Track>,
    val waypoints: List<Waypoint>,
)

data class GpxImportOutcome(
    val tracks: Int = 0,
    val waypoints: Int = 0,
    val empty: Boolean = false,
    val failed: Boolean = false,
)

object Gpx {
    fun writeTrack(track: Track): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Azimuth\" ")
        sb.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        sb.append("xmlns:azimut=\"https://azimuth.app/gpx/1\" ")
        sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        sb.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
        sb.append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${escape(track.name)}</name>\n")
        sb.append("    <time>${iso(track.startedAt)}</time>\n")
        sb.append("    <extensions><azimut:profile>${escape(track.profile)}</azimut:profile></extensions>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${escape(track.name)}</name>\n")
        sb.append("    <type>${escape(track.profile)}</type>\n")
        if (track.kind != KIND_TRACK) {
            sb.append("    <extensions><azimut:kind>${escape(track.kind)}</azimut:kind></extensions>\n")
            track.plannedTimeSeconds?.let {
                sb.append("    <extensions><azimut:plannedTimeSeconds>${formatFixed(it, 0)}</azimut:plannedTimeSeconds></extensions>\n")
            }
        }
        sb.append("    <trkseg>\n")
        for (p in track.points) {
            sb.append("      <trkpt lat=\"${coord(p.lat)}\" lon=\"${coord(p.lon)}\">\n")
            if (p.altitude != null) sb.append("        <ele>${formatFixed(p.altitude, 1)}</ele>\n")
            if (p.timeMillis > 0) sb.append("        <time>${iso(p.timeMillis)}</time>\n")
            sb.append("        <extensions>\n")
            if (p.accuracy != null) sb.append("          <azimut:accuracy>${p.accuracy}</azimut:accuracy>\n")
            if (p.speed != null) sb.append("          <azimut:speed>${p.speed}</azimut:speed>\n")
            if (p.bearing != null) sb.append("          <azimut:bearing>${p.bearing}</azimut:bearing>\n")
            sb.append("          <azimut:susanin>${p.susanin}</azimut:susanin>\n")
            if (p.trail) sb.append("          <azimut:trail>true</azimut:trail>\n")
            sb.append("        </extensions>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun writeWaypoints(waypoints: List<Waypoint>, trackName: String = "Azimuth waypoints"): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Azimuth\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata><name>${escape(trackName)}</name></metadata>\n")
        for (w in waypoints) {
            sb.append("  <wpt lat=\"${coord(w.lat)}\" lon=\"${coord(w.lon)}\">\n")
            if (w.altitude != null) sb.append("    <ele>${formatFixed(w.altitude, 1)}</ele>\n")
            sb.append("    <name>${escape(w.name)}</name>\n")
            if (w.description.isNotBlank()) sb.append("    <desc>${escape(w.description)}</desc>\n")
            sb.append("    <type>${w.poiType}</type>\n")
            if (w.createdAt > 0) sb.append("    <time>${iso(w.createdAt)}</time>\n")
            sb.append(photoLinks(w))
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun writeTrackWithWaypoints(track: Track, waypoints: List<Waypoint>): String {
        val base = writeTrack(track)
        if (waypoints.isEmpty()) return base
        val wptBlock = waypoints.joinToString("") { w ->
            buildString {
                append("  <wpt lat=\"${coord(w.lat)}\" lon=\"${coord(w.lon)}\">\n")
                if (w.altitude != null) append("    <ele>${formatFixed(w.altitude, 1)}</ele>\n")
                append("    <name>${escape(w.name)}</name>\n")
                if (w.description.isNotBlank()) append("    <desc>${escape(w.description)}</desc>\n")
                append("    <type>${w.poiType}</type>\n")
                if (w.createdAt > 0) append("    <time>${iso(w.createdAt)}</time>\n")
                append(photoLinks(w))
                append("  </wpt>\n")
            }
        }
        return base.replace("  <trk>\n", wptBlock + "  <trk>\n")
    }

    fun parseImport(xml: String, fallbackProfile: String = "enduro"): ParsedGpx {
        val text = xml.removePrefix("\uFEFF")
        require(text.contains("<gpx", ignoreCase = true)) { "Not a GPX document" }
        return ParsedGpx(parseTracks(text, fallbackProfile), parseWaypoints(text))
    }

    fun parseTracks(xml: String, fallbackProfile: String = "enduro"): List<Track> {
        val result = mutableListOf<Track>()
        val trkRegex = Regex("<trk\\b[^>]*>(.*?)</trk>", RegexOption.DOT_MATCHES_ALL)
        trkRegex.findAll(xml).forEach { match ->
            val body = match.groupValues[1]
            val points = parseTrackPoints(body)
            if (points.isEmpty()) return@forEach
            val name = tagValue(body, "name")?.let(::unescape).orEmpty()
            val profile = normalizeProfile(tagValue(body, "type")?.let(::unescape), fallbackProfile)
            val startedAt = points.firstOrNull { it.timeMillis > 0 }?.timeMillis
                ?: metadataTime(xml)
                ?: Clock.System.now().toEpochMilliseconds()
            var distance = 0.0
            for (i in 1 until points.size) {
                distance += distanceMeters(
                    GeoPoint(points[i - 1].lat, points[i - 1].lon),
                    GeoPoint(points[i].lat, points[i].lon),
                )
            }
            result.add(
                Track(
                    id = "import-$startedAt-${result.size}",
                    name = name,
                    profile = profile,
                    startedAt = startedAt,
                    points = points,
                    distanceMeters = distance,
                    susanin = points.any { it.susanin },
                    kind = tagValue(body, "azimut:kind")?.let(::unescape) ?: KIND_TRACK,
                    plannedTimeSeconds = tagValue(body, "azimut:plannedTimeSeconds")?.let(::unescape)?.toDoubleOrNull(),
                )
            )
        }
        return result
    }

    fun parseWaypoints(xml: String): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        val wptRegex = Regex("<wpt\\b([^>]*)>(.*?)</wpt>", RegexOption.DOT_MATCHES_ALL)
        wptRegex.findAll(xml.removePrefix("\uFEFF")).forEach { m ->
            val attributes = m.groupValues[1]
            val lat = attrValue(attributes, "lat")?.toDoubleOrNull() ?: return@forEach
            val lon = attrValue(attributes, "lon")?.toDoubleOrNull() ?: return@forEach
            val body = m.groupValues[2]
            val name = tagValue(body, "name")?.let(::unescape) ?: "Точка"
            val desc = tagValue(body, "desc")?.let(::unescape).orEmpty()
            result.add(
                Waypoint(
                    id = "import-${lat}-${lon}-${result.size}",
                    name = name,
                    description = desc,
                    lat = lat,
                    lon = lon,
                    altitude = tagValue(body, "ele")?.toDoubleOrNull(),
                    createdAt = parseTimeMillis(tagValue(body, "time"))
                        ?: Clock.System.now().toEpochMilliseconds(),
                    kind = "imported",
                    category = PoiTypes.fromGpx(
                        sym = tagValue(body, "sym")?.let(::unescape),
                        type = tagValue(body, "type")?.let(::unescape),
                        name = name,
                    ),
                    isPoi = true,
                )
            )
        }
        return result
    }

    private fun parseTrackPoints(xml: String): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        val trkptRegex = Regex("<trkpt\\b([^>]*)>(.*?)</trkpt>", RegexOption.DOT_MATCHES_ALL)
        trkptRegex.findAll(xml).forEach { m ->
            val attributes = m.groupValues[1]
            val lat = attrValue(attributes, "lat")?.toDoubleOrNull() ?: return@forEach
            val lon = attrValue(attributes, "lon")?.toDoubleOrNull() ?: return@forEach
            val body = m.groupValues[2]
            result.add(
                TrackPoint(
                    lat = lat,
                    lon = lon,
                    altitude = tagValue(body, "ele")?.toDoubleOrNull(),
                    timeMillis = parseTimeMillis(tagValue(body, "time")) ?: 0L,
                    accuracy = tagValue(body, "azimut:accuracy")?.toFloatOrNull(),
                    speed = tagValue(body, "azimut:speed")?.toFloatOrNull(),
                    bearing = tagValue(body, "azimut:bearing")?.toFloatOrNull(),
                    susanin = tagValue(body, "azimut:susanin")?.equals("true", ignoreCase = true) == true,
                    trail = tagValue(body, "azimut:trail")?.equals("true", ignoreCase = true) == true,
                )
            )
        }
        return result
    }

    // Manual scans instead of Regex(...) per call: a 10k-waypoint file asks for these
    // values ~60k times, and compiling a fresh pattern each time dominated the import.
    private fun attrValue(attributes: String, name: String): String? {
        var from = 0
        while (true) {
            val start = attributes.indexOf(name, from, ignoreCase = true)
            if (start < 0) return null
            var i = start + name.length
            while (i < attributes.length && attributes[i].isWhitespace()) i++
            if (i < attributes.length && attributes[i] == '=') {
                i++
                while (i < attributes.length && attributes[i].isWhitespace()) i++
                if (i < attributes.length && attributes[i] == '"') {
                    val end = attributes.indexOf('"', i + 1)
                    if (end > i + 1) return attributes.substring(i + 1, end)
                }
            }
            from = start + name.length
        }
    }

    private fun tagValue(xml: String, tag: String): String? {
        val open = "<$tag"
        var from = 0
        while (true) {
            val start = xml.indexOf(open, from, ignoreCase = true)
            if (start < 0) return null
            val afterName = start + open.length
            if (afterName < xml.length &&
                xml[afterName] != '>' && !xml[afterName].isWhitespace()
            ) {
                from = afterName
                continue
            }
            val openEnd = xml.indexOf('>', afterName)
            if (openEnd < 0) return null
            val closeStart = xml.indexOf("</$tag>", openEnd + 1, ignoreCase = true)
            if (closeStart < 0) return null
            return xml.substring(openEnd + 1, closeStart).trim()
        }
    }

    private fun parseTimeMillis(raw: String?): Long? =
        raw?.takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull()
        }

    private fun normalizeProfile(raw: String?, fallback: String): String {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return fallback
        return when {
            value.contains("hiking") || value.contains("foot") || value.contains("walk") -> "foot"
            value.contains("mtb") -> "mtb"
            value.contains("bike") || value.contains("cycl") -> "bike"
            value.contains("motor") || value.contains("moto") -> "motorcycle"
            value.contains("atv") || value.contains("quad") -> "atv"
            value.contains("4x4") || value.contains("offroad") || value.contains("suv") -> "4x4"
            value.contains("car") || value.contains("auto") -> "car"
            value.contains("enduro") -> "enduro"
            else -> fallback
        }
    }

    fun parsePoints(xml: String): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        val trkRegex = Regex("<trkpt[^>]*lat=\"([-0-9.]+)\"[^>]*lon=\"([-0-9.]+)\"[^>]*>(.*?)</trkpt>", RegexOption.DOT_MATCHES_ALL)
        val eleRegex = Regex("<ele>(.*?)</ele>", RegexOption.DOT_MATCHES_ALL)
        trkRegex.findAll(xml).forEach { m ->
            val lat = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            val lon = m.groupValues[2].toDoubleOrNull() ?: return@forEach
            val ele = eleRegex.find(m.groupValues[3])?.groupValues?.get(1)?.toDoubleOrNull()
            result.add(GeoPoint(lat, lon, ele))
        }
        return result
    }

    /** GPX 1.1 links to the point's photos, relative to the NavData/ folder. */
    private fun photoLinks(w: Waypoint): String = w.photos.joinToString("") { photo ->
        "    <link href=\"photos/${escape(photo)}\"><type>image/jpeg</type></link>\n"
    }

    private fun coord(value: Double): String = formatFixed(value, 7)

    /** <metadata><time> of the document — the start time for point-less routes. */
    private fun metadataTime(xml: String): Long? =
        Regex("<metadata>.*?<time>(.*?)</time>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()
            ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    private fun iso(timeMillis: Long): String =
        Instant.fromEpochMilliseconds(timeMillis).toString()

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
}
