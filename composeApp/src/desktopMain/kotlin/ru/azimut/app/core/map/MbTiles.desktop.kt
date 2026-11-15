package ru.azimut.app.core.map

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

actual object MbTiles {
    private val connections = ConcurrentHashMap<String, Connection>()

    actual fun readInfo(path: String): MbTilesInfo? = runCatching {
        val file = File(path)
        if (!file.isFile) return null
        open(path).let { conn ->
            synchronized(conn) {
                val metadata = HashMap<String, String>()
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT name, value FROM metadata").use { rs ->
                        while (rs.next()) metadata[rs.getString(1).lowercase()] = rs.getString(2).orEmpty()
                    }
                }
                var minZoom: Int? = null
                var maxZoom: Int? = null
                if ("minzoom" !in metadata || "maxzoom" !in metadata) {
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles").use { rs ->
                            if (rs.next()) {
                                minZoom = rs.getInt(1)
                                maxZoom = rs.getInt(2)
                            }
                        }
                    }
                }
                MbTilesInfo.from(metadata, file.nameWithoutExtension, minZoom, maxZoom)
            }
        }
    }.getOrElse {
        close(path)
        null
    }

    /** Tile in XYZ addressing; MBTiles stores rows in TMS order (y flipped). */
    fun readTile(path: String, z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val conn = open(path)
        synchronized(conn) {
            conn.prepareStatement(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?"
            ).use { st ->
                st.setInt(1, z)
                st.setInt(2, x)
                st.setInt(3, (1 shl z) - 1 - y)
                st.executeQuery().use { rs -> if (rs.next()) rs.getBytes(1) else null }
            }
        }
    }.getOrNull()

    /**
     * Writes tiles (XYZ addressing) into a new raster .mbtiles at [dest].
     * [bytesOf] returns null for tiles that are not available; those are skipped.
     * Returns the number of tiles written.
     */
    fun write(
        dest: File,
        name: String,
        tiles: List<Triple<Int, Int, Int>>,
        bytesOf: (z: Int, x: Int, y: Int) -> ByteArray?,
    ): Int {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.delete()
        var written = 0
        var format: String? = null
        var minZ = Int.MAX_VALUE
        var maxZ = Int.MIN_VALUE
        DriverManager.getConnection("jdbc:sqlite:${tmp.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE metadata (name TEXT, value TEXT)")
                st.executeUpdate(
                    "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)"
                )
                st.executeUpdate("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")
            }
            conn.autoCommit = false
            conn.prepareStatement("INSERT OR REPLACE INTO tiles VALUES (?, ?, ?, ?)").use { insert ->
                for ((z, x, y) in tiles) {
                    val bytes = bytesOf(z, x, y) ?: continue
                    if (format == null) format = imageFormat(bytes)
                    insert.setInt(1, z)
                    insert.setInt(2, x)
                    insert.setInt(3, (1 shl z) - 1 - y)
                    insert.setBytes(4, bytes)
                    insert.addBatch()
                    minZ = minOf(minZ, z)
                    maxZ = maxOf(maxZ, z)
                    if (++written % 500 == 0) insert.executeBatch()
                }
                insert.executeBatch()
            }
            if (written > 0) {
                val bounds = boundsOf(tiles.filter { it.first == maxZ })
                conn.prepareStatement("INSERT INTO metadata VALUES (?, ?)").use { meta ->
                    for ((key, value) in listOf(
                        "name" to name,
                        "format" to (format ?: "png"),
                        "type" to "baselayer",
                        "version" to "1.1",
                        "minzoom" to minZ.toString(),
                        "maxzoom" to maxZ.toString(),
                        "bounds" to bounds,
                    )) {
                        meta.setString(1, key)
                        meta.setString(2, value)
                        meta.addBatch()
                    }
                    meta.executeBatch()
                }
            }
            conn.commit()
        }
        if (written == 0) {
            tmp.delete()
            return 0
        }
        close(dest.absolutePath)
        java.nio.file.Files.move(
            tmp.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        return written
    }

    private fun imageFormat(bytes: ByteArray): String = when {
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size > 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> "png"
    }

    /** "west,south,east,north" covering the given tiles of one zoom level. */
    private fun boundsOf(tiles: List<Triple<Int, Int, Int>>): String {
        if (tiles.isEmpty()) return "-180,-85,180,85"
        val z = tiles.first().first
        val n = (1 shl z).toDouble()
        fun lon(x: Int) = x / n * 360.0 - 180.0
        fun lat(y: Int) = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n))))
        val west = lon(tiles.minOf { it.second })
        val east = lon(tiles.maxOf { it.second } + 1)
        val north = lat(tiles.minOf { it.third })
        val south = lat(tiles.maxOf { it.third } + 1)
        return "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.ROOT, west, south, east, north)
    }

    actual fun closeAll() {
        connections.values.forEach { runCatching { it.close() } }
        connections.clear()
    }

    fun close(path: String) {
        connections.remove(path)?.let { runCatching { it.close() } }
    }

    private fun open(path: String): Connection = connections.getOrPut(path) {
        DriverManager.getConnection("jdbc:sqlite:file:${File(path).absolutePath}?mode=ro")
    }
}
