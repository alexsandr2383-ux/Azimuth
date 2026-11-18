package ru.azimut.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.azimut.app.core.export.AutoExport
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.platform.PlatformFiles
import java.nio.file.Files

class AutoExportTest {
    private fun track(name: String) = Track(
        id = "track-1",
        name = name,
        profile = "enduro",
        startedAt = 1_000L,
        points = listOf(
            TrackPoint(lat = 44.7, lon = 38.7, timeMillis = 1_000L),
            TrackPoint(lat = 44.71, lon = 38.71, timeMillis = 2_000L),
        ),
    )

    @Test
    fun fileNameSanitizesAndUniquifies() {
        assertEquals("Маршрут 13.11 01-20.gpx", AutoExport.fileName(track("Маршрут 13.11 01:20")))
        assertEquals("a-b-c-d-e-f-g-h-i-j.gpx", AutoExport.fileName(track("a/b\\c:d*e?f\"g<h>i|j")))
        assertEquals("track-1000.gpx", AutoExport.fileName(track("   ")))
        assertEquals("name.gpx", AutoExport.fileName(track("name"), taken = setOf("other.gpx")))
        assertEquals("name (2).gpx", AutoExport.fileName(track("name"), taken = setOf("NAME.gpx")))
    }

    @Test
    fun writesCopyIntoExportFolderAndUniquifiesOnDisk() = runBlocking {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-autoexport").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            PlatformFiles.ensureDirs()
            assertEquals("Test ride.gpx", AutoExport.saveTrackCopy(track("Test ride"), ""))
            assertTrue(PlatformFiles.exists("export/Test ride.gpx"))
            val gpx = PlatformFiles.readText("export/Test ride.gpx") ?: ""
            assertTrue("<trkpt" in gpx)

            // Same name again: no overwrite, a « (2)» copy appears.
            assertEquals("Test ride (2).gpx", AutoExport.saveTrackCopy(track("Test ride"), ""))
            assertTrue(PlatformFiles.exists("export/Test ride (2).gpx"))
            assertFalse(PlatformFiles.listFiles("export").isEmpty())
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }
}
