package ru.azimut.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.azimut.app.core.compare.OFF_ROUTE_THRESHOLD_M
import ru.azimut.app.core.compare.compareTracks
import ru.azimut.app.core.dem.DemStore
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint

class TrackCompareTest {

    private fun track(points: List<TrackPoint>, distance: Double = 0.0, plannedTime: Double? = null): Track =
        Track(id = "t", name = "T", profile = "hike", startedAt = 0, points = points, distanceMeters = distance,
            kind = KIND_ROUTE, plannedTimeSeconds = plannedTime)

    private fun p(lat: Double, lon: Double, timeMillis: Long = 0L) = TrackPoint(lat = lat, lon = lon, timeMillis = timeMillis)

    /** Straight east–west plan line at 47°N from 41.0 to 41.02. */
    private fun planLine(distance: Double = 0.0, plannedTime: Double? = null): Track = track(
        listOf(p(47.0, 41.0), p(47.0, 41.02)),
        distance = distance,
        plannedTime = plannedTime,
    )

    @Test
    fun lateralDeviationAndOffRouteShare() {
        // Recorded: one point exactly on the line, one ~55.7 m north of it (beyond the threshold).
        val recorded = track(
            listOf(p(47.0, 41.01), p(47.0005, 41.01)),
        )
        val r = compareTracks(recorded, planLine(), DemStore())
        assertEquals(0.5, r.offRouteShare!!, 1e-9)
        assertTrue(r.lateralMaxMeters!! > OFF_ROUTE_THRESHOLD_M && r.lateralMaxMeters < 57.0, "max=${r.lateralMaxMeters}")
        assertTrue(r.lateralAvgMeters!! > 27.0 && r.lateralAvgMeters < 29.0, "avg=${r.lateralAvgMeters}")
    }

    @Test
    fun distanceAndTimeDeltas() {
        // Recorded walks half of the plan line: two 0.005°-lon steps (~380 m each at 47°N).
        val recorded = track(
            listOf(p(47.0, 41.0, 1_000_000L), p(47.0, 41.005, 1_600_000L), p(47.0, 41.01, 2_200_000L)),
        )
        val r = compareTracks(recorded, planLine(distance = 1518.0, plannedTime = 3600.0), DemStore())
        assertTrue(r.actualDistanceMeters!! in 758.0..762.0, "dist=${r.actualDistanceMeters}")
        assertEquals(1200.0, r.actualTimeSeconds!!, 1e-9)
        assertEquals(3600.0, r.plannedTimeSeconds!!, 1e-9)
    }

    @Test
    fun missingTimesAndOldPlan() {
        val recorded = track(listOf(p(47.0, 41.0), p(47.0, 41.01)))
        val r = compareTracks(recorded, planLine(plannedTime = null), DemStore())
        assertNull(r.actualTimeSeconds)
        assertNull(r.plannedTimeSeconds)
    }

    @Test
    fun emptyRecordedPoints() {
        val r = compareTracks(track(emptyList()), planLine(), DemStore())
        assertNull(r.lateralAvgMeters)
        assertNull(r.lateralMaxMeters)
        assertNull(r.offRouteShare)
        assertNull(r.actualDistanceMeters)
        assertNull(r.actualTimeSeconds)
        assertNull(r.actualElevation)
        assertEquals("T", r.plannedName)
    }

    @Test
    fun noDemNoHeights() {
        val recorded = track(listOf(p(47.0, 41.0), p(47.0, 41.01)))
        val r = compareTracks(recorded, planLine(), DemStore())
        assertNull(r.plannedElevation)
        assertNull(r.actualElevation)
    }
}
