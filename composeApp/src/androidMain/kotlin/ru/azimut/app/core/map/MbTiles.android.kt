package ru.azimut.app.core.map

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Only metadata is read here: MapLibre renders .mbtiles itself via the mbtiles:// scheme. */
actual object MbTiles {
    actual fun readInfo(path: String): MbTilesInfo? = runCatching {
        val file = File(path)
        if (!file.isFile) return null
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val metadata = HashMap<String, String>()
            db.rawQuery("SELECT name, value FROM metadata", null).use { c ->
                while (c.moveToNext()) metadata[c.getString(0).lowercase()] = c.getString(1).orEmpty()
            }
            var minZoom: Int? = null
            var maxZoom: Int? = null
            if ("minzoom" !in metadata || "maxzoom" !in metadata) {
                db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { c ->
                    if (c.moveToFirst()) {
                        minZoom = c.getInt(0)
                        maxZoom = c.getInt(1)
                    }
                }
            }
            MbTilesInfo.from(metadata, file.nameWithoutExtension, minZoom, maxZoom)
        }
    }.getOrNull()

    actual fun closeAll() = Unit
}
