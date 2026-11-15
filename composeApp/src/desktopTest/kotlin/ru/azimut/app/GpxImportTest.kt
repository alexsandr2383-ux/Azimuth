package ru.azimut.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.gpx.GpxImporter
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.storage.PointsStore
import ru.azimut.app.core.storage.SettingsStore
import ru.azimut.app.core.storage.TrackStore

class GpxImportTest {
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <wpt lat="44.70000" lon="38.70000"><name>Test point</name></wpt>
          <trk>
            <name></name>
            <type>hiking</type>
            <trkseg>
              <trkpt lat="44.70000" lon="38.70000"><ele>100</ele><time>2026-10-26T10:00:00Z</time></trkpt>
              <trkpt lat="44.71000" lon="38.71000"><ele>120</ele><time>2026-10-26T10:01:00Z</time></trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun importsTrackAndWaypointWithStableIds() {
        val first = GpxImporter.parse("sample.gpx", sample, "enduro")
        assertFalse(first.outcome.failed)
        assertFalse(first.outcome.empty)
        assertEquals(1, first.outcome.tracks)
        assertEquals(1, first.outcome.waypoints)

        val track = first.tracks.single()
        assertEquals("sample", track.name)
        assertEquals("foot", track.profile)
        assertEquals(2, track.points.size)
        assertTrue(track.distanceMeters > 0)
        assertEquals("Test point", first.waypoints.single().name)

        val second = GpxImporter.parse("sample.gpx", sample, "enduro")
        assertEquals(track.id, second.tracks.single().id)
        assertEquals(first.waypoints.single().id, second.waypoints.single().id)
    }

    @Test
    fun usesFallbackProfileAndFileName() {
        val noType = sample.replace("<type>hiking</type>", "")
        val result = GpxImporter.parse("moto-route.gpx", noType, "motorcycle")
        assertEquals("motorcycle", result.tracks.single().profile)
        assertEquals("moto-route", result.tracks.single().name)
    }

    @Test
    fun rejectsNonGpxAndReportsEmptyDocument() {
        assertTrue(GpxImporter.parse("bad.gpx", "hello", "enduro").outcome.failed)
        assertTrue(
            GpxImporter.parse("empty.gpx", "<gpx version=\"1.1\"></gpx>", "enduro")
                .outcome.empty
        )
    }

    @Test
    fun importsTenThousandWaypointsQuickly() {
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"test\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            for (i in 0 until 10_000) {
                val lat = 43.0 + (i % 100) * 0.01
                val lon = 37.0 + (i / 100) * 0.01
                append("  <wpt lat=\"").append(lat).append("\" lon=\"").append(lon).append("\">")
                append("<name>POI ").append(i).append("</name>")
                append("<desc>natural=spring</desc>")
                append("<ele>").append(100 + i % 50).append("</ele>")
                append("</wpt>\n")
            }
            append("</gpx>\n")
        }

        val started = System.currentTimeMillis()
        val result = GpxImporter.parse("poi.gpx", xml, "foot")
        val elapsed = System.currentTimeMillis() - started

        assertFalse(result.outcome.failed)
        assertEquals(10_000, result.outcome.waypoints)
        assertEquals(10_000, result.waypoints.size)
        assertEquals("POI 0", result.waypoints.first().name)
        assertEquals("natural=spring", result.waypoints.first().description)
        assertEquals(10_000, result.waypoints.map { it.id }.toSet().size)
        assertTrue(elapsed < 5_000, "Importing 10k waypoints took $elapsed ms")
    }

    @Test
    fun corruptStorageIsPreservedAndIndexRebuilt() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-test").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val store = TrackStore()
            val track = Track(
                id = "track-1",
                name = "Saved ride",
                profile = "enduro",
                startedAt = 1_000L,
                points = listOf(
                    TrackPoint(lat = 44.7, lon = 38.7, timeMillis = 1_000L),
                    TrackPoint(lat = 44.71, lon = 38.71, timeMillis = 2_000L),
                ),
            )
            store.save(track)

            PlatformFiles.writeText("tracks/index.json", "[{\"id\":\"trunc")
            val rebuilt = store.list()
            assertEquals(listOf("track-1"), rebuilt.map { it.id })
            assertEquals("Saved ride", rebuilt.single().name)
            assertTrue(PlatformFiles.exists("tracks/index.json.corrupt"))

            PlatformFiles.writeText("points.json", "[{\"id\":\"p1\",")
            assertTrue(PointsStore().load().isEmpty())
            assertEquals("[{\"id\":\"p1\",", PlatformFiles.readText("points.json.corrupt"))
            assertFalse(PlatformFiles.exists("points.json.tmp"))
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }

    @Test
    fun recoversAutosaveAndReadsBomJson() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-test").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val track = Track(
                id = "autosave-test",
                name = "Crash track",
                profile = "enduro",
                startedAt = 1_000L,
                points = listOf(
                    TrackPoint(lat = 44.7, lon = 38.7, timeMillis = 1_000L),
                    TrackPoint(lat = 44.71, lon = 38.71, timeMillis = 2_000L),
                ),
            )
            val store = TrackStore()
            store.autosave(track)
            val loaded = store.loadAutosave()
            assertNotNull(loaded)
            assertEquals("Crash track", loaded.name)
            assertEquals("enduro", loaded.profile)
            assertEquals(2, loaded.points.size)

            store.clearAutosave()
            assertNull(store.loadAutosave())

            PlatformFiles.ensureDirs()
            PlatformFiles.writeText(
                "settings.json",
                "\uFEFF{\"language\":\"en\",\"mapStyle\":\"osm\"}",
            )
            assertEquals("en", SettingsStore().load().language)
            assertEquals("osm", SettingsStore().load().mapStyle)

            PlatformFiles.writeText(
                "points.json",
                "\uFEFF[{\"id\":\"p1\",\"name\":\"Point\",\"lat\":44.7,\"lon\":38.7}]",
            )
            assertEquals("p1", PointsStore().load().single().id)

            PlatformFiles.writeText(
                "tracks/index.json",
                "\uFEFF[{\"id\":\"t1\",\"name\":\"Track\",\"profile\":\"enduro\"," +
                    "\"startedAt\":1000,\"distanceMeters\":10.0,\"pointCount\":2}]",
            )
            assertEquals("t1", store.list().single().id)

            val parsed = Gpx.parseImport("\uFEFF$sample")
            assertEquals(1, parsed.tracks.size)
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }
}
