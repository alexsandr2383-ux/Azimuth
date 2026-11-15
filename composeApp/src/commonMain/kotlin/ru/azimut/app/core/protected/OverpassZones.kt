package ru.azimut.app.core.protected

import ru.azimut.app.core.platform.PlatformHttp

/**
 * Downloads protected-area polygons from the public Overpass API (OpenStreetMap):
 * boundary=protected_area / national_park and leisure=nature_reserve, as GeoJSON.
 * The user initiates every download (offline-first); OSM completeness is honest
 * — what OSM does not have, this layer will not show.
 */
object OverpassZones {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    enum class Outcome { OK, EMPTY, NO_NET, SERVER_BUSY, FAILED }

    data class Result(val outcome: Outcome, val zones: Int)

    /**
     * Fetches zones around [lat]/[lon] for a +- [spanDeg] box. Closed ways become
     * polygons; multipolygon relations are not assembled (OSM complexity) — the
     * result may under-represent very large parks.
     */
    suspend fun fetchAround(lat: Double, lon: Double, spanDeg: Double = 0.5): Result {
        val latMin = (lat - spanDeg).coerceIn(-89.0, 89.0)
        val latMax = (lat + spanDeg).coerceIn(-89.0, 89.0)
        val lonMin = (lon - spanDeg).coerceIn(-179.0, 179.0)
        val lonMax = (lon + spanDeg).coerceIn(-179.0, 179.0)
        val query = "[out:geojson][timeout:25];" +
            "(" +
            "way[\"boundary\"=\"protected_area\"]($latMin,$lonMin,$latMax,$lonMax);" +
            "way[\"boundary\"=\"national_park\"]($latMin,$lonMin,$latMax,$lonMax);" +
            "way[\"leisure\"=\"nature_reserve\"]($latMin,$lonMin,$latMax,$lonMax);" +
            ");out geom;"
        val resp = PlatformHttp.request(
            "POST",
            ENDPOINT,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            "data=${formEncode(query)}".toByteArray(Charsets.UTF_8),
        ) ?: return Result(Outcome.NO_NET, 0)
        if (resp.status == 429 || resp.status == 504) return Result(Outcome.SERVER_BUSY, 0)
        if (resp.status !in 200..299) return Result(Outcome.FAILED, 0)
        val geoJson = resp.body.toString(Charsets.UTF_8)
        val polygons = runCatching { ProtectedAreas.parseGeoJson(geoJson, "Overpass / OpenStreetMap") }
            .getOrDefault(emptyList())
        if (polygons.isEmpty()) return Result(Outcome.EMPTY, 0)
        val fileName = "osm-zones-" + System.currentTimeMillis() + ".json"
        val stored = ProtectedAreas.import(fileName, geoJson, "Overpass / OpenStreetMap © OpenStreetMap contributors")
        return when (stored) {
            ProtectedImportResult.OK -> Result(Outcome.OK, polygons.size)
            ProtectedImportResult.NO_SPACE -> Result(Outcome.FAILED, 0)
            else -> Result(Outcome.FAILED, 0)
        }
    }

    /** Percent-encoding for form bodies (UTF-8), space as +. */
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
