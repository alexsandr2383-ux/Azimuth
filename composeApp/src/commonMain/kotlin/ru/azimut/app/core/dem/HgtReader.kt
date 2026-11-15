package ru.azimut.app.core.dem

/**
 * SRTM .hgt parser: raw big-endian Int16 grid, 1201x1201 (SRTM1) or 3601x3601 (SRTM3),
 * row 0 = north edge, void = -32768. Byte length is the only reliable size detector:
 * SRTM files have no header.
 */
object HgtReader {
    fun parse(fileName: String, bytes: ByteArray): DemTile? {
        if (bytes.size < 4) return null
        val rows = when (bytes.size) {
            1201 * 1201 * 2 -> 1201
            3601 * 3601 * 2 -> 3601
            else -> return null
        }
        val bounds = DemIndex.tileBounds(fileName.substringBeforeLast('.'))
            ?: return null
        val (lat, lonLeft) = bounds
        // SW-corner label: the file's first row is the NORTH edge (lat+1), descending south.
        val data = ShortArray(rows * rows)
        var i = 0
        var idx = 0
        while (idx < data.size) {
            val hi = bytes[i].toInt() and 0xFF
            val lo = bytes[i + 1].toInt() and 0xFF
            data[idx] = ((hi shl 8) or lo).toShort()
            i += 2
            idx++
        }
        return DemTile(
            latTop = lat + 1.0,
            lonLeft = lonLeft.toDouble(),
            stepDeg = DemIndex.stepForSize(rows),
            rows = rows,
            cols = rows,
            nodata = -32768.0,
            data = data,
        )
    }
}
