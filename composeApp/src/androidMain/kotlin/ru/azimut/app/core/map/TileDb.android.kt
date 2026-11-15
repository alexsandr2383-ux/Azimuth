package ru.azimut.app.core.map

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Android raster-tile-db converter over the framework SQLiteDatabase (no extra
 * dependency). Same schemas as the desktop version.
 */
actual object TileDb {
    private class Schema(
        val tableName: String,
        val zoomCol: String,
        val xCol: String,
        val yCol: String,
        val blobCol: String,
        val flipY: Boolean,
    )

    private fun detectSchema(db: SQLiteDatabase, kind: String): Schema? {
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { rows ->
            while (rows.moveToNext()) tables.add(rows.getString(0).lowercase())
        }
        return when (kind) {
            "gpkg" -> {
                if (!tables.contains("gpkg_contents")) return null
                db.rawQuery("SELECT table_name FROM gpkg_contents WHERE data_type='tiles' LIMIT 1", null).use { rs ->
                    if (!rs.moveToFirst()) return null
                    val tileTable = rs.getString(0)
                    Schema(tileTable, "zoom_level", "tile_column", "tile_row", "tile_data", flipY = true)
                }
            }
            else -> {
                val candidates = listOf("tiles", "images", "image", "maps")
                val name = candidates.firstOrNull { it in tables } ?: tables.firstOrNull() ?: return null
                val columns = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info($name)", null).use { rs ->
                    while (rs.moveToNext()) columns.add(rs.getString(1).lowercase())
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
        if (!head.startsWith("SQLite format 3")) return null
        val kind = if (path.endsWith(".gpkg", ignoreCase = true)) "gpkg" else "sqlitedb"
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val schema = detectSchema(db, kind) ?: return null
            var minZ = 99
            var maxZ = 0
            var count = 0L
            db.rawQuery(
                "SELECT MIN(${schema.zoomCol}), MAX(${schema.zoomCol}), COUNT(*) FROM ${schema.tableName}",
                null,
            ).use { rs ->
                if (rs.moveToFirst()) {
                    minZ = rs.getInt(0)
                    maxZ = rs.getInt(1)
                    count = rs.getLong(2)
                }
            }
            if (count == 0L) return null
            val format = detectFormat(db, schema)
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

    private fun detectFormat(db: SQLiteDatabase, schema: Schema): String {
        db.rawQuery(
            "SELECT substr(${schema.blobCol},1,8) FROM ${schema.tableName} " +
                "WHERE ${schema.blobCol} IS NOT NULL LIMIT 1",
            null,
        ).use { rs ->
            if (rs.moveToFirst()) {
                val bytes = rs.getBlob(0) ?: return "png"
                if (bytes.size >= 2) {
                    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
                    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
                    val ascii = String(bytes, Charsets.US_ASCII)
                    if (ascii.startsWith("RIFF")) return "webp"
                }
            }
        }
        return "png"
    }

    actual fun convertToMbtiles(path: String, destPath: String, flipY: Boolean): Boolean = runCatching {
        val kind = if (path.endsWith(".gpkg", ignoreCase = true)) "gpkg" else "sqlitedb"
        File(destPath).delete()
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { src ->
            val schema = detectSchema(src, kind) ?: return false
            val useFlip = flipY || schema.flipY
            SQLiteDatabase.openOrCreateDatabase(destPath, null).use { dst ->
                dst.beginTransaction()
                try {
                    dst.execSQL("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT)")
                    dst.execSQL(
                        "CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, " +
                            "tile_row INTEGER, tile_data BLOB)",
                    )
                    dst.execSQL("INSERT INTO metadata VALUES ('type', 'overlay')")
                    dst.execSQL("INSERT INTO metadata VALUES ('format', '${detectFormat(src, schema)}')")
                    val stmt = dst.compileStatement(
                        "INSERT INTO tiles VALUES (?,?,?,?)",
                    )
                    src.rawQuery(
                        "SELECT ${schema.zoomCol}, ${schema.xCol}, ${schema.yCol}, ${schema.blobCol} " +
                            "FROM ${schema.tableName} WHERE ${schema.blobCol} IS NOT NULL",
                        null,
                    ).use { rs ->
                        while (rs.moveToNext()) {
                            val z = rs.getInt(0)
                            val x = rs.getInt(1)
                            val y = rs.getInt(2)
                            val blob = rs.getBlob(3) ?: continue
                            val tmsY = if (useFlip) (1 shl z) - 1 - y else y
                            stmt.bindLong(1, z.toLong())
                            stmt.bindLong(2, x.toLong())
                            stmt.bindLong(3, tmsY.toLong())
                            stmt.bindBlob(4, blob)
                            stmt.executeInsert()
                        }
                    }
                    dst.execSQL(
                        "CREATE INDEX IF NOT EXISTS tiles_idx ON tiles (zoom_level, tile_column, tile_row)",
                    )
                    dst.setTransactionSuccessful()
                } finally {
                    dst.endTransaction()
                }
            }
        }
        File(destPath).length() > 0
    }.getOrDefault(false)
}
