package ru.azimut.app

import ru.azimut.app.core.import.PointsIO
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.protected.ProtectedAreas
import ru.azimut.app.core.protected.ProtectedGeometry
import ru.azimut.app.core.protected.ProtectedPolygon
import ru.azimut.app.core.protected.ProtectedWatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectedAndImportTest {

    private val square = ProtectedPolygon(
        rings = listOf(
            listOf(
                listOf(0.0, 0.0), listOf(10.0, 0.0), listOf(10.0, 10.0), listOf(0.0, 10.0), listOf(0.0, 0.0),
            ),
        ),
        name = "Test zone",
        access = "private",
        lonMin = 0.0, latMin = 0.0, lonMax = 10.0, latMax = 10.0,
    )

    private val squareWithHole = ProtectedPolygon(
        rings = listOf(
            listOf(
                listOf(0.0, 0.0), listOf(10.0, 0.0), listOf(10.0, 10.0), listOf(0.0, 10.0), listOf(0.0, 0.0),
            ),
            listOf(
                listOf(3.0, 3.0), listOf(6.0, 3.0), listOf(6.0, 6.0), listOf(3.0, 6.0), listOf(3.0, 3.0),
            ),
        ),
        lonMin = 0.0, latMin = 0.0, lonMax = 10.0, latMax = 10.0,
    )

    @Test
    fun pointInPolygonAndHoles() {
        assertTrue(ProtectedGeometry.inside(square, 5.0, 5.0))
        assertFalse(ProtectedGeometry.inside(square, 11.0, 5.0))
        assertFalse(ProtectedGeometry.inside(square, 5.0, -1.0))
        // Inside the outline but inside the hole: not in the zone.
        assertFalse(ProtectedGeometry.inside(squareWithHole, 4.5, 4.5))
        // Between outline and hole.
        assertTrue(ProtectedGeometry.inside(squareWithHole, 1.0, 1.0))
    }

    @Test
    fun segmentCrossesPolygon() {
        // Segment entirely outside.
        assertFalse(ProtectedGeometry.segmentHits(square, GeoPoint(-5.0, -5.0), GeoPoint(-1.0, -1.0)))
        // Segment crossing the border.
        assertTrue(ProtectedGeometry.segmentHits(square, GeoPoint(-2.0, 5.0), GeoPoint(2.0, 5.0)))
        // Segment entirely inside.
        assertTrue(ProtectedGeometry.segmentHits(square, GeoPoint(4.0, 4.0), GeoPoint(5.0, 5.0)))
        // Segment fully inside the hole is not a contact (the hole is not the zone).
        assertFalse(ProtectedGeometry.segmentHits(squareWithHole, GeoPoint(4.0, 4.5), GeoPoint(5.0, 4.5)))
        // Segment from the zone proper into the hole touches the zone (endpoint inside).
        assertTrue(ProtectedGeometry.segmentHits(squareWithHole, GeoPoint(2.0, 4.5), GeoPoint(4.5, 4.5)))
    }

    @Test
    fun watchFiresOncePerEpisode() {
        val watch = ProtectedWatch(listOf(square))
        val first = watch.onPosition(5.0, 5.0)
        assertTrue(first != null && first.polygon.access == "private")
        // Same position again: no repeat within the episode.
        assertNull(watch.onPosition(5.0, 5.0))
        // Leave and come back: fires again.
        watch.onPosition(50.0, 50.0)
        assertTrue(watch.onPosition(5.0, 5.0) != null)
    }

    private fun assertNull(value: Any?) = kotlin.test.assertNull(value)

    @Test
    fun geoJsonParsingWithBadGeometry() {
        val geoJson = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"name":"Парк","protection":"national_park"},
           "geometry":{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]]]}},
          {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[1,1],[2]]]}},
          {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[5,5]}}
        ]}
        """.trimIndent()
        val parsed = ProtectedAreas.parseGeoJson(geoJson, "test")
        // Bad ring and point features are skipped; one valid polygon remains.
        assertEquals(1, parsed.size)
        assertEquals("Парк", parsed[0].name)
        assertEquals("national_park", parsed[0].protection)
    }

    @Test
    fun routeCheckHitsZoneOnce() {
        val watch = ProtectedWatch(listOf(square))
        val route = listOf(GeoPoint(-5.0, 5.0), GeoPoint(5.0, 5.0), GeoPoint(15.0, 5.0))
        val hits = watch.onRoute(route)
        assertEquals(1, hits.size)
    }

    // ---- PointsIO ----

    @Test
    fun csvRoundTrip() {
        val csv = PointsIO.exportCsv(
            listOf(
                ru.azimut.app.core.model.Waypoint(
                    id = "1", name = "База, восток", description = "С \"кавычками\"",
                    lat = 55.123456, lon = 37.654321, category = "poi",
                ),
            ),
        )
        assertTrue(csv.contains("55.1234560,37.6543210"))
        val parsed = PointsIO.parseCsv(csv)
        assertEquals(1, parsed.size)
        assertEquals("База, восток", parsed[0].name)
        assertEquals("С \"кавычками\"", parsed[0].description)
        assertEquals(55.123456, parsed[0].lat, 1e-6)
    }

    @Test
    fun geoJsonPointsImport() {
        val geoJson = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"name":"Home","category":"base"},
           "geometry":{"type":"Point","coordinates":[37.5,55.5,120.0]}}
        ]}
        """.trimIndent()
        val parsed = PointsIO.parseGeoJsonPoints(geoJson)
        assertEquals(1, parsed.size)
        assertEquals("Home", parsed[0].name)
        assertEquals(120.0, parsed[0].altitude!!, 1e-9)
        // Polygon documents yield no points.
        assertEquals(0, PointsIO.parseGeoJsonPoints("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1]]]}").size)
    }

    @Test
    fun kmlExportParsesBack() {
        val kml = PointsIO.exportKml(
            listOf(
                ru.azimut.app.core.model.Waypoint(id = "1", name = "Точка А", lat = 55.5, lon = 37.5),
            ),
            "Test",
        )
        val parsed = PointsIO.import("points.kml", kml, null)
        assertEquals(1, parsed.size)
        assertEquals("Точка А", parsed[0].name)
        assertEquals(55.5, parsed[0].lat, 1e-6)
    }
}
