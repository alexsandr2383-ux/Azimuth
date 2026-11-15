package ru.azimut.app

import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.ui.map.polygonAreaKm2
import ru.azimut.app.ui.map.segmentNames
import ru.azimut.app.ui.map.tilesAlongLine
import ru.azimut.app.ui.map.tilesInPolygon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AreaSelectionTest {
    // Roughly 0.2° × 0.2° near Krasnodar.
    private val rect = listOf(
        GeoPoint(44.6, 38.4), GeoPoint(44.6, 38.6), GeoPoint(44.4, 38.6), GeoPoint(44.4, 38.4),
    )
    private val triangle = listOf(GeoPoint(44.6, 38.4), GeoPoint(44.6, 38.6), GeoPoint(44.4, 38.4))

    @Test
    fun rectangleCoversItsBoundingTilesAndTriangleAboutHalf() {
        val rectTiles = assertNotNull(tilesInPolygon(rect, 14, 1_000_000))
        val xs = rectTiles.map { (it shr 32).toInt() }
        val ys = rectTiles.map { (it and 0xFFFFFFFFL).toInt() }
        assertEquals((xs.max() - xs.min() + 1) * (ys.max() - ys.min() + 1), rectTiles.size)

        val triTiles = assertNotNull(tilesInPolygon(triangle, 14, 1_000_000))
        assertTrue(rectTiles.containsAll(triTiles))
        val ratio = triTiles.size.toDouble() / rectTiles.size
        assertTrue(ratio in 0.45..0.65, "ratio=$ratio")
    }

    @Test
    fun limitStopsEarlyAndHelpersWork() {
        assertNull(tilesInPolygon(rect, 18, 1_000))
        assertEquals(1, assertNotNull(tilesInPolygon(rect, 8, 10)).size)
        val km2 = polygonAreaKm2(rect)
        assertTrue(km2 in 330.0..370.0, "km2=$km2")
        assertEquals(listOf("E35_N40.rd5"), segmentNames(rect))
        assertEquals(
            listOf("E35_N40.rd5", "E40_N40.rd5"),
            segmentNames(listOf(GeoPoint(44.0, 39.0), GeoPoint(44.5, 41.0), GeoPoint(43.5, 40.0))),
        )
    }

    @Test
    fun corridorFollowsTheLineAndWidensWithRadius() {
        // ~16 km straight east-west line.
        val line = listOf(GeoPoint(44.6, 38.5), GeoPoint(44.6, 38.7))
        val narrow = assertNotNull(tilesAlongLine(line, 500.0, 16, 1_000_000))
        val wide = assertNotNull(tilesAlongLine(line, 2000.0, 16, 1_000_000))
        assertTrue(wide.containsAll(narrow))
        // z16 tile ≈ 435 m here: ±500 m → 3–4 rows, ±2 km → ~10 rows, ~37 columns.
        val narrowRows = narrow.map { (it and 0xFFFFFFFFL).toInt() }.distinct().size
        val wideRows = wide.map { (it and 0xFFFFFFFFL).toInt() }.distinct().size
        assertTrue(narrowRows in 3..4, "rows=$narrowRows")
        assertTrue(wideRows in 9..11, "rows=$wideRows")
        val columns = narrow.map { (it shr 32).toInt() }.distinct().size
        assertTrue(columns in 37..41, "columns=$columns")
        // Much cheaper than the bounding square of the line.
        assertTrue(narrow.size < 200)
        assertNull(tilesAlongLine(line, 2000.0, 18, 100))
    }
}
