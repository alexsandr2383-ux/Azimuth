package ru.azimut.app.core.import

/** Reads a .kml out of a .kmz archive (doc.kml preferred, else the first .kml). */
expect object ZipKml {
    fun extract(bytes: ByteArray): String?
}
