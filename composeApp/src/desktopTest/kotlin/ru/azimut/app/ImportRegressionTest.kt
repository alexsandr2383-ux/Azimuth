package ru.azimut.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.azimut.app.core.import.TrackImport

/** gx:Track (Google Earth recordings) must import, and plain Track must keep working. */
class ImportRegressionTest {

    private val gxKml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>Earth ride</name>
              <gx:Track>
                <when>2026-01-01T10:00:00Z</when>
                <when>2026-01-01T10:01:00Z</when>
                <gx:coord>38.70000 44.70000</gx:coord>
                <gx:coord>38.71000 44.71000</gx:coord>
              </gx:Track>
            </Placemark>
          </Document>
        </kml>
    """.trimIndent()

    private val plainKml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>Plain ride</name>
              <LineString><coordinates>38.7,44.7 38.71,44.71</coordinates></LineString>
            </Placemark>
          </Document>
        </kml>
    """.trimIndent()

    @Test
    fun gxTrackImportsWithLonLatOrder() {
        val parsed = TrackImport.parse("ride.kml", gxKml, "enduro")
        assertEquals(1, parsed.tracks.size)
        val track = parsed.tracks.single()
        assertEquals(2, track.points.size)
        // gx:coord is "lon lat [alt]".
        assertEquals(44.70, track.points.first().lat, 1e-6)
        assertEquals(38.70, track.points.first().lon, 1e-6)
        assertEquals(44.71, track.points.last().lat, 1e-6)
        assertTrue(track.distanceMeters > 0)
    }

    @Test
    fun plainLineStringStillImports() {
        val parsed = TrackImport.parse("ride.kml", plainKml, "enduro")
        assertEquals(1, parsed.tracks.size)
        assertEquals(2, parsed.tracks.single().points.size)
    }
}
