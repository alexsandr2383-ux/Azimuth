package ru.azimut.app.core.dem

import ru.azimut.app.core.platform.PlatformFiles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * One in-memory DEM tile: a regular lat/lon grid of heights in metres.
 * [latTop] of row 0, [lonLeft] of column 0; [stepDeg] is the grid cell size.
 * [nodata] marks missing values (void), never reported as a real height.
 */
data class DemTile(
    val latTop: Double,
    val lonLeft: Double,
    val stepDeg: Double,
    val rows: Int,
    val cols: Int,
    val nodata: Double = -32767.0,
    val data: ShortArray,
) {
    val latBottom: Double get() = latTop - (rows - 1) * stepDeg
    val lonRight: Double get() = lonLeft + (cols - 1) * stepDeg

    fun covers(lat: Double, lon: Double): Boolean =
        lat >= latBottom && lat < latTop && lon >= lonLeft && lon < lonRight

    fun contains(tile: String): Boolean = tile == tileName

    val tileName: String
        get() = DemIndex.tileLabel(floor(latBottom + 1e-6).toInt(), floor(lonLeft + 1e-6).toInt())

    fun raw(row: Int, col: Int): Double {
        if (row !in 0 until rows || col !in 0 until cols) return nodata
        return data[row * cols + col].toDouble()
    }

    fun valid(row: Int, col: Int): Boolean {
        if (row !in 0 until rows || col !in 0 until cols) return false
        // Exact nodata compare: the > nodata/2 heuristic misfires for positive nodata
        // (9999 would void everything below 4999 m) and for nodata=0.
        return data[row * cols + col].toDouble() != nodata
    }

    /**
     * Bilinear interpolation at the point; null when any corner is void
     * or the point falls outside the grid. Plain nearest-neighbour fallback
     * would smear cliffs, so void corners honestly void the sample.
     */
    fun sample(lat: Double, lon: Double): Double? {
        if (!covers(lat, lon)) return null
        val fy = (latTop - lat) / stepDeg
        val fx = (lon - lonLeft) / stepDeg
        val y0 = floor(fy).toInt()
        val x0 = floor(fx).toInt()
        val ty = fy - y0
        val tx = fx - x0
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val x1 = (x0 + 1).coerceAtMost(cols - 1)
        if (!valid(y0, x0) || !valid(y0, x1) || !valid(y1, x0) || !valid(y1, x1)) return null
        val h00 = raw(y0, x0)
        val h01 = raw(y0, x1)
        val h10 = raw(y1, x0)
        val h11 = raw(y1, x1)
        return h00 * (1 - tx) * (1 - ty) + h01 * tx * (1 - ty) + h10 * (1 - tx) * ty + h11 * tx * ty
    }
}

/**
 * Finds the DEM grid file covering a coordinate and samples the height.
 * File naming: N47E041.hgt/.tif — integer SW corner (SRTM/Copernicus convention,
 * N47E041 covers [47..48]N x [41..42]E).
 */
object DemIndex {
    /** SW corner of the 1x1-degree tile named like "N47E041"; the tile spans [lat, lat+1) x [lon, lon+1). */
    fun tileBounds(name: String): Pair<Double, Double>? {
        val m = Regex("([NS])(\\d{1,2})([EW])(\\d{1,3})").find(name.trim().uppercase()) ?: return null
        val (lat, lon) = Pair(
            (if (m.groupValues[1] == "N") 1 else -1) * m.groupValues[2].toInt(),
            (if (m.groupValues[3] == "E") 1 else -1) * m.groupValues[4].toInt(),
        )
        return lat.toDouble() to lon.toDouble()
    }

    /** Degrees per cell for the tile size: 1201 -> 1/1200 (SRTM 1"), 3601 -> 1/3600 (SRTM 3"). */
    fun stepForSize(rows: Int): Double = 1.0 / (rows - 1)

    fun tileOf(lat: Double, lon: Double): String =
        tileLabel(floor(lat).toInt(), floor(lon).toInt())

    /** Zero-padded SRTM/Copernicus label, e.g. "N47E041", "S12W073". */
    fun tileLabel(latIdx: Int, lonIdx: Int): String {
        val latLabel = if (latIdx >= 0) "N%02d".format(latIdx) else "S%02d".format(-latIdx)
        val lonLabel = if (lonIdx >= 0) "E%03d".format(lonIdx) else "W%03d".format(-lonIdx)
        return "$latLabel$lonLabel"
    }
}

/** Result of a DEM lookup for one point. */
data class DemSample(val heightMeters: Double?, val fromTile: String?)

/**
 * In-memory DEM provider: imports .hgt/.tif into NavData/dem, indexes them,
 * samples with bilinear interpolation. One tile per 1-degree cell is cached;
 * tiles are dropped when the cache exceeds [maxCached] to bound memory.
 */
open class DemStore(private val dir: String = "dem") {
    private val cache = HashMap<String, DemTile>()
    private val cacheOrder = ArrayDeque<String>()
    val availableTiles: Set<String> get() = cache.keys

    var lastMiss = false
        private set

    fun clearCache() {
        cache.clear()
        cacheOrder.clear()
    }

    fun cachedTileNames(): List<String> = cache.keys.toList()

    /** True when a DEM file for the 1-degree tile of this point exists or is cached. */
    fun hasTileFor(lat: Double, lon: Double): Boolean {
        val name = DemIndex.tileOf(lat, lon)
        if (cache.containsKey(name)) return true
        val files = PlatformFiles.listFiles(dir)
        return files.any { f -> f.substringBeforeLast('.').uppercase() == name.uppercase() }
    }

    open fun heightAt(lat: Double, lon: Double): Double? = sampleAt(lat, lon).heightMeters

    fun sampleAt(lat: Double, lon: Double): DemSample {
        lastMiss = false
        val name = DemIndex.tileOf(lat, lon)
        val tile = tileFor(name, lat, lon) ?: run {
            lastMiss = true
            return DemSample(null, null)
        }
        return DemSample(tile.sample(lat, lon), name)
    }

    /** Height of the tile grid for [name], loading the file on demand. */
    private fun tileFor(name: String, lat: Double, lon: Double): DemTile? {
        cache[name]?.let { if (it.covers(lat, lon)) return it else return null }
        val files = PlatformFiles.listFiles(dir)
        val match = files.firstOrNull { f ->
            val base = f.substringBeforeLast('.')
            DemIndex.tileBounds(base)?.let { (lat0, lon0) ->
                lat >= lat0 && lat < lat0 + 1 && lon >= lon0 && lon < lon0 + 1
            } == true
        } ?: return null
        val bytes = PlatformFiles.readBytes("$dir/$match") ?: return null
        val tile = runCatching {
            if (match.endsWith(".hgt", ignoreCase = true)) {
                HgtReader.parse(match, bytes)
            } else {
                GeoTiffReader.parse(bytes)
            }
        }.getOrNull() ?: return null
        cache[name] = tile
        cacheOrder.addLast(name)
        while (cacheOrder.size > MAX_CACHED_TILES) {
            val evict = cacheOrder.removeFirst()
            cache.remove(evict)
        }
        return tile
    }

    companion object {
        private const val MAX_CACHED_TILES = 8
    }
}

/** Angle from an observer to the terrain horizon, used by sun-times correction. */
fun horizonAngleMeters(heightDifference: Double, distanceMeters: Double): Double =
    kotlin.math.atan2(heightDifference, distanceMeters) * 180.0 / PI

/** Great-circle distance in metres between two lat/lon points (same model as core/model/Geo). */
fun demDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val h = kotlin.math.sin(dLat / 2).let { it * it } +
        cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * kotlin.math.sin(dLon / 2).let { it * it }
    return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1.0 - h))
}

/** Local compass bearing from point 1 to point 2, degrees clockwise from north. */
fun demBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val latRad1 = lat1 * PI / 180
    val latRad2 = lat2 * PI / 180
    val dLon = (lon2 - lon1) * PI / 180
    val y = kotlin.math.sin(dLon) * cos(latRad2)
    val x = cos(latRad1) * kotlin.math.sin(latRad2) -
        kotlin.math.sin(latRad1) * cos(latRad2) * cos(dLon)
    return ((kotlin.math.atan2(y, x) * 180 / PI) + 360.0) % 360.0
}

/** True when [lat] is close enough to a pole that sunrise/sunset math degenerates. */
fun isPolarLat(lat: Double): Boolean = abs(lat) > 84.0
