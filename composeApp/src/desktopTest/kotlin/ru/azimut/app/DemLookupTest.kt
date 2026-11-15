package ru.azimut.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.azimut.app.core.dem.DemIndex
import ru.azimut.app.core.dem.DemStore
import ru.azimut.app.core.dem.DemTile
import ru.azimut.app.core.dem.HgtReader
import ru.azimut.app.core.platform.PlatformFiles

/**
 * Regression tests for the SW-corner tile anchoring: SRTM/Copernicus name N47E041
 * covers [47..48]N x [41..42]E, and the sample grid's first row is the north edge.
 */
class DemLookupTest {

    /** 1201x1201 SRTM1 grid for N47E041: height = 100 + row (row 0 = lat 48, row 1200 = lat 47). */
    private fun syntheticHgt(size: Int = 1201): ByteArray {
        val bytes = ByteArray(size * size * 2)
        var i = 0
        for (row in 0 until size) {
            for (col in 0 until size) {
                val value = 100 + row
                bytes[i] = ((value shr 8) and 0xFF).toByte()
                bytes[i + 1] = (value and 0xFF).toByte()
                i += 2
            }
        }
        return bytes
    }

    @Test
    fun tileLabelsUsePaddedSwCorner() {
        assertEquals("N47E041", DemIndex.tileOf(47.2, 41.5))
        assertEquals("N00E006", DemIndex.tileOf(0.5, 6.5))
        assertEquals("S13W074", DemIndex.tileOf(-12.3, -73.4))
        val tile = HgtReader.parse("N47E041.hgt", syntheticHgt())
        assertEquals("N47E041", tile!!.tileName)
        // hasTileFor and the file matcher agree on the same padded name.
        assertEquals("N47E041", DemIndex.tileBounds("N47E041.tif")!!.let { (a, b) -> DemIndex.tileLabel(a.toInt(), b.toInt()) })
    }

    @Test
    fun demStoreSamplesCorrectBandFromRealFile() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-dem-test").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            PlatformFiles.writeBytes("dem/N47E041.hgt", syntheticHgt())
            val store = DemStore()
            // Point inside the tile: mid-tile lat 47.5 -> row 600 -> height 700.
            val sample = store.sampleAt(47.5, 41.5)
            assertEquals(700.0, sample.heightMeters!!, 2.0)
            assertEquals("N47E041", sample.fromTile)
            assertTrue(store.hasTileFor(47.2, 41.5))
            // One degree south is a different (missing) tile: honest miss.
            val miss = store.sampleAt(46.5, 41.5)
            assertNull(miss.heightMeters)
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }

    @Test
    fun nodataHandlingIsExactForPositiveAndNegative() {
        // Positive nodata (9999) must not void everything below 4999 m...
        val positive = DemTile(
            latTop = 10.0, lonLeft = 20.0, stepDeg = 1.0, rows = 2, cols = 2,
            nodata = 9999.0,
            data = shortArrayOf(0, 10, 20, 30),
        )
        assertEquals(15.0, positive.sample(9.5, 20.5)!!, 1e-9)
        // ...and an exact nodata value stays void regardless of sign.
        val voided = DemTile(
            latTop = 10.0, lonLeft = 20.0, stepDeg = 1.0, rows = 2, cols = 2,
            nodata = 9999.0,
            data = shortArrayOf(0, 10, 20, 9999),
        )
        assertNull(voided.sample(9.5, 20.5))
    }
}
