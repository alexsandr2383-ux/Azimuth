package ru.azimut.app

import ru.azimut.app.core.import.PointsIO
import ru.azimut.app.core.import.ZipKml
import ru.azimut.app.core.map.TileDb
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TileDbAndZipTest {

    /** Minimal valid PNG signature + IHDR stub; enough for the format sniffing. */
    private fun fakePng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG magic
        0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, // IHDR length+type
        1, 0, 0, 0, 1, 0, 0, 0, 8, 6, 0, 0, 0, // w=1 h=1 bit=8 col=6
        0x1A, 0x72, 0x1A, 0x57, // CRC placeholder
    )

    private fun pngBlob(): ByteArray = fakePng() + ByteArray(64)

    @Test
    fun rmapsSqliteConvertsToMbtilesNoFlip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "azimut-test-${System.nanoTime()}").apply { mkdirs() }
        val src = File(dir, "rmaps.sqlitedb")
        val dst = File(dir, "rmaps.mbtiles")
        DriverManager.getConnection("jdbc:sqlite:${src.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE tiles (x INTEGER, y INTEGER, z INTEGER, image BLOB)")
            }
            conn.prepareStatement("INSERT INTO tiles VALUES (?,?,?,?)").use { ps ->
                // TMS y values, as RMaps-family databases store them.
                ps.setInt(1, 137); ps.setInt(2, 84); ps.setInt(3, 8); ps.setBytes(4, pngBlob()); ps.executeUpdate()
                ps.setInt(1, 138); ps.setInt(2, 85); ps.setInt(3, 8); ps.setBytes(4, pngBlob()); ps.executeUpdate()
                ps.setInt(1, 55); ps.setInt(2, 27); ps.setInt(3, 6); ps.setBytes(4, pngBlob()); ps.executeUpdate()
            }
        }

        val info = TileDb.readInfo(src.absolutePath)
        assertNotNull(info, "readInfo should detect the rmaps database")
        assertEquals("sqlitedb", info.kind)
        assertEquals("png", info.format)
        assertEquals(6, info.minZoom)
        assertEquals(8, info.maxZoom)
        assertEquals(3L, info.tileCount)

        assertTrue(TileDb.convertToMbtiles(src.absolutePath, dst.absolutePath, flipY = false))
        DriverManager.getConnection("jdbc:sqlite:${dst.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT COUNT(*) FROM tiles")
                rs.next()
                assertEquals(3L, rs.getLong(1))
                val rs2 = st.executeQuery(
                    "SELECT tile_column, tile_row, zoom_level FROM tiles WHERE zoom_level=8 AND tile_column=137",
                )
                rs2.next()
                // TMS -> mbtiles: direct copy, y unchanged.
                assertEquals(84, rs2.getInt(2))
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun gpkgConvertsToMbtilesWithFlip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "azimut-test-${System.nanoTime()}").apply { mkdirs() }
        val src = File(dir, "tiles.gpkg")
        val dst = File(dir, "tiles.mbtiles")
        DriverManager.getConnection("jdbc:sqlite:${src.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE gpkg_contents (table_name TEXT, data_type TEXT, identifier TEXT, " +
                        "description TEXT, last_change TEXT, min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER)",
                )
                st.executeUpdate("CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, zoom_level INTEGER, " +
                    "tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
                st.executeUpdate(
                    "INSERT INTO gpkg_contents VALUES ('t', 'tiles', 't', '', '2025-01-01', 0, 0, 1, 1, 4326)",
                )
            }
            conn.prepareStatement("INSERT INTO t (zoom_level, tile_column, tile_row, tile_data) VALUES (?,?,?,?)").use { ps ->
                ps.setInt(1, 5); ps.setInt(2, 10); ps.setInt(3, 3); ps.setBytes(4, pngBlob()); ps.executeUpdate()
            }
        }

        val info = TileDb.readInfo(src.absolutePath)
        assertNotNull(info)
        assertEquals("gpkg", info.kind)

        assertTrue(TileDb.convertToMbtiles(src.absolutePath, dst.absolutePath, flipY = false))
        DriverManager.getConnection("jdbc:sqlite:${dst.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT tile_row FROM tiles WHERE zoom_level=5 AND tile_column=10")
                rs.next()
                // GeoPackage rows count from the top; mbtiles/TMS counts from the bottom: 2^5-1-3 = 28.
                assertEquals(28, rs.getInt(1))
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun zipKmlExtractPrefersDocKml() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("images/logo.png"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("other.kml"))
                zip.write("<kml>other</kml>".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("doc.kml"))
                zip.write("<kml>doc</kml>".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertEquals("<kml>doc</kml>", ZipKml.extract(bytes))
    }

    @Test
    fun pointsKmzRoundTrip() {
        val points = listOf(
            ru.azimut.app.core.model.Waypoint(id = "w1", name = "База", lat = 55.5, lon = 37.5),
        )
        val (ext, kmz) = PointsIO.export(PointsIO.Format.KMZ, points, "round trip")
        assertEquals(".kmz", ext)
        val kml = ZipKml.extract(kmz)
        assertNotNull(kml)
        val parsed = PointsIO.import("points.kmz", "", kmz)
        assertEquals(1, parsed.size)
        assertEquals("База", parsed[0].name)
        assertEquals(55.5, parsed[0].lat, 1e-6)
    }
}
