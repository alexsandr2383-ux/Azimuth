package ru.azimut.app.core.model

/**
 * Minimal EXIF GPS reader for JPEG files (pure Kotlin — the desktop target has
 * no platform EXIF API). Reads only what waypoint geotagging needs: the GPS
 * latitude/longitude from the GPS IFD. Returns null when the file has no
 * JPEG/EXIF/GPS structure or it is malformed — a photo without a tag is an
 * honest "no coordinates", not an error.
 */
fun extractExifGeo(jpeg: ByteArray): Pair<Double, Double>? {
    // JPEG SOI + APP1 "Exif\0\0" TIFF header.
    if (jpeg.size < 16 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null
    if (jpeg[2] != 0xFF.toByte() || jpeg[3] != 0xE1.toByte()) return null
    if (jpeg[6] != 'E'.code.toByte() || jpeg[7] != 'x'.code.toByte() ||
        jpeg[8] != 'i'.code.toByte() || jpeg[9] != 'f'.code.toByte() ||
        jpeg[10] != 0.toByte() || jpeg[11] != 0.toByte()
    ) {
        return null
    }
    val exif = 12 // TIFF payload start: marker(2) + length(2) + "Exif\0\0"(6)
    if (exif + 8 > jpeg.size) return null
    val le = jpeg[exif] == 'I'.code.toByte() && jpeg[exif + 1] == 'I'.code.toByte()
    val be = jpeg[exif] == 'M'.code.toByte() && jpeg[exif + 1] == 'M'.code.toByte()
    if (!le && !be) return null

    fun u16(pos: Int): Int =
        if (le) (jpeg[pos].toInt() and 0xFF) or ((jpeg[pos + 1].toInt() and 0xFF) shl 8)
        else ((jpeg[pos].toInt() and 0xFF) shl 8) or (jpeg[pos + 1].toInt() and 0xFF)

    fun u32(pos: Int): Int =
        if (le) {
            (jpeg[pos].toInt() and 0xFF) or ((jpeg[pos + 1].toInt() and 0xFF) shl 8) or
                ((jpeg[pos + 2].toInt() and 0xFF) shl 16) or ((jpeg[pos + 3].toInt() and 0xFF) shl 24)
        } else {
            ((jpeg[pos].toInt() and 0xFF) shl 24) or ((jpeg[pos + 1].toInt() and 0xFF) shl 16) or
                ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
        }

    fun tiffPos(entry: Int): Int = exif + u32(entry + 8)

    val ifd0 = exif + u32(exif + 4)
    if (ifd0 < 0 || ifd0 + 2 > jpeg.size) return null
    val entryCount = u16(ifd0)
    var gpsIfd = -1
    var i = ifd0 + 2
    for (e in 0 until entryCount) {
        if (i + 12 > jpeg.size) return null
        if (u16(i) == 0x8825) { // GPSInfo pointer
            gpsIfd = tiffPos(i)
            break
        }
        i += 12
    }
    if (gpsIfd < 0 || gpsIfd + 2 > jpeg.size) return null

    var latRef = ""
    var lonRef = ""
    var latPos = -1
    var lonPos = -1
    val gpsCount = u16(gpsIfd)
    var g = gpsIfd + 2
    for (e in 0 until gpsCount) {
        if (g + 12 > jpeg.size) return null
        when (u16(g)) {
            // Ref tags are ASCII count=2 ("N\0") — the value sits inline in the entry field.
            1 -> latRef = inlineAscii(jpeg, g + 8, 2)
            2 -> latPos = tiffPos(g)
            3 -> lonRef = inlineAscii(jpeg, g + 8, 2)
            4 -> lonPos = tiffPos(g)
        }
        g += 12
    }

    val lat = rationalTriple(jpeg, latPos, le) ?: return null
    val lon = rationalTriple(jpeg, lonPos, le) ?: return null
    val latDeg = lat[0] + lat[1] / 60.0 + lat[2] / 3600.0
    val lonDeg = lon[0] + lon[1] / 60.0 + lon[2] / 3600.0
    if (latDeg < 0.0 || latDeg > 90.0 || lonDeg < 0.0 || lonDeg > 180.0) return null
    val latFinal = if (latRef == "S") -latDeg else latDeg
    val lonFinal = if (lonRef == "W") -lonDeg else lonDeg
    return latFinal to lonFinal
}

/** ASCII value stored inline in the entry's 4-byte field (count ≤ 4). */
private fun inlineAscii(jpeg: ByteArray, pos: Int, count: Int): String {
    val sb = StringBuilder()
    for (k in 0 until count) {
        if (pos + k >= jpeg.size) break
        val c = jpeg[pos + k].toInt() and 0xFF
        if (c == 0) break
        sb.append(c.toChar())
    }
    return sb.toString()
}

/** GPS lat/lon is RATIONAL count=3 (deg/min/sec), 24 bytes at a TIFF offset. */
private fun rationalTriple(jpeg: ByteArray, pos: Int, le: Boolean): DoubleArray? {
    if (pos < 0 || pos + 24 > jpeg.size) return null
    fun u32(base: Int): Int =
        if (le) {
            (jpeg[base].toInt() and 0xFF) or ((jpeg[base + 1].toInt() and 0xFF) shl 8) or
                ((jpeg[base + 2].toInt() and 0xFF) shl 16) or ((jpeg[base + 3].toInt() and 0xFF) shl 24)
        } else {
            ((jpeg[base].toInt() and 0xFF) shl 24) or ((jpeg[base + 1].toInt() and 0xFF) shl 16) or
                ((jpeg[base + 2].toInt() and 0xFF) shl 8) or (jpeg[base + 3].toInt() and 0xFF)
        }
    val out = DoubleArray(3)
    for (k in 0..2) {
        val base = pos + k * 8
        val num = u32(base)
        val den = u32(base + 4)
        out[k] = if (den == 0) 0.0 else num.toDouble() / den
    }
    return out
}
