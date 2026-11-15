package ru.azimut.app.core.dem

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One rendered hillshade image anchored to a geographic rectangle (NW corner +
 * step). [pixels] is ARGB row-major, top-down: lit slopes white, shaded ones
 * black, alpha carries the shading strength.
 */
class HillshadeTile(
    val name: String,
    val latTop: Double,
    val lonLeft: Double,
    val latBottom: Double,
    val lonRight: Double,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
)

/**
 * Renders hillshade tiles from the imported DEM (standard Horn algorithm,
 * sun from NW at 45°). Pure common code — platforms wrap [HillshadeTile.pixels]
 * into their own bitmaps. Cached per tile name to keep panning cheap.
 */
object DemShade {
    private const val GRID = 384 // samples per tile side
    private const val SUN_AZIMUTH_DEG = 315.0
    private const val SUN_ALTITUDE_DEG = 45.0

    private val cache = LinkedHashMap<String, HillshadeTile>()

    /** Shade for the tile covering [lat]/[lon]; null when the DEM has no tile there. */
    fun tileFor(dem: DemStore, lat: Double, lon: Double, opacity: Float = 0.35f): HillshadeTile? {
        val name = DemIndex.tileOf(lat, lon)
        cache[name]?.let { return it }
        val shaded = render(dem, name, opacity) ?: return null
        cache[name] = shaded
        while (cache.size > 6) {
            cache.remove(cache.keys.first())
        }
        return shaded
    }

    fun clearCache() {
        cache.clear()
    }

    /** Renders one 1° tile by sampling the DEM store on a regular grid. */
    fun render(dem: DemStore, tileName: String, opacity: Float): HillshadeTile? {
        val bounds = DemIndex.tileBounds(tileName) ?: return null
        // SW-corner label: the rendered band spans [lat, lat+1), so the top edge is lat+1.
        val latTop = bounds.first.toDouble() + 1.0
        val lonLeft = bounds.second.toDouble()
        val step = 1.0 / GRID
        val heights = arrayOfNulls<DoubleArray>(GRID)
        var any = false
        for (r in 0 until GRID) {
            val row = DoubleArray(GRID)
            val lat = latTop - r * step
            for (c in 0 until GRID) {
                val h = dem.heightAt(lat, lonLeft + c * step)
                if (h != null) any = true
                row[c] = h ?: Double.NaN
            }
            heights[r] = row
        }
        if (!any) return null

        // Metres per cell at this latitude for the slope math.
        val centerLat = latTop - 0.5
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = metersPerDegLat * cos(centerLat * PI / 180.0)
        val cellMeters = step * metersPerDegLat

        val zenith = (90.0 - SUN_ALTITUDE_DEG) * PI / 180.0
        val sunAz = SUN_AZIMUTH_DEG * PI / 180.0
        val pixels = IntArray(GRID * GRID)
        for (r in 0 until GRID) {
            val row = heights[r]!!
            val north = heights[(r - 1).coerceAtLeast(0)]!!
            val south = heights[(r + 1).coerceAtMost(GRID - 1)]!!
            for (c in 0 until GRID) {
                val h = row[c]
                if (h.isNaN()) {
                    pixels[r * GRID + c] = 0
                    continue
                }
                val west = row[(c - 1).coerceAtLeast(0)]
                val east = row[(c + 1).coerceAtMost(GRID - 1)]
                // Neighbour cells may be void (NaN): treat them as the current height (flat edge).
                val dzDx = ((if (east.isNaN()) h else east) - (if (west.isNaN()) h else west)) /
                    (2.0 * cellMeters)
                val hN = if (north[c].isNaN()) h else north[c]
                val hS = if (south[c].isNaN()) h else south[c]
                val dzDy = (hN - hS) / (2.0 * cellMeters) // north positive
                val slope = atan2(sqrt(dzDx * dzDx + dzDy * dzDy), 1.0)
                val aspect = atan2(dzDx, dzDy) // 0 = facing north
                val shade = cos(zenith) * cos(slope) +
                    sin(zenith) * sin(slope) * cos(sunAz - aspect)
                val normalized = shade.coerceIn(0.0, 2.0) / 2.0
                val alpha = (opacity * 255).toInt()
                val gray = (255 * normalized).toInt().coerceIn(0, 255)
                // Lit slopes brighten (white overlay), shadows darken (black overlay).
                pixels[r * GRID + c] = if (normalized >= 0.5) {
                    (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
                } else {
                    val inv = 255 - gray
                    (alpha shl 24) or (inv shl 16) or (inv shl 8) or inv
                }
            }
        }
        return HillshadeTile(
            name = tileName,
            latTop = latTop,
            lonLeft = lonLeft,
            latBottom = latTop - 1.0,
            lonRight = lonLeft + 1.0,
            width = GRID,
            height = GRID,
            pixels = pixels,
        )
    }
}
