package ru.azimut.app

import ru.azimut.app.core.export.PdfWriter
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.dem.DemTile
import ru.azimut.app.core.dem.DemStore
import ru.azimut.app.core.dem.TrackElevation
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.sync.SyncEngine
import ru.azimut.app.core.sync.SyncEntry
import ru.azimut.app.core.sync.SyncManifest
import ru.azimut.app.core.import.TrackImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncAndElevationTest {

    @Test
    fun syncPlanNewerWinsAndConflicts() {
        val local = SyncManifest(
            listOf(
                SyncEntry("a.txt", 10, 2000), // newer locally
                SyncEntry("b.txt", 10, 1000), // newer remotely
                SyncEntry("c.txt", 10, 1500), // same time, different size -> conflict
                SyncEntry("d.txt", 10, 1500), // same time, same size -> nothing
                SyncEntry("new-local.txt", 5, 900), // only local
            ),
        )
        val remote = listOf(
            SyncEntry("a.txt", 10, 1000),
            SyncEntry("b.txt", 10, 2000),
            SyncEntry("c.txt", 99, 1500),
            SyncEntry("d.txt", 10, 1500),
            SyncEntry("new-remote.txt", 7, 800),
        )
        val plan = SyncEngine.plan(local, remote)
        assertEquals(listOf("a.txt", "new-local.txt"), plan.upload)
        assertEquals(listOf("b.txt", "new-remote.txt"), plan.download)
        assertEquals(1, plan.conflicts.size)
        assertEquals("c.txt", plan.conflicts[0].path)
    }

    @Test
    fun syncManifestExcludesBackups() {
        val local = SyncManifest(
            listOf(
                SyncEntry("tracks/x.gpx", 10, 1000),
                SyncEntry("backups/azimut-backup.zip", 10, 1000),
                SyncEntry("settings.json.tmp", 10, 1000),
            ),
        )
        val plan = SyncEngine.plan(local, emptyList())
        // backups/ and *.tmp are never synced; no file is ever deleted remotely.
        assertEquals(listOf("tracks/x.gpx"), plan.upload)
    }

    private fun flatDem(height: Double): DemStore {
        val rows = 901
        val cols = 901
        val tile = DemTile(
            latTop = 56.0, lonLeft = 38.0, stepDeg = 1.0 / 900.0, rows = rows, cols = cols,
            nodata = -32768.0,
            data = ShortArray(rows * cols) { height.toInt().toShort() },
        )
        return object : DemStore() {
            override fun heightAt(lat: Double, lon: Double): Double? = tile.sample(lat, lon)
        }
    }

    @Test
    fun elevationAscendDescendAndGrade() {
        // 500 m long climb of 50 m, then a flat part.
        val points = mutableListOf<TrackPoint>()
        for (i in 0..20) {
            points.add(TrackPoint(lat = 55.0 + i * 0.0002, lon = 37.0, altitude = i * 2.5))
        }
        for (i in 1..10) {
            points.add(TrackPoint(lat = 55.004 + i * 0.0002, lon = 37.0, altitude = 50.0))
        }
        val dem = flatDem(0.0) // DEM is flat; recorded altitudes are the fallback
        val profile = TrackElevation.analyze(points.toList(), dem)
        // DEM has no tile for these points -> recorded altitude is used.
        assertTrue(profile.hasData)
        assertEquals(50.0, profile.ascendMeters, 5.0)
        assertEquals(0.0, profile.descendMeters, 1.0)
        assertTrue((profile.maxGradePercent ?: 0.0) > 4.0, "grade was ${profile.maxGradePercent}")
    }

    @Test
    fun pdfStructureIsValid() {
        val w = 4
        val h = 4
        val rgb = IntArray(w * h) { 0xFF0000 }
        val pdf = PdfWriter().build(rgb, w, h, listOf("Azimuth export", "Distance km: 12.3"))
        val text = pdf.toString(Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Type /Catalog"))
        assertTrue(text.contains("/FlateDecode"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("startxref"))
    }

    @Test
    fun trackImportDetectsFormats() {
        assertEquals(
            ru.azimut.app.core.import.TrackImport.detect("a.gpx", "<gpx></gpx>"),
            ru.azimut.app.core.import.TrackImport.Kind.GPX,
        )
        assertEquals(
            ru.azimut.app.core.import.TrackImport.detect("b.kml", "<kml><Document/></kml>"),
            ru.azimut.app.core.import.TrackImport.Kind.KML,
        )
        assertEquals(
            ru.azimut.app.core.import.TrackImport.detect("c.tcx", "<TrainingCenterDatabase/>"),
            ru.azimut.app.core.import.TrackImport.Kind.TCX,
        )
        assertEquals(
            ru.azimut.app.core.import.TrackImport.detect(
                "d.geojson",
                "{\"type\":\"FeatureCollection\",\"features\":[{\"geometry\":{\"type\":\"Polygon\"}}]}",
            ),
            ru.azimut.app.core.import.TrackImport.Kind.OOPT_GEOJSON,
        )
    }

    @Test
    fun kmlTrackImport() {
        val kml = """
        <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
          <Placemark><name>Line</name>
            <LineString><coordinates>37.0,55.0,100 37.01,55.01,110</coordinates></LineString>
          </Placemark>
          <Placemark><name>Pin</name><Point><coordinates>37.02,55.02</coordinates></Point></Placemark>
        </Document></kml>
        """.trimIndent()
        val parsed = TrackImport.parse("route.kml", kml, "enduro")
        assertEquals(1, parsed.tracks.size)
        assertEquals(2, parsed.tracks[0].points.size)
        assertEquals(1, parsed.waypoints.size)
        assertEquals("Pin", parsed.waypoints[0].name)
        assertNotNull(parsed.tracks[0].distanceMeters > 0.0)
    }
}
