package ru.azimut.app.core.map

data class MapStyle(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    val tileUrl: String,
    val cacheLayer: String,
    val maxZoom: Int,
    val androidMaxZoom: Int,
    val attribution: String,
    val minTileBytes: Long,
    val bulkDownload: Boolean,
    val hasContours: Boolean = false,
    val underlayFromZoom: Int? = null,
    /** Absolute path of an imported .mbtiles file; such maps are offline and never downloaded. */
    val localPath: String? = null,
) {
    fun title(lang: String): String = if (lang == "en") titleEn else titleRu
}

object MapStyles {
    const val DEFAULT_ID = "esri_street"

    val satelliteOptions = listOf(
        "satellite_hd" to Pair("Esri World Imagery", "Esri World Imagery"),
        "satellite_esri" to Pair("Esri Satellite", "Esri Satellite"),
        "satellite_google" to Pair("Google Satellite", "Google Satellite"),
    )

    val all: List<MapStyle> get() = builtIn + UserXyz.asStyles() + UserMaps.styles

    private val builtIn = listOf(
        MapStyle(
            id = "esri_street",
            titleRu = "Esri Street",
            titleEn = "Esri Street",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
            cacheLayer = "street",
            maxZoom = 18,
            androidMaxZoom = 16,
            attribution = "Map © Esri, HERE, Garmin, OSM contributors",
            minTileBytes = 2800,
            bulkDownload = true,
            underlayFromZoom = 16,
        ),
        MapStyle(
            id = "esri_topo",
            titleRu = "Esri Topo",
            titleEn = "Esri Topo",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
            cacheLayer = "base_esri_topo",
            maxZoom = 18,
            androidMaxZoom = 16,
            attribution = "Map © Esri, HERE, Garmin, USGS, NGA",
            minTileBytes = 2800,
            bulkDownload = true,
            hasContours = true,
            underlayFromZoom = 16,
        ),
        MapStyle(
            id = "osm",
            titleRu = "OSM стандарт",
            titleEn = "OSM Standard",
            tileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            cacheLayer = "base_osm",
            maxZoom = 19,
            androidMaxZoom = 19,
            attribution = "© OpenStreetMap contributors",
            minTileBytes = 300,
            bulkDownload = false,
        ),
        MapStyle(
            id = "osm_hot",
            titleRu = "OSM Humanitarian",
            titleEn = "OSM Humanitarian",
            tileUrl = "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
            cacheLayer = "base_osm_hot",
            maxZoom = 19,
            androidMaxZoom = 19,
            attribution = "© OpenStreetMap contributors, HOT",
            minTileBytes = 300,
            bulkDownload = false,
        ),
        MapStyle(
            id = "cyclosm",
            titleRu = "CyclOSM",
            titleEn = "CyclOSM",
            tileUrl = "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png",
            cacheLayer = "base_cyclosm",
            maxZoom = 19,
            androidMaxZoom = 19,
            attribution = "© OpenStreetMap contributors, CyclOSM",
            minTileBytes = 300,
            bulkDownload = true,
        ),
        MapStyle(
            id = "opentopomap",
            titleRu = "OpenTopoMap",
            titleEn = "OpenTopoMap",
            tileUrl = "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
            cacheLayer = "base_opentopomap",
            maxZoom = 17,
            androidMaxZoom = 17,
            attribution = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
            minTileBytes = 500,
            bulkDownload = true,
            hasContours = true,
        ),
    )

    fun byId(id: String?): MapStyle = all.firstOrNull { it.id == id } ?: all.first()

    fun byCacheLayer(layer: String): MapStyle? = all.firstOrNull { it.cacheLayer == layer }

    fun title(style: MapStyle, lang: String): String = style.title(lang)

    fun url(style: MapStyle, z: Int, x: Int, y: Int): String = style.tileUrl
        .replace("{z}", z.toString())
        .replace("{x}", x.toString())
        .replace("{y}", y.toString())
}
