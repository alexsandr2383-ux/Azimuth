package ru.azimut.app

import kotlinx.coroutines.runBlocking
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.TransportProfile
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.routing.RouteData
import ru.azimut.app.core.routing.RouteEngine
import ru.azimut.app.core.routing.RouteRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.azimut.app.core.routing.RouteAnalysis
import ru.azimut.app.core.routing.RouteResult
import ru.azimut.app.core.routing.RouteSectionKind

class RouteEngineTest {
    @Test
    fun segmentNamesFollowBRouterGrid() {
        assertEquals("E35_N40.rd5", RouteData.segmentFileName(GeoPoint(44.52, 38.51)))
        assertEquals("E10_N65.rd5", RouteData.segmentFileName(GeoPoint(67.28, 14.40)))
        assertEquals("W5_N50.rd5", RouteData.segmentFileName(GeoPoint(51.5, -0.1)))
        assertEquals("W75_S35.rd5", RouteData.segmentFileName(GeoPoint(-33.4, -70.6)))
    }

    @Test
    fun routesInsideImportedBodoSegment() = runBlocking {
        val segment = File(PlatformFiles.navDataRoot(), "${RouteData.SEGMENTS_DIR}/E10_N65.rd5")
        if (!segment.isFile) return@runBlocking

        val result = RouteEngine.route(
            RouteRequest(
                from = GeoPoint(67.2800, 14.4000),
                to = GeoPoint(67.2900, 14.4100),
                profile = TransportProfile.BIKE,
            )
        )

        assertTrue(result.ok, result.details)
        assertTrue(result.distanceMeters > 0)
        assertTrue(result.points.size >= 2)
        assertTrue(result.points.all { it.lat in 67.0..68.0 && it.lon in 14.0..15.0 })
    }

    @Test
    fun enduroProfileRoutesAndReportsSections() = runBlocking {
        val segment = File(PlatformFiles.navDataRoot(), "${RouteData.SEGMENTS_DIR}/E10_N65.rd5")
        if (!segment.isFile) return@runBlocking

        val result = RouteEngine.route(
            RouteRequest(
                from = GeoPoint(67.2800, 14.4000),
                to = GeoPoint(67.2900, 14.4100),
                profile = TransportProfile.ENDURO,
            )
        )
        assertTrue(result.ok, result.details)
        assertTrue(result.sections.isNotEmpty())
        assertEquals(result.points.size, result.sections.sumOf { it.points.size } - (result.sections.size - 1))
        println("ENDURO dist=${result.distanceMeters} trail=${result.trailMeters}/${result.trailCount} " +
            "banned=${result.restrictedMeters} fords=${result.fordCount} grade=${result.maxGradePercent} " +
            "descent=${result.descentMeters} kinds=${result.sections.map { it.kind }}")
    }

    @Test
    fun analysisSplitsTrailsAndFlagsBans() {
        val pts = (0..4).map { GeoPoint(60.0 + it * 0.001, 30.0, 100.0 + it * 10) }
        val messages = RouteAnalysis.parseMessages(
            listOf(
                "30000000\t60001000\t110\t111\t1000\t0\t0\t0\t0\thighway=track ford=yes\t\t1\t1",
                "30000000\t60003000\t130\t222\t1000\t0\t0\t0\t0\thighway=path motor_vehicle=no\t\t1\t1",
                "30000000\t60004000\t140\t111\t1000\t0\t0\t0\t0\thighway=unclassified\t\t1\t1",
            )
        )
        assertEquals(3, messages.size)
        val r = RouteAnalysis.analyze(RouteResult(points = pts), messages)
        assertEquals(
            listOf(RouteSectionKind.ROAD, RouteSectionKind.TRAIL, RouteSectionKind.ROAD),
            r.sections.map { it.kind },
        )
        assertEquals(1, r.trailCount)
        assertTrue(r.trailMeters in 210..235, "trail=${r.trailMeters}")
        assertEquals(r.trailMeters, r.restrictedMeters)
        assertEquals(1, r.fordCount)
        assertEquals(9, r.maxGradePercent)
        assertEquals(0, r.descentMeters)
    }
}
