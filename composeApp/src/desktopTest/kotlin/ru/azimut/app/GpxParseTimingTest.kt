package ru.azimut.app

import org.junit.Test
import ru.azimut.app.core.gpx.Gpx
import java.io.File

/** Manual timing probe for the 1.4 MB / 6654-point track that stalled the phone startup. */
class GpxParseTimingTest {
    @Test
    fun parseTiming() {
        val file = File(System.getProperty("user.home"), "az-slow-track.gpx")
        if (!file.isFile) {
            println("AZ-TIME file missing, skipping")
            return
        }
        val text = file.readText()
        var t = System.currentTimeMillis()
        val parsed = Gpx.parseImport(text)
        println("AZ-TIME full parseImport: ${System.currentTimeMillis() - t} ms, points=${parsed.tracks.firstOrNull()?.points?.size}")
    }
}
