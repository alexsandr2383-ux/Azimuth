package ru.azimut.app

import ru.azimut.app.core.map.MapImportResult
import ru.azimut.app.core.map.MapStyles
import ru.azimut.app.core.map.MbTiles
import ru.azimut.app.core.map.UserMaps
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MbTilesTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Test
    fun writesReadsAndRegistersRasterMbtiles() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-mbtiles").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val dir = File(temp, "Documents/Azimut/NavData/maps")
            val file = File(dir, "area.mbtiles")
            val tiles = listOf(Triple(10, 600, 300), Triple(11, 1200, 601), Triple(11, 1201, 601))
            val written = MbTiles.write(file, "Test area", tiles) { z, x, _ -> if (x == 1201) null else png + z.toByte() }
            assertEquals(2, written)

            val info = assertNotNull(MbTiles.readInfo(file.absolutePath))
            assertEquals("Test area", info.name)
            assertEquals("png", info.format)
            assertEquals(10, info.minZoom)
            assertEquals(11, info.maxZoom)
            assertContentEquals(png + 11.toByte(), MbTiles.readTile(file.absolutePath, 11, 1200, 601))
            assertNull(MbTiles.readTile(file.absolutePath, 11, 1201, 601))

            assertEquals(MapImportResult.OK, UserMaps.checkImported("area.mbtiles"))
            UserMaps.refresh()
            val style = MapStyles.byId(UserMaps.PREFIX + "area.mbtiles")
            assertEquals("Test area", style.titleRu)
            assertEquals(11, style.maxZoom)

            File(dir, "junk.mbtiles").writeText("not a database")
            assertEquals(MapImportResult.NOT_MBTILES, UserMaps.checkImported("junk.mbtiles"))
            assertEquals(false, File(dir, "junk.mbtiles").exists())
            MbTiles.close(file.absolutePath)
        } finally {
            System.setProperty("user.home", oldHome)
            UserMaps.refresh()
            temp.deleteRecursively()
        }
    }
}
