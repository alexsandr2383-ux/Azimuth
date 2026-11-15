package ru.azimut.app.core.import

/** Small helpers shared by the auto-scan flow. */
object TrackImportDetect {
    /** GeoJSON that is primarily polygons belongs to the OOPT layer, not tracks. */
    fun isOopt(content: String): Boolean =
        content.contains("\"Polygon\"") || content.contains("\"MultiPolygon\"")
}
