package ru.azimut.app

import ru.azimut.app.core.location.NmeaParser
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NmeaTest {
    private fun withChecksum(body: String): String =
        "$" + body + "*" + body.fold(0) { acc, c -> acc xor c.code }.toString(16).uppercase().padStart(2, '0')

    @Test
    fun parsesRmcWithGgaAltitudeAndHdop() {
        val parser = NmeaParser()
        assertNull(parser.feed(withChecksum("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,")))
        val fix = assertNotNull(parser.feed(withChecksum("GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W")))
        assertTrue(abs(fix.lat - 48.1173) < 1e-4)
        assertTrue(abs(fix.lon - 11.516667) < 1e-4)
        assertEquals(545.4, fix.altitude)
        assertEquals(4.5f, fix.accuracy)
        assertTrue(abs(fix.speed!! - 11.523f) < 0.01f)
        assertEquals(84.4f, fix.bearing)
        assertEquals(764426119000L, fix.timeMillis)
    }

    @Test
    fun handlesSouthWestGnssTalkerAndRejectsBadInput() {
        val parser = NmeaParser()
        val fix = assertNotNull(parser.feed(withChecksum("GNRMC,010203.50,A,3326.400,S,07036.000,W,0.0,,281026,,,A")))
        assertTrue(abs(fix.lat + 33.44) < 1e-6)
        assertTrue(abs(fix.lon + 70.6) < 1e-6)
        assertNull(fix.bearing)
        assertNull(parser.feed(withChecksum("GPRMC,123519,V,,,,,,,230394,,")))
        assertNull(parser.feed("\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*00"))
        assertNull(parser.feed("garbage"))
    }
}
