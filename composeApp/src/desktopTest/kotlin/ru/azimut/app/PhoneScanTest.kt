package ru.azimut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.storage.PhoneScan
import java.io.File
import java.nio.file.Files

class PhoneScanTest {
    @Test
    fun `phone scan finds and dedups files in Documents`() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-phonescan").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val gpx = File(temp, "Documents/trip.gpx")
            gpx.parentFile.mkdirs()
            gpx.writeText("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>")
            val map = File(temp, "Downloads/area.mbtiles")
            map.parentFile.mkdirs()
            map.writeBytes(ByteArray(64))

            val names = PhoneScan.pending().map { it.name }
            assertTrue("found: " + names, "trip.gpx" in names)
            assertTrue("found: " + names, "area.mbtiles" in names)

            PhoneScan.mark(PhoneScan.pending())
            assertEquals("marked files must not reappear", 0, PhoneScan.pending().size)

            map.writeBytes(ByteArray(65))
            assertEquals("size change must re-surface the file", 1, PhoneScan.pending().size)
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
        assertTrue(PlatformFiles.hasAllFilesAccess())
    }
}
