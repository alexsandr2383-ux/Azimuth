package ru.azimut.app

import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.gpx.GpxImporter
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.routing.RouteFollower
import ru.azimut.app.core.routing.RouteResult
import ru.azimut.app.core.routing.RouteSection
import ru.azimut.app.core.routing.RouteSectionKind
import ru.azimut.app.core.routing.toPlannedTrack
import ru.azimut.app.core.routing.trackSections
import ru.azimut.app.ui.components.gpxFileName
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanTest {
    private val a = GeoPoint(0.00001, 0.00002, 100.0)
    private val b = GeoPoint(0.001, 0.0, 110.0)
    private val c = GeoPoint(0.002, 0.0, 120.0)
    private val d = GeoPoint(0.003, 0.0, 115.0)

    @Test
    fun gpxFileNameIsValidOnWindows() {
        assertEquals("a_b_c.gpx", gpxFileName("a\\b/c"))
        assertEquals("ab.gpx", gpxFileName("a\nb\t"))
        assertEquals("trip.gpx", gpxFileName("trip. . "))
        assertEquals("route.gpx", gpxFileName(" \r\n. "))
    }

    @Test
    fun plannedRouteSurvivesGpxRoundTripWithTrails() {
        val route = RouteResult(
            distanceMeters = 333,
            points = listOf(a, b, c, d),
            sections = listOf(
                RouteSection(RouteSectionKind.ROAD, listOf(a, b)),
                RouteSection(RouteSectionKind.TRAIL, listOf(b, c)),
                RouteSection(RouteSectionKind.ROAD, listOf(c, d)),
            ),
        )
        val track = route.toPlannedTrack("plan-1", "К озеру", "enduro", 1_000L)
        assertEquals(listOf(false, false, true, false), track.points.map { it.trail })

        val xml = Gpx.writeTrack(track)
        assertFalse(xml.contains("E-"), "scientific notation in GPX")
        assertTrue(xml.contains("lat=\"0.0000100\""))

        val imported = GpxImporter.parse("plan.gpx", xml, "foot").tracks.single()
        assertEquals(KIND_ROUTE, imported.kind)
        assertEquals("enduro", imported.profile)
        assertEquals(listOf(false, false, true, false), imported.points.map { it.trail })
        assertEquals(
            listOf(RouteSectionKind.ROAD, RouteSectionKind.TRAIL, RouteSectionKind.ROAD),
            trackSections(imported.points).map { it.kind },
        )
    }

    @Test
    fun followerMeasuresProgressAndOffRouteWithHysteresis() {
        val follower = RouteFollower(listOf(GeoPoint(55.0, 37.0), GeoPoint(55.01, 37.0)))
        val total = follower.totalMeters
        val mid = follower.update(GeoPoint(55.005, 37.0))!!
        assertTrue(mid.offRouteMeters < 1.0)
        assertTrue(abs(mid.remainingMeters - total / 2) < 2.0)
        assertFalse(follower.offRoute)

        // ~64 m east: off route.
        follower.update(GeoPoint(55.005, 37.001))
        assertTrue(follower.offRoute)
        // ~38 m: inside the hysteresis band, still off.
        follower.update(GeoPoint(55.005, 37.0006))
        assertTrue(follower.offRoute)
        // ~13 m: back on route.
        follower.update(GeoPoint(55.005, 37.0002))
        assertFalse(follower.offRoute)

        assertTrue(follower.update(GeoPoint(55.01, 37.0))!!.finished)
    }

    @Test
    fun followerFollowsLoopWithoutResettingAtFinish() {
        val p0 = GeoPoint(55.0, 37.0)
        val p1 = GeoPoint(55.0036, 37.0)
        val p2 = GeoPoint(55.0036, 37.00628)
        val p3 = GeoPoint(55.0, 37.00628)
        val follower = RouteFollower(listOf(p0, p1, p2, p3, p0))
        val total = follower.totalMeters

        val start = follower.update(p0)!!
        assertFalse(start.finished)
        assertTrue(abs(start.remainingMeters - total) < 30.0)

        var lastRemaining = start.remainingMeters
        val corners = listOf(p0, p1, p2, p3, p0)
        for (s in 0 until corners.size - 1) {
            val from = corners[s]
            val to = corners[s + 1]
            for (k in 1..8) {
                val t = k / 8.0
                val point = GeoPoint(
                    from.lat + (to.lat - from.lat) * t,
                    from.lon + (to.lon - from.lon) * t,
                )
                val progress = follower.update(point)!!
                assertTrue(progress.remainingMeters <= lastRemaining + 60.0, "remaining grew on a loop")
                lastRemaining = progress.remainingMeters
            }
        }
        assertTrue(follower.update(p0)!!.finished)
    }

    @Test
    fun followerHandlesOutAndBackRoute() {
        val a = GeoPoint(55.0, 37.0)
        val b = GeoPoint(55.008993, 37.0)
        val follower = RouteFollower(listOf(a, b, a))
        val total = follower.totalMeters

        follower.update(b)
        val halfwayBack = GeoPoint(55.0044965, 37.0)
        val progress = follower.update(halfwayBack)!!
        assertTrue(abs(progress.remainingMeters - total / 4) < 30.0)
    }

    @Test
    fun followerDoesNotJumpBetweenCloseParallelLegs() {
        // Out-and-back on long straight segments, the way back runs ~8 m east of the way out.
        val a = GeoPoint(55.0, 37.0)
        val b = GeoPoint(55.008993, 37.0)
        val bBack = GeoPoint(55.008993, 37.000125)
        val aBack = GeoPoint(55.0, 37.000125)
        val follower = RouteFollower(listOf(a, b, bBack, aBack))
        val total = follower.totalMeters

        // GPS noise on the way out puts the fix closer to the way back.
        val out = follower.update(GeoPoint(55.0045, 37.00008))!!
        assertTrue(abs(out.remainingMeters - (total - 500)) < 30.0, "jumped to the way back: ${out.remainingMeters}")

        for (lat in listOf(55.006, 55.0075, 55.008993)) follower.update(GeoPoint(lat, 37.0))
        follower.update(bBack)
        // On the way back, noise puts the fix closer to the way out.
        val back = follower.update(GeoPoint(55.0045, 37.00004))!!
        assertTrue(abs(back.remainingMeters - 500) < 30.0, "jumped back to the way out: ${back.remainingMeters}")
    }

    @Test
    fun followerDoesNotFinishWhenMiddlePassesNearFinish() {
        val start = GeoPoint(55.0, 37.0)
        val nearFinish = GeoPoint(55.0036, 37.00018)
        val far = GeoPoint(55.0036, 37.006)
        val north = GeoPoint(55.0045, 37.006)
        val finish = GeoPoint(55.0036, 37.0)
        val follower = RouteFollower(listOf(start, nearFinish, far, north, finish))
        val total = follower.totalMeters

        val progress = follower.update(nearFinish)!!
        assertTrue(progress.remainingMeters > total / 2)
        assertFalse(progress.finished)
    }
}
