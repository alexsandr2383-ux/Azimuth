package ru.azimut.app

import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.storage.PointsStore
import ru.azimut.app.ui.components.loadPhoto
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoTest {
    @Test
    fun waypointPhotosPersistLoadAndExport() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-photo").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val photo = File(temp, "Documents/Azimut/NavData/photos/p1.jpg").apply { parentFile.mkdirs() }
            ImageIO.write(BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB), "jpg", photo)

            val point = Waypoint(id = "w1", name = "Брод", lat = 44.7, lon = 38.7, photos = listOf("p1.jpg"))
            PointsStore().save(listOf(point))
            assertEquals(listOf("p1.jpg"), PointsStore().load().single().photos)

            val thumb = assertNotNull(loadPhoto("p1.jpg", 256))
            assertEquals(256, thumb.width)
            assertTrue(Gpx.writeWaypoints(listOf(point)).contains("<link href=\"photos/p1.jpg\">"))
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }
}
