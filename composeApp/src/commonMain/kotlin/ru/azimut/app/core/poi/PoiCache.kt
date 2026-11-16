package ru.azimut.app.core.poi

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.azimut.app.core.model.PoiTypes
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.platform.PlatformHttp

/**
 * Offline POI cache for downloaded regions: Overpass nodes (named, in the app's
 * POI categories) stored per region as JSON in NavData/poi/. Regions are fetched
 * together with an offline map download or explicitly from the menu; with no
 * network the cache still answers (offline-first).
 */
object PoiCache {
    private const val DIR = "poi"
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Entry(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val category: String,
    )

    @Serializable
    data class Region(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double,
        val createdAt: Long,
        val points: List<Entry>,
    )

    enum class Outcome { OK, EMPTY, NO_NET, SERVER_BUSY, FAILED }
    data class Result(val outcome: Outcome, val count: Int)

    private var cache: List<Entry>? = null

    private fun dirExists(): Boolean = PlatformFiles.listFiles(DIR).isNotEmpty()

    /** All cached POI entries across regions (parsed once per file set). */
    fun all(): List<Entry> {
        cache?.let { return it }
        if (!dirExists()) return emptyList()
        val entries = PlatformFiles.listFiles(DIR)
            .filter { it.endsWith(".json") }
            .flatMap { file ->
                runCatching {
                    json.decodeFromString(Region.serializer(), PlatformFiles.readText("$DIR/$file") ?: "").points
                }.getOrDefault(emptyList())
            }
        cache = entries
        return entries
    }

    private fun invalidate() {
        cache = null
    }

    fun search(query: String): List<Entry> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return all().filter { it.name.lowercase().contains(q) }.take(20)
    }

    fun asWaypoints(): List<Waypoint> = all().map {
        Waypoint(
            id = it.id,
            name = it.name,
            description = "",
            lat = it.lat,
            lon = it.lon,
            createdAt = 0,
            category = it.category,
            isPoi = true,
        )
    }

    fun entryToWaypoint(entry: Entry) = Waypoint(
        id = entry.id,
        name = entry.name,
        description = "",
        lat = entry.lat,
        lon = entry.lon,
        createdAt = 0,
        category = entry.category,
        isPoi = true,
    )

    /**
     * Downloads named POI nodes for the bbox from Overpass and appends them as a
     * new region file. Only known categories are kept; unnamed nodes are skipped.
     */
    suspend fun fetchRegion(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
    ): Result {
        val lMin = latMin.coerceIn(-89.0, 89.0)
        val lMax = latMax.coerceIn(-89.0, 89.0)
        val gMin = lonMin.coerceIn(-179.0, 179.0)
        val gMax = lonMax.coerceIn(-179.0, 179.0)
        val bbox = "$lMin,$gMin,$lMax,$gMax"
        val query = "[out:json][timeout:60];" +
            "(" +
            "node[\"amenity\"][\"name\"]($bbox);" +
            "node[\"shop\"][\"name\"]($bbox);" +
            "node[\"tourism\"][\"name\"]($bbox);" +
            "node[\"natural\"][\"name\"]($bbox);" +
            "node[\"man_made\"][\"name\"]($bbox);" +
            "node[\"waterway\"=\"waterfall\"][\"name\"]($bbox);" +
            ");out body;"
        val resp = PlatformHttp.request(
            "POST",
            ENDPOINT,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            "data=${formEncode(query)}".toByteArray(Charsets.UTF_8),
        ) ?: return Result(Outcome.NO_NET, 0)
        if (resp.status == 429 || resp.status == 504) return Result(Outcome.SERVER_BUSY, 0)
        if (resp.status !in 200..299) return Result(Outcome.FAILED, 0)

        val entries = runCatching { parseOverpass(resp.body.toString(Charsets.UTF_8)) }.getOrDefault(emptyList())
        if (entries.isEmpty()) return Result(Outcome.EMPTY, 0)
        val region = Region(lMin, gMin, lMax, gMax, System.currentTimeMillis(), entries)
        PlatformFiles.ensureDirs()
        PlatformFiles.writeText("$DIR/poi-${region.createdAt}.json", json.encodeToString(Region.serializer(), region))
        invalidate()
        return Result(Outcome.OK, entries.size)
    }

    fun clearAll() {
        PlatformFiles.listFiles(DIR).forEach { PlatformFiles.delete("$DIR/$it") }
        invalidate()
    }

    /** Parses Overpass JSON `elements` into named, categorised entries. */
    internal fun parseOverpass(body: String): List<Entry> {
        val result = mutableListOf<Entry>()
        val elementRegex = Regex("\\{[^{}]*\"type\"\\s*:\\s*\"node\"[^{}]*\\}")
        val latRe = Regex("\"lat\"\\s*:\\s*(-?[0-9.]+)")
        val lonRe = Regex("\"lon\"\\s*:\\s*(-?[0-9.]+)")
        val idRe = Regex("\"id\"\\s*:\\s*([0-9]+)")
        val nameRe = Regex("\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val tagRe = Regex("\"([a-z_]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        for (m in elementRegex.findAll(body)) {
            val block = m.value
            val lat = latRe.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val lon = lonRe.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val id = idRe.find(block)?.groupValues?.get(1) ?: continue
            val name = nameRe.find(block)?.groupValues?.get(1)?.replace("\\\"", "\"") ?: continue
            if (name.isBlank()) continue
            val tags = tagRe.findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
            val category = categorise(tags) ?: continue
            result.add(Entry("poi-osm-$id", name, lat, lon, category))
        }
        return result.distinctBy { it.id }
    }

    /** OSM tag set → one of the app's POI categories; null = not worth showing. */
    internal fun categorise(tags: Map<String, String>): String? {
        val amenity = tags["amenity"]
        val shop = tags["shop"]
        val tourism = tags["tourism"]
        val natural = tags["natural"]
        val manMade = tags["man_made"]
        val waterway = tags["waterway"]
        return when {
            amenity in setOf("cafe", "restaurant", "fast_food", "food_court", "pub", "bar") -> PoiTypes.FOOD
            shop != null && shop in setOf(
                "supermarket", "convenience", "greengrocer", "bakery", "department_store",
                "kiosk", "mall", "butcher", "alcohol", "beverages",
            ) -> PoiTypes.FOOD
            amenity in setOf("fuel", "charging_station") -> PoiTypes.FUEL
            amenity in setOf("pharmacy", "hospital", "clinic", "doctors", "dentist") -> PoiTypes.MEDICAL
            amenity in setOf("parking", "bus_station", "bus_stop", "ferry_terminal") ||
                (amenity == "station" || tags["railway"] in setOf("station", "halt")) -> PoiTypes.TRANSPORT
            amenity in setOf("drinking_water", "water_point", "fountain") -> PoiTypes.WATER
            amenity == "shelter" -> PoiTypes.SHELTER
            tourism in setOf("hotel", "hostel", "motel", "guest_house", "camp_site", "caravan_site", "alpine_hut", "wilderness_hut") -> PoiTypes.CAMP
            tourism in setOf("attraction", "viewpoint", "museum", "monument", "artwork") -> PoiTypes.SIGHT
            natural in setOf("spring", "water") -> PoiTypes.WATER
            natural == "waterfall" || waterway == "waterfall" -> PoiTypes.SIGHT
            manMade == "water_well" || manMade == "adit" -> if (manMade == "water_well") PoiTypes.WATER else PoiTypes.OTHER
            amenity == "place_of_worship" -> PoiTypes.SIGHT
            else -> null
        }
    }

    private fun formEncode(text: String): String = buildString {
        text.toByteArray(Charsets.UTF_8).forEach { b ->
            val v = b.toInt() and 0xFF
            when {
                v in '0'.code..'9'.code || v in 'A'.code..'Z'.code || v in 'a'.code..'z'.code ||
                    v == '-'.code || v == '_'.code || v == '.'.code || v == '*'.code ->
                    append(v.toChar())
                v == ' '.code -> append('+')
                else -> append("%%%02X".format(v))
            }
        }
    }
}
