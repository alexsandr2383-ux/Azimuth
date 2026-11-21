package ru.azimut.app.core.dem

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.platform.PlatformHttp
import kotlin.math.ceil
import kotlin.math.floor

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

    /** 1-degree tiles covering the bbox; rough size estimate for progress/UI. */
    fun planTiles(source: DemSourceDef, customTemplate: String, latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): List<TilePlan> {
        val tplDef = if (source.id == "custom") source.copy(urlTemplate = customTemplate) else source
        if (tplDef.urlTemplate.isBlank()) return emptyList()
        val perTile = if (source.id == "glo90") 500_000L else 30_000_000L
        val tiles = mutableListOf<TilePlan>()
        val latA = floor(latMin).toInt()
        val latB = floor(latMax).toInt()
        val lonA = floor(lonMin).toInt()
        val lonB = floor(lonMax).toInt()
        for (lat in latB downTo latA) {
            for (lon in lonA..lonB) {
                val tile = DemIndex.tileOf(lat.toDouble() + 0.5, lon.toDouble() + 0.5)
                val url = DemSources.urlFor(tplDef, tile) ?: continue
                tiles.add(TilePlan(tile, url, perTile))
            }
        }
        return tiles
    }

    data class DownloadOutcome(
        val imported: Int,
        val failed: List<String>,
        val cancelled: Boolean,
        val offline: Boolean,
        val errors: List<String> = emptyList(),
    )

    /**
     * Downloads the tiles into dem/, skipping ones already present; resumes via the
     * existing-file size (Range) when a previous run was interrupted. Files that a
     * server does not have (404 - ocean tiles, e.g.) are recorded as absent, not failures.
     */
    suspend fun downloadTiles(
        plans: List<TilePlan>,
        onProgress: (done: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): DownloadOutcome {
        var imported = 0
        val failed = mutableListOf<String>()
        val errors = mutableListOf<String>()
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
                    offline = true
                    onProgress(done, plans.size)
                    return DownloadOutcome(imported, failed, cancelled, offline, errors)
                }
                if (resp.status == 404) {
                    PlatformFiles.delete("$DIR/$fileName.tmp")
                } else if (resp.status !in 200..299) {
                    failed.add(fileName)
                    errors += "${plan.tile}: HTTP ${resp.status}"
                } else {
                    val chunk = resp.body
                    val full = if (existing != null && resp.status == 206) existing + chunk else chunk
                    val free = PlatformFiles.freeSpaceBytes()
                    if (free in 0 until full.size.toLong()) {
                        failed.add(fileName)
                        errors += "${plan.tile}: no space"
                    } else {
                        PlatformFiles.writeBytes("$DIR/$fileName.tmp", full)
                        val validation = runCatching { GeoTiffReader.parse(full) }
                        val valid = validation.getOrNull() != null
                        if (valid) {
                            PlatformFiles.writeBytes("$DIR/$fileName", full)
                            PlatformFiles.delete("$DIR/$fileName.tmp")
                            remember(fileName)
                            imported++
                        } else {
                            PlatformFiles.delete("$DIR/$fileName.tmp")
                            failed.add(fileName)
                            val reason = validation.exceptionOrNull()?.message ?: "invalid DEM"
                            errors += "${plan.tile}: $reason"
                        }
                    }
                }
            }
            done++
            onProgress(done, plans.size)
        }
        return DownloadOutcome(imported, failed, cancelled, offline, errors)
    }

    /** Tiles covering a track polyline's bbox - used by "download DEM along the route". */
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
