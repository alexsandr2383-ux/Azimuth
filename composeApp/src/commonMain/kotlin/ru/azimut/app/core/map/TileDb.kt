package ru.azimut.app.core.map

/**
 * Raster tile databases: RMaps-family .sqlitedb and GeoPackage .gpkg.
 * Both are SQLite files, so the actual readers differ by platform only in the
 * driver (desktop: sqlite-jdbc, Android: framework SQLiteDatabase).
 */
expect object TileDb {
    /** Detects a raster tile database; null when the file is not one. */
    fun readInfo(path: String): TileDbInfo?

    /**
     * Converts the source into an .mbtiles at [destPath].
     * [flipY]: GeoPackage rows are top-left origin and must be flipped to TMS
     * (mbtiles); .sqlitedb is assumed TMS already (flipY=false). Callers may pass
     * flipY=true for .sqlitedb sources that turn out vertically mirrored.
     */
    fun convertToMbtiles(path: String, destPath: String, flipY: Boolean): Boolean
}

data class TileDbInfo(
    val kind: String, // "sqlitedb" | "gpkg"
    val format: String, // png/jpg/webp
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Long,
    val tableName: String,
)
