package ru.azimut.app.core.map

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Statement

/**
 * Desktop raster-tile-db converter over sqlite-jdbc. Handles:
 *  - RMaps family: table tiles(x, y, z[, s|image]) — TMS y assumed;
 *  - GeoPackage: gpkg_contents(data_type='tiles') -> tile table with
 *    zoom_level/tile_column/tile_row/tile_data, rows flipped to TMS.
 */
actual object TileDb {
    private class Schema(
        val tableName: String,
        val zoomExpr: String,
        val xExpr: String,
        val yExpr: String,
        val blobExpr: String,
        val flipY: Boolean,
    )

    private fun detectSchema(conn: Connection, kind: String): Schema? {
        val tables = conn.createStatement().use { st ->
            val rows = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
            val out = mutableListOf<String>()
            while (rows.next()) out.add(rows.getString(1).lowercase())
            out
        }
        return when (kind) {
            "gpkg" -> {
                if (!tables.contains("gpkg_contents")) return null
                conn.createStatement().use { st ->
                    val rs = st.executeQuery("SELECT table_name FROM gpkg_contents WHERE data_type='tiles' LIMIT 1")
                    if (!rs.next()) return null
                    val tileTable = rs.getString(1)
                    Schema(tileTable, "zoom_level", "tile_column", "tile_row", "tile_data", flipY = true)
                }
            }
            else -> {
                // RMaps/Locus/Orux-style: pick the first table with a blob column.
                val candidates = listOf("tiles", "images", "image", "maps")
                val name = candidates.firstOrNull { it in tables } ?: tables.firstOrNull() ?: return null
                val columns = conn.createStatement().use { st ->
                    val rs = st.executeQuery("PRAGMA table_info(${sqlIdent(name)})")
                    val out = mutableListOf<String>()
                    while (rs.next()) out.add(rs.getString(2).lowercase())
                    out
                }
                val blobCol = listOf("image", "image_data", "blob", "img", "tile_data").firstOrNull { it in columns }
                    ?: return null
                val zoomCol = listOf("z", "zoom", "zoom_level").firstOrNull { it in columns } ?: return null
                val xCol = listOf("x", "tile_column", "tilecolumn").firstOrNull { it in columns } ?: return null
                val yCol = listOf("y", "tile_row", "tilerow").firstOrNull { it in columns } ?: return null
                Schema(name, zoomCol, xCol, yCol, blobCol, flipY = false)
            }
        }
    }

    actual fun readInfo(path: String): TileDbInfo? = runCatching {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return null
        val head = file.inputStream().use { stream ->
            val buf = ByteArray(16)
            val n = stream.read(buf)
            String(buf, 0, n.coerceAtLeast(0), Charsets.US_ASCII)
        }
        val kind = if (head.startsWith("SQLite format 3")) {
            if (path.endsWith(".gpkg", ignoreCase = true)) "gpkg" else "sqlitedb"
        } else {
            return null
        }
        DriverManager.getConnection("jdbc:sqlite:$path").use { conn ->
            val schema = detectSchema(conn, kind) ?: return null
            var minZ = 99
            var maxZ = 0
            var count = 0L
            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    "SELECT MIN(${schema.zoomExpr}), MAX(${schema.zoomExpr}), COUNT(*) " +
                        "FROM ${sqlIdent(schema.tableName)}",
                )
                if (rs.next()) {
                    minZ = rs.getObject(1) as? Int ?: rs.getInt(1)
                    maxZ = rs.getInt(2)
                    count = rs.getLong(3)
                }
            }
            if (count == 0L) return null
            val format = detectFormat(conn, schema)
            TileDbInfo(
                kind = kind,
                format = format,
                minZoom = minZ.coerceIn(0, 22),
                maxZoom = maxZ.coerceIn(0, 22),
                tileCount = count,
                tableName = schema.tableName,
            )
        }
    }.getOrNull()

/** Table names come from an untrusted db file (sqlite_master) — quote defensively. */
    private fun sqlIdent(name: String): String =
        "\"" + name.replace("\"", "\"\"") + "\""

    private fun detectFormat(conn: Connection, schema: Schema): String {
        conn.createStatement().use { st ->
            val rs = st.executeQuery(
                "SELECT substr(${schema.blobExpr},1,8) FROM ${sqlIdent(schema.tableName)} " +
                    "WHERE ${schema.blobExpr} IS NOT NULL LIMIT 1",
            )
            if (rs.next()) {
                val bytes = rs.getBytes(1) ?: return "png"
                if (bytes.size >= 8) {
                    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
                    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
                    // RIFF....WEBP
                    if (bytes[0] == 0x52.toByte() && bytes[8 - 1] == 0x00.toByte() && bytes.size >= 8) {
                        val ascii = String(bytes, Charsets.US_ASCII)
                        if (ascii.startsWith("RIFF")) return "webp"
                    }
                }
            }
        }
        return "png"
    }

    actual fun convertToMbtiles(path: String, destPath: String, flipY: Boolean): Boolean = runCatching {
        val kind = if (path.endsWith(".gpkg", ignoreCase = true)) "gpkg" else "sqlitedb"
        File(destPath).delete()
        DriverManager.getConnection("jdbc:sqlite:$path").use { src ->
            val schema = detectSchema(src, kind) ?: return false
            val useFlip = flipY || schema.flipY
            DriverManager.getConnection("jdbc:sqlite:$destPath").use { dst ->
                dst.createStatement().use { st ->
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT)")
                    st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, " +
                            "tile_row INTEGER, tile_data BLOB)",
                    )
                }
                dst.createStatement().use { st ->
                    st.executeUpdate("INSERT INTO metadata VALUES ('type', 'overlay')")
                    st.executeUpdate("INSERT INTO metadata VALUES ('format', '${detectFormat(src, schema)}')")
                }
                val srcQuery =
                    "SELECT ${schema.zoomExpr}, ${schema.xExpr}, ${schema.yExpr}, ${schema.blobExpr} " +
                        "FROM ${sqlIdent(schema.tableName)} WHERE ${schema.blobExpr} IS NOT NULL"
                src.createStatement().use { st ->
                    val rs = st.executeQuery(srcQuery)
                    dst.autoCommit = false
                    val insert: PreparedStatement = dst.prepareStatement(
                        "INSERT INTO tiles VALUES (?,?,?,?)",
                    )
                    while (rs.next()) {
                        val z = rs.getInt(1)
                        val x = rs.getInt(2)
                        val y = rs.getInt(3)
                        val blob = rs.getBytes(4) ?: continue
                        // Untrusted source db: absurd zooms would break tms math and the
                        // mbtiles layout — skip instead of writing garbage tiles.
                        if (z !in 0..30) continue
                        val tmsY = if (useFlip) (1 shl z) - 1 - y else y
                        insert.setInt(1, z)
                        insert.setInt(2, x)
                        insert.setInt(3, tmsY)
                        insert.setBytes(4, blob)
                        insert.executeUpdate()
                    }
                    insert.close()
                    dst.commit()
                }
                dst.createStatement().use { st ->
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS tiles_idx ON tiles (zoom_level, tile_column, tile_row)")
                }
            }
        }
        File(destPath).length() > 0
    }.getOrDefault(false)
}
