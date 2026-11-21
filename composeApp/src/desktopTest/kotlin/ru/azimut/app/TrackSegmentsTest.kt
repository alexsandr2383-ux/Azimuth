package ru.azimut.app

import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.location.LocationFix
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackSegmentsTest {
    @Test
    fun gpxRoundTripKeepsSegments() {
        val pts = listOf(
            TrackPoint(55.0, 37.0, timeMillis = 1000),
            TrackPoint(55.001, 37.0, timeMillis = 2000),
            TrackPoint(55.5, 37.5, timeMillis = 90000, segStart = true),
            TrackPoint(55.501, 37.5, timeMillis = 91000),
        )
        val xml = Gpx.writeTrack(Track(id = "t", name = "t", profile = "enduro", startedAt = 1000, points = pts))
        assertEquals(2, Regex("<trkseg>").findAll(xml).count())
        val back = Gpx.parseTracks(xml).single()
        assertEquals(listOf(false, false, true, false), back.points.map { it.segStart })
        // the 70+ km bridge must not count as distance
        assertTrue(back.distanceMeters < 500)
    }

    @Test
    fun recorderSplitsOnGap() {
        TrackRecorder.configure(1, false)
        TrackRecorder.start("enduro", false)
        val t0 = 1_000_000L
        TrackRecorder.onFix(LocationFix(55.0, 37.0, timeMillis = t0))
        TrackRecorder.onFix(LocationFix(55.0001, 37.0, timeMillis = t0 + 1_000))
        TrackRecorder.onFix(LocationFix(56.0, 38.0, timeMillis = t0 + 120_000))
        val s = TrackRecorder.state.value
        assertEquals(listOf(false, false, true), s.points.map { it.segStart })
        assertTrue(s.distanceMeters < 100)
        TrackRecorder.clear()
        assertFalse(TrackRecorder.state.value.recording)
    }
}
