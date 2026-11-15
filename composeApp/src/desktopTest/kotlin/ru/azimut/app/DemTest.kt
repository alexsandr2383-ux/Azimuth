package ru.azimut.app

import ru.azimut.app.core.dem.DemIndex
import ru.azimut.app.core.dem.DemTile
import ru.azimut.app.core.dem.GeoTiffReader
import ru.azimut.app.core.dem.HgtReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DemTest {

    /** Synthetic 1201x1201 SRTM1 grid for tile N47E041: height = 100 + row*0 + col (0..1200). */
    private fun syntheticHgt(size: Int = 1201): ByteArray {
        val bytes = ByteArray(size * size * 2)
        var i = 0
        for (row in 0 until size) {
            for (col in 0 until size) {
                val value = 100 + col
                bytes[i] = ((value shr 8) and 0xFF).toByte()
                bytes[i + 1] = (value and 0xFF).toByte()
                i += 2
            }
        }
        return bytes
    }

    @Test
    fun hgtParsesSizesAndSamples() {
        val tile = HgtReader.parse("N47E041.hgt", syntheticHgt())
        assertNotNull(tile)
        assertEquals(1201, tile.rows)
        // SRTM/Copernicus SW-corner naming: N47E041 covers [47..48]N x [41..42]E,
        // so the grid's top (row 0) sits at lat 48.
        assertEquals(48.0, tile.latTop, 1e-9)
        assertEquals(41.0, tile.lonLeft, 1e-9)
        assertEquals("N47E041", tile.tileName)
        // First cell (NW) = row 0, col 0 -> height 100.
        assertEquals(100.0, tile.sample(47.9999999, 41.0000001)!!, 1.0)
        // Center of the tile ~ col 600 -> ~700 m.
        assertEquals(700.0, tile.sample(47.5, 41.5)!!, 2.0)
        // Outside the tile -> null (one degree south is a different tile).
        assertNull(tile.sample(46.5, 41.5))
        assertNull(tile.sample(48.5, 41.5))
    }

    @Test
    fun hgtRejectsBadSizeAndName() {
        assertNull(HgtReader.parse("N47E041.hgt", ByteArray(100)))
        assertNull(HgtReader.parse("bogus.hgt", syntheticHgt()))
    }

    @Test
    fun tileBoundsParsesNames() {
        assertEquals(47.0 to 41.0, DemIndex.tileBounds("N47E041"))
        assertEquals(-12.0 to -73.0, DemIndex.tileBounds("S12W073"))
        assertNull(DemIndex.tileBounds("garbage"))
    }

    /** Minimal little-endian GeoTIFF: one strip, Int16, 4x4 grid, WGS84 pixel scale. */
    private fun syntheticGeoTiff(): ByteArray {
        val width = 4
        val height = 4
        val samples = ShortArray(width * height) { i -> (100 + i).toShort() }
        // TIFF header: II, 42, offset 8.
        val header = byteArrayOf(0x49, 0x49, 42, 0, 8, 0, 0, 0)
        // IFD: 8 entries.
        val entries = mutableListOf<Int>()
        fun tagEntry(tag: Int, type: Int, count: Int, value: Int): Pair<Int, Int> = tag to value // inline types

        // Build IFD manually: 8 tags x 12 bytes + 4 bytes next-IFD offset.
        val tags = listOf(
            Triple(256, 3, width), // ImageWidth SHORT
            Triple(257, 3, height), // ImageLength SHORT
            Triple(258, 3, 16), // BitsPerSample SHORT
            Triple(259, 3, 1), // Compression = none
            Triple(273, 4, 0), // StripOffsets LONG (patched below)
            Triple(277, 3, 1), // SamplesPerPixel
            Triple(278, 3, height), // RowsPerStrip
            Triple(279, 4, width * height * 2), // StripByteCounts
            Triple(33550, 12, -1), // ModelPixelScale DOUBLE x3 (needs external value)
            Triple(33922, 12, -2), // ModelTiepoint DOUBLE x6 (needs external value)
            Triple(339, 3, 2), // SampleFormat = signed
        )
        val entryCount = tags.size
        val ifdSize = 2 + entryCount * 12 + 4
        val dataOffset = 8 + ifdSize + 3 * 8 + 6 * 8 // strip data after double arrays
        val buffer = java.io.ByteArrayOutputStream()
        buffer.write(header)

        val ifdStart = 8
        val doublesStart = ifdStart + 2 + entryCount * 12 + 4
        val pixelScaleOffset = doublesStart
        val tiepointOffset = doublesStart + 3 * 8
        val stripOffset = doublesStart + 9 * 8

        fun putShort(v: Int) {
            buffer.write(v and 0xFF)
            buffer.write((v shr 8) and 0xFF)
        }
        fun putInt(v: Int) {
            putShort(v and 0xFFFF)
            putShort((v shr 16) and 0xFFFF)
        }
        fun putDouble(v: Double) {
            val bits = java.lang.Double.doubleToLongBits(v)
            for (s in 0..7) buffer.write(((bits shr (8 * s)) and 0xFF).toInt())
        }

        putShort(entryCount)
        for ((tag, type, value) in tags) {
            putShort(tag)
            putShort(type)
            putInt(when (type) {
                3 -> 1 // SHORT count
                4 -> 1 // LONG count
                else -> if (tag == 33550) 3 else 6 // DOUBLE arrays
            })
            when (tag) {
                33550 -> putInt(pixelScaleOffset)
                33922 -> putInt(tiepointOffset)
                273 -> putInt(stripOffset)
                else -> putInt(value) // inline value (SHORT/LONG fit in 4 bytes)
            }
        }
        putInt(0) // next IFD

        // PixelScale: 1/1200 deg per cell.
        putDouble(1.0 / 1200.0)
        putDouble(1.0 / 1200.0)
        putDouble(0.0)
        // Tiepoint: raster (i,j,k) -> world (x,y,z): (0,0,0) -> (41,47,0).
        putDouble(0.0)
        putDouble(0.0)
        putDouble(0.0)
        putDouble(41.0)
        putDouble(47.0)
        putDouble(0.0)
        // Pad to strip offset.
        while (buffer.size() < stripOffset) buffer.write(0)
        // Strip: little-endian Int16 samples ("II" byte order).
        for (s in samples) {
            val v = s.toInt()
            buffer.write(v and 0xFF)
            buffer.write((v shr 8) and 0xFF)
        }
        return buffer.toByteArray()
    }

    @Test
    fun geoTiffParsesGeoreferencingAndValues() {
        val tile = try {
            GeoTiffReader.parse(syntheticGeoTiff())
        } catch (e: Exception) {
            fail("parse failed: ${e.message}")
        }
        assertNotNull(tile)
        assertEquals(47.0, tile.latTop, 1e-9)
        assertEquals(41.0, tile.lonLeft, 1e-9)
        // Grid cells verified directly: row-major, data[i] = 100 + i.
        assertEquals(100.0, tile.raw(0, 0), 1e-6)
        assertEquals(103.0, tile.raw(0, 3), 1e-6)
        assertEquals(115.0, tile.raw(3, 3), 1e-6)
        // Sampling near the NW corner stays at ~100 m.
        assertEquals(100.0, tile.sample(46.9999999, 41.0000001)!!, 0.5)
    }

    @Test
    fun bilinearInterpolationAndVoid() {
        // 2x2 grid: corners 0, 10, 20, 30; step 1 deg.
        val tile = DemTile(
            latTop = 10.0, lonLeft = 20.0, stepDeg = 1.0, rows = 2, cols = 2,
            nodata = -32768.0,
            data = shortArrayOf(0, 10, 20, 30),
        )
        // Center of the cell = average of four corners = 15.
        assertEquals(15.0, tile.sample(9.5, 20.5)!!, 1e-9)
        // On the south edge, mid-x: interpolation between 20 and 30 gives 25.
        assertEquals(25.0, tile.sample(9.0, 20.5)!!, 1e-9)
        // Outside: null.
        assertNull(tile.sample(10.5, 20.5))
        // Void corner poisons the cell.
        val voided = tile.copy(data = shortArrayOf(0, 10, 20, -32768))
        assertNull(voided.sample(9.5, 20.5))
    }

    @Test
    fun nodataIsNotZero() {
        val tile = DemTile(
            latTop = 10.0, lonLeft = 20.0, stepDeg = 1.0, rows = 2, cols = 2,
            nodata = -32768.0,
            data = shortArrayOf(-32768, -32768, -32768, -32768),
        )
        assertNull(tile.sample(9.5, 20.5))
    }
}
