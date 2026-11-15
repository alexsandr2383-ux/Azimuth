package ru.azimut.app.core.dem

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.platform.PlatformHttp
import kotlin.math.ceil
import kotlin.math.floor

enum class DemImportResult { OK, DUPLICATE, BAD_FILE, NO_SPACE, IO_ERROR }

/** A DEM download source; the built-ins are public Copernicus mirrors with no token. */
@Serializable
data class DemSourceDef(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    /**
     * {tile} -> N47E041, {lat},{lon} -> tile corner numbers. Empty = user-entered template
     * in settings (demSource == "custom").
     */
    val urlTemplate: String,
    val note: String = "",
) {
    fun title(lang: String): String = if (lang == "en") titleEn else titleRu
}

object DemSources {
    val builtIn = listOf(
        // Copernicus GLO-30 public S3 mirror, no registration; one GeoTIFF per tile.
        DemSourceDef(
            "glo30",
            "Copernicus GLO-30 (~30 м)",
            "Copernicus GLO-30 (~30 m)",
            "https://copernicus-dem-30m.s3.amazonaws.com/Copernicus_DSM_COG_10_{tile}_00_DEM/Copernicus_DSM_COG_10_{tile}_00_DEM.tif",
            "© ESA/Copernicus",
        ),
        DemSourceDef(
            "glo90",
            "Copernicus GLO-90 (~90 м)",
            "Copernicus GLO-90 (~90 m)",
            "https://copernicus-dem-90m.s3.amazonaws.com/Copernicus_DSM_COG_10_{tile}_00_DEM/Copernicus_DSM_COG_10_{tile}_00_DEM.tif",
            "© ESA/Copernicus",
        ),
    )

    fun byId(id: String): DemSourceDef? = builtIn.firstOrNull { it.id == id }

    /** Expands the tile placeholders. {tile} is N47E041 of the tile's NW corner. */
    fun urlFor(def: DemSourceDef, tile: String): String? = def.urlTemplate
        .takeIf { it.isNotBlank() }
        ?.replace("{tile}", tile)
}

/**
 * DEM storage/import: files land in NavData/dem, index json keeps a list of
 * what the user already has; parsing validates the copy right after it lands.
 */
object DemImport {
    const val DIR = "dem"
    private const val INDEX = "dem/index.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class DemEntry(val name: String, val addedAt: Long = 0, val sizeBytes: Long = 0)

    fun list(): List<DemEntry> {
        val text = PlatformFiles.readText(INDEX) ?: return emptyList()
        return runCatching { json.decodeFromString<List<DemEntry>>(text.removePrefix("\uFEFF")) }
            .getOrDefault(emptyList())
    }

    private fun saveIndex(entries: List<DemEntry>) {
        runCatching { PlatformFiles.writeText(INDEX, json.encodeToString(entries)) }
    }

    private fun remember(name: String) {
        val entries = list().filterNot { it.name == name } +
            DemEntry(name, System.currentTimeMillis(), PlatformFiles.size("$DIR/$name"))
        saveIndex(entries)
    }

    fun remove(name: String): Boolean {
        val ok = PlatformFiles.delete("$DIR/$name")
        if (ok) saveIndex(list().filterNot { it.name == name })
        return ok
    }

    /** Copies a picked file into dem/ and validates by parsing. Bad files are removed. */
    fun importFromBytes(name: String, bytes: ByteArray): DemImportResult {
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
        if (!safe.endsWith(".hgt", true) && !safe.endsWith(".tif", true) && !safe.endsWith(".tiff", true)) {
            return DemImportResult.BAD_FILE
        }
        if (PlatformFiles.exists("$DIR/$safe")) return DemImportResult.DUPLICATE
        val free = PlatformFiles.freeSpaceBytes()
        if (free in 0 until bytes.size.toLong()) return DemImportResult.NO_SPACE
        PlatformFiles.writeBytes("$DIR/$safe", bytes)
        val valid = runCatching {
            if (safe.endsWith(".hgt", true)) HgtReader.parse(safe, bytes) != null
            else GeoTiffReader.parse(bytes) != null
        }.getOrDefault(false)
        if (!valid) {
            PlatformFiles.delete("$DIR/$safe")
            return DemImportResult.BAD_FILE
        }
        remember(safe)
        return DemImportResult.OK
    }

    /** New DEM files dropped into NavData/import/ since the last autoscan run. */
    fun pendingImports(known: Map<String, String>): List<Pair<String, String>> =
        PlatformFiles.listFiles("import")
            .filter { it.endsWith(".hgt", true) || it.endsWith(".tif", true) || it.endsWith(".tiff", true) }
            .mapNotNull { name ->
                val fp = PlatformFiles.size("import/$name").toString()
                if (known[name] == fp) null else name to fp
            }

    fun moveImported(name: String): DemImportResult {
        val bytes = PlatformFiles.readBytes("import/$name") ?: return DemImportResult.IO_ERROR
        val result = importFromBytes(name, bytes)
        if (result == DemImportResult.OK || result == DemImportResult.DUPLICATE) {
            PlatformFiles.delete("import/$name")
        }
        return result
    }

    // ---- Area planning and download ----

    data class TilePlan(val tile: String, val url: String, val sizeBytes: Long)

    /** 1-degree tiles covering the bbox; size estimate 1.5 MB/tile for GLO-30, ~0.5 for GLO-90. */
    fun planTiles(source: DemSourceDef, customTemplate: String, latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): List<TilePlan> {
        val urlTemplate = if (source.id == "custom") customTemplate else source.urlTemplate
        if (urlTemplate.isBlank()) return emptyList()
        val perTile = if (source.id == "glo90") 500_000L else 1_500_000L
        val tiles = mutableListOf<TilePlan>()
        val latA = floor(latMin).toInt()
        val latB = floor(latMax).toInt()
        val lonA = floor(lonMin).toInt()
        val lonB = floor(lonMax).toInt()
        for (lat in latB downTo latA) { // SW labels descend
            for (lon in lonA..lonB) {
                val tile = DemIndex.tileOf(lat.toDouble() + 0.5, lon.toDouble() + 0.5)
                val def = if (source.id == "custom") source else source
                val url = (if (source.id == "custom") customTemplate else def.urlTemplate)
                    .takeIf { it.isNotBlank() }
                    ?.replace("{tile}", tile) ?: continue
                tiles.add(TilePlan(tile, url, perTile))
            }
        }
        return tiles
    }

    data class DownloadOutcome(val imported: Int, val failed: List<String>, val cancelled: Boolean, val offline: Boolean)

    /**
     * Downloads the tiles into dem/, skipping ones already present; resumes via the
     * existing-file size (Range) when a previous run was interrupted. Files that a
     * server does not have (404 — ocean tiles, e.g.) are recorded as absent, not failures.
     */
    suspend fun downloadTiles(
        plans: List<TilePlan>,
        onProgress: (done: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): DownloadOutcome {
        var imported = 0
        val failed = mutableListOf<String>()
        var cancelled = false
        var offline = false
        var done = 0
        for (plan in plans) {
            if (isCancelled()) {
                cancelled = true
                break
            }
            val fileName = "${plan.tile}.tif"
            if (!PlatformFiles.exists("$DIR/$fileName")) {
                val existing = PlatformFiles.readBytes("$DIR/$fileName.tmp")
                val resp = PlatformHttp.request(
                    "GET",
                    plan.url,
                    rangeFrom = existing?.size?.toLong() ?: 0L,
                )
                if (resp == null) {
                    // No connectivity: do not count every tile as failed, stop politely.
                    offline = true
                    onProgress(done, plans.size)
                    return DownloadOutcome(imported, failed, cancelled, offline)
                }
                if (resp.status == 404) {
                    // Ocean/no-data tile: skip without error; drop a stale partial too.
                    PlatformFiles.delete("$DIR/$fileName.tmp")
                } else if (resp.status !in 200..299) {
                    failed.add(fileName)
                } else {
                    val chunk = resp.body
                    val full = if (existing != null && resp.status == 206) existing + chunk else chunk
                    val free = PlatformFiles.freeSpaceBytes()
                    if (free in 0 until full.size.toLong()) {
                        failed.add(fileName)
                    } else {
                        // Persist the assembled bytes before validating, so an interrupted
                        // next run resumes from this .tmp instead of restarting the download.
                        PlatformFiles.writeBytes("$DIR/$fileName.tmp", full)
                        val valid = runCatching { GeoTiffReader.parse(full) != null }.getOrDefault(false)
                        if (valid) {
                            PlatformFiles.writeBytes("$DIR/$fileName", full)
                            PlatformFiles.delete("$DIR/$fileName.tmp")
                            remember(fileName)
                            imported++
                        } else {
                            PlatformFiles.delete("$DIR/$fileName.tmp")
                            failed.add(fileName)
                        }
                    }
                }
            }
            done++
            onProgress(done, plans.size)
        }
        return DownloadOutcome(imported, failed, cancelled, offline)
    }

    /** Tiles covering a track polyline's bbox — used by "download DEM along the route". */
    fun tilesForPoints(latlons: List<Pair<Double, Double>>, source: DemSourceDef, customTemplate: String): List<TilePlan> {
        if (latlons.isEmpty()) return emptyList()
        var latMin = 90.0; var latMax = -90.0; var lonMin = 180.0; var lonMax = -180.0
        for ((lat, lon) in latlons) {
            if (lat < latMin) latMin = lat
            if (lat > latMax) latMax = lat
            if (lon < lonMin) lonMin = lon
            if (lon > lonMax) lonMax = lon
        }
        val margin = 0.02
        return planTiles(source, customTemplate, latMin - margin, latMax + margin, lonMin - margin, lonMax + margin)
    }

    fun ceil(v: Double): Int = ceil(v).toInt()
}
