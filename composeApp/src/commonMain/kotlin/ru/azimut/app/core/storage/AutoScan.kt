package ru.azimut.app.core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.map.MapImportResult
import ru.azimut.app.core.map.MbTiles
import ru.azimut.app.core.map.TileDb
import ru.azimut.app.core.map.UserMaps
import ru.azimut.app.core.platform.PlatformFiles

@Serializable
private data class ScannedFile(val name: String, val fingerprint: String)

@Serializable
private data class ScanState(val files: List<ScannedFile> = emptyList())

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val TRACK_EXTS = listOf("gpx", "kml", "kmz", "tcx", "geojson", "json")
private val MAP_EXTS = listOf("mbtiles", "sqlitedb", "gpkg")
private val DEM_EXTS = listOf("hgt", "tif", "tiff")

/**
 * Imports new track/map/DEM files dropped into NavData/import/ and remembers
 * what was already seen. OOPT GeoJSON goes through the protected-areas layer.
 */
object AutoScan {
    private const val STATE_PATH = "import/autoscan.json"

    data class Pending(val name: String, val fingerprint: String)

    fun pending(): List<Pending> = runCatching {
        val known = loadState().files.associate { it.name to it.fingerprint }
        PlatformFiles.listFiles("import")
            .filter { name ->
                if (name == "autoscan.json") return@filter false // our own state file
                val ext = name.substringAfterLast('.', "").lowercase()
                ext in TRACK_EXTS || ext in MAP_EXTS || ext in DEM_EXTS
            }
            .mapNotNull { name ->
                val fingerprint = fingerprint(name) ?: return@mapNotNull null
                if (known[name] == fingerprint) null else Pending(name, fingerprint)
            }
            .sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun readText(name: String): String? = PlatformFiles.readText("import/$name")

    fun readBytes(name: String): ByteArray? = PlatformFiles.readBytes("import/$name")

    /** Copies an .mbtiles into maps/; false when it is not a usable raster map. */
    fun importMap(name: String): Boolean {
        MbTiles.closeAll()
        if (!PlatformFiles.copy("import/$name", "${UserMaps.DIR}/$name")) return false
        return UserMaps.checkImported(name) == MapImportResult.OK
    }

    /**
     * Converts a raster .sqlitedb/.gpkg from import/ into an .mbtiles in maps/.
     * RMaps databases are assumed TMS (flip off); GeoPackage rows are flipped by spec.
     */
    fun importTileDb(name: String): Boolean = runCatching {
        val base = name.substringBeforeLast('.')
        val sourcePath = "${PlatformFiles.navDataRoot()}/import/$name"
        val destPath = "${PlatformFiles.navDataRoot()}/${UserMaps.DIR}/$base.mbtiles"
        val flipped = PlatformFiles.exists("import/$name.flip") // manual override file
        val ok = TileDb.convertToMbtiles(sourcePath, destPath, flipY = flipped)
        if (ok) {
            val info = MbTiles.readInfo(destPath)
            if (info == null || !info.isRaster) {
                PlatformFiles.delete("${UserMaps.DIR}/$base.mbtiles")
                return false
            }
        }
        ok
    }.getOrDefault(false)

    fun mark(files: List<Pending>) {
        if (files.isEmpty()) return
        val merged = loadState().files.associateBy { it.name }.toMutableMap()
        files.forEach { merged[it.name] = ScannedFile(it.name, it.fingerprint) }
        runCatching {
            PlatformFiles.writeText(
                STATE_PATH,
                json.encodeToString(ScanState(merged.values.toList())),
            )
        }
    }

    private fun loadState(): ScanState {
        val text = PlatformFiles.readText(STATE_PATH) ?: return ScanState()
        return runCatching { json.decodeFromString<ScanState>(text.removePrefix("\uFEFF")) }
            .getOrDefault(ScanState())
    }

    private fun fingerprint(name: String): String? = when {
        name.endsWith(".kmz", ignoreCase = true) ||
            name.endsWith(".sqlitedb", ignoreCase = true) ||
            name.endsWith(".gpkg", ignoreCase = true) ||
            name.endsWith(".hgt", ignoreCase = true) ||
            name.endsWith(".tif", ignoreCase = true) ||
            name.endsWith(".tiff", ignoreCase = true) -> {
            val size = PlatformFiles.size("import/$name")
            if (size <= 0L) null else size.toString()
        }
        else -> {
            val text = PlatformFiles.readText("import/$name") ?: return null
            "${text.length}:${text.hashCode()}"
        }
    }
}
