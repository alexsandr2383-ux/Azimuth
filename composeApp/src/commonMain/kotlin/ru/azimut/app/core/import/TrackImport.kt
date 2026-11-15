package ru.azimut.app.core.import

import kotlinx.datetime.Clock
import ru.azimut.app.core.gpx.GpxImportOutcome
import ru.azimut.app.core.gpx.GpxImporter
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.distanceMeters
import ru.azimut.app.core.protected.ProtectedAreas
import ru.azimut.app.core.protected.ProtectedImportResult
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unified multi-format track import: GPX, KML, KMZ (unzipped by the caller),
 * GeoJSON (tracks/routes as LineStrings), TCX. One outcome shape for the UI.
 */
object TrackImport {
    enum class Kind { GPX, KML, KMZ, GEOJSON, TCX, OOPT_GEOJSON, UNKNOWN }

    /** Detects the format from extension first, then content. */
    fun detect(fileName: String, content: String): Kind {
        val lower = fileName.lowercase().substringAfterLast('/')
        return when {
            lower.endsWith(".gpx") -> Kind.GPX
            lower.endsWith(".kml") -> Kind.KML
            lower.endsWith(".kmz") -> Kind.KMZ
            lower.endsWith(".tcx") -> Kind.TCX
            lower.endsWith(".geojson") || lower.endsWith(".json") -> {
                if (content.contains("\"LineString\"") || content.contains("\"Track\"") ||
                    content.contains("\"MultiLineString\"") ||
                    content.contains("\"Polygon\"") || content.contains("\"MultiPolygon\"")
                ) {
                    if (content.contains("\"Polygon\"") || content.contains("\"MultiPolygon\"")) {
                        Kind.OOPT_GEOJSON
                    } else {
                        Kind.GEOJSON
                    }
                } else {
                    Kind.UNKNOWN
                }
            }
            content.contains("<gpx", ignoreCase = true) -> Kind.GPX
            content.contains("<kml", ignoreCase = true) -> Kind.KML
            content.contains("<TrainingCenterDatabase", ignoreCase = true) -> Kind.TCX
            else -> Kind.UNKNOWN
        }
    }

    data class Parsed(val outcome: GpxImportOutcome, val tracks: List<Track>, val waypoints: List<Waypoint>)

    fun parse(fileName: String, content: String, fallbackProfile: String): Parsed = when (detect(fileName, content)) {
        Kind.GPX -> GpxImporter.parse(fileName, content, fallbackProfile).let {
            Parsed(it.outcome, it.tracks, it.waypoints)
        }
        Kind.KML -> parseKml(fileName, content, fallbackProfile)
        Kind.TCX -> parseTcx(fileName, content, fallbackProfile)
        Kind.GEOJSON -> parseGeoJsonTracks(fileName, content, fallbackProfile)
        Kind.KMZ, Kind.OOPT_GEOJSON, Kind.UNKNOWN -> Parsed(GpxImportOutcome(failed = true), emptyList(), emptyList())
    }

    // ---- KML ----

    private fun parseKml(fileName: String, xml: String, fallbackProfile: String): Parsed {
        val doc = runCatching { KmlDoc.parse(xml) }.getOrNull() ?: return Parsed(GpxImportOutcome(failed = true), emptyList(), emptyList())
        val sourceId = "kml-${Clock.System.now().toEpochMilliseconds()}"
        val tracks = doc.tracks.mapIndexed { index, pts ->
            trackFromPoints(pts, "${fileName.substringBeforeLast('.')}-${index + 1}", sourceId, index, fallbackProfile)
        }.filterNotNull()
        val waypoints = doc.places.mapIndexed { index, place ->
            Waypoint(
                id = "kmlw-$sourceId-$index",
                name = place.name,
                description = place.description,
                lat = place.lat,
                lon = place.lon,
                altitude = place.altitude,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                kind = "imported",
                isPoi = true,
            )
        }
        val outcome = GpxImportOutcome(
            tracks = tracks.size,
            waypoints = waypoints.size,
            empty = tracks.isEmpty() && waypoints.isEmpty(),
        )
        return Parsed(outcome, tracks, waypoints)
    }

    private fun trackFromPoints(
        pts: List<KmlPt>,
        name: String,
        sourceId: String,
        index: Int,
        fallbackProfile: String,
    ): Track? {
        if (pts.size < 2) return null
        val points = pts.map { TrackPoint(lat = it.lat, lon = it.lon, altitude = it.altitude, timeMillis = 0L) }
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += distanceMeters(
                ru.azimut.app.core.model.GeoPoint(points[i - 1].lat, points[i - 1].lon),
                ru.azimut.app.core.model.GeoPoint(points[i].lat, points[i].lon),
            )
        }
        return Track(
            id = "$sourceId-$index",
            name = name,
            profile = fallbackProfile,
            startedAt = Clock.System.now().toEpochMilliseconds(),
            points = points,
            distanceMeters = distance,
        )
    }

    private class KmlPt(val lat: Double, val lon: Double, val altitude: Double?)
    private class KmlPlace(val name: String, val description: String, val lat: Double, val lon: Double, val altitude: Double?)
    private class KmlDoc(val tracks: List<List<KmlPt>>, val places: List<KmlPlace>) {
        companion object {
            fun parse(xml: String): KmlDoc {
                val tracks = mutableListOf<List<KmlPt>>()
                Regex("<LineString\\b[^>]*>(.*?)</LineString>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml)
                    .forEach { m ->
                        val coords = tag(m.groupValues[1], "coordinates") ?: return@forEach
                        val pts = parseCoords(coords)
                        if (pts.size >= 2) tracks.add(pts)
                    }
                Regex("<(?:gx:)?Track\\b[^>]*>(.*?)</(?:gx:)?Track>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml) // gx:Track from Google Earth recordings (and plain <Track>)
                    .forEach { m ->
                        // Every gx:coord holds one "lon lat [alt]" triple; scanning the
                        // whole group would pair coordinates across adjacent elements.
                        val coords = Regex("<gx:coord[^>]*>([\\s\\S]*?)</gx:coord>", RegexOption.IGNORE_CASE)
                            .findAll(m.groupValues[1])
                            .mapNotNull { c ->
                                val nums = Regex("([\\-0-9.eE+]+)\\s+([\\-0-9.eE+]+)(?:\\s+([\\-0-9.eE+]+))?")
                                    .find(c.groupValues[1]) ?: return@mapNotNull null
                                KmlPt(
                                    nums.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null,
                                    nums.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null,
                                    nums.groupValues[3]?.toDoubleOrNull(),
                                )
                            }
                            .toList()
                        if (coords.size >= 2) tracks.add(coords)
                    }
                val places = mutableListOf<KmlPlace>()
                Regex("<Placemark\\b[^>]*>(.*?)</Placemark>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml)
                    .forEach { m ->
                        val body = m.groupValues[1]
                        // Only placemarks with a Point geometry are waypoints; lines become tracks.
                        if (body.contains("<Point")) {
                            val coords = tag(body, "coordinates") ?: return@forEach
                            val pt = parseCoords(coords).firstOrNull() ?: return@forEach
                            places.add(
                                KmlPlace(
                                    tag(body, "name")?.let(::unescape) ?: "Точка",
                                    tag(body, "description")?.let(::unescape).orEmpty(),
                                    pt.lat,
                                    pt.lon,
                                    pt.altitude,
                                )
                            )
                        }
                    }
                return KmlDoc(tracks, places)
            }

            private fun parseCoords(raw: String): List<KmlPt> = raw.trim()
                .split(Regex("\\s+"))
                .mapNotNull { triple ->
                    val parts = triple.split(',')
                    if (parts.size < 2) return@mapNotNull null
                    val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                    val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                    KmlPt(lat, lon, parts.getOrNull(2)?.toDoubleOrNull())
                }

            private fun tag(xml: String, name: String): String? {
                val open = "<$name"
                val start = xml.indexOf(open, ignoreCase = true)
                if (start < 0) return null
                val openEnd = xml.indexOf('>', start)
                if (openEnd < 0) return null
                val closeStart = xml.indexOf("</$name>", openEnd, ignoreCase = true)
                if (closeStart < 0) return null
                return xml.substring(openEnd + 1, closeStart).trim()
            }

            private fun unescape(s: String): String = s
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }

    // ---- TCX ----

    private fun parseTcx(fileName: String, xml: String, fallbackProfile: String): Parsed {
        val sourceId = "tcx-${Clock.System.now().toEpochMilliseconds()}"
        val tracks = mutableListOf<Track>()
        Regex("<Activity\\b[^>]*>(.*?)</Activity>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .forEachIndexed { activityIndex, m ->
                val body = m.groupValues[1]
                val sport = Regex("Sport=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    ?: fallbackProfile
                val pts = mutableListOf<TrackPoint>()
                Regex("<Trackpoint\\b[^>]*>(.*?)</Trackpoint>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(body)
                    .forEach { p ->
                        val pb = p.groupValues[1]
                        val lat = tag(pb, "LatitudeDegrees")?.toDoubleOrNull() ?: return@forEach
                        val lon = tag(pb, "LongitudeDegrees")?.toDoubleOrNull() ?: return@forEach
                        val alt = tag(pb, "AltitudeMeters")?.toDoubleOrNull()
                        val time = tag(pb, "Time")
                        pts.add(
                            TrackPoint(
                                lat = lat,
                                lon = lon,
                                altitude = alt,
                                timeMillis = time?.let(::parseIso) ?: 0L,
                            )
                        )
                    }
                if (pts.size >= 2) {
                    var distance = 0.0
                    for (i in 1 until pts.size) {
                        distance += distanceMeters(
                            ru.azimut.app.core.model.GeoPoint(pts[i - 1].lat, pts[i - 1].lon),
                            ru.azimut.app.core.model.GeoPoint(pts[i].lat, pts[i].lon),
                        )
                    }
                    tracks.add(
                        Track(
                            id = "$sourceId-$activityIndex",
                            name = "${fileName.substringBeforeLast('.')} ${activityIndex + 1}",
                            profile = normalizeSport(sport, fallbackProfile),
                            startedAt = pts.firstOrNull { it.timeMillis > 0 }?.timeMillis
                                ?: Clock.System.now().toEpochMilliseconds(),
                            points = pts,
                            distanceMeters = distance,
                        )
                    )
                }
            }
        val outcome = GpxImportOutcome(tracks = tracks.size, empty = tracks.isEmpty())
        return Parsed(outcome, tracks, emptyList())
    }

    private fun tag(xml: String, name: String): String? {
        val start = xml.indexOf("<$name", ignoreCase = true)
        if (start < 0) return null
        val openEnd = xml.indexOf('>', start)
        if (openEnd < 0) return null
        val closeStart = xml.indexOf("</$name>", openEnd, ignoreCase = true)
        if (closeStart < 0) return null
        return xml.substring(openEnd + 1, closeStart).trim()
    }

    private fun parseIso(raw: String): Long = runCatching {
        kotlinx.datetime.Instant.parse(raw.trim()).toEpochMilliseconds()
    }.getOrDefault(0L)

    private fun normalizeSport(sport: String, fallback: String): String = when {
        sport.contains("hiking", true) || sport.contains("walk", true) -> "foot"
        sport.contains("biking", true) || sport.contains("cycl", true) -> "bike"
        sport.contains("run", true) -> "foot"
        sport.contains("motor", true) -> "motorcycle"
        else -> fallback
    }

    // ---- GeoJSON LineStrings (tracks/routes) ----

    private fun parseGeoJsonTracks(fileName: String, geoJson: String, fallbackProfile: String): Parsed {
        val sourceId = "gj-${Clock.System.now().toEpochMilliseconds()}"
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(geoJson) }.getOrNull()
            ?: return Parsed(GpxImportOutcome(failed = true), emptyList(), emptyList())
        val lines = mutableListOf<Triple<List<List<Double>>, String, Int>>() // coords, name, index
        fun collectLineString(coords: kotlinx.serialization.json.JsonArray, name: String): Boolean {
            val pts = mutableListOf<List<Double>>()
            for (p in coords) {
                val arr = (p as? kotlinx.serialization.json.JsonArray) ?: return false
                if (arr.size < 2) return false
                val lon = arr[0].doubleOrNull() ?: return false
                val lat = arr[1].doubleOrNull() ?: return false
                pts.add(listOf(lon, lat, arr.getOrNull(2)?.doubleOrNull() ?: Double.NaN))
            }
            if (pts.size >= 2) {
                lines.add(Triple(pts, name, lines.size))
                return true
            }
            return false
        }

        val obj = root as? kotlinx.serialization.json.JsonObject
        when (obj?.get("type")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }) {
            "FeatureCollection" -> (obj["features"] as? kotlinx.serialization.json.JsonArray)?.forEach { f ->
                val fo = f as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val geom = fo["geometry"] as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val name = (fo["properties"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?: fileName.substringBeforeLast('.')
                when ((geom["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content) {
                    "LineString" -> (geom["coordinates"] as? kotlinx.serialization.json.JsonArray)?.let {
                        collectLineString(it, name)
                    }
                    "MultiLineString" -> (geom["coordinates"] as? kotlinx.serialization.json.JsonArray)?.forEach {
                        (it as? kotlinx.serialization.json.JsonArray)?.let { line -> collectLineString(line, name) }
                    }
                }
            }
            "LineString" -> (obj["coordinates"] as? kotlinx.serialization.json.JsonArray)?.let {
                collectLineString(it, fileName.substringBeforeLast('.'))
            }
        }

        val tracks = lines.mapNotNull { (coords, name, index) ->
            val pts = coords.mapNotNull { c ->
                val alt = if (c.size > 2 && !c[2].isNaN()) c[2] else null
                TrackPoint(lat = c[1], lon = c[0], altitude = alt, timeMillis = 0L)
            }
            if (pts.size < 2) return@mapNotNull null
            var distance = 0.0
            for (i in 1 until pts.size) {
                distance += distanceMeters(
                    ru.azimut.app.core.model.GeoPoint(pts[i - 1].lat, pts[i - 1].lon),
                    ru.azimut.app.core.model.GeoPoint(pts[i].lat, pts[i].lon),
                )
            }
            Track(
                id = "$sourceId-$index",
                name = name,
                profile = fallbackProfile,
                startedAt = Clock.System.now().toEpochMilliseconds(),
                points = pts,
                distanceMeters = distance,
            )
        }
        val outcome = GpxImportOutcome(tracks = tracks.size, empty = tracks.isEmpty())
        return Parsed(outcome, tracks, emptyList())
    }

    private fun kotlinx.serialization.json.JsonElement.doubleOrNull(): Double? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.let { runCatching { it.content.toDoubleOrNull() }.getOrNull() }

    /** OOPT GeoJSON goes to the protected layer instead of tracks. */
    fun importOopt(fileName: String, geoJson: String): ProtectedImportResult =
        ProtectedAreas.import(fileName, geoJson, sourceLabel = fileName.substringBeforeLast('.'))
}
