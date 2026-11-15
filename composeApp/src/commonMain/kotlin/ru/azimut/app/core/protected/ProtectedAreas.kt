package ru.azimut.app.core.protected

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.platform.PlatformFiles

/** One polygon of a protected area; rings[0] is the outline, the rest are holes. */
@Serializable
data class ProtectedPolygon(
    /** Ring = list of [lon, lat] pairs (GeoJSON order). */
    val rings: List<List<List<Double>>>,
    val name: String = "",
    val protection: String = "",
    val access: String = "",
    val owner: String = "",
    val source: String = "",
    /** lonMin, latMin, lonMax, latMax for a cheap bbox pre-filter. */
    val lonMin: Double = 0.0,
    val latMin: Double = 0.0,
    val lonMax: Double = 0.0,
    val latMax: Double = 0.0,
) {
    val bboxHit: (Double, Double) -> Boolean
        get() = { lat, lon -> lon in lonMin..lonMax && lat in latMin..latMax }
}

@Serializable
data class ProtectedAreaFile(
    val fileName: String,
    val importedAt: Long = 0,
    val source: String = "",
    val polygons: List<ProtectedPolygon> = emptyList(),
)

enum class ProtectedImportResult { OK, BAD_FILE, NO_SPACE, EMPTY }

/**
 * A segment/point position relative to one zone.
 * [inside] covers both fully-inside and crossing: any contact is what matters.
 */
data class ZoneHit(val file: ProtectedAreaFile, val polygon: ProtectedPolygon)

/** GeoJSON import + storage of the protected-areas layer (NavData/protected/). */
object ProtectedAreas {
    const val DIR = "protected"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun list(): List<ProtectedAreaFile> = PlatformFiles.listFiles(DIR)
        .filter { it.endsWith(".json", ignoreCase = true) && it != "state.json" }
        .mapNotNull { name ->
            val text = PlatformFiles.readText("$DIR/$name") ?: return@mapNotNull null
            runCatching { json.decodeFromString<ProtectedAreaFile>(text.removePrefix("\uFEFF")) }.getOrNull()
        }

    fun allPolygons(): List<ProtectedPolygon> = list().flatMap { it.polygons }

    fun remove(fileName: String): Boolean = PlatformFiles.delete("$DIR/$fileName")

    /** Parses a GeoJSON document and stores it. Skips non-polygon features gracefully. */
    fun import(fileName: String, geoJson: String, sourceLabel: String): ProtectedImportResult {
        val safe = fileName.substringAfterLast('/').substringAfterLast('\\')
            .let { if (it.endsWith(".json", true)) it else "$it.json" }
        val polygons = runCatching { parseGeoJson(geoJson, sourceLabel) }.getOrDefault(emptyList())
        if (polygons.isEmpty()) return ProtectedImportResult.BAD_FILE
        val free = PlatformFiles.freeSpaceBytes()
        if (free in 0 until geoJson.length.toLong()) return ProtectedImportResult.NO_SPACE
        val existing = list().firstOrNull { it.fileName == safe }
        val file = ProtectedAreaFile(
            fileName = safe,
            importedAt = System.currentTimeMillis(),
            source = sourceLabel,
            polygons = polygons,
        )
        return runCatching {
            PlatformFiles.writeText("$DIR/$safe", json.encodeToString(file))
            if (existing != null && existing.fileName != safe) PlatformFiles.delete("$DIR/${existing.fileName}")
            ProtectedImportResult.OK
        }.getOrDefault(ProtectedImportResult.BAD_FILE)
    }

    /** Parses FeatureCollection/Feature/Polygon/MultiPolygon; bad geometries are skipped. */
    fun parseGeoJson(geoJson: String, sourceLabel: String): List<ProtectedPolygon> {
        val root = runCatching { Json.parseToJsonElement(geoJson) }.getOrNull() as? JsonObject ?: return emptyList()
        val out = mutableListOf<ProtectedPolygon>()
        val geometryLists = mutableListOf<Pair<JsonElement, JsonObject>>() // geometry + properties
        when (root["type"]?.jsonPrimitive?.contentOrNullSafe()) {
            "FeatureCollection" -> {
                val features = root["features"] as? JsonArray ?: return emptyList()
                for (f in features.jsonArray) {
                    val obj = f as? JsonObject ?: continue
                    geometryLists.add((obj["geometry"] ?: continue) to (obj["properties"] as? JsonObject ?: JsonObject(emptyMap())))
                }
            }
            "Feature" -> geometryLists.add((root["geometry"] ?: return emptyList()) to (root["properties"] as? JsonObject ?: JsonObject(emptyMap())))
            "Polygon", "MultiPolygon" -> geometryLists.add(root to JsonObject(emptyMap()))
        }
        for ((geometry, props) in geometryLists) {
            when (geometry.jsonObject["type"]?.jsonPrimitive?.contentOrNullSafe()) {
                "Polygon" -> geometry.jsonObject["coordinates"]?.jsonArray?.let { rings ->
                    val parsed = parseRings(rings)
                    if (parsed != null) out.add(build(parsed, props, sourceLabel))
                }
                "MultiPolygon" -> geometry.jsonObject["coordinates"]?.jsonArray?.forEach { polyEl ->
                    val poly = polyEl as? JsonArray ?: return@forEach
                    val parsed = parseRings(poly)
                    if (parsed != null) out.add(build(parsed, props, sourceLabel))
                }
            }
        }
        return out
    }

    private fun build(rings: List<List<List<Double>>>, props: JsonObject, sourceLabel: String): ProtectedPolygon {
        var lonMin = 180.0; var latMin = 90.0; var lonMax = -180.0; var latMax = -90.0
        for (ring in rings) for (p in ring) {
            if (p[0] < lonMin) lonMin = p[0]
            if (p[0] > lonMax) lonMax = p[0]
            if (p[1] < latMin) latMin = p[1]
            if (p[1] > latMax) latMax = p[1]
        }
        return ProtectedPolygon(
            rings = rings,
            name = props.string("name") ?: "",
            protection = props.string("protection") ?: props.string("type") ?: props.string("protect_class") ?: "",
            access = props.string("access") ?: "",
            owner = props.string("owner") ?: "",
            source = props.string("source") ?: sourceLabel,
            lonMin = lonMin, latMin = latMin, lonMax = lonMax, latMax = latMax,
        )
    }

    private fun parseRings(coordinates: JsonArray): List<List<List<Double>>>? {
        val rings = mutableListOf<List<List<Double>>>()
        for (ringEl in coordinates) {
            val ring = (ringEl as? JsonArray) ?: return null
            val points = mutableListOf<List<Double>>()
            for (ptEl in ring.jsonArray) {
                val pt = (ptEl as? JsonArray) ?: return null
                val list = pt.jsonArray
                if (list.size < 2) return null
                val lon = list[0].jsonPrimitive.doubleOrNullSafe() ?: return null
                val lat = list[1].jsonPrimitive.doubleOrNullSafe() ?: return null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
                points.add(listOf(lon, lat))
            }
            if (points.size >= 4) rings.add(points) // degenerate rings are skipped
        }
        return if (rings.isEmpty()) null else rings
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

    private fun JsonElement?.contentOrNullSafe(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    private fun kotlinx.serialization.json.JsonPrimitive.doubleOrNullSafe(): Double? = runCatching { content.toDoubleOrNull() }.getOrNull()
}

/** Geometry checks against zone polygons. */
object ProtectedGeometry {
    /** Ray-casting point-in-polygon with holes: inside the outline and not inside a hole. */
    fun inside(polygon: ProtectedPolygon, lat: Double, lon: Double): Boolean {
        if (!polygon.bboxHit(lat, lon)) return false
        if (polygon.rings.isEmpty()) return false
        if (!ringInside(polygon.rings[0], lat, lon)) return false
        for (i in 1 until polygon.rings.size) {
            if (ringInside(polygon.rings[i], lat, lon)) return false
        }
        return true
    }

    private fun ringInside(ring: List<List<Double>>, lat: Double, lon: Double): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i][0]; val yi = ring[i][1]
            val xj = ring[j][0]; val yj = ring[j][1]
            val intersects = (yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-12) + xi
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * True when the segment (or its either end) touches the polygon: an endpoint inside,
     * or a proper crossing with the outline (holes included, since crossing a hole edge
     * from inside to outside is still leaving through a fence line).
     */
    fun segmentHits(polygon: ProtectedPolygon, a: GeoPoint, b: GeoPoint): Boolean {
        val lonMin = minOf(a.lon, b.lon); val lonMax = maxOf(a.lon, b.lon)
        val latMin = minOf(a.lat, b.lat); val latMax = maxOf(a.lat, b.lat)
        if (lonMax < polygon.lonMin || lonMin > polygon.lonMax) return false
        if (latMax < polygon.latMin || latMin > polygon.latMax) return false
        if (inside(polygon, a.lat, a.lon) || inside(polygon, b.lat, b.lon)) return true
        return polygon.rings.any { ring -> segmentIntersectsRing(ring, a, b) }
    }

    private fun segmentIntersectsRing(ring: List<List<Double>>, a: GeoPoint, b: GeoPoint): Boolean {
        for (i in 1 until ring.size) {
            if (segmentsCross(
                    a.lon, a.lat, b.lon, b.lat,
                    ring[i - 1][0], ring[i - 1][1], ring[i][0], ring[i][1],
                )
            ) return true
        }
        return false
    }

    /** Standard orientation-based segment intersection, degenerate collinear overlaps count. */
    private fun segmentsCross(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        if (d1 == 0.0 && onSegment(cx, cy, dx, dy, ax, ay)) return true
        if (d2 == 0.0 && onSegment(cx, cy, dx, dy, bx, by)) return true
        if (d3 == 0.0 && onSegment(ax, ay, bx, by, cx, cy)) return true
        if (d4 == 0.0 && onSegment(ax, ay, bx, by, dx, dy)) return true
        return false
    }

    private fun cross(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double =
        (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

    private fun onSegment(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double): Boolean =
        px in minOf(ax, bx)..maxOf(ax, bx) && py in minOf(ay, by)..maxOf(ay, by)
}

/**
 * Episode watch: fires once per zone while the position stays in contact, resets
 * when the user leaves every zone. Returns the zone just entered, if any.
 */
class ProtectedWatch(private val polygons: List<ProtectedPolygon>) {
    private val alerted = HashSet<ProtectedPolygon>()

    fun reset() {
        alerted.clear()
    }

    /** Returns a zone the position just entered (not alerted before in this episode). */
    fun onPosition(lat: Double, lon: Double): ZoneHit? {
        var contact: ProtectedPolygon? = null
        for (polygon in polygons) {
            if (ProtectedGeometry.inside(polygon, lat, lon)) {
                contact = polygon
                break
            }
        }
        // Outside every zone: the episode ends, a later re-entry may alert again.
        val zone = contact ?: run {
            alerted.clear()
            return null
        }
        if (zone in alerted) return null
        alerted.add(zone)
        return ZoneHit(ProtectedAreaFile("watch", source = zone.source), zone)
    }

    /**
     * Checks a planned line against all zones; returns every zone touched (deduped).
     * For long tracks, samples every [sampleEveryNth] point to keep it cheap.
     */
    fun onRoute(points: List<GeoPoint>, sampleEveryNth: Int = 1): List<ZoneHit> {
        // Keyed by polygon index: `polygon in hits` would deep-compare all rings
        // (equals/hashCode) on every sampled point of every zone.
        val hits = LinkedHashMap<Int, ZoneHit>()
        for ((index, point) in points.withIndex()) {
            if (index % sampleEveryNth != 0) continue
            val prev = points.getOrNull(index - 1)
            for ((pi, polygon) in polygons.withIndex()) {
                if (pi in hits) continue
                val touched = ProtectedGeometry.inside(polygon, point.lat, point.lon) ||
                    (prev != null && ProtectedGeometry.segmentHits(polygon, prev, point))
                if (touched) hits[pi] = ZoneHit(ProtectedAreaFile("plan", source = polygon.source), polygon)
            }
        }
        return hits.values.toList()
    }
}
