package ru.azimut.app.core.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.azimut.app.core.platform.PlatformFiles

data class MbTilesInfo(
    val name: String,
    /** png, jpg, webp — raster; pbf — vector (not supported yet). */
    val format: String,
    val minZoom: Int,
    val maxZoom: Int,
    val attribution: String,
) {
    val isRaster: Boolean get() = format != "pbf"

    companion object {
        fun from(metadata: Map<String, String>, fallbackName: String, minZoom: Int?, maxZoom: Int?): MbTilesInfo =
            MbTilesInfo(
                name = metadata["name"]?.takeIf { it.isNotBlank() } ?: fallbackName,
                format = metadata["format"]?.lowercase()?.takeIf { it.isNotBlank() } ?: "png",
                minZoom = metadata["minzoom"]?.toIntOrNull() ?: minZoom ?: 0,
                maxZoom = metadata["maxzoom"]?.toIntOrNull() ?: maxZoom ?: 18,
                attribution = metadata["attribution"].orEmpty(),
            )
    }
}

expect object MbTiles {
    /** Reads an .mbtiles file at an absolute [path]; null if it is not a readable MBTiles database. */
    fun readInfo(path: String): MbTilesInfo?

    /** Drops cached database handles before files are replaced (restore). */
    fun closeAll()
}

enum class MapImportResult { OK, NOT_MBTILES, VECTOR }

/** Raster .mbtiles maps imported into NavData/maps/, exposed as extra map styles. */
object UserMaps {
    const val DIR = "maps"
    const val PREFIX = "mbtiles:"

    private val _styles = MutableStateFlow<List<MapStyle>>(emptyList())
    val stylesFlow: StateFlow<List<MapStyle>> get() = _styles
    var styles: List<MapStyle>
        get() = _styles.value
        private set(v) { _styles.value = v }

    fun pathOf(fileName: String): String = "${PlatformFiles.navDataRoot()}/$DIR/$fileName"

    fun refresh() {
        styles = PlatformFiles.listFiles(DIR)
            .filter { it.endsWith(".mbtiles", ignoreCase = true) }
            .sortedBy { it.lowercase() }
            .mapNotNull { fileName ->
                val path = pathOf(fileName)
                val info = MbTiles.readInfo(path)?.takeIf { it.isRaster } ?: return@mapNotNull null
                MapStyle(
                    id = PREFIX + fileName,
                    titleRu = info.name,
                    titleEn = info.name,
                    tileUrl = "mbtiles://$path",
                    cacheLayer = PREFIX + fileName,
                    maxZoom = info.maxZoom,
                    androidMaxZoom = info.maxZoom,
                    attribution = info.attribution.ifBlank { info.name },
                    minTileBytes = 1,
                    bulkDownload = false,
                    localPath = path,
                )
            }
    }

    /** Checks a file just copied into maps/; unusable files are removed again. */
    fun checkImported(fileName: String): MapImportResult {
        val info = MbTiles.readInfo(pathOf(fileName))
        val result = when {
            info == null -> MapImportResult.NOT_MBTILES
            !info.isRaster -> MapImportResult.VECTOR
            else -> MapImportResult.OK
        }
        if (result != MapImportResult.OK) PlatformFiles.delete("$DIR/$fileName")
        return result
    }
}
