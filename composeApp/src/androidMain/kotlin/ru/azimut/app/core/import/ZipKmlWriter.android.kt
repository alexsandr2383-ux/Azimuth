package ru.azimut.app.core.import

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual object ZipKmlWriter {
    actual fun zipKml(kml: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }.toByteArray()
}
